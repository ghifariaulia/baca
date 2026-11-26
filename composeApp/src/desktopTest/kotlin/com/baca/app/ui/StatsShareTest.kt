package com.baca.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.baca.shared.domain.stats.Achievement
import com.baca.shared.domain.stats.DayRecord
import com.baca.shared.domain.stats.Level
import com.baca.shared.domain.stats.StatsSummary
import org.junit.Rule
import org.junit.Test

class StatsShareTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `share button opens shareable stats card dialog`() {
        val summary = StatsSummary(
            xp = 600,
            level = Level(number = 4, xpIntoLevel = 0, xpForNext = 400),
            streak = 5,
            longestStreak = 10,
            goalMinutes = 15,
            today = DayRecord(seconds = 900, locations = 15, xp = 25, goalMet = true),
            week = listOf("2026-11-26" to DayRecord(seconds = 900, locations = 15, xp = 25, goalMet = true)),
            history = emptyList(),
            locations = 1200,
            seconds = 5400,
            booksFinished = 3,
            achievements = listOf(Achievement("Bookworm", 3L, listOf(1L, 5L), listOf("1 book", "5 books"))),
        )
        rule.setContent {
            AppTheme {
                StatsScreen(summary = summary)
            }
        }

        rule.onNodeWithText("Share").performClick()
        rule.onNodeWithText("Share stats").assertExists()
        rule.onNodeWithText("Lv. 4 · 600 XP").assertExists()
        rule.onNodeWithText("1 of 1 achievements unlocked").assertExists()
        rule.onNodeWithText("Copy text").assertExists()
        rule.onNodeWithText("Save PNG").assertExists()
    }
}
