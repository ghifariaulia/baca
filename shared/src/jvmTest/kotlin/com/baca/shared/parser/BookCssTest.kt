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
            ".centered { text-align: center } .first { text-indent: 0 !important } span.sc { font-variant: small-caps }",
            """<p class="centered">Title</p><p class="first">One <span class="sc">tokyo</span></p><p>Two</p>""",
        )
        assertEquals(TextAlign.CENTER, b[0].align)
        assertFalse(b[1].indent)
        assertEquals(listOf(false, true), b[1].spans.map { it.smallCaps })
        assertTrue(b[2].indent)
        assertEquals(TextAlign.START, b[2].align)
    }

    @Test
    fun `like LibreraX, the reader's indent wins unless the book's rule is important`() {
        val b = blocks(".firstpara { text-indent: 0 }", """<p class="firstpara">One</p>""")
        assertTrue(b.single().indent)
    }

    @Test
    fun `headings are centred unless the book aligns them`() {
        val b = blocks("h2.left { text-align: left }", """<h1>Centred</h1><h2 class="left">Left</h2>""")
        assertEquals(listOf(TextAlign.CENTER, TextAlign.START), b.map { it.align })
    }

    @Test
    fun `links the book colours without underline draw as text`() {
        val b = blocks(
            "div.title-block a { text-decoration: none; color: #000000 !important } a { color: inherit; text-decoration: none }",
            """<div class="title-block"><h1><a href="toc.xhtml">Hallway</a></h1></div><p><a href="n.xhtml">note</a></p>""",
        )
        assertEquals(listOf(true, false), b.map { it.spans.single().plainLink })
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

    @Test
    fun `descendant and child selectors match through ancestors`() {
        // Strange Buildings: the chapter title block's lines are centred through their section's class
        val b = blocks(
            ".Chapter-rw .title-block-rw .title-num-rw { text-align: center } .box > p { text-align: right } body p { text-indent: 0 }",
            """<section class="Chapter-rw"><div class="title-block-rw"><p class="title-num-rw">FILE 1</p></div></section>""" +
                """<div class="title-block-rw"><p class="title-num-rw">Elsewhere</p></div>""" +
                """<div class="box"><p>Direct</p><div><p>Nested</p></div></div>""",
        )
        assertEquals(listOf(TextAlign.CENTER, TextAlign.START, TextAlign.END, TextAlign.START), b.map { it.align })
        // the reader's indent is important, so a book-wide `body p` rule can't remove it
        assertTrue(b[1].indent)
    }

    @Test
    fun `negative text-indent makes a hanging paragraph`() {
        val b = blocks(
            "div.hanging-rw p { text-indent: -1.3em !important; margin-left: 1.3em !important }",
            """<div class="hanging-rw"><p><b>NEGISHI:</b> There was something odd.</p></div><p>Narration.</p>""",
        )
        assertTrue(b[0].hanging)
        assertFalse(b[0].indent)
        assertFalse(b[1].hanging)
        assertTrue(b[1].indent)
    }
}
