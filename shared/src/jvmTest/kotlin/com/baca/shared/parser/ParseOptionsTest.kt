package com.baca.shared.parser

import com.baca.shared.domain.DisplaySettings
import com.baca.shared.domain.decodeDisplay
import com.baca.shared.domain.encode
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseOptionsTest {

    private fun page(body: String, head: String = "") = "<html><head>$head</head><body>$body</body></html>"

    @Test
    fun `the reader's direction sets the root's, over the book's language but under its own dir`() {
        val html = page("""<p>plain</p><p dir="ltr">own</p>""")
        assertEquals(listOf(false, false), parseXhtml(html).map { (it as Block.Text).rtl })
        assertEquals(listOf(true, false), parseXhtml(html, options = ParseOptions(rtl = true)).map { (it as Block.Text).rtl })
        // forced left to right in a book whose language reads right to left
        assertEquals(listOf(false, false), parseXhtml(html, defaultRtl = true, options = ParseOptions(rtl = false)).map { (it as Block.Text).rtl })
        // a book's own dir on the root gives way to the reader's
        val rooted = "<html dir=\"rtl\"><body><p>x</p></body></html>"
        assertEquals(false, (parseXhtml(rooted, options = ParseOptions(rtl = false)).single() as Block.Text).rtl)
    }

    @Test
    fun `the reader's CSS comes after the book's and wins where the weight is equal`() {
        val html = page("<p>text</p>", head = "<style>p { text-align: center }</style>")
        assertEquals(TextAlign.CENTER, (parseXhtml(html).single() as Block.Text).align)
        val own = parseXhtml(html, options = ParseOptions(userCss = "p { text-align: right }")).single() as Block.Text
        assertEquals(TextAlign.END, own.align)
    }

    @Test
    fun `replacements apply to the book's text, in order, plain text`() {
        val options = ParseOptions(replacements = listOf(TextReplacement("colour", "color"), TextReplacement("color", "hue"), TextReplacement("", "x")))
        assertEquals(listOf("The hue of hue."), parseXhtml(page("<p>The colour of color.</p>"), options = options).map { it.text })
        // a text file too
        assertEquals("a hue", txtEpub("t.txt", "a colour", options).chapters.single().blocks.single().text)
    }

    @Test
    fun `a book is read again when the reader's rules change, or the scheme in a book that asks for it`() {
        val book = ParsedEpub("t", "", null, emptyList(), options = ParseOptions(), schemeAware = false)
        val aware = book.copy(schemeAware = true)
        assertEquals(false, book.options.needsReread(book, ParseOptions(darkScheme = true)))
        assertEquals(true, aware.options.needsReread(aware, ParseOptions(darkScheme = true)))
        assertEquals(true, book.options.needsReread(book, ParseOptions(userCss = "p {}")))
        assertEquals(true, book.options.needsReread(book, ParseOptions(rtl = true)))
        assertEquals(true, book.options.needsReread(book, ParseOptions(replacements = listOf(TextReplacement("a", "b")))))
        assertEquals(false, book.options.needsReread(book, ParseOptions()))
    }

    @Test
    fun `the settings save and read back, older ones taking the defaults`() {
        val display = DisplaySettings(direction = "rtl", userCss = "p { margin: 0 }", replacements = listOf(TextReplacement("a", "b")), columns = 2, flow = "scrolled")
        assertEquals(display, decodeDisplay(display.encode()))
        assertEquals(DisplaySettings(), decodeDisplay("""{"fontSizePercent":120}"""))
        assertEquals(ParseOptions(darkScheme = true, rtl = true, userCss = "p { margin: 0 }", replacements = listOf(TextReplacement("a", "b"))), display.parseOptions(darkScheme = true))
    }
}
