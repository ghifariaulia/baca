package com.baca.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.baca.app.App
import com.baca.shared.db.Baca
import org.junit.Rule
import org.junit.Test

/** Every main-menu item opens its screen, on phone (bottom nav) and desktop (sidebar). */
class AppNavTest {

    @get:Rule
    val rule = createComposeRule()

    private fun newDb(): Baca {
        val driver = JdbcSqliteDriver("jdbc:sqlite:")
        Baca.Schema.create(driver)
        return Baca(driver)
    }

    private fun goTo(label: String, expect: String) {
        // nav icons carry the item label as their description
        rule.onNodeWithContentDescription(label).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(expect, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun walkMenu() {
        rule.waitForIdle()
        goTo("Bookmarks", "No bookmarks yet")
        goTo("Notes", "No highlights yet")
        goTo("Library", "Library")
        goTo("Bookmarks", "No bookmarks yet")
    }

    @Test
    fun `phone bottom nav reaches bookmarks and notes`() {
        rule.setContent { Box(Modifier.size(360.dp, 760.dp)) { App(newDb()) } }
        walkMenu()
    }

    @Test
    fun `desktop sidebar reaches bookmarks and notes`() {
        rule.setContent { Box(Modifier.fillMaxSize()) { App(newDb()) } }
        walkMenu()
    }
}
