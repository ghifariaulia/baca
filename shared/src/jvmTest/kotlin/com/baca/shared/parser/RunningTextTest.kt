package com.baca.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunningTextTest {

    // a face comes with the family the book named for it, as the parser gives it
    private fun para(text: String, scale: Float? = null, face: BookFace? = null) =
        Block.Text(listOf(FormattedSpan(text, false, false, face = face, family = face?.name?.lowercase())), BlockKind.PARAGRAPH, 0, scale = scale)

    private val prose = "word ".repeat(100)

    @Test
    fun `with too little text to tell, the chapter's body stands for the running text`() {
        // a picture book: its body set at 1.25em in Georgia, a caption in it
        val caption = Block.Text(listOf(FormattedSpan("A short caption", false, false, family = "georgia")), BlockKind.PARAGRAPH, 0, scale = 1.25f)
        val chapter = Chapter("c", null, listOf(caption), bodyScale = 1.25f, bodyFamily = "georgia")
        val text = withRunningText(listOf(chapter)).single().blocks.single() as Block.Text
        // the reader's size and font
        assertNull(text.scale)
        assertNull(text.spans.single().family)
        assertNull(text.spans.single().face)
    }

    @Test
    fun `the size most text is set in becomes the reader's size, and other sizes keep their ratio`() {
        val book = listOf(
            Chapter(
                "c.xhtml", null,
                listOf(para(prose, 0.9f), para("Log", 0.72f, BookFace.MONO), para("Unsized"), Block.Text(listOf(FormattedSpan("Title", true, false)), BlockKind.HEADING, 1)),
            ),
        )
        val (body, log, unsized, title) = withRunningText(book).single().blocks.map { it as Block.Text }
        assertNull(body.scale)
        assertEquals(0.8f, log.scale!!, 0.001f)
        assertEquals(1f / 0.9f, unsized.scale!!, 0.001f)
        assertEquals(1f / 0.9f, title.scale!!, 0.001f)
    }

    @Test
    fun `a book set mostly in monospace reads in the reader's font`() {
        val book = listOf(Chapter("c.xhtml", null, listOf(para(prose, face = BookFace.MONO), para("Aside", face = BookFace.SANS))))
        val faces = withRunningText(book).single().blocks.map { (it as Block.Text).spans.single().face }
        assertEquals(listOf(null, BookFace.SANS), faces)
    }

    @Test
    fun `a book with too little text keeps its sizes`() {
        val book = listOf(Chapter("c.xhtml", null, listOf(para("Short", 0.9f))))
        assertEquals(0.9f, (withRunningText(book).single().blocks.single() as Block.Text).scale)
    }

    @Test
    fun `spacing goes into em of the running text, and 4em or more is halved`() {
        val spaced = para(prose, 0.5f).copy(gapBefore = Spacing(1f), inset = Spacing(4f), indent = Spacing(-1f))
        val text = withRunningText(listOf(Chapter("c.xhtml", null, listOf(spaced)))).single().blocks.single() as Block.Text
        // 1em of 16px is 2em of 8px running text; 4em is 8, wide enough to be halved
        assertEquals(listOf(2f, 4f, -2f), listOf(text.gapBefore?.em, text.inset.em, text.indent.em))
    }

    @Test
    fun `px spacing stays in CSS px, unless it's wide enough to be halved`() {
        val spaced = para(prose, 0.5f).copy(gapBefore = Spacing(px = 10f), inset = Spacing(px = 40f))
        val text = withRunningText(listOf(Chapter("c.xhtml", null, listOf(spaced)))).single().blocks.single() as Block.Text
        assertEquals(Spacing(px = 10f), text.gapBefore)
        // 40px is 5em of 8px running text: halved, and kept in em of it from then on
        assertEquals(Spacing(em = 2.5f), text.inset)
    }

    @Test
    fun `wide spacing is halved per element, not on what nested elements add up to`() {
        // a list in a list: each sets its items in by 40px, short of the 64px that is halved,
        // so the inner items sit 80px in, as a browser keeps them
        val xhtml = """<html><body><ul><li>outer<ul><li>inner</li></ul></li></ul>""" +
            "<p>${"running text ".repeat(30)}</p></body></html>"
        val blocks = withRunningText(listOf(Chapter("c.xhtml", null, parseXhtml(xhtml)))).single().blocks.map { it as Block.Text }
        assertEquals(Spacing(px = 80f), blocks[1].inset)
        // and a single 5em margin is still halved, to 2.5em
        val wide = withRunningText(listOf(Chapter("c.xhtml", null, parseXhtml(
            """<html><body><p>${"running text ".repeat(30)}</p><p style="margin-top: 5em">x</p></body></html>""",
        )))).single().blocks[1]
        assertEquals(Spacing(2.5f), wide.gapBefore)
    }

    @Test
    fun `text in the running family takes the reader's font, other families keep theirs`() {
        val css = """<style>body { font-family: "Minion Pro", serif } h1 { font-family: Helvetica, sans-serif } .k { font-family: "JKaishoM" } .t { font-family: Times }</style>"""
        val xhtml = """<html><head>$css</head><body><h1>Title</h1><p>${"running text ".repeat(30)}<span class="k">kaisho</span><span class="t">times</span></p></body></html>"""
        val blocks = withRunningText(listOf(Chapter("c.xhtml", null, parseXhtml(xhtml))), embedded = setOf("jkaishom")).single().blocks.map { it as Block.Text }
        val (title, body) = blocks
        // the running text: the reader's font
        assertEquals(null to null, body.spans.first().let { it.face to it.family })
        // a sans heading: the system's sans
        assertEquals(BookFace.SANS to null, title.spans.single().let { it.face to it.family })
        // an embedded font by its name
        assertEquals("jkaishom", body.spans.first { it.text == "kaisho" }.family)
        // a serif the book names but doesn't ship: the system's serif, not the reader's font
        assertEquals(BookFace.SERIF to null, body.spans.first { it.text == "times" }.let { it.face to it.family })
    }
}
