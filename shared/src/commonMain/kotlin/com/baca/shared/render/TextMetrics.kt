package com.baca.shared.render

import com.baca.shared.parser.InlineImage

/**
 * Text measurement abstraction for the layout engine. Implementations:
 * - ComposeTextMetrics (composeApp): wraps Compose TextMeasurer for real rendering
 * - fake metrics in tests: deterministic grid metrics, no Compose needed
 *
 * A layout is a list of line spans: [start, endExclusive) char offsets into the
 * measured text, honoring explicit newlines and the width constraint.
 *
 * The measurement MUST use the exact style the renderer draws with (font
 * family, per-span bold/italic) — otherwise the measured line breaks differ
 * from the rendered ones and pages drift off their reserved height.
 */
interface TextMetrics {
    /** One styled span range inside the block text. */
    data class StyleRange(
        val start: Int,
        val endExclusive: Int,
        val bold: Boolean,
        val italic: Boolean,
        val smallCaps: Boolean = false,
        /** font size relative to the block's */
        val scale: Float = 1f,
        /** drawn in the reader's sans font */
        val sans: Boolean = false,
        /** an image in the line instead of the range's one char */
        val image: InlineImage? = null,
    )

    /** [fontFamily]: "serif" | "sans" | "mono" | "libron" | null = system default. */
    fun layoutLines(
        text: String,
        fontSizePx: Int,
        lineHeightPx: Int,
        maxWidthPx: Int,
        fontFamily: String? = null,
        styles: List<StyleRange> = emptyList(),
        /** first line starts this far in (paragraph indent); the renderer pads the same */
        firstLineIndentPx: Int = 0,
    ): List<LineSpan>

    /** [rightPx]: where the line's text ends, from the left of the measured width (indent included). */
    data class LineSpan(val start: Int, val endExclusive: Int, val rightPx: Int = 0)
}
