package com.baca.app.reader

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import com.baca.app.ui.AppTheme
import com.baca.app.ui.LocalAppFonts
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
        assertEquals(listOf(TextMetrics.LineSpan(0, text.length)), lines)
    }
}
