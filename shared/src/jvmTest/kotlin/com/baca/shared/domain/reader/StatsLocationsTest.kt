package com.baca.shared.domain.reader

import com.baca.shared.domain.Locator
import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.Chapter
import com.baca.shared.parser.FormattedSpan
import com.baca.shared.parser.TocEntry
import com.baca.shared.render.GridMetrics
import com.baca.shared.render.ReflowLayout
import com.baca.shared.render.ReflowSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsLocationsTest {

    private val store = object : PositionStore {
        override val deviceId = "test"
        override fun loadPosition(bookHash: String): Locator? = null
        override fun savePosition(bookHash: String, locator: Locator) {}
        override fun getLocal(key: String): String? = null
        override fun putLocal(key: String, value: String) {}
    }

    private val text = (1..200).joinToString(" ") { "word$it" }
    // three chapters of 15000 bytes: 10 locations each
    private val chapters = (0..2).map {
        Chapter("c$it", null, listOf(Block.Text(listOf(FormattedSpan(text, false, false)), BlockKind.PARAGRAPH, 0)), size = 15_000)
    }

    private fun session() = ReaderSession(
        bookHash = "h",
        chapters = chapters,
        repo = store,
        layoutFactory = { i, vp, st -> ReflowLayout(chapters[i].blocks, GridMetrics(), st, vp.columnWidthPx, vp.pageHeightPx) },
        onChange = {},
        toc = chapters.map { TocEntry(it.href, "") },
    )

    @Test
    fun `the pages read through cover every location once in order, whatever the page size`() = runTest {
        for (pageHeight in listOf(100, 300)) {
            val session = session()
            session.ensureView(Viewport(100, pageHeight), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1.0f, imageHeightPx = 60))
            val shown = mutableListOf<IntRange>()
            while (true) {
                shown += session.locationsShown(1)!!
                if (!session.canTurn(1)) break
                session.turnPage(1)
            }
            assertEquals(0, shown.first().first)
            assertEquals(29, shown.last().last)
            shown.zipWithNext { a, b -> assertEquals(true, b.first in a.last..a.last + 1, "pages $a then $b at height $pageHeight") }
        }
    }

    @Test
    fun `chapters are the contents' entries as ranges of locations`() {
        assertEquals(listOf(0..9, 10..19, 20..29), session().chapterLocations())
    }
}
