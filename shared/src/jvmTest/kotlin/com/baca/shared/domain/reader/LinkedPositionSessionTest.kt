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

private class LinkedStore(val own: Locator?, val linked: Locator?) : PositionStore {
    override val deviceId = "test"
    override fun loadPosition(bookHash: String) = own
    override fun loadLinkedPosition(bookHash: String) = linked
    override fun savePosition(bookHash: String, locator: Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}

private val TEXT = (1..200).joinToString(" ") { "word$it" }

private fun chapter(href: String) = Chapter(
    href, null,
    listOf(
        Block.Text(listOf(FormattedSpan("Heading", false, false)), BlockKind.HEADING, 1),
        Block.Text(listOf(FormattedSpan(TEXT, false, false)), BlockKind.PARAGRAPH, 0),
    ),
)

class LinkedPositionSessionTest {

    private val chapters = listOf(chapter("Text/a.xhtml"), chapter("Text/b.xhtml"), chapter("Text/c.xhtml"))

    private fun session(store: PositionStore) = ReaderSession(
        "h", chapters, store,
        layoutFactory = { i, vp, st -> ReflowLayout(chapters[i].blocks, GridMetrics(), st, vp.columnWidthPx, vp.pageHeightPx) },
        onChange = {},
    )

    private val viewport = Viewport(100, 100)
    private val settings = ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.0f)

    @Test
    fun `newer position from another file restores by quote, not offsets`() = runTest {
        val quoteAt = TEXT.indexOf("word120")
        // offsets say "heading of the first chapter"; only the quote is right
        val linked = Locator("Text/c.xhtml", blockIndex = 0, charOffset = 0, progression = 0.0, quote = TEXT.substring(quoteAt, quoteAt + 40))
        val s = session(LinkedStore(own = null, linked = linked))

        s.ensureView(viewport, settings)

        assertEquals(2, s.chapterIndex)
        assertEquals(s.layoutFor(2).pageForPosition(1, quoteAt, "")!!.pageIndex, s.pageIndex)
    }

    @Test
    fun `unknown quote falls back to the book-wide fraction`() = runTest {
        val linked = Locator("x", 0, 0, progression = 0.5, quote = "text this edition does not contain")
        val s = session(LinkedStore(own = null, linked = linked))

        s.ensureView(viewport, settings)

        val expected = 1 + (0.5 * (s.bookTotalPages() - 1)).toInt()
        assertEquals(expected, s.bookPageNumber())
    }
}
