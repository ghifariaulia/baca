package com.baca.shared.domain.reader

import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.Chapter
import com.baca.shared.parser.FormattedSpan
import com.baca.shared.domain.AnnotationEntry
import com.baca.shared.domain.PointDto
import com.baca.shared.domain.ReflowAnchorDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun para(text: String) = Block.Text(listOf(FormattedSpan(text, false, false)), BlockKind.PARAGRAPH, 0)

// the "other file": same text, different chapter paths and an extra block up front
private val chapters = listOf(
    Chapter("Text/front.xhtml", null, listOf(para("Front matter that the first file did not have."))),
    Chapter(
        "Text/ch1.xhtml",
        null,
        listOf(para("Inserted note."), para("It was on a dreary night of November that I beheld the accomplishment of my toils.")),
    ),
)

private fun highlight(quote: String) = AnnotationEntry(
    id = "a1", kind = "highlight",
    // offsets from the first file: chapter c1, block 0, chars 0..20
    reflowAnchor = ReflowAnchorDto("OEBPS/c1.xhtml", PointDto(0, 0), PointDto(0, 20), quote),
    createdAt = 0, lamport = 1,
)

class CrossFileTest {

    @Test
    fun `quote is found regardless of chapter path and block number`() {
        assertEquals(TextPoint(1, 1, 10), findQuote(chapters, "a dreary night of November", preferHref = "OEBPS/c1.xhtml"))
    }

    @Test
    fun `short or missing quotes are not matched`() {
        assertNull(findQuote(chapters, "It", null))
        assertNull(findQuote(chapters, "not in this book at all", null))
    }

    @Test
    fun `relinked highlight keeps its length on the re-found text`() {
        val moved = relinkEntry(highlight("It was on a dreary night"), chapters).reflowAnchor!!
        assertEquals("Text/ch1.xhtml", moved.href)
        assertEquals(PointDto(1, 0), moved.start)
        assertEquals(PointDto(1, 20), moved.end)
    }

    @Test
    fun `unfound highlight loses its location instead of marking other text`() {
        val moved = relinkEntry(highlight("a sentence the other file lacks"), chapters).reflowAnchor!!
        assertEquals("", moved.href)
    }
}
