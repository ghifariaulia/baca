package com.baca.shared.domain.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AchievementsTest {

    private fun named(name: String, books: Int = 0, streak: Int = 0, pages: Int = 0, seconds: Long = 0, level: Int = 1) =
        achievementsOf(books, streak, pages, seconds, level).single { it.name == name }

    @Test
    fun `an achievement climbs a tier at each goal and shows the next one`() {
        val none = named("Bookworm", books = 0)
        assertEquals(0, none.tier)
        assertEquals("Bookworm", none.title)
        assertEquals("Finish a book", none.description)
        assertEquals(1L, none.nextGoal)

        val seven = named("Bookworm", books = 7)
        assertEquals(2, seven.tier)
        assertEquals("Bookworm II", seven.title)
        assertEquals("Finish 10 books", seven.description)
        assertEquals(10L, seven.nextGoal)
    }

    @Test
    fun `past the last goal every tier is reached and the last goal is shown`() {
        val all = named("Bookworm", books = 250)
        assertEquals(6, all.tier)
        assertEquals("Bookworm VI", all.title)
        assertNull(all.nextGoal)
        assertEquals("Finish 100 books", all.description)
    }

    @Test
    fun `each track counts its own measure`() {
        assertEquals("On a roll II", named("On a roll", streak = 8).title)
        assertEquals("Read 5,000 pages", named("Page turner", pages = 1_200).description)
        assertEquals("Devoted II", named("Devoted", seconds = 12 * 3600L).title)
        assertEquals("Reach level 10", named("Seasoned", level = 7).description)
    }
}
