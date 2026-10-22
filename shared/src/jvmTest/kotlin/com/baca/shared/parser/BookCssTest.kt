package com.baca.shared.parser

import com.baca.shared.render.lineBox
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
    fun `plain monospace is set smaller only where its size comes from a keyword`() {
        val b = blocks(
            ".px { font-size: 20px } .mono { font-family: monospace } .named { font-family: 'Courier New', monospace } .large { font-size: large }",
            """<p class="mono">a</p><div class="px"><p class="mono">b</p></div><p class="named">c</p><p class="mono large">d</p><p class="large">e</p>""",
        )
        // under the default medium, 13px; under a 20px div, still 20; a named monospace font, 16
        assertEquals(listOf(13f / 16, 20f / 16, 1f), b.take(3).map { it.scale ?: 1f })
        // a keyword from monospace's own table (large: 16px), or the usual one (18px)
        assertEquals(listOf(1f, 18f / 16), b.drop(3).map { it.scale ?: 1f })
    }

    @Test
    fun `u and ins are underlined, s, strike and del struck through, as a browser draws them`() {
        val b = blocks(".gone { text-decoration: line-through }", """<p><u>u</u><ins>i</ins><abbr title="t">a</abbr><abbr>n</abbr><s>s</s><del>d</del><span class="gone">g</span></p>""").single()
        assertEquals(listOf(true, true, true, false, false, false, false), b.spans.flatMap { s -> s.text.map { s.underline } })
        assertEquals(listOf(false, false, false, false, true, true, true), b.spans.flatMap { s -> s.text.map { s.strike } })
    }

    @Test
    fun `negative margins collapse with positive ones, and a padding keeps a parent's margin from its child's`() {
        // 1em below "a", -0.5em above "b": the largest positive and the most negative add up
        val negative = blocks(".up { margin-top: -0.5em }", """<p>a</p><p class="up">b</p>""")
        assertEquals(Spacing(0.5f), negative[1].gapBefore)
        // the div's 2em and the p's 1em collapse across nothing, but not across the div's 1px padding
        val padded = blocks(".d { margin-top: 2em; padding-top: 1px }", """<p>a</p><div class="d"><p>x</p></div>""")
        assertEquals(Spacing(3f, 1f), padded[1].gapBefore)
        val bare = blocks(".d { margin-top: 2em }", """<p>a</p><div class="d"><p>x</p></div>""")
        assertEquals(Spacing(2f), bare[1].gapBefore)
    }

    @Test
    fun `a percent text-indent is of the block's own width, inherited as a percentage`() {
        val b = blocks(".i { text-indent: 10% } .narrow { margin-left: 100px }", """<div class="i"><p>a</p><p class="narrow">b</p></div>""")
        assertEquals(listOf(0.1f, 0.1f), b.map { it.indentShare })
        // 10% of a 400px column, and of the 300px the margin leaves
        assertEquals(listOf(40, 30), b.map { it.lineBox(bodyFontPx = 16f, cssPx = 1f, columnPx = 400).firstLinePx })
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
    fun `a left border makes a box beside the element's blocks, with their text inset`() {
        val b = blocks(
            ".sidebar { border-left: 1px solid rgb(0, 0, 0); padding-left: 1.2em; margin-left: 0em } div.left { margin-left: 1.3em !important }",
            """<p>Before</p><div class="sidebar"><p>One</p><div class="hanging"><p>Two</p></div></div>""" +
                """<div class="sidebar left"><p>Three</p></div><p>After</p>""",
        )
        val (before, one, two, three, after) = b
        assertTrue(before.boxes.isEmpty())
        val box = one.boxes.single()
        assertEquals(listOf(0f, 0f, 0f, 1f), box.border)
        assertEquals(Spacing.ZERO, box.left)
        // the text sits in by the border and the padding
        assertEquals(Spacing(em = 1.2f, px = 1f), one.inset)
        // nested blocks share the box, so it runs on between them
        assertEquals(box.id, two.boxes.single().id)
        assertEquals(Spacing(em = 1.3f), three.boxes.single().left)
        assertTrue(three.boxes.single().id != box.id)
        assertTrue(after.boxes.isEmpty())
        assertEquals(Spacing.ZERO, after.inset)
    }

    @Test
    fun `a bordered box keeps its border and padding between its edges and its text`() {
        val b = blocks(
            "p { margin: 0 } .end { margin: 1em 0; padding: 0.5em; border: 3px solid rgb(0, 0, 0); background-color: rgb(244, 244, 244); border-radius: 15px }",
            """<p>Before</p><p class="end">END OF FILE 8</p><p>After</p>""",
        )
        val box = b[1].boxes.single()
        assertEquals(listOf(3f, 3f, 3f, 3f), box.border)
        assertEquals(0xFFF4F4F4L, box.background)
        assertEquals(15f, box.radiusPx)
        // above the text: the box's border and padding; above the box: its margin
        assertEquals(Spacing(em = 0.5f, px = 3f), box.topInset)
        assertEquals(Spacing(em = 0.5f, px = 3f), box.bottomInset)
        assertEquals(Spacing(em = 1.5f, px = 3f), b[1].gapBefore)
        assertEquals(Spacing(em = 1.5f, px = 3f), b[2].gapBefore)
        assertEquals(Spacing(em = 0.5f, px = 3f), b[1].inset)
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
        // a third of the parent's size up, a fifth down (in the smaller sup's own em, 0.4 and 0.24), and a device px more
        assertEquals(1f / 1.2f, sup.scale)
        assertEquals(0.4f, sup.baseline, 0.0001f)
        assertEquals(-0.24f, sub.baseline, 0.0001f)
        assertEquals(listOf(1f, -1f, 0f), listOf(sup, sub, small).map { it.baselinePx })
        assertEquals(1f / 1.2f to 0f, small.scale to small.baseline)
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
    fun `table cells know their row, column and the table's column widths`() {
        val b = blocks(
            "",
            """<table><colgroup><col style="width:15%"/><col style="width:85%"/></colgroup>""" +
                """<tr><th>Head</th><th>Two</th></tr><tr><td><p>AUTHOR:</p></td><td><p>One.</p><p>Two.</p></td></tr></table>""",
        )
        assertEquals(listOf("Head", "Two", "AUTHOR:", "One.", "Two."), b.map { it.text })
        assertEquals(listOf(0, 1, 0, 1, 1), b.map { it.cell!!.column })
        assertEquals(1, b.map { it.cell!!.row }.distinct().size - 1)
        assertEquals(listOf(0.15f, 0.85f), b.first().cell!!.widths)
        assertTrue(b[0].spans.all { it.bold })
        assertEquals(TextAlign.CENTER, b[0].align)
    }

    @Test
    fun `tables take a browser's defaults and the table's attributes`() {
        // 2px of spacing round the cells, 1px of padding in them, no indent from outside the table
        val plain = blocks("", """<div style="text-indent: 2em"><table><tr><td>a</td></tr><tr><td>b</td></tr></table></div><p>after</p>""")
        assertEquals(Spacing(px = 2f), plain[0].cell!!.spacing)
        assertEquals(Spacing.ZERO, plain[0].indent)
        // above each row its spacing and the cell's padding; below the table the last row's
        assertEquals(listOf(3f, 4f, 3f), plain.map { it.gapBefore!!.px })
        // cellspacing and cellpadding say otherwise; the book's own CSS still wins over them
        val attrs = blocks("td.own { padding: 0 }", """<table cellspacing="5" cellpadding="4"><tr><td>a</td><td class="own">b</td></tr></table>""")
        assertEquals(Spacing(px = 5f), attrs[0].cell!!.spacing)
        assertEquals(listOf(Spacing(px = 4f), Spacing.ZERO), attrs.map { it.inset })
        // collapsed borders leave no spacing
        val collapsed = blocks("table { border-collapse: collapse }", "<table><tr><td>a</td></tr></table>")
        assertEquals(Spacing.ZERO, collapsed[0].cell!!.spacing)
    }

    @Test
    fun `cells in a row group sit in the middle of their row, unless the book says otherwise`() {
        val grouped = blocks("td.top { vertical-align: top }", """<table><tbody><tr><td>a</td><td class="top">b</td><td style="vertical-align: bottom">c</td></tr></tbody></table>""")
        assertEquals(listOf(0.5f, 0f, 1f), grouped.map { it.cell!!.spareAbove })
        // XHTML has no tbody the book doesn't write: the table's baseline, the top
        val bare = blocks("", "<table><tr><td>a</td></tr></table>")
        assertEquals(0f, bare.single().cell!!.spareAbove)
    }

    @Test
    fun `a picture set inline keeps its containing block's font for the line it stands in`() {
        val all = parseXhtml(
            """<html><head><style>.s { font-family: sans-serif; font-size: 2em } img.b { display: block }</style></head><body>""" +
                """<div class="s"><img src="a.png"/></div><div><img class="b" src="b.png"/></div></body></html>""",
        ).filterIsInstance<Block.Image>()
        assertEquals(BookFace.SANS, all[0].strut!!.face)
        assertEquals(2f, all[0].strut!!.scale)
        assertNull(all[1].strut)
    }

    @Test
    fun `a width or height attribute without a unit is in CSS px`() {
        val all = parseXhtml("""<html><body><img src="a.png" width="300" height="200"/><img src="b.png" width="50%"/><img src="c.png" width="120px"/></body></html>""")
            .filterIsInstance<Block.Image>()
        assertEquals(ImageBox(CssLength.Px(300f), CssLength.Px(200f)), all[0].box)
        assertEquals(CssLength.Percent(50f), all[1].box.width)
        assertEquals(CssLength.Px(120f), all[2].box.width)
    }

    @Test
    fun `an align attribute aligns the text and the narrower blocks inside, under the book's CSS`() {
        val b = blocks(
            ".own { text-align: left }",
            """<div align="center"><p>a</p><div style="width: 50%">b</div></div><p align="right">c</p><p class="own" align="center">d</p>""" +
                """text<center>e</center>""",
        )
        assertEquals(listOf("a", "b", "c", "d", "text", "e"), b.map { it.text })
        assertEquals(listOf(TextAlign.CENTER, TextAlign.CENTER, TextAlign.END, TextAlign.START, TextAlign.START, TextAlign.CENTER), b.map { it.align })
        // the half-wide block in the centre: a quarter of the column each side
        assertEquals(0.25f to 0.25f, b[1].insetShare to b[1].insetEndShare)
    }

    @Test
    fun `a block knows the language its element or the nearest one round it gives`() {
        val b = parseXhtml("""<html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><body><p>a</p><div xml:lang="en"><p>b</p></div></body></html>""")
        assertEquals(listOf("ja", "en"), b.map { (it as Block.Text).lang })
    }

    @Test
    fun `imported stylesheets apply before the one importing them, and sheets for other media don't`() {
        val files = mapOf(
            "css/main.css" to "@import url(\"base.css\"); @import 'print.css' print; .b { text-align: right }",
            "css/base.css" to ".a { text-align: center } .b { text-align: center }",
            "css/print.css" to ".a { text-align: right }",
            "css/alt.css" to ".a { text-align: right }",
            "css/tv.css" to ".a { text-align: right }",
        )
        val b = parseXhtml(
            """<html><head><link rel="stylesheet" href="css/main.css"/><link rel="alternate stylesheet" title="x" href="css/alt.css"/>""" +
                """<link rel="stylesheet" media="tv" href="css/tv.css"/><style media="print">.b { text-align: left }</style></head>""" +
                """<body><p class="a">a</p><p class="b">b</p></body></html>""",
            loadCss = { files[it] },
        ).filterIsInstance<Block.Text>()
        assertEquals(listOf(TextAlign.CENTER, TextAlign.END), b.map { it.align })
    }

    @Test
    fun `a table's border attribute borders it and its cells, and its cells take align`() {
        val b = blocks("", """<table border="2"><tr><td align="right">a</td><td>b</td></tr></table>""")
        val (table, cell) = b[0].boxes
        assertEquals(listOf(2f, 2f, 2f, 2f) to false, table.border to table.cell)
        assertEquals(listOf(1f, 1f, 1f, 1f) to true, cell.border to cell.cell)
        assertEquals(0xFF808080, cell.borderColor.first())
        assertEquals(listOf(TextAlign.END, TextAlign.START), b.map { it.align })
    }

    @Test
    fun `a box's bottom edge is below the margins and boxes inside it`() {
        // the table's 0.5em margin, then the box's 1em padding and its border: all inside the box
        val b = blocks(
            ".box { border: 1px solid; padding-bottom: 1em } .t { margin-bottom: 0.5em }",
            """<div class="box"><p class="t">a</p></div><p>b</p>""",
        )
        assertEquals(Spacing(1.5f, 1f), b[0].boxes.single().bottomInset)
    }

    @Test
    fun `vertical margins in vh and percent are shares of the page, window and column`() {
        // a title page set down 30vh in a style element (of the page), in a linked sheet (of the window), and 5% (of the width)
        val styled = parseXhtml("""<html><head><style>.t { margin-top: 30vh } .p { margin-top: 5% }</style></head><body><p class="t">a</p><p class="p">b</p></body></html>""")
            .filterIsInstance<Block.Text>()
        assertEquals(Spacing(pageH = 0.3f), styled[0].gapBefore)
        assertEquals(Spacing(width = 0.05f), styled[1].gapBefore)
        val linked = blocks(".t { margin-top: 30vh }", """<p class="t">a</p>""")
        assertEquals(Spacing(windowH = 0.3f), linked[0].gapBefore)
        // a blank block held at half the page
        val held = parseXhtml("""<html><body><p>a</p><div style="height: 50vh"><p>&#160;</p></div></body></html>""").filterIsInstance<Block.Rule>().single()
        assertEquals(Spacing(pageH = 0.5f), held.height)
    }

    @Test
    fun `prefers-color-scheme follows the reading theme`() {
        val doc = """<html><head><style>@media (prefers-color-scheme: dark) { p { text-align: center } } @media (prefers-color-scheme: light) { p { text-align: right } }</style></head><body><p>a</p></body></html>"""
        assertEquals(TextAlign.END, (parseXhtml(doc).single() as Block.Text).align)
        assertEquals(TextAlign.CENTER, (parseXhtml(doc, darkScheme = true).single() as Block.Text).align)
    }

    @Test
    fun `font size, face and color, and a list's type, as a browser takes the attributes`() {
        val p = blocks("", """<p>a<font size="1">b</font><font size="+2" face="monospace" color="#ff0000">c</font></p>""").single()
        val (_, small, big) = p.spans
        assertEquals(10f / 16, small.scale, 0.001f)
        // +2 from 3 is 5, x-large: in monospace's own table, 20px
        assertEquals(20f / 16, big.scale, 0.001f)
        assertEquals(BookFace.MONO to 0xFFFF0000, big.face to big.color)
        val lists = blocks("", """<ol type="A"><li>x</li></ol><ol type="i"><li>y</li></ol><ul type="square"><li>z</li></ul>""")
        assertEquals(listOf("A.", "i.", "▪"), lists.map { it.marker })
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
    fun `a blank block of fixed height holds that height, its paragraph's margins collapsing out of the top only`() {
        val all = parseXhtml(
            """<html><head><style>div.blank { height: 1em; margin: 0 }</style></head>""" +
                """<body><p>A</p><div class="blank"><p>&#160;</p></div><p>B</p></body></html>""",
        )
        val band = all[1] as Block.Rule
        assertEquals(listOf("A", "", "B"), all.map { it.text })
        assertEquals(false, band.line)
        assertEquals(Spacing(1f), band.height)
        // A's bottom margin and the paragraph's top margin collapse above the div
        assertEquals(Spacing(1f), band.gapBefore)
        // the paragraph's bottom margin stays inside the div; B's own top margin is the gap after it
        assertEquals(Spacing(1f), all[2].gapBefore)
    }

    @Test
    fun `column-count lays the text inside in columns, a gap apart`() {
        val b = blocks(
            ".cols { -webkit-column-count: 2; column-count: 2 } .wide { column-gap: 2em } .one { column-count: 1 }",
            """<div class="cols"><p>In two</p></div><div class="cols wide"><p>Wide gap</p></div><div class="cols one"><p>One</p></div><p>Plain</p>""",
        )
        assertEquals(listOf(Columns(2, Spacing(1f)), Columns(2, Spacing(2f)), null, null), b.map { it.columns })
    }

    @Test
    fun `a paragraph holding only a printed page number keeps its line, as a browser lays it`() {
        val all = parseXhtml(
            """<html><head><style>span.pb { font-size: 0; visibility: hidden }</style></head><body>""" +
                """<p><span class="pb" epub:type="pagebreak">15</span></p>""" +
                """<p>Text<span class="pb" epub:type="pagebreak">16</span> runs on.</p></body></html>""",
        )
        // an empty line where the number was, and nothing of it in running text
        assertEquals(listOf("\u00A0", "Text runs on."), all.map { it.text })
    }

    @Test
    fun `a picture sits in its containing block, inside its margins and padding`() {
        val image = parseXhtml(
            """<html><body><blockquote><p><img src="a.png"/></p></blockquote><div style="margin-left: 10%"><img src="b.png"/></div></body></html>""",
        ).filterIsInstance<Block.Image>()
        // a blockquote's 40px each side; a 10% left margin
        assertEquals(listOf(Spacing(px = 40f), Spacing.ZERO), image.map { it.inset })
        assertEquals(listOf(Spacing(px = 40f), Spacing.ZERO), image.map { it.insetEnd })
        assertEquals(listOf(0f, 0.1f), image.map { it.insetShare })
    }

    @Test
    fun `a stylesheet's @font-face rules name the book's own fonts`() {
        val fonts = fontFacesOf(
            """@font-face { font-family: "JKaishoM"; font-style: normal; font-weight: normal; src : url("../Fonts/JKaishoM.TTF"); }
               @font-face { font-family: Body; font-style: italic; font-weight: bold; src: url(f/body-bi.otf) format("opentype"), local(x); }""",
        ) { "OEBPS/" + it.removePrefix("../") }
        assertEquals(
            listOf(BookFont("jkaishom", "OEBPS/Fonts/JKaishoM.TTF", 400, false), BookFont("body", "OEBPS/f/body-bi.otf", 700, true)),
            fonts,
        )
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
    fun `vh in a linked stylesheet is of the window, in a style element of the page`() {
        val doc = """<html><head><link rel="stylesheet" href="s.css"/><style>.b img { height: 50vh }</style></head>""" +
            """<body><div class="a"><img src="a.jpg"/></div><div class="b"><img src="b.jpg"/></div></body></html>"""
        val images = parseXhtml(doc, loadCss = { ".a img { height: 70vh; width: 10vw }" }).filterIsInstance<Block.Image>()
        assertEquals(ImageBox(CssLength.WindowVw(10f), CssLength.WindowVh(70f)), images[0].box)
        assertEquals(ImageBox(height = CssLength.Vh(50f)), images[1].box)
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

    @Test
    fun `vertical-align top on a cell aligns the cell, it doesn't raise its text`() {
        val b = blocks("td { vertical-align: top } .up { vertical-align: 30% }", """<table><tr><td><p>Cell <span class="up">x</span></p></td></tr></table>""").single()
        assertEquals(listOf(0f, 0f), b.spans.map { it.baseline })
        // a percentage is of the line height, worked out when laid out
        assertEquals(listOf(0f, 0.3f), b.spans.map { it.baselineLine })
    }

    @Test
    fun `an inline element's padding and margins are space beside it in the line`() {
        val b = blocks(".space { padding-left: 0.5em } .m { margin-right: 8px }", """<p>7:40<span class="space"/>Left <b class="m">home</b>.</p>""").single()
        assertEquals("7:40${INLINE_IMAGE}Left home$INLINE_IMAGE.", b.text)
        assertEquals(listOf(InlineSpacer(0.5f), InlineSpacer(0.5f)), b.spans.mapNotNull { it.spacer })
    }

    @Test
    fun `an inline-block's min-width pads its text`() {
        val b = blocks(".n { display: inline-block; min-width: 1.75em }", """<p><span class="n">(1)</span> Point.</p>""").single()
        val pad = b.spans.single { it.spacer != null }
        assertEquals(1.75f, pad.spacer!!.widthEm)
        assertEquals(pad.spacer!!.padsBox, b.spans.first().inlineBox)
        assertEquals("(1)$INLINE_IMAGE Point.", b.text)
    }

    @Test
    fun `headings keep the book's line height and hyphenation, running text takes the reader's`() {
        val b = blocks(
            "h2 { line-height: 1.5; hyphens: none } p { line-height: 3; hyphens: none } h3 { line-height: 18px }",
            """<h2>Title</h2><h3 style="font-size: 1.5em">Sub</h3><p>Body</p><h4>Plain</h4>""",
        )
        // a px line height stays px, whatever the heading's size
        assertEquals(listOf(1.5f, null, null, null), b.map { it.lineRatio })
        assertEquals(listOf(null, 18f, null, null), b.map { it.linePx })
        assertEquals(listOf(false, true, true, true), b.map { it.hyphenate })
    }

    @Test
    fun `left stays ragged where the reader's justification isn't set, and captions are centred`() {
        val b = blocks(
            "table { text-align: left } .j { text-align: justify } dd { text-align: left }",
            """<table><caption>Cap</caption><tr><td>cell</td><td class="j">just</td><td><p>para</p></td></tr></table><dl><dd>def</dd></dl><p style="text-align: left">body</p>""",
        )
        assertEquals(listOf("Cap", "cell", "just", "para", "def", "body"), b.map { it.text })
        assertEquals(listOf(false, true, false, false, true, false), b.map { it.ragged })
        assertEquals(TextAlign.CENTER, b[0].align)
    }

    @Test
    fun `the book's body colour gives way to the theme's text, its own colours stay`() {
        val b = blocks("body { color: #333333 } .red { color: #aa0000 }", """<p>plain</p><p class="red">red</p>""")
        assertEquals(listOf(null, 0xFFAA0000), b.map { it.spans.single().color })
    }

    @Test
    fun `text inside any anchor knows it, a link or not`() {
        val b = blocks("", """<p><a id="x">named <b>bold</b></a> plain <a href="n.xhtml">link</a></p>""").single()
        assertEquals(listOf(true, true, false, true), b.spans.map { it.inAnchor })
    }

    @Test
    fun `contents links are underlined, a list's named anchors aren't`() {
        val b = blocks("a { text-decoration: none }", """<ul><li><a href="c1.xhtml">One</a></li><li><a id="two">Two</a></li></ul>""")
        // the link drawn underlined (not a plain link), the named anchor not underlined
        assertEquals(false, b[0].spans.single().plainLink)
        assertEquals(false, b[1].spans.single().underline)
    }

    @Test
    fun `quotes round q, mark on yellow, big larger, and script, noscript and rp not shown`() {
        val b = blocks(
            "",
            """<p>He said <q>she said <q>no</q></q>.<script>var x</script><noscript>off</noscript></p>""" +
                """<p><mark>found</mark> <big>big</big> <ruby>漢<rp>(</rp><rt>kan</rt><rp>)</rp></ruby></p>""",
        )
        assertEquals("He said \u201Cshe said \u2018no\u2019\u201D.", b[0].text)
        val (mark, big) = b[1].spans.filter { it.text == "found" || it.text == "big" }
        assertEquals(0xFFFFFF00 to 0xFF000000, mark.background to mark.color)
        assertEquals(1.2f, big.scale, 0.001f)
        assertEquals("found big 漢kan", b[1].text)
    }

    @Test
    fun `a linked stylesheet's -epub- properties are the plain ones`() {
        val b = blocks("h2 { -epub-hyphens: none; -epub-text-transform: uppercase }", "<h2>word</h2>").single()
        assertEquals(false, b.hyphenate)
        assertEquals("WORD", b.text)
    }

    @Test
    fun `the book's html padding gives way to foliate's`() {
        val b = blocks("html { padding: 3em } p { margin: 0 }", "<p>a</p>").single()
        assertEquals(Spacing.ZERO, b.inset)
        assertEquals(Spacing.ZERO, b.gapBefore ?: Spacing.ZERO)
    }

    @Test
    fun `a paragraph inside a heading keeps the heading's leading`() {
        val b = blocks("h1 { line-height: 1.1 }", """<h1><p>In a heading</p></h1><p>Body</p>""")
        assertEquals(listOf(1.1f, null), b.map { it.lineRatio })
    }

    @Test
    fun `hidden elements, wbr, visibility and word-spacing`() {
        val b = blocks(
            ".v { visibility: hidden } h1 { word-spacing: 0.2em }",
            """<p hidden="">Gone</p><p>long<wbr/>word <span class="v">ghost</span></p><h1>A title</h1>""",
        )
        assertEquals("long${WORD_BREAK}word ghost", b[0].text)
        assertEquals(INVISIBLE, b[0].spans.last().color)
        assertEquals(0.2f, b[1].spans.single().wordSpacing)
    }

    @Test
    fun `page breaks force a new page only as a browser's columns take them`() {
        val body = """<p>a</p><p class="p">b</p><p class="c">c</p><p class="w">d</p><p class="l">e</p><p>f</p></body></html>"""
        val rules = ".p { page-break-before: always } .c { break-before: column } .w { -webkit-column-break-before: always } " +
            ".l { break-before: left; break-after: avoid-page }"
        // a <style> keeps its page breaks, which columns ignore; their avoid-page doesn't hold either
        val styled = parseXhtml("<html><head><style>$rules</style></head><body>$body")
        assertEquals(listOf(false, false, true, true, false, false), styled.map { it.breakBefore })
        assertEquals(false, styled.any { it.keepWithPrevious })
        // a linked sheet's page breaks become column breaks, as foliate rewrites them (left and right
        // don't), and its avoid-page a column's avoid
        val linked = parseXhtml("""<html><head><link rel="stylesheet" href="s.css"/></head><body>$body""", loadCss = { rules })
        assertEquals(listOf(false, true, true, true, false, false), linked.map { it.breakBefore })
        assertEquals(listOf(false, false, false, false, false, true), linked.map { it.keepWithPrevious })
        // inline styles too keep what they wrote
        val inline = parseXhtml("""<html><body><p>a</p><p style="page-break-before: always">b</p><p style="break-before: column">c</p></body></html>""")
        assertEquals(listOf(false, false, true), inline.map { it.breakBefore })
    }

    @Test
    fun `avoided breaks keep blocks together`() {
        val blocks = parseXhtml(
            """<html><head><style>h2 { page-break-after: avoid } .poem { page-break-inside: avoid } .c { break-before: avoid-column }</style></head><body>""" +
                """<p>a</p><h2>Title</h2><p>b</p><div class="poem"><p>l1</p><p>l2</p></div><p>d</p><p class="c">e</p></body></html>""",
        )
        assertEquals(listOf("a", "Title", "b", "l1", "l2", "d", "e"), blocks.map { it.text })
        assertEquals(listOf(false, false, true, false, true, false, true), blocks.map { it.keepWithPrevious })
        assertEquals(listOf(false, false, false, true, true, false, false), blocks.map { it.keepLines })
    }

    @Test
    fun `forced page breaks, rule widths, contents entries and percent vertical-align`() {
        val all = parseXhtml(
            """<html><head><link rel="stylesheet" href="s.css"/></head><body>""" +
                """<div id="hdr"><p>Header</p></div><p>After header</p><h2>Chapter</h2><hr/>""" +
                """<ol><li><a href="c1.xhtml">One</a></li><li>Plain <a href="x.xhtml">link</a></li></ol><p>x<sup>2</sup></p></body></html>""",
            loadCss = { "h2 { page-break-before: always } #hdr { page-break-after: always } hr { width: 80% } sup { vertical-align: 35% }" },
        )
        val text = all.filterIsInstance<Block.Text>()
        assertEquals(listOf(false, true, true), text.take(3).map { it.breakBefore })
        assertEquals(0.8f, all.filterIsInstance<Block.Rule>().single().widthShare)
        assertEquals(listOf(true, false), text.filter { it.marker != null }.map { it.ragged })
        assertEquals(0.35f, text.last().spans.last().baselineLine)
    }

    @Test
    fun `pseudo-classes that look ahead at later siblings match`() {
        val b = blocks(
            "div > :last-child { text-align: center } p:only-of-type { font-style: italic } li:nth-child(2n) { font-weight: bold } li:nth-last-child(1) { text-align: right }",
            """<div><p>One</p><p>Two</p></div><section><p>Only</p><h2>Head</h2></section><ol><li>a</li><li>b</li><li>c</li></ol>""",
        )
        assertEquals(listOf(TextAlign.START, TextAlign.CENTER), b.take(2).map { it.align })
        assertEquals(listOf(false, false, true), b.take(3).map { it.spans.single().italic })
        val items = b.filter { it.marker != null }
        assertEquals(listOf(false, true, false), items.map { it.spans.single().bold })
        assertEquals(TextAlign.END, items.last().align)
    }

    @Test
    fun `media rules for a phone's screen apply, others don't`() {
        val b = blocks(
            "@media screen { .a { text-align: center } } @media print { .b { text-align: center } } " +
                "@media amzn-kf8 { .c { display: none } } @media (max-width: 600px) { .d { text-align: right } } " +
                "@media (prefers-color-scheme: dark) { .e { text-align: center } }",
            """<p class="a">A</p><p class="b">B</p><p class="c">C</p><p class="d">D</p><p class="e">E</p>""",
        )
        assertEquals(listOf(TextAlign.CENTER, TextAlign.START, TextAlign.START, TextAlign.END, TextAlign.START), b.map { it.align })
    }
}
