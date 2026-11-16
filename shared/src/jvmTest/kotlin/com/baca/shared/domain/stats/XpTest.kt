package com.baca.shared.domain.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XpTest {

    // a 100-location book in two chapters: 0-49 and 50-99
    private val chapters = listOf(0..49, 50..99)
    private fun page(range: IntRange, dwellMs: Long = 20_000) = PageReading(range, 100, chapters, dwellMs, chars = 1500)

    private fun readAll(state: ReadThrough, ranges: List<IntRange>): Pair<ReadThrough, Int> =
        ranges.fold(state to 0) { (s, xp), r -> credit(s, page(r)).let { it.state to xp + it.xp } }

    private val pages = (0 until 100 step 2).map { it..it + 1 }

    @Test
    fun `levels start at 1 and need 100, 300, 600 XP`() {
        assertEquals(Level(1, 0, 100), levelOf(0))
        assertEquals(Level(1, 99, 100), levelOf(99))
        assertEquals(Level(2, 0, 200), levelOf(100))
        assertEquals(Level(3, 50, 300), levelOf(350))
        assertEquals(4, levelOf(600).number)
    }

    @Test
    fun `location sets merge runs and count what falls in a range`() {
        val set = LocationSet.decode("0-9,20")
        set.add(10..12)
        set.add(19..19)
        assertEquals("0-12,19-20", set.encode())
        assertEquals(15, set.count())
        assertEquals(4, set.countIn(10..19))
        assertEquals("0-12,19-20", LocationSet.decode(set.encode()).encode())
    }

    @Test
    fun `a page flipped past too quickly for its words earns nothing`() {
        val result = credit(ReadThrough(), page(0..1, dwellMs = 1_000))
        assertEquals(0, result.xp)
        assertEquals(ReadThrough(), result.state)
    }

    @Test
    fun `each location pays once a read-through, however the pages fall`() {
        val first = credit(ReadThrough(), page(0..1))
        assertEquals(2, first.xp)
        assertEquals(0, credit(first.state, page(0..1)).xp, "the same page again")
        // a bigger font splits the book into smaller pages: only the new location pays
        assertEquals(1, credit(first.state, page(1..2)).xp)
    }

    @Test
    fun `a chapter pays once most of it is read, not by jumping to its end`() {
        assertFalse(credit(ReadThrough(), page(48..49)).chapterDone)
        val (state, xp) = readAll(ReadThrough(), pages.take(20)) // locations 0-39: 80% of chapter one
        assertEquals(40 + XpRules.CHAPTER, xp)
        assertEquals(listOf(0), state.chapters)
        assertEquals(0, credit(state, page(38..39)).xp, "paid once")
    }

    @Test
    fun `finishing the book pays its bonus once, and a re-read pays pages and chapters again but not the book`() {
        val (firstRead, firstXp) = readAll(ReadThrough(), pages)
        assertEquals(100 + 2 * XpRules.CHAPTER + XpRules.BOOK, firstXp)
        assertTrue(firstRead.finished)
        assertEquals(1, firstRead.finishes)

        val (reRead, reXp) = readAll(firstRead, pages)
        assertEquals(100 + 2 * XpRules.CHAPTER, reXp)
        assertEquals(2, reRead.pass)
        assertEquals(2, reRead.finishes)
    }

    @Test
    fun `going back to the start of an unfinished book doesn't begin a new read-through`() {
        val (halfway, _) = readAll(ReadThrough(), pages.take(30))
        assertEquals(0, credit(halfway, page(0..1)).xp)
        assertEquals(1, credit(halfway, page(0..1)).state.pass)
    }

    @Test
    fun `a book is finished at 98%, where the app counts it finished, with the end matter left unread`() {
        val (state, _) = readAll(ReadThrough(), pages.take(49)) // locations 0-97
        assertTrue(state.finished)
        assertEquals(1, state.finishes)
        assertTrue(state.bookBonusPaid)
    }

    @Test
    fun `jumping to the last page without reading doesn't finish the book`() {
        val result = credit(ReadThrough(), page(98..99))
        assertFalse(result.bookDone)
        assertFalse(result.state.finished)
    }

    @Test
    fun `dates step back across months, years and leap days`() {
        assertEquals("2026-02-28", dayBefore("2026-03-01"))
        assertEquals("2024-02-29", dayBefore("2024-03-01"))
        assertEquals("2025-12-31", dayBefore("2026-01-01"))
        assertEquals(listOf("2026-11-14", "2026-11-15", "2026-11-16"), lastDays("2026-11-16", 3))
        assertEquals(0, weekdayOf("2026-11-16"), "a Monday")
        assertEquals(3, weekdayOf("1970-01-01"), "a Thursday")
        assertEquals(6, weekdayOf("2024-03-03"), "a Sunday")
    }

    @Test
    fun `the streak counts days in a row with the goal met and survives until a whole day is missed`() {
        val met = DayRecord(goalMet = true)
        val days = mapOf("2026-11-13" to met, "2026-11-14" to met, "2026-11-15" to met, "2026-11-10" to met)
        assertEquals(3, currentStreak(days, "2026-11-16"), "today not met yet")
        assertEquals(4, currentStreak(days + ("2026-11-16" to met), "2026-11-16"))
        assertEquals(0, currentStreak(days, "2026-11-17"))
        assertEquals(3, longestStreak(days))
    }
}
