package com.baca.shared.domain

import com.baca.shared.domain.reader.PositionStore
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
    val theme: String = "paper",     // white | paper | grey | dim | dusk | black
    /** line height over the font size, in tenths */
    val lineSpacingTenths: Int = 17,
    /** the page's side margins, in percent of its width; the band above the text is 4dp a percent */
    val paddingPercent: Int = 6,
    /** phones: tapping the page's left/right edge turns the page (swipes always do) */
    val tapToTurn: Boolean = true,
    /** phones: the status and navigation bars hide with the reader's bars (focus mode) */
    val fullScreen: Boolean = true,
) {
    init {
        require(font in FONTS) { "unknown font: $font" }
        require(theme in THEMES) { "unknown theme: $theme" }
        require(fontSizePercent in FONT_SIZE_PERCENTS) { "font size out of range: $fontSizePercent" }
        require(lineSpacingTenths in LINE_SPACING_TENTHS) { "line spacing out of range: $lineSpacingTenths" }
        require(paddingPercent in PADDING_PERCENTS) { "padding out of range: $paddingPercent" }
    }

    val fontSizeSp: Float get() = BROWSER_FONT_SP * fontSizePercent / 100f

    fun lineRatio(): Float = lineSpacingTenths / 10f

    /** One step up (+1) or down (-1) of each setting, held to its range. */
    fun stepFontSize(by: Int) = copy(fontSizePercent = (fontSizePercent + by * FONT_SIZE_STEP).coerceIn(FONT_SIZE_PERCENTS))
    fun stepLineSpacing(by: Int) = copy(lineSpacingTenths = (lineSpacingTenths + by).coerceIn(LINE_SPACING_TENTHS))
    fun stepPadding(by: Int) = copy(paddingPercent = (paddingPercent + by).coerceIn(PADDING_PERCENTS))

    companion object {
        val FONTS = setOf("serif", "sans", "mono", "libron")
        val THEMES = setOf("white", "paper", "grey", "dim", "dusk", "black")
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
