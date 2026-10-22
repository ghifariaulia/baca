package com.baca.app.reader

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.baca.app.ui.AppTheme
import com.baca.app.ui.LocalAppFonts
import com.baca.shared.parser.INLINE_IMAGE
import com.baca.shared.parser.InlineImage
import com.baca.shared.render.TextMetrics
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeTextMetricsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `text measured before the page has a font size gives no lines, not a crash`() {
        // a table cell's widest line is measured unbounded, before the reader knows its size
        var lines: List<TextMetrics.LineSpan>? = null
        rule.setContent {
            AppTheme {
                val serif = LocalAppFonts.current.serif
                val metrics = ComposeTextMetrics(rememberTextMeasurer(), LocalDensity.current) { serif }
                lines = metrics.layoutLines("Short", fontSizePx = 0f, lineHeightPx = 0, maxWidthPx = 1_000_000, fontFamily = "serif", styles = emptyList())
            }
        }
        rule.waitForIdle()
        assertEquals(emptyList(), lines)
    }

    @Test
    fun `short indented paragraph stays on one line`() {
        val text = "To Mrs. Saville, England."
        var lines = emptyList<TextMetrics.LineSpan>()
        rule.setContent {
            AppTheme {
                val serif = LocalAppFonts.current.serif
                val metrics = ComposeTextMetrics(rememberTextMeasurer(), LocalDensity.current) { serif }
                lines = metrics.layoutLines(
                    text, fontSizePx = 19f, lineHeightPx = 32, maxWidthPx = 388, fontFamily = "serif",
                    styles = listOf(TextMetrics.StyleRange(0, text.length, bold = false, italic = true)),
                    firstLineIndentPx = 19,
                )
            }
        }
        rule.waitForIdle()
        assertEquals(listOf(0 to text.length), lines.map { it.start to it.endExclusive })
    }

    @Test
    fun `a line takes the words that fit and the first part of the one that doesn't, hyphenated`() {
        val text = "the influential family"
        var lines = emptyList<String>()
        rule.setContent {
            AppTheme {
                val serif = LocalAppFonts.current.serif
                val metrics = ComposeTextMetrics(rememberTextMeasurer(), LocalDensity.current, language = "en") { serif }
                fun layout(t: String, width: Int) = metrics.layoutLines(t, fontSizePx = 20f, lineHeightPx = 34, maxWidthPx = width, fontFamily = "serif")
                // just room for "the influen" and its hyphen
                val width = layout("the influen-", 10_000).single().rightPx
                lines = layout(text, width).map { text.substring(it.start, it.endExclusive) }
            }
        }
        rule.waitForIdle()
        assertEquals(listOf("the influen", "tial family"), lines)
    }

    @Test
    fun `text in a language of its own is hyphenated by it, not the book's`() {
        val text = "the influential family"
        var english = emptyList<String>()
        var japanese = emptyList<String>()
        rule.setContent {
            AppTheme {
                val serif = LocalAppFonts.current.serif
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                // a Japanese book with an English passage, and the other way round
                fun lines(book: String, own: String): List<String> {
                    val metrics = ComposeTextMetrics(measurer, density, language = book) { serif }
                    val width = metrics.layoutLines("the influen-", 20f, 34, 10_000, "serif").single().rightPx
                    return metrics.layoutLines(text, 20f, 34, width, "serif", language = own).map { text.substring(it.start, it.endExclusive) }
                }
                english = lines(book = "ja", own = "en")
                japanese = lines(book = "en", own = "ja")
            }
        }
        rule.waitForIdle()
        assertEquals(listOf("the influen", "tial family"), english)
        assertEquals(listOf("the", "influential", "family"), japanese)
    }

    @Test
    fun `a no-break space stretches with the other spaces`() {
        var stretchedAt = emptyList<Int>()
        rule.setContent {
            AppTheme {
                val style = TextStyle(fontFamily = LocalAppFonts.current.serif, fontSize = 20.sp)
                val line = justified(AnnotatedString("Mr.\u00A0Smith came"), style, 400, rememberTextMeasurer(), LocalDensity.current, emptyList())
                stretchedAt = line.spanStyles.map { it.start }
            }
        }
        rule.waitForIdle()
        assertEquals(listOf(3, 9), stretchedAt)
    }

    @Test
    fun `a short line before a break is stretched to the column edge however far`() {
        var stretched = 0
        rule.setContent {
            AppTheme {
                val measurer = rememberTextMeasurer()
                val style = TextStyle(fontFamily = LocalAppFonts.current.serif, fontSize = 20.sp)
                val line = justified(AnnotatedString("herit the"), style, 400, measurer, LocalDensity.current, emptyList())
                stretched = measurer.measure(line, style, softWrap = false).size.width
            }
        }
        rule.waitForIdle()
        // within the slack left so rounding never wraps it
        assertTrue(stretched in 395..400, "stretched to $stretched")
    }

    @Test
    fun `a soft hyphen takes no room unless a line breaks at it`() {
        var plain = 0
        var marked = 0
        var broken = emptyList<String>()
        rule.setContent {
            AppTheme {
                val serif = LocalAppFonts.current.serif
                val metrics = ComposeTextMetrics(rememberTextMeasurer(), LocalDensity.current, language = "en") { serif }
                fun layout(t: String, width: Int) = metrics.layoutLines(t, fontSizePx = 20f, lineHeightPx = 34, maxWidthPx = width, fontFamily = "serif")
                plain = layout("connected", 10_000).single().rightPx
                marked = layout("con\u00ADnected", 10_000).single().rightPx
                // room for "nected" but not the whole word: the line breaks at the mark
                val text = "con\u00ADnected"
                broken = layout(text, layout("nected", 10_000).single().rightPx).map { text.substring(it.start, it.endExclusive) }
            }
        }
        rule.waitForIdle()
        assertEquals(plain, marked)
        assertEquals(listOf("con\u00AD", "nected"), broken)
    }

    @Test
    fun `italic text is set in the font's own italic, not a slanted roman`() {
        val text = "minimum information"
        var roman = 0
        var italic = 0
        rule.setContent {
            AppTheme {
                val serif = LocalAppFonts.current.serif
                val metrics = ComposeTextMetrics(rememberTextMeasurer(), LocalDensity.current) { serif }
                fun width(italics: Boolean) = metrics.layoutLines(
                    text, fontSizePx = 40f, lineHeightPx = 60, maxWidthPx = 10_000, fontFamily = "serif",
                    styles = listOf(TextMetrics.StyleRange(0, text.length, bold = false, italic = italics)),
                ).single().rightPx
                roman = width(false)
                italic = width(true)
            }
        }
        rule.waitForIdle()
        // a slanted roman keeps the roman's widths; Literata Italic's letters are narrower
        assertTrue(italic < roman - 10, "italic $italic, roman $roman")
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
                        text, fontSizePx = 20f, lineHeightPx = 34, maxWidthPx = 1000, fontFamily = "serif",
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

    @Test
    fun `a book's sans-serif and monospace are the system's, as a browser draws them`() {
        rule.setContent {
            AppTheme {
                val fonts = LocalAppFonts.current
                assertEquals(androidx.compose.ui.text.font.FontFamily.SansSerif, readerFontFamily(faceFontName(com.baca.shared.parser.BookFace.SANS), fonts))
                assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, readerFontFamily(faceFontName(com.baca.shared.parser.BookFace.MONO), fonts))
                // the reader's own Sans setting keeps its font
                assertEquals(fonts.sans, readerFontFamily("sans", fonts))
            }
        }
        rule.waitForIdle()
    }
}
