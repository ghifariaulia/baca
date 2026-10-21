package com.baca.shared.domain.reader

import com.baca.shared.domain.Locator
import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.Chapter
import com.baca.shared.parser.FormattedSpan
import com.baca.shared.render.GridMetrics
import com.baca.shared.render.ReflowLayout
import com.baca.shared.render.ReflowSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeStore : PositionStore {
    val positions = mutableMapOf<String, Locator>()
    val local = mutableMapOf<String, String>()
    override val deviceId: String = "test-device"
    override fun loadPosition(bookHash: String): Locator? = positions[bookHash]
    override fun savePosition(bookHash: String, locator: Locator) {
        positions[bookHash] = locator
    }
    override fun getLocal(key: String): String? = local[key]
    override fun putLocal(key: String, value: String) {
        local[key] = value
    }
}

private fun chapter(href: String, paraText: String): Chapter = Chapter(
    href,
    title = null,
    listOf(
        Block.Text(listOf(FormattedSpan("Heading", false, false)), BlockKind.HEADING, 1),
        Block.Text(listOf(FormattedSpan(paraText, false, false)), BlockKind.PARAGRAPH, 0),
    ),
)

// Distinct (non-periodic) text so quote snippets are unambiguous.
private const val PARA_WORDS = 200
private val PARA = (1..PARA_WORDS).joinToString(" ") { "word$it" } // ~1400 chars, ~140 lines

private fun session(
    store: FakeStore,
    onChange: () -> Unit = {},
): ReaderSession {
    val chapters = listOf(
        chapter("OEBPS/c0.xhtml", PARA),
        chapter("OEBPS/c1.xhtml", PARA),
        chapter("OEBPS/c2.xhtml", PARA),
    )
    return ReaderSession(
        bookHash = "h",
        chapters = chapters,
        repo = store,
        layoutFactory = { i, vp, st ->
            ReflowLayout(chapters[i].blocks, GridMetrics(), st, vp.columnWidthPx, vp.pageHeightPx)
        },
        onChange = onChange,
    )
}

private val VIEWPORT = Viewport(100, 100)
private val SETTINGS = ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.0f, imageHeightPx = 60)

class ReaderSessionTest {

    @Test
    fun `fresh open starts at first text chapter page 1 and saves position`() = runTest {
        val store = FakeStore()
        val s = session(store)
        s.ensureView(VIEWPORT, SETTINGS)
        assertEquals(0, s.chapterIndex)
        assertEquals(0, s.pageIndex)
        assertTrue(store.positions.containsKey("h"))
        assertEquals(0.0, store.positions["h"]!!.progression)
    }

    @Test
    fun `turn pages advance and save position`() = runTest {
        val store = FakeStore()
        val s = session(store)
        s.ensureView(VIEWPORT, SETTINGS)
        val pages = s.layoutFor(0).pageCount()
        s.turnPage(1)
        assertEquals(1, s.pageIndex)
        assertEquals(1.0 / (s.bookTotalPages() - 1), store.positions["h"]!!.progression, 0.0001)
        s.turnPage(-1)
        assertEquals(0, s.pageIndex)
    }

    @Test
    fun `footer counts pages left in the chapter and marks where chapters start`() = runTest {
        val s = session(FakeStore())
        s.ensureView(VIEWPORT, SETTINGS)
        val pages = s.layoutFor(0).pageCount()
        assertEquals(pages - 1, s.chapterPagesLeft())
        s.turnPage(1)
        assertEquals(pages - 2, s.chapterPagesLeft())
        // three equal chapters: ticks at a third and two thirds
        val ticks = s.chapterStartFractions()
        assertEquals(2, ticks.size)
        assertEquals(1f / 3, ticks[0], 0.001f)
        assertEquals(2f / 3, ticks[1], 0.001f)
    }

    @Test
    fun `a jump asked for before the first layout lands there, not on the saved position`() = runTest {
        val store = FakeStore()
        store.positions["h"] = Locator("OEBPS/c0.xhtml", 1, 0, 0.0)
        val s = session(store)
        // the Notes and Bookmarks screens open the reader with a jump before it has laid out a page
        s.jumpToAnchor("OEBPS/c2.xhtml", 1, 500)
        s.ensureView(VIEWPORT, SETTINGS)
        assertEquals(2, s.chapterIndex)
        val expected = s.layoutFor(2).pages.indexOfFirst { page -> page.any { it.blockIndex == 1 && 500 in it.start until it.endExclusive } }
        assertEquals(expected, s.pageIndex)
    }

    @Test
    fun `an anchor in the middle of a page's last line opens that page, not the next`() = runTest {
        val s = session(FakeStore())
        s.ensureView(VIEWPORT, SETTINGS)
        val layout = s.layoutFor(1)
        val pageNo = 2
        val last = layout.pages[pageNo].last { !it.isImage }
        s.jumpToAnchor("OEBPS/c1.xhtml", last.blockIndex, last.start + 1)
        assertEquals(1, s.chapterIndex)
        assertEquals(pageNo, s.pageIndex)
    }

    @Test
    fun `page turn at chapter end moves to next chapter page 1`() = runTest {
        val store = FakeStore()
        val s = session(store)
        s.ensureView(VIEWPORT, SETTINGS)
        val pages = s.layoutFor(0).pageCount()
        repeat(pages) { s.turnPage(1) }
        assertEquals(1, s.chapterIndex)
        assertEquals(0, s.pageIndex)
        s.turnPage(-1)
        assertEquals(0, s.chapterIndex)
        assertEquals(pages - 1, s.pageIndex)
    }

