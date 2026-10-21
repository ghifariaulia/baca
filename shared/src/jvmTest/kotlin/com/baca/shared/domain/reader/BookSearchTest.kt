package com.baca.shared.domain.reader

import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.Chapter
import com.baca.shared.parser.FormattedSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookSearchTest {

    private fun para(text: String) = Block.Text(listOf(FormattedSpan(text, false, false)), BlockKind.PARAGRAPH, 0)

    private val chapters = listOf(
        Chapter("c0.xhtml", "One", listOf(para("The hallway led nowhere. Nowhere at all."), Block.Image("", "a.png"))),
        Chapter("c1.xhtml", "Two", listOf(para("He’d seen it before."), para("A hallway again, " + "long ".repeat(30) + "and dark."))),
    )

    @Test
    fun `finds every match in reading order, ignoring case`() {
        val hits = searchBook(chapters, "nowhere")
        assertEquals(listOf(0 to 16, 0 to 25), hits.map { it.blockIndex to it.charOffset })
        assertTrue(hits.all { it.chapterIndex == 0 && it.length == 7 })
        assertEquals(listOf(0, 1), searchBook(chapters, "HALLWAY").map { it.chapterIndex })
    }

    @Test
    fun `a straight apostrophe finds a curly one`() {
        val hit = searchBook(chapters, "he'd").single()
        assertEquals(1 to 0, hit.chapterIndex to hit.blockIndex)
    }

    @Test
    fun `the snippet shows the match in its words, cut at word edges`() {
        val hit = searchBook(chapters, "dark").single()
        assertEquals("dark", hit.snippet.substring(hit.matchInSnippet))
        assertTrue(hit.snippet.startsWith("…long"), hit.snippet)
        assertTrue(hit.snippet.endsWith("dark."), hit.snippet)
    }

    @Test
    fun `very short queries and the limit keep results small`() {
        assertTrue(searchBook(chapters, "a").isEmpty())
        assertEquals(1, searchBook(chapters, "long", limit = 1).size)
    }
}
