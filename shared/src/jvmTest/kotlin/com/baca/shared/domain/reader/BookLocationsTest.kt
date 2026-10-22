package com.baca.shared.domain.reader

import com.baca.shared.parser.Chapter
import com.baca.shared.parser.TocEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookLocationsTest {

    // Strange Buildings: each spine document's bytes in the EPUB, in reading order
    private val sizes = listOf(
        1500, 1500, 1500, 1500, 2900, 4570, 1500, 50584, 1500, 43272, 1500, 19803, 1500, 36259, 1500, 60357,
        1500, 30504, 1500, 20596, 1500, 36343, 1500, 30984, 1500, 28467, 1500, 46177, 1500, 13170, 10835, 9364,
        17493, 7964, 34651, 20160, 7700, 5103, 12262, 13625, 68585, 1500, 2166,
    )
    private val chapters = sizes.mapIndexed { i, size -> Chapter("c$i", null, emptyList(), size) }
    // its contents: title page, contents, FILE 1–11 (each a title document before its text), Kurihara's Deductions…
    private val toc = listOf(1, 5, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 41, 42).map { TocEntry("c$it", "") }
    private val book = BookLocations(chapters, toc)

    @Test
    fun `chapters sharing a document start where their anchors are in its text`() {
        // one document holding two chapters, the second halfway through its text, then another document
        val one = Chapter("a", null, emptyList(), 3000, anchors = mapOf("c2" to 0.5f))
        val two = Chapter("b", null, emptyList(), 3000)
        val starts = BookLocations(listOf(one, two), listOf(TocEntry("a", "1"), TocEntry("a", "2", anchor = "c2"), TocEntry("b", "3"))).chapterStarts
        assertEquals(listOf(0.25f, 0.5f), starts)
    }

    @Test
    fun `a book counts a location for every 1500 bytes, whatever the layout`() {
        assertEquals(440, book.total)
    }

    @Test
    fun `page numbers and pages left match LibreraX's on the same pages`() {
        // FILE 5's text (document 15) runs from location 114.03 to 154.30; LibreraX showed
        // "126 / 440" with 30 left, and further on "137 / 440" with 19 left
        assertEquals(30, book.pagesLeft(125.1 / 440))
        assertEquals(19, book.pagesLeft(136.1 / 440))
        val start = book.place(15, 0, 40)
        val page = (0 until 40).first { book.number(15, it, 40) == 126 }
        assertEquals(126, book.number(15, page, 40))
        assertEquals(30, book.pagesLeft(book.place(15, page, 40)))
        assertEquals(114, (start * 440).toInt())
    }

    @Test
    fun `seeking finds the document a fraction falls in, and how far into it`() {
        val (chapter, within) = book.chapterAt(125.1 / 440)
        assertEquals(15, chapter)
        assertEquals(0.27, within, 0.01)
    }

    @Test
    fun `the book's last page shows the last location`() {
        assertEquals(440, book.number(chapters.lastIndex, 0, 1))
    }

    @Test
    fun `chapters start where the contents' top levels do`() {
        val nested = listOf(TocEntry("c6", "", 1), TocEntry("c7", "", 2), TocEntry("c8", "", 1), TocEntry("c9", "", 3))
        // levels 1 and 2 are ticks; the third level isn't
        assertEquals(3, BookLocations(chapters, nested).chapterStarts.size)
    }

    @Test
    fun `without known sizes or chapters there are no locations to count`() {
        val plain = BookLocations(listOf(Chapter("txt", null, emptyList())), emptyList())
        assertEquals(0, plain.total)
        assertNull(plain.pagesLeft(0.5))
    }
}
