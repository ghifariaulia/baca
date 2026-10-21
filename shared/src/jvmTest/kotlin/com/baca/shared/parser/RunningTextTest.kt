package com.baca.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunningTextTest {

    private fun para(text: String, scale: Float? = null, face: BookFace? = null) =
        Block.Text(listOf(FormattedSpan(text, false, false, face = face)), BlockKind.PARAGRAPH, 0, scale = scale)

    private val prose = "word ".repeat(100)

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
}
