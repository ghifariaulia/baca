package com.baca.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

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
        // a heading without a size of its own keeps the reader's heading size
        assertNull(title.scale)
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
        assertSame(book, withRunningText(book))
    }
}
