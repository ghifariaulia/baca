package com.baca.shared.domain.stats

import com.baca.shared.db.DbStatsStore
import com.baca.shared.db.inMemoryDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingStatsTest {

    private fun stats() = ReadingStats(DbStatsStore(inMemoryDb().first))
    private val chapters = listOf(0..49, 50..99)
    private fun page(range: IntRange, dwellMs: Long = 20_000) = PageReading(range, 100, chapters, dwellMs, chars = 1500)
    private fun tenths(total: Int) = (0 until 10).map { it * total / 10 until (it + 1) * total / 10 }
    private fun fifths(total: Int) = (0 until 5).map { it * total / 5 until (it + 1) * total / 5 }

    @Test
    fun `pages add to the day, the goal pays once, and levels up are reported`() {
        val stats = stats()
        // 50 pages of 20 seconds: past the 15-minute goal and past level 2's 100 XP
        val outcomes = (0 until 100 step 2).map { stats.pageRead("book", page(it..it + 1), "2026-11-16") }
        assertEquals(1, outcomes.count { it.goalMet })
        assertEquals(2, outcomes.mapNotNull { it.levelUp }.first())
        assertEquals(1, outcomes.count { it.bookDone })

        val summary = stats.summary("2026-11-16")
        assertEquals(100 + 2 * XpRules.CHAPTER + XpRules.BOOK + XpRules.DAILY_GOAL, summary.xp)
        assertEquals(1000, summary.today.seconds)
        assertEquals(100, summary.today.locations)
        assertEquals(1, summary.streak)
        assertEquals(1, summary.booksFinished)
        assertTrue(summary.achievements.first { it.title == "First book" }.unlocked)
        assertEquals("2026-11-16", summary.week.last().first)
    }

    @Test
    fun `a page left open long counts five minutes of reading at most`() {
        val stats = stats()
        stats.pageRead("book", page(0..1, dwellMs = 3_600_000), "2026-11-16")
        assertEquals(300, stats.summary("2026-11-16").today.seconds)
    }

    @Test
    fun `earlier reading is caught up once from saved positions, copies of an edition counted once`() {
        val stats = stats()
        val books = listOf(
            EarlierBook("frank", locations = 300, chapters = tenths(300), progress = 0.4f, finished = false),
            EarlierBook("frank", locations = 300, chapters = tenths(300), progress = 0.1f, finished = false),
            EarlierBook("emma", locations = 200, chapters = fifths(200), progress = 0.99f, finished = true),
            EarlierBook("unread", locations = 100, chapters = chapters, progress = 0f, finished = false),
        )
        var asked = 0
        stats.catchUpOnce { asked++; books }
        stats.catchUpOnce { asked++; books }
        assertEquals(1, asked)

        val summary = stats.summary("2026-11-16")
        val frank = 120 + 4 * XpRules.CHAPTER
        val emma = 200 + 5 * XpRules.CHAPTER + XpRules.BOOK
        assertEquals(frank + emma, summary.xp)
        assertEquals(320, summary.locations)
        assertEquals(1, summary.booksFinished)
        assertEquals(0, summary.streak, "no days are known for earlier reading")
    }

    @Test
    fun `after the catch-up, reading on pays only new locations and earlier chapters don't pay again`() {
        val stats = stats()
        stats.catchUpOnce { listOf(EarlierBook("book", 100, chapters, progress = 0.6f, finished = false)) }
        assertEquals(0, stats.pageRead("book", page(10..11), "2026-11-16").xp, "read before")
        assertEquals(2, stats.pageRead("book", page(60..61), "2026-11-16").xp)
    }

    @Test
    fun `pages read before the catch-up ran are merged with it, not paid twice`() {
        val stats = stats()
        (0 until 10 step 2).forEach { stats.pageRead("book", page(it..it + 1), "2026-11-16") }
        val live = stats.summary("2026-11-16").xp
        assertEquals(10, live)
        stats.catchUpOnce { listOf(EarlierBook("book", 100, chapters, progress = 0.5f, finished = false)) }
        // locations 10-49 are new, and chapter one (0-49) is now read through
        assertEquals(live + 40 + XpRules.CHAPTER, stats.summary("2026-11-16").xp)
        assertEquals(0, stats.pageRead("book", page(20..21), "2026-11-16").xp)
    }

    @Test
    fun `stats with nothing read are all zero`() {
        val summary = stats().summary("2026-11-16")
        assertEquals(0, summary.xp)
        assertEquals(Level(1, 0, 100), summary.level)
        assertNull(summary.achievements.firstOrNull { it.unlocked })
    }
}
