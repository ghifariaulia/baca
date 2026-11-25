package com.baca.shared.domain.reader

import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.Chapter
import com.baca.shared.parser.FormattedSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun textBlock(text: String, kind: BlockKind = BlockKind.PARAGRAPH) =
    Block.Text(listOf(FormattedSpan(text, false, false)), kind, if (kind == BlockKind.HEADING) 1 else 0)

class FootnoteLookupTest {

    @Test
    fun `resolves footnote in non-linear chapter up to next anchor`() {
        val chapters = listOf(
            Chapter("OEBPS/ch1.xhtml", "Chapter 1", listOf(textBlock("Body text."))),
            Chapter(
                href = "OEBPS/notes.xhtml",
                title = "Notes",
                blocks = listOf(
                    textBlock("Notes", BlockKind.HEADING),
                    textBlock("1. First footnote."),
                    textBlock("Second paragraph of first footnote."),
                    textBlock("2. Second footnote."),
                ),
                linear = false,
                anchorBlocks = mapOf("n1" to 1, "n2" to 3),
            ),
        )

        val hit = resolveFootnote(chapters, "OEBPS/notes.xhtml#n1")
        assertNotNull(hit)
        assertEquals("Notes", hit.title)
        assertEquals("1. First footnote.\n\nSecond paragraph of first footnote.", hit.text)
    }

    @Test
    fun `resolves inline footnote in linear chapter when fragment looks like a note`() {
        val chapters = listOf(
            Chapter(
                href = "OEBPS/ch1.xhtml",
                title = "Chapter 1",
                blocks = listOf(
                    textBlock("Main text."),
                    textBlock("* The author's note."),
                ),
                linear = true,
                anchorBlocks = mapOf("fn1" to 1, "sec2" to 0),
            ),
        )

        val note = resolveFootnote(chapters, "OEBPS/ch1.xhtml#fn1")
        assertNotNull(note)
        assertEquals("* The author's note.", note.text)

        // Regular section link in a linear chapter is NOT treated as a footnote
        assertNull(resolveFootnote(chapters, "OEBPS/ch1.xhtml#sec2"))
        assertNull(resolveFootnote(chapters, "OEBPS/ch1.xhtml"))
    }
}
