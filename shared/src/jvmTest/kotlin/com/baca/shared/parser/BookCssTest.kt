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
        assertEquals(0f, b[1].indent.em)
        assertEquals(listOf(false, true), b[1].spans.map { it.smallCaps })
        assertEquals(TextAlign.START, b[2].align)
    }

    @Test
    fun `links the book colours without underline draw as text`() {
        val b = blocks(
            "div.title-block a { text-decoration: none; color: #000000 !important } a { color: inherit; text-decoration: none }",
            """<div class="title-block"><h1><a href="toc.xhtml">Hallway</a></h1></div><p><a href="n.xhtml">note</a></p>""",
        )
        assertEquals(listOf(true, true), b.map { it.spans.single().plainLink })
        assertEquals(listOf(0xFF000000L, TEXT_COLOR), b.map { it.spans.single().color })
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
        assertEquals(0f, b[1].indent.em)
        assertTrue(b[1].floatsRight)
    }

    @Test
    fun `a float with no text before it in its paragraph stays on its own line`() {
        val b = blocks(".place { float: right }", """<p>Earlier.</p><p><span class="place">tokyo</span></p>""")
        assertFalse(b[1].floatsRight)
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
    fun `the book's sizes are kept, relative and absolute, as LibreraX keeps them`() {
        val b = blocks(".label { font-size: 1.1em } .huge { font-size: 3em } .px { font-size: 24px }",
            """<p class="label">SON</p><h1 class="huge">Big</h1><p class="px">Px</p><h2>Plain heading</h2>""")
        assertEquals(listOf(1.1f, 3f, 1.5f, 1.5f), b.map { it.scale })
    }

    @Test
    fun `more specific rules and inline styles win, pseudo-classes included`() {
        val b = blocks(
            ".r { text-align: right } p.c { text-align: center } div p { text-align: right } .c:first-child { display: none }",
            """<p class="r c">A</p><p class="c" style="text-align: right">B</p><p class="r">C</p>""",
        )
        // A is the first child, hidden by .c:first-child
        assertEquals(listOf(TextAlign.END, TextAlign.END), b.map { it.align })
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
    fun `negative text-indent makes a hanging paragraph, the book's own amounts`() {
        val b = blocks(
            "div.hanging-rw p { text-indent: -1.3em !important; margin-left: 1.3em !important }",
            """<div class="hanging-rw"><p><b>NEGISHI:</b> There was something odd.</p></div><p>Narration.</p>""",
        )
        assertEquals(-1.3f to 1.3f, b[0].indent.em to b[0].inset.em)
        assertEquals(0f to 0f, b[1].indent.em to b[1].inset.em)
    }

    // What LibreraX shows for Strange Buildings: the book's own CSS decides, as in a browser.

    @Test
    fun `the book's indent rules win, including next-sibling selectors`() {
        val b = blocks(
            "p { text-indent: 0 } p+p { text-indent: 1.3em } div+p { text-indent: 0 }",
            """<p>First</p><p>Second</p><div><p>In div</p></div><p>After div</p>""",
        )
        assertEquals(listOf(0f, 1.3f, 0f, 0f), b.map { it.indent.em })
    }

    @Test
    fun `without indent rules there is no indent, as in a browser`() {
        val b = blocks(".x { text-align: center }", """<p>One</p><p>Two</p>""")
        assertEquals(listOf(0f, 0f), b.map { it.indent.em })
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
        assertEquals(listOf(0f, 0f, 1f, 0.2f, 1f, 1.2f), b.map { it.gapBefore?.em })
    }

    @Test
    fun `a block without margin rules has a browser's 1em between paragraphs`() {
        assertEquals(1f, blocks("", """<p>A</p><p>B</p>""")[1].gapBefore?.em)
    }

    @Test
    fun `spans keep the book's smaller size and sans-serif face`() {
        val b = blocks(".label { font-family: sans-serif; font-size: 0.85em }", """<p><b><span class="label">NEGISHI:</span></b> Words.</p>""")
        val (label, words) = b.single().spans
        assertEquals(0.85f to BookFace.SANS, label.scale to label.face)
        assertEquals(1f to null, words.scale to words.face)
        assertNull(b.single().scale)
    }

    @Test
    fun `paragraphs and headings keep the face the book sets them in`() {
        val b = blocks(".t { font-family: sans-serif } .m { font-family: \"Courier New\", monospace }", """<h1 class="t">Title</h1><p class="t">Date</p><p class="m">Log</p>""")
        assertEquals(listOf(BookFace.SANS, BookFace.SANS, BookFace.MONO), b.map { it.spans.single().face })
    }

    @Test
    fun `monospace is set smaller, as a browser sets it, and text inside it in another face is not`() {
        val b = blocks(
            "p.t { font-family: monospace; font-size: 1.2em } span.who { font-family: sans-serif }",
            """<p class="t"><span class="who">Man</span>: Hello.</p>""",
        ).single()
        assertEquals(1.2f * 13 / 16, b.scale!!, 0.001f)
        val (who, words) = b.spans
        assertEquals(BookFace.SANS to 16f / 13, who.face to who.scale)
        assertEquals(BookFace.MONO to 1f, words.face to words.scale)
    }

    @Test
    fun `text the book underlines is underlined, links aside`() {
        val b = blocks("span.u { text-decoration: underline }", """<p><span class="u">Man</span>: <a href="n.xhtml">note</a></p>""").single()
        assertEquals(listOf(true, false, false), b.spans.map { it.underline })
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
        assertEquals(0f, one.leftRule!!.at.em)
        assertEquals(1.2f, one.inset.em)
        // nested blocks share the rule, so it runs on between them
        assertEquals(one.leftRule, two.leftRule)
        assertEquals(1.2f, two.inset.em)
        assertEquals(1.3f, three.leftRule!!.at.em)
        assertEquals(2.5f, three.inset.em, 0.001f)
        assertTrue(three.leftRule!!.group != one.leftRule!!.group)
        assertNull(after.leftRule)
        assertEquals(0f, after.inset.em)
    }

    @Test
    fun `a letter-sized image stays in its line of text, in place`() {
        val all = parseXhtml(
            """<html><head><style>img.inline { height: 1em; vertical-align: -0.1em }</style></head><body>""" +
                """<p>This is <span class="c"><img class="inline" src="white2.jpg"/></span> [the name].</p>""" +
                """<p>Caption <img src="map.jpg"/></p></body></html>""",
        )
        val line = all[0] as Block.Text
        assertEquals("This is $INLINE_IMAGE [the name].", line.text)
        assertEquals(listOf(InlineImage("white2.jpg", 1f)), line.spans.mapNotNull { it.image })
        // without a text-sized height an image is still a figure of its own
        assertEquals("map.jpg", all.filterIsInstance<Block.Image>().single().src)
    }

    // A browser's default stylesheet, which LibreraX leaves under a book's CSS (the gap probe book).

    @Test
    fun `headings take a browser's sizes and margins, ranged left`() {
        val b = blocks("", """<h1>One</h1><h2>Two</h2><p>Body</p>""")
        assertEquals(listOf(2f, 1.5f, null), b.map { it.scale })
        assertEquals(listOf(TextAlign.START, TextAlign.START), b.take(2).map { it.align })
        // h1's 0.67em bottom margin is of its 2em font; h2's 0.83em top margin of its 1.5em font
        assertEquals(1.34f * 16, b[1].gapBefore!!.em * 16, 0.5f)
    }

    @Test
    fun `lists are set in 40px with a marker on each item`() {
        val b = blocks(
            "ol.roman { list-style-type: upper-roman }",
            """<ul><li>One</li><li>Two<ul><li>Inner</li></ul></li></ul><ol start="3"><li>Three</li></ol>""" +
                """<ol class="roman"><li>Roman</li></ol><ul style="list-style: none"><li>Bare</li></ul>""",
        )
        assertEquals(listOf("•", "•", "◦", "3.", "I.", null), b.map { it.marker })
        // a browser's 40px, which stays 40 CSS px whatever the font size
        assertEquals(Spacing(px = 40f), b[0].inset)
        assertEquals(Spacing(px = 80f), b[2].inset)
    }

    @Test
    fun `blockquotes and right margins set text in from both sides`() {
        val b = blocks("p.n { margin-right: 2.3em }", """<blockquote><p>Quoted</p></blockquote><p class="n">Note</p>""")
        assertEquals(Spacing(px = 40f) to Spacing(px = 40f), b[0].inset to b[0].insetEnd)
        assertEquals(Spacing.ZERO to Spacing(em = 2.3f), b[1].inset to b[1].insetEnd)
    }

    @Test
    fun `absolute font sizes don't depend on the parent's`() {
        val b = blocks("div { font-size: 2em } p.px { font-size: 20px }", """<div><p class="px">Twenty</p></div>""")
        assertEquals(1.25f, b.single().scale)
    }

    @Test
    fun `sup and sub ride above and below the line, smaller, and small is smaller`() {
        val b = blocks("", """<p>x<sup>2</sup> H<sub>2</sub>O <small>fine</small></p>""").single()
        val (sup, sub, small) = b.spans.filter { it.scale != 1f }
        assertEquals(0.83f to 0.33f, sup.scale to sup.baseline)
        assertEquals(0.83f to -0.2f, sub.scale to sub.baseline)
        assertEquals(0.83f to 0f, small.scale to small.baseline)
    }

    @Test
    fun `text-transform changes letters one for one, so offsets hold`() {
        val b = blocks(
            ".up { text-transform: uppercase } .cap { text-transform: capitalize }",
            """<p class="up">straße here</p><p class="cap">the end of it</p>""",
        )
        assertEquals(listOf("STRAßE HERE", "The End Of It"), b.map { it.text })
    }

    @Test
    fun `letter-spacing is carried in em`() {
        val b = blocks(".ls { letter-spacing: 0.3em } .px { letter-spacing: 2px }", """<p class="ls">wide <span class="px">px</span></p>""").single()
        assertEquals(listOf(0.3f, 0.125f), b.spans.map { it.letterSpacing })
    }

    @Test
    fun `pre keeps spaces and line breaks, minus the newlines at its edges`() {
        val b = blocks("", "<pre>\npre   keeps\n\tspaces\n</pre>").single()
        assertEquals("pre   keeps\n        spaces", b.text)
        assertTrue(b.keepsSpaces)
        assertEquals(BookFace.MONO, b.spans.single().face)
    }

    @Test
    fun `table cells sit side by side in their row`() {
        val b = blocks("", """<table><tr><th>Head</th><th>Two</th></tr><tr><td>cell A1</td><td>cell B1</td></tr></table>""")
        assertEquals(listOf("Head Two", "cell A1 cell B1"), b.map { it.text })
        assertTrue(b[0].spans.all { it.bold })
    }

    @Test
    fun `hr is a rule, or the book's ornament when it hides the border`() {
        val all = parseXhtml(
            """<html><head><style>hr.orn { background: url('../Images/orn_1.jpg') no-repeat center; height: 1em; border: none }</style></head>""" +
                """<body><p>A</p><hr/><p>B</p><hr class="orn"/></body></html>""",
        ).filterIsInstance<Block.Rule>()
        assertEquals(listOf(true to null, false to "../Images/orn_1.jpg"), all.map { it.line to it.ornament })
        assertEquals(1f, all[1].height?.em)
    }

    @Test
    fun `id, attribute, star, later-sibling and structural selectors match as in a browser`() {
        val b = blocks(
            "#byid { font-weight: bold } p[data-x] { font-style: italic } p[lang|=en] { text-align: center } " +
                "* + h2 ~ p { text-align: right } div > :first-child { text-indent: 2em } p:not(.plain) { font-variant: small-caps } " +
                "p:hover { font-weight: bold } p[epub|type~=note] { font-size: 0.5em }",
            """<p id="byid" class="plain">Id</p><p data-x="1" class="plain">Attr</p><p lang="en-GB" class="plain">Lang</p>""" +
                """<h2>H</h2><p class="plain">Later</p><div><p class="plain">First</p></div><p epub:type="note x">Not</p>""",
        )
        assertEquals(listOf(true, false), b.take(2).map { it.spans.single().bold })
        assertTrue(b[1].spans.single().italic)
        assertEquals(TextAlign.CENTER, b[2].align)
        assertEquals(TextAlign.END, b[4].align)
        assertEquals(2f, b[5].indent.em)
        assertEquals(listOf(false, true), listOf(b[5], b[6]).map { it.spans.single().smallCaps })
        assertEquals(0.5f, b[6].scale)
    }

    @Test
    fun `colours are read in hex, rgb and names, and inherit`() {
        assertEquals(listOf(0xFF6161FFL, 0xFFFF0000L, 0xFF00FF00L, 0x80112233L), listOf("#6161ff", "red", "rgb(0, 255, 0)", "#11223380").map { parseColor(it) })
        val b = blocks(".r { color: red }", """<p class="r">Red <span>still</span></p><p>Ink</p>""")
        assertEquals(listOf(0xFFFF0000L), b[0].spans.map { it.color }.distinct())
        assertNull(b[1].spans.single().color)
    }

    @Test
    fun `display decides what is a block, whatever the tag`() {
        val b = blocks(
            "h3.run { display: inline } span.line { display: block }",
            """<div><h3 class="run">Run-in.</h3> Text after it.</div><p>Before <span class="line">own line</span> after</p>""",
        )
        assertEquals(listOf("Run-in. Text after it.", "Before", "own line", "after"), b.map { it.text.trim() })
        assertTrue(b[0].spans.first().bold)
    }

    @Test
    fun `a picture wrapped in SVG is drawn in the SVG's box`() {
        val all = parseXhtml(
            """<html><body><div><svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="100%" height="100%" viewBox="0 0 600 900">""" +
                """<image width="600" height="900" xlink:href="cover.jpg"/><text>ignored</text></svg></div></body></html>""",
        )
        val image = all.single() as Block.Image
        assertEquals("cover.jpg", image.src)
        assertEquals(ImageBox(CssLength.Percent(100f), CssLength.Vh(100f)), image.box)
    }

    @Test
    fun `contents links stay underlined, as LibreraX keeps them`() {
        val b = blocks("a { text-decoration: none }", """<ol><li><a href="c1.xhtml">One</a></li></ol><p><a href="n.xhtml">note</a></p>""")
        assertEquals(listOf(false, true), b.map { it.spans.single().plainLink })
    }

    @Test
    fun `percent margins and a narrower centred width are shares of the column`() {
        val b = blocks(
            ".q { margin: 0 10% } .c { width: 60%; margin-left: auto; margin-right: auto } .c p { margin-left: 10% }",
            """<p class="q">Quoted</p><div class="c"><p>Centred</p></div>""",
        )
        assertEquals(0.1f to 0.1f, b[0].insetShare to b[0].insetEndShare)
        // 60% wide leaves 40%, half each side; the inner 10% is of the 60%
        assertEquals(0.26f, b[1].insetShare, 0.001f)
        assertEquals(0.2f, b[1].insetEndShare, 0.001f)
    }

    @Test
    fun `a floated first letter is a drop cap`() {
        val b = blocks(".drop { float: left; font-size: 3em; line-height: 1 }", """<p><span class="drop">D</span>rop cap.</p><p>Plain <span class="drop">x</span></p>""")
        assertEquals(DropCap(1, 1f), b[0].dropCap)
        assertEquals("Drop cap.", b[0].text)
        assertNull(b[1].dropCap)
    }
}
