package com.baca.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookCssTest {

    private fun blocks(css: String, body: String): List<Block.Text> =
        parseXhtml(
            """<html><head><link rel="stylesheet" href="s.css"/></head><body>$body</body></html>""",
            loadCss = { if (it == "s.css") css else null },
        ).filterIsInstance<Block.Text>()

    @Test
    fun `alignment, no-indent and small caps come from classes`() {
        val b = blocks(
            ".centered { text-align: center } .first { text-indent: 0 } span.sc { font-variant: small-caps }",
            """<p class="centered">Title</p><p class="first">One <span class="sc">tokyo</span></p><p>Two</p>""",
        )
        assertEquals(TextAlign.CENTER, b[0].align)
        assertFalse(b[1].indent)
        assertEquals(listOf(false, true), b[1].spans.map { it.smallCaps })
        assertTrue(b[2].indent)
        assertEquals(TextAlign.START, b[2].align)
    }

    @Test
    fun `book-wide paragraph rules leave indent and alignment to the reader`() {
        val b = blocks("p { text-indent: 0; text-align: center } h1 { text-align: center }", """<h1>Title</h1><p>Body</p>""")
        assertEquals(TextAlign.CENTER, b[0].align)
        assertTrue(b[1].indent)
        assertEquals(TextAlign.START, b[1].align)
    }

    @Test
    fun `left and justify leave the reader's justification in charge`() {
        val b = blocks(".l { text-align: left } .j { text-align: justify }", """<p class="l">One</p><p class="j">Two</p>""")
        assertEquals(listOf(TextAlign.START, TextAlign.START), b.map { it.align })
    }

    @Test
    fun `float right becomes its own right-aligned line`() {
        val b = blocks(
            ".place { float: right }",
            """<p class="x"><i>28th</i><i> </i><i>February</i> <span class="place">meguro, tokyo</span></p>""",
        )
        assertEquals(listOf("28th February", "meguro, tokyo"), b.map { it.text.trim() })
        assertEquals(TextAlign.END, b[1].align)
        assertFalse(b[1].indent)
    }

    @Test
    fun `display none hides an element and its children`() {
        val b = blocks(".hide { display: none }", """<p>Seen <span class="hide">not <b>this</b></span>too</p><p class="hide">gone</p>""")
        assertEquals(listOf("Seen too"), b.map { it.text })
    }

    @Test
    fun `bold and italic follow css, including turning them off`() {
        val b = blocks(".plain { font-weight: normal; font-style: normal } .heavy { font-weight: 700 }",
            """<p><b>Bold <span class="plain">plain</span></b> <span class="heavy">heavy</span></p>""")
        val spans = b.single().spans
        assertEquals(listOf("Bold " to true, "plain " to false, "heavy" to true), spans.map { it.text to it.bold })
    }

    @Test
    fun `relative sizes scale blocks within limits, absolute sizes are ignored`() {
        val b = blocks(".label { font-size: 1.1em } .huge { font-size: 5em } .px { font-size: 30px }",
            """<p class="label">SON</p><h1 class="huge">Big</h1><p class="px">Px</p><h2>Plain heading</h2>""")
        assertEquals(1.1f, b[0].scale)
        assertEquals(2f, b[1].scale)
        assertNull(b[2].scale)
        assertNull(b[3].scale)
    }

    @Test
    fun `more specific rules and inline styles win, complex selectors are skipped`() {
        val b = blocks(
            ".r { text-align: right } p.c { text-align: center } div p { text-align: right } .c:first-child { display: none }",
            """<p class="r c">A</p><p class="c" style="text-align: right">B</p><p class="r">C</p>""",
        )
        assertEquals(listOf(TextAlign.CENTER, TextAlign.END, TextAlign.END), b.map { it.align })
    }

    @Test
    fun `style elements and at-rules are handled`() {
        val b = parseXhtml(
            """<html><head><style>@media print { p { display: none } } @font-face { font-family: x } .c { text-align: center }</style></head>""" +
                """<body><p class="c">Shown</p></body></html>""",
        ).filterIsInstance<Block.Text>()
        assertEquals(TextAlign.CENTER, b.single().align)
    }
}
