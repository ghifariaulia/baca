package com.baca.app.reader

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import com.baca.app.ui.AppTheme
import com.baca.app.ui.LocalAppFonts
import com.baca.shared.parser.INLINE_IMAGE
import com.baca.shared.parser.InlineImage
import com.baca.shared.render.TextMetrics
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ComposeTextMetricsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `short indented paragraph stays on one line`() {
        val text = "To Mrs. Saville, England."
        var lines = emptyList<TextMetrics.LineSpan>()
        rule.setContent {
            AppTheme {
                val serif = LocalAppFonts.current.serif
                val metrics = ComposeTextMetrics(rememberTextMeasurer(), LocalDensity.current) { serif }
                lines = metrics.layoutLines(
                    text, fontSizePx = 19, lineHeightPx = 32, maxWidthPx = 388, fontFamily = "serif",
                    styles = listOf(TextMetrics.StyleRange(0, text.length, bold = false, italic = true)),
                    firstLineIndentPx = 19,
                )
            }
        }
        rule.waitForIdle()
        assertEquals(listOf(0 to text.length), lines.map { it.start to it.endExclusive })
    }

    @Test
    fun `an inline image takes its picture's width in the line`() {
        val text = "This is $INLINE_IMAGE [name]."
        val at = text.indexOf(INLINE_IMAGE)
        var plain = emptyList<TextMetrics.LineSpan>()
        var withImage = emptyList<TextMetrics.LineSpan>()
        rule.setContent {
            AppTheme {
                val serif = LocalAppFonts.current.serif
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                fun measure(aspect: Float) = ComposeTextMetrics(measurer, density, imageAspect = { aspect }) { serif }
                    .layoutLines(
                        text, fontSizePx = 20, lineHeightPx = 34, maxWidthPx = 1000, fontFamily = "serif",
                        styles = listOf(TextMetrics.StyleRange(at, at + 1, false, false, image = InlineImage("x.jpg", 1f))),
                    )
                plain = measure(1f)
                withImage = measure(6f)
            }
        }
        rule.waitForIdle()
        // five more em of picture: 100px at 20px type
        assertEquals(100, withImage.single().rightPx - plain.single().rightPx)
    }
}
