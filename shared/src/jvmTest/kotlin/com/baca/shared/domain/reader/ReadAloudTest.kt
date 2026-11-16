package com.baca.shared.domain.reader

import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.Chapter
import com.baca.shared.parser.FormattedSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadAloudTest {

    private fun para(text: String) = Block.Text(listOf(FormattedSpan(text, false, false)), BlockKind.PARAGRAPH, 0)

    private fun spoken(chapters: List<Chapter>, from: Utterance? = utteranceFrom(chapters, 0, 0, 0)): List<String> =
        generateSequence(from) { utteranceAfter(chapters, it) }.map { it.text }.toList()

    @Test
    fun `a paragraph is read sentence by sentence, going on where the text goes on in lower case`() {
        val chapters = listOf(Chapter("c1", null, listOf(para("“Is it you?” she asked. He nodded! Then… silence."))))
        assertEquals(listOf("“Is it you?” she asked.", "He nodded!", "Then… silence."), spoken(chapters))
    }

    @Test
    fun `titles and initials don't end a sentence`() {
        val chapters = listOf(Chapter("c1", null, listOf(para("Mr. Darcy met J. Austen at St. Paul's. It rained."))))
        assertEquals(listOf("Mr. Darcy met J. Austen at St. Paul's.", "It rained."), spoken(chapters))
    }

    @Test
    fun `reading starts mid-sentence where the page starts and goes on past pictures into the next chapter`() {
        val chapters = listOf(
            Chapter("c1", null, listOf(para("First one. Second one."), Block.Image("", "a.png"), para("  "))),
            Chapter("notes", null, listOf(para("A note.")), linear = false),
            Chapter("c2", null, listOf(para("Next chapter."))),
        )
        val first = utteranceFrom(chapters, 0, 0, 3)!!
        assertEquals(Utterance(0, 0, 3, 10, "st one."), first)
        assertEquals(listOf("st one.", "Second one.", "Next chapter."), spoken(chapters, first))
    }

    @Test
    fun `a sentence too long for the engine is broken at a space`() {
        val long = List(300) { "word" }.joinToString(" ") + "."
        val parts = spoken(listOf(Chapter("c1", null, listOf(para(long)))))
        assertEquals(long, parts.joinToString(" "))
        assertEquals(true, parts.all { it.length <= MAX_UTTERANCE }, "each part fits: ${parts.map { it.length }}")
    }

    @Test
    fun `nothing is left to read at the end of the book`() {
        val chapters = listOf(Chapter("c1", null, listOf(para("Done."))))
        assertNull(utteranceFrom(chapters, 0, 0, 5))
    }
}
