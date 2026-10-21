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

    // What LibreraX shows for Strange Buildings: the book's own CSS decides, as in a browser.

    @Test
    fun `the book's indent rules win, including next-sibling selectors`() {
        val b = blocks(
            "p { text-indent: 0 } p+p { text-indent: 1.3em } div+p { text-indent: 0 }",
            """<p>First</p><p>Second</p><div><p>In div</p></div><p>After div</p>""",
        )
        assertEquals(listOf(false, true, false, false), b.map { it.indent })
    }

    @Test
    fun `without indent rules the reader's indent applies`() {
        val b = blocks(".x { text-align: center }", """<p>One</p><p>Two</p>""")
        assertEquals(listOf(true, true), b.map { it.indent })
    }

    @Test
    fun `descendant and child selectors match through ancestors, and alignment inherits`() {
        val b = blocks(
            ".Chapter-rw .title-block-rw { text-align: center } .box > p { text-align: right }",
            """<section class="Chapter-rw"><div class="title-block-rw"><p>FILE 1</p></div></section>""" +
                """<div class="title-block-rw"><p>Elsewhere</p></div>""" +
                """<div class="box"><p>Direct</p><div><p>Nested</p></div></div>""",
        )
        assertEquals(listOf(TextAlign.CENTER, TextAlign.START, TextAlign.END, TextAlign.START), b.map { it.align })
    }

    @Test
    fun `vertical margins and padding become the gap before a block, collapsing like CSS`() {
        val b = blocks(
            "div, p { margin: 0 } div.hanging { margin-top: 1em; margin-bottom: 1em } div.hanging p { margin-bottom: 0.2em } .title { padding-top: 1.2em }",
            """<p>A</p><p>B</p><div class="hanging"><p>C</p><p>D</p></div><p>E</p><p class="title">F</p>""",
        )
        assertEquals(listOf(0f, 0f, 1f, 0.2f, 1f, 1.2f), b.map { it.gapBeforeEm })
    }

    @Test
    fun `a block without margin rules keeps the reader's gap`() {
        assertNull(blocks("", """<p>A</p><p>B</p>""")[1].gapBeforeEm)
    }

    @Test
    fun `spans keep the book's smaller size and sans-serif face`() {
        val b = blocks(".label { font-family: sans-serif; font-size: 0.85em }", """<p><b><span class="label">NEGISHI:</span></b> Words.</p>""")
        val (label, words) = b.single().spans
        assertEquals(0.85f to true, label.scale to label.sans)
        assertEquals(1f to false, words.scale to words.sans)
        assertNull(b.single().scale)
    }

    @Test
    fun `paragraphs and headings keep the reader's font even when the book says sans-serif`() {
        val b = blocks(".t { font-family: sans-serif }", """<h1 class="t">Title</h1><p class="t">Date</p>""")
        assertEquals(listOf(false, false), b.map { it.spans.single().sans })
    }

    @Test
    fun `headings are bold unless the book says normal`() {
        val b = blocks(".title h1 { font-weight: normal }", """<h2>Bold</h2><div class="title"><h1>Plain</h1></div>""")
        assertEquals(listOf(true, false), b.map { it.spans.single().bold })
    }

    @Test
    fun `a left border becomes a rule beside the element's blocks, with their text inset`() {
        val b = blocks(
            ".sidebar { border-left: 1px solid rgb(0, 0, 0); padding-left: 1.2em; margin-left: 0em } div.left { margin-left: 1.3em !important }",
            """<p>Before</p><div class="sidebar"><p>One</p><div class="hanging"><p>Two</p></div></div>""" +
                """<div class="sidebar left"><p>Three</p></div><p>After</p>""",
        )
        val (before, one, two, three, after) = b
        assertNull(before.leftRule)
        assertEquals(0f, one.leftRule!!.atEm)
        assertEquals(1.2f, one.insetEm)
        // nested blocks share the rule, so it runs on between them
        assertEquals(one.leftRule, two.leftRule)
        assertEquals(1.2f, two.insetEm)
        assertEquals(1.3f, three.leftRule!!.atEm)
        assertEquals(2.5f, three.insetEm, 0.001f)
        assertTrue(three.leftRule!!.group != one.leftRule!!.group)
        assertNull(after.leftRule)
        assertEquals(0f, after.insetEm)
    }
}
