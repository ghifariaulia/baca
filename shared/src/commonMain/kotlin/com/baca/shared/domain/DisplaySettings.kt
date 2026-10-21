package com.baca.shared.domain

import com.baca.shared.domain.reader.PositionStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Display settings the reader screen exposes (docs/TECH.md 10.4). Never synced. */
@Serializable
data class DisplaySettings(
    val fontSizeSp: Int = 19,
    val font: String = "serif",      // serif (Literata) | sans (DM Sans) | libron | mono
    val theme: String = "paper",     // paper | white | dim | black
    val lineSpacing: String = "normal", // compact | normal | relaxed
    val margins: String = "medium",  // narrow | medium | wide
    /** phones: tapping the page's left/right edge turns the page (swipes always do) */
    val tapToTurn: Boolean = true,
    /** phones: the status and navigation bars hide with the reader's bars (focus mode) */
    val fullScreen: Boolean = true,
) {
    init {
        require(font in FONTS) { "unknown font: $font" }
        require(theme in THEMES) { "unknown theme: $theme" }
        require(lineSpacing in LINE_SPACINGS) { "unknown lineSpacing: $lineSpacing" }
        require(margins in MARGINS) { "unknown margins: $margins" }
    }

    fun lineRatio(): Float = LINE_SPACING_RATIOS.getValue(lineSpacing)

    companion object {
        val FONTS = setOf("serif", "sans", "mono", "libron")
        val THEMES = setOf("paper", "white", "dim", "black")
        val LINE_SPACINGS = setOf("compact", "normal", "relaxed")
        val MARGINS = setOf("narrow", "medium", "wide")

        val LINE_SPACING_RATIOS = mapOf("compact" to 1.5f, "normal" to 1.7f, "relaxed" to 1.9f)
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

fun decodeDisplay(text: String): DisplaySettings = displayJson.decodeFromString(text)
