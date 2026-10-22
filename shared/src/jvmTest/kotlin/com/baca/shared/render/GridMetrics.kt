package com.baca.shared.render

import com.baca.shared.parser.BookFace

/**
 * Deterministic grid metrics for tests: every char is [charW] px wide, lines
 * wrap every (maxWidthPx / charW) chars, explicit newlines break.
 */
class GridMetrics(private val charW: Int = 10) : TextMetrics {
    override fun layoutLines(
        text: String,
        fontSizePx: Float,
        lineHeightPx: Int,
        maxWidthPx: Int,
        fontFamily: String?,
        styles: List<TextMetrics.StyleRange>,
        firstLineIndentPx: Int,
        hyphenate: Boolean,
        language: String?,
        rtl: Boolean,
    ): List<TextMetrics.LineSpan> {
        val fullCols = (maxWidthPx / charW).coerceAtLeast(1)
        val lines = mutableListOf<TextMetrics.LineSpan>()
        var start = 0
        while (start < text.length) {
            val cols = if (start == 0) (fullCols - firstLineIndentPx / charW).coerceAtLeast(1) else fullCols
            var end = (start + cols).coerceAtMost(text.length)
            val nl = text.indexOf('\n', start)
            if (nl in start until end) end = nl + 1
            val indent = if (start == 0) firstLineIndentPx / charW * charW else 0
            val visible = text.substring(start, end).trimEnd('\n').length
            lines.add(TextMetrics.LineSpan(start, end, indent + visible * charW))
            start = end
        }
        return lines
    }

    // every grid font: ascent 0.8 and descent 0.2 of its size
    override fun verticalMetrics(fontFamily: String?, face: BookFace?, family: String?): TextMetrics.VerticalMetrics = TextMetrics.VerticalMetrics(0.8f, 0.2f)
}
