package com.baca.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.assertEquals
import com.baca.shared.domain.stats.EarlierBook
import com.baca.shared.domain.stats.PageReading
import com.baca.shared.domain.stats.ReadingStats
import com.baca.shared.domain.stats.StatsStore
import org.junit.Rule
import org.junit.Test

class StatsScreenTest {
    @get:Rule val rule = createComposeRule()

    private class MapStore : StatsStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun withPrefix(prefix: String) = map.filterKeys { it.startsWith(prefix) }
    }

    @Test
    fun `the stats screen shows the level, today's reading, totals and achievements`() {
        val stats = ReadingStats(MapStore())
        stats.catchUpOnce { listOf(EarlierBook("emma", 200, listOf(0..39, 40..79, 80..119, 120..159, 160..199), progress = 1f, finished = true)) }
        (0 until 50).forEach { stats.pageRead("dracula", PageReading(it * 2..it * 2 + 1, 300, emptyList(), 20_000, 1500), "2026-11-16") }
        val summary = stats.summary("2026-11-16")
        rule.setContent { AppTheme { StatsScreen(summary) } }

        rule.onNodeWithText("${summary.level.number}").assertExists()
        rule.onNodeWithText("${summary.xp} XP").assertExists()
        // 50 pages of 20 seconds, today and in all
        assertEquals(2, rule.onAllNodesWithText("16 min").fetchSemanticsNodes().size)
        rule.onNodeWithText("Goal met").assertExists()
        // earlier reading counts in the totals: Emma's 200 pages and the 100 read today
        rule.onNodeWithText("300").assertExists()
        assertEquals(0, rule.onAllNodesWithText("Earlier reading").fetchSemanticsNodes().size)
        // one book finished: Bookworm's first tier, and the next asks for five
        rule.onNodeWithText("Bookworm I").assertExists()
        rule.onNodeWithText("Finish 5 books").assertExists()
        rule.onNodeWithText("1 / 5").assertExists()
    }

    @Test
    fun `daily goal stepper and history calendar are interactive`() {
        val stats = ReadingStats(MapStore())
        stats.pageRead("dracula", PageReading(0..1, 300, emptyList(), 300_000L, 1500), "2026-11-16")
        var changedGoal = 0
        rule.setContent {
            AppTheme {
                StatsScreen(
                    summary = stats.summary("2026-11-16"),
                    onGoalMinutesChange = { changedGoal = it },
                )
            }
        }

        rule.onNodeWithText("HISTORY").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Increase daily goal").performScrollTo().performClick()
        assertEquals(20, changedGoal)
    }
}
