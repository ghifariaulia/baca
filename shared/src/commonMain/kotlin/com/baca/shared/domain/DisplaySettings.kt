package com.baca.shared.domain

import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.parser.ParseOptions
import com.baca.shared.parser.TextReplacement
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.roundToInt

/**
 * Display settings the reader screen exposes (docs/TECH.md 10.4), in LibreraX's steps.
 * Never synced.
 */
@Serializable
data class DisplaySettings(
    /** the running text's size, in percent of a browser's 16px */
    val fontSizePercent: Int = 120,
    val font: String = "serif",      // serif (Literata) | sans (DM Sans) | libron | mono
    val theme: String = "paper",     // white | paper | ink | grey | dim | dusk | black
    /** line height over the font size, in tenths */
    val lineSpacingTenths: Int = 17,
    /** the page's side margins, in percent of its width */
    val paddingPercent: Int = 6,
    /** the band above the text, 4dp a percent; null follows the side margins, as LibreraX does */
    val topPercent: Int? = null,
    /** the band below the text (over the footer's strip), 4dp a percent; null follows the side margins */
    val bottomPercent: Int? = null,
    /** phones: tapping the page's left/right edge turns the page (swipes always do) */
    val tapToTurn: Boolean = true,
    /** phones: the status and navigation bars hide with the reader's bars (focus mode) */
    val fullScreen: Boolean = true,
    /** the page's root direction: the book's own (auto), or forced left to right or right to left */
    val direction: String = "auto",
    /** the reader's own CSS, over the book's */
    val userCss: String = "",
    /** find and replace in the book's text */
    val replacements: List<TextReplacement> = emptyList(),
    /** columns across a wide page (1 or 2) */
    val columns: Int = 1,
    /** `paged`, or `scrolled`: the book as one continuous column to scroll */
    val flow: String = "paged",
) {
    init {
        require(font in FONTS) { "unknown font: $font" }
        require(theme in THEMES) { "unknown theme: $theme" }
        require(fontSizePercent in FONT_SIZE_PERCENTS) { "font size out of range: $fontSizePercent" }
        require(lineSpacingTenths in LINE_SPACING_TENTHS) { "line spacing out of range: $lineSpacingTenths" }
        require(paddingPercent in PADDING_PERCENTS) { "padding out of range: $paddingPercent" }
        require(topPercent == null || topPercent in PADDING_PERCENTS) { "top margin out of range: $topPercent" }
        require(bottomPercent == null || bottomPercent in PADDING_PERCENTS) { "bottom margin out of range: $bottomPercent" }
        require(direction in DIRECTIONS) { "unknown direction: $direction" }
        require(columns in 1..2) { "columns out of range: $columns" }
        require(flow in FLOWS) { "unknown flow: $flow" }
    }

    /** What these settings change in how a book is read, for a page of [darkScheme]'s kind. */
    fun parseOptions(darkScheme: Boolean) = ParseOptions(
        darkScheme = darkScheme,
        rtl = when (direction) { "rtl" -> true; "ltr" -> false; else -> null },
        userCss = userCss,
        replacements = replacements.filter { it.find.isNotEmpty() },
    )

    val fontSizeSp: Float get() = BROWSER_FONT_SP * fontSizePercent / 100f

    fun lineRatio(): Float = lineSpacingTenths / 10f

    /** One step up (+1) or down (-1) of each setting, held to its range. */
    fun stepFontSize(by: Int) = copy(fontSizePercent = (fontSizePercent + by * FONT_SIZE_STEP).coerceIn(FONT_SIZE_PERCENTS))
    fun stepLineSpacing(by: Int) = copy(lineSpacingTenths = (lineSpacingTenths + by).coerceIn(LINE_SPACING_TENTHS))
    fun stepPadding(by: Int) = copy(paddingPercent = (paddingPercent + by).coerceIn(PADDING_PERCENTS))
    fun stepTop(by: Int) = copy(topPercent = (topMarginPercent + by).coerceIn(PADDING_PERCENTS))
    fun stepBottom(by: Int) = copy(bottomPercent = (bottomMarginPercent + by).coerceIn(PADDING_PERCENTS))

    /** The top and bottom margins in force: the one chosen, else the side margins'. */
    val topMarginPercent: Int get() = topPercent ?: paddingPercent
    val bottomMarginPercent: Int get() = bottomPercent ?: paddingPercent

    companion object {
        val FONTS = setOf("serif", "sans", "mono", "libron")
        val DIRECTIONS = setOf("auto", "ltr", "rtl")
        val FLOWS = setOf("paged", "scrolled")
        val THEMES = setOf("white", "paper", "ink", "grey", "dim", "dusk", "black")
        val FONT_SIZE_PERCENTS = 50..250
        val LINE_SPACING_TENTHS = 10..25
        val PADDING_PERCENTS = 0..25
        const val FONT_SIZE_STEP = 5
        const val BROWSER_FONT_SP = 16f
    }
}

private val displayJson = Json { ignoreUnknownKeys = true }

const val KEY_DISPLAY = "reader_display"

/** Display settings live in the (global) local bucket of the position store. */
fun PositionStore.loadDisplay(): DisplaySettings =
    getLocal(KEY_DISPLAY)?.let { runCatching { decodeDisplay(it) }.getOrNull() } ?: DisplaySettings()

fun PositionStore.saveDisplay(display: DisplaySettings) {
    putLocal(KEY_DISPLAY, display.encode())
}

fun DisplaySettings.encode(): String = displayJson.encodeToString(this)

fun decodeDisplay(text: String): DisplaySettings {
    val saved = displayJson.parseToJsonElement(text).jsonObject
    return displayJson.decodeFromJsonElement(DisplaySettings.serializer(), JsonObject(saved + fromNamedSteps(saved)))
}

/**
 * Settings saved before LibreraX's steps: a size in sp and named line spacings and
 * margins, each taken to its nearest step. Nothing for settings saved since.
 */
private fun fromNamedSteps(saved: JsonObject): Map<String, JsonPrimitive> = buildMap {
    if ("fontSizePercent" !in saved) (saved["fontSizeSp"] as? JsonPrimitive)?.intOrNull?.let { sp ->
        val step = DisplaySettings.FONT_SIZE_STEP
        put("fontSizePercent", JsonPrimitive((sp * 100 / DisplaySettings.BROWSER_FONT_SP / step).roundToInt() * step))
    }
    if ("lineSpacingTenths" !in saved) {
        when ((saved["lineSpacing"] as? JsonPrimitive)?.contentOrNull) {
            "compact" -> 15
            "normal" -> 17
            "relaxed" -> 19
            else -> null
        }?.let { put("lineSpacingTenths", JsonPrimitive(it)) }
    }
    if ("paddingPercent" !in saved) {
        when ((saved["margins"] as? JsonPrimitive)?.contentOrNull) {
            "narrow" -> 3
            "medium" -> 6
            "wide" -> 9
            else -> null
        }?.let { put("paddingPercent", JsonPrimitive(it)) }
    }
}