    @Test
    fun `reflow restores position via offsets`() = runTest {
        val store = FakeStore()
        val s = session(store)
        s.ensureView(VIEWPORT, SETTINGS)
        repeat(7) { s.turnPage(1) }
        val before = store.positions["h"]!!

        val big = ReflowSettings(fontSizePx = 20, lineHeightRatio = 2.0f, imageHeightPx = 60)
        s.ensureView(VIEWPORT, big)
        assertEquals(0, s.chapterIndex)
        assertTrue(s.pageIndex > 0)
        val after = store.positions["h"]!!
        // same reading text: reflow must restore to the same block+offset text
        assertEquals(before.quote, after.quote)
        assertEquals(before.href, after.href)
        assertEquals(before.blockIndex, after.blockIndex)
    }

    @Test
    fun `stale offsets restore via quote fallback`() = runTest {
        val store = FakeStore()
        // simulate a stale anchor: offsets beyond any parsed block, quote intact
        val quoteStart = PARA.indexOf("word30 ")
        store.positions["h"] = Locator(
            href = "OEBPS/c1.xhtml",
            blockIndex = 500,
            charOffset = 99999,
            progression = 0.5,
            quote = PARA.substring(quoteStart, quoteStart + 40),
        )
        val s = session(store)
        s.ensureView(VIEWPORT, SETTINGS)
        assertEquals(1, s.chapterIndex)
        // first line whose start >= quoteStart: ceil(quoteStart / 10) / 5 lines per page
        val expectedLine = (quoteStart + 9) / 10
        assertEquals(expectedLine / 5, s.pageIndex)
    }

    @Test
    fun `unknown href falls back to progression`() = runTest {
        val store = FakeStore()
        store.positions["h"] = Locator(
            href = "not-in-book.xhtml",
            blockIndex = 0,
            charOffset = 0,
            progression = 0.6,
            quote = "nope",
        )
        val s = session(store)
        s.ensureView(VIEWPORT, SETTINGS)
        assertEquals(1, s.chapterIndex) // 0.6 * 3 = 1.8 -> chapter 1
        assertEquals(0, s.pageIndex)
    }

    @Test
    fun `changes notify the UI`() = runTest {
        var ticks = 0
        val s = session(FakeStore()) { ticks++ }
        s.ensureView(VIEWPORT, SETTINGS)
        s.turnPage(1)
        assertTrue(ticks >= 2)
    }

    @Test
    fun `book-wide page number matches chapter accumulation - 30 of 300 semantics`() = runTest {
        val store = FakeStore()
        val s = session(store)
        s.ensureView(VIEWPORT, SETTINGS)
        val per = s.layoutFor(0).pageCount()
        assertEquals(per * 3, s.bookTotalPages())
        assertEquals(1, s.bookPageNumber())
        repeat(per) { s.turnPage(1) }
        // first page of chapter 2 = per + 1
        assertEquals(per + 1, s.bookPageNumber())
    }

    @Test
    fun `progress bar seek lands on the right page and commits at gesture end`() = runTest {
        val store = FakeStore()
        val s = session(store)
        s.ensureView(VIEWPORT, SETTINGS)
        val total = s.bookTotalPages()
        val per = s.layoutFor(0).pageCount()
        // seek to the first page of chapter 2
        s.jumpToBookPage(per + 1)
        assertEquals(1, s.chapterIndex)
        assertEquals(0, s.pageIndex)
        // uncommitted drag: position store still holds the old position
        assertEquals("OEBPS/c0.xhtml", store.positions["h"]!!.href)
        s.commitPosition()
        assertEquals("OEBPS/c1.xhtml", store.positions["h"]!!.href)
        // seek within a chapter
        s.jumpToBookPage(per + 3)
        assertEquals(2, s.pageIndex)
        // seek clamps at the very end of the book
        s.jumpToBookPage(total + 100)
        assertEquals(s.layoutFor(s.chapterIndex).pageCount() - 1, s.pageIndex)
    }

    @Test
    fun `progress percent matches page position - 2 of 258 not 8 percent`() = runTest {
        val store = FakeStore()
        val s = session(store)
        s.ensureView(VIEWPORT, SETTINGS)
        val total = s.bookTotalPages()
        repeat(1) { s.turnPage(1) }
        val expected = 1.0 / (total - 1)
        assertEquals(expected, store.positions["h"]!!.progression, 0.01)
    }

    @Test
    fun `book totals change after reflow`() = runTest {
        val store = FakeStore()
        val s = session(store)
        s.ensureView(VIEWPORT, SETTINGS)
        val before = s.bookTotalPages()
        s.ensureView(VIEWPORT, SETTINGS.copy(lineHeightRatio = 2.0f))
        assertTrue(s.bookTotalPages() != before)
    }

    @Test
    fun `ensureView is idempotent for same viewport and settings`() = runTest {
        val store = FakeStore()
        val s = session(store)
        s.ensureView(VIEWPORT, SETTINGS)
        s.turnPage(1)
        val before = store.positions["h"]!!
        s.ensureView(VIEWPORT, SETTINGS)
        assertEquals(before, store.positions["h"]!!)
        assertEquals(1, s.pageIndex)
    }

    @Test
    fun `canTurn is false only at the ends of the book`() = runTest {
        val s = session(FakeStore())
        s.ensureView(VIEWPORT, SETTINGS)
        assertEquals(false, s.canTurn(-1))
        assertEquals(true, s.canTurn(1))

        s.jumpToBookPage(s.bookTotalPages())
        assertEquals(true, s.canTurn(-1))
        assertEquals(false, s.canTurn(1))
    }
}
