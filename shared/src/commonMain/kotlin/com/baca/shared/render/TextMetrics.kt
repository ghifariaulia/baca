package com.baca.shared.render

import com.baca.shared.parser.BookFace
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
        /** drawn in the reader's sans or monospace font; null is the reading font */
        val face: BookFace? = null,
        /** an image in the line instead of the range's one char */
        val image: InlineImage? = null,
        /** raised (positive) or lowered, in em */
        val baseline: Float = 0f,
        /** extra space after each letter, in em */
        val letterSpacing: Float = 0f,
        /** horizontal space in px in place of the range's one char (an inline element's padding) */
        val spacerPx: Int? = null,
        /** extra space at each space char, in em (`word-spacing`) */
        val wordSpacing: Float = 0f,
        /** a font the book ships, by family ([com.baca.shared.parser.FormattedSpan.family]); over [face] */
        val family: String? = null,
    )

    /** [fontFamily]: "serif" | "sans" | "mono" | "libron" | null = system default. */
    fun layoutLines(
        text: String,
        fontSizePx: Float,
        lineHeightPx: Int,
        maxWidthPx: Int,
        fontFamily: String? = null,
        styles: List<StyleRange> = emptyList(),
        /** first line starts this far in (paragraph indent); the renderer pads the same */
        firstLineIndentPx: Int = 0,
        /** false where the book turns hyphenation off */
        hyphenate: Boolean = true,
        /** the text's own language (`lang`); null is the book's */
        language: String? = null,
        /** right-to-left text: its glyphs run from the right, so a line's width is the sum of its characters' */
        rtl: Boolean = false,
    ): List<LineSpan>

    /** A font's [ascent] and [descent] (both positive), as fractions of its size. */
    data class VerticalMetrics(val ascent: Float, val descent: Float)

    /**
     * The ascent and descent of the reader's font [fontFamily], or of a book's own [face] or
     * shipped [family], as the platform's text renderer has them: what a browser sizes each
     * inline box by.
     */
    fun verticalMetrics(fontFamily: String?, face: BookFace?, family: String? = null): VerticalMetrics

    /** [rightPx]: where the line's text ends, from the left of the measured width (indent included). */
    data class LineSpan(val start: Int, val endExclusive: Int, val rightPx: Int = 0)
}
