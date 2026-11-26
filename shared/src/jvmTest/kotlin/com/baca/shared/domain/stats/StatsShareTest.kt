package com.baca.shared.domain.stats

import kotlin.test.Test
import kotlin.test.assertTrue

class StatsShareTest {

    @Test
    fun `formats reading stats summary for sharing`() {
        val summary = StatsSummary(
            xp = 1250,
            level = Level(number = 5, xpIntoLevel = 250, xpForNext = 500),
            streak = 7,
            longestStreak = 14,
            goalMinutes = 15,
            today = DayRecord(seconds = 900, locations = 20, xp = 30, goalMet = true),
            week = emptyList(),
            history = emptyList(),
            locations = 3420,
            seconds = 12_600,
            booksFinished = 4,
            achievements = emptyList(),
        )

        val text = formatStatsShareText(summary)
        assertTrue(text.contains("Level 5 · 1,250 XP"))
        assertTrue(text.contains("Streak: 7 days (best 14)"))
        assertTrue(text.contains("Books finished: 4"))
        assertTrue(text.contains("Pages read: 3,420"))
        assertTrue(text.contains("Time read: 3h 30m"))
    }
}
