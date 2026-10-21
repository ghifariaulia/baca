package com.baca.app.reader

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.baca.shared.render.TextMetrics

/**
 * Real text measurement for the layout engine. [fontSizePx]/[lineHeightPx] are
 * pixels; TextMeasurer works in text units, so the adapter converts with the
 * [Density] passed by the caller (from LocalDensity at the point of use).
 *
 * Measures with the same font family and bold/italic spans the renderer draws
 * with, so measured line breaks equal rendered line breaks (the offsets
 * contract, TECH 5.2/5.3).
 */
class ComposeTextMetrics(
    private val measurer: TextMeasurer,
    private val density: Density,
    /** display font name → family, from the app's bundled fonts. */
    private val families: (String?) -> FontFamily?,
) : TextMetrics {

    override fun layoutLines(
        text: String,
        fontSizePx: Int,
        lineHeightPx: Int,
        maxWidthPx: Int,
        fontFamily: String?,
        styles: List<TextMetrics.StyleRange>,
        firstLineIndentPx: Int,
    ): List<TextMetrics.LineSpan> {
        if (text.isEmpty() || maxWidthPx <= 0) return emptyList()
        val style = with(density) {
            TextStyle(
                fontSize = fontSizePx.toFloat().toSp(),
                lineHeight = lineHeightPx.toFloat().toSp(),
                fontFamily = families(fontFamily),
                textIndent = TextIndent(firstLine = firstLineIndentPx.toFloat().toSp()),
            )
        }
        val annotated = if (styles.isEmpty()) {
            androidx.compose.ui.text.AnnotatedString(text)
        } else {
            buildAnnotatedString {
                append(text)
                styles.forEach { range ->
                    addStyle(
                        SpanStyle(
                            fontWeight = if (range.bold) FontWeight.Bold else null,
                            fontStyle = if (range.italic) FontStyle.Italic else null,
                            fontFeatureSettings = if (range.smallCaps) SMALL_CAPS else null,
                        ),
                        range.start,
                        range.endExclusive,
                    )
                }
            }
        }
        val layout = measurer.measure(
            annotated,
            style,
            // fixed, not max: with a max-only width Compose sizes short text to
            // its natural width without the first-line indent, then wraps it
            constraints = Constraints.fixedWidth(maxWidthPx),
        )
        return (0 until layout.lineCount).map { i ->
            TextMetrics.LineSpan(
                start = layout.getLineStart(i),
                endExclusive = layout.getLineEnd(i, visibleEnd = true),
            )
        }
    }
}

/** OpenType small caps; fonts without them draw normal lowercase. Measured and drawn the same way. */
internal const val SMALL_CAPS = "smcp"
