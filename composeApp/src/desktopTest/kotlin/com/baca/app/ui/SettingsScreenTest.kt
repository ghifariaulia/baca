package com.baca.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.baca.app.App
import com.baca.shared.domain.Locator
import com.baca.shared.domain.reader.PositionStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Settings screen edits the same display store the reader reads. */
class SettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun newDb(): com.baca.shared.db.Baca {
        val driver = JdbcSqliteDriver("jdbc:sqlite:")
        com.baca.shared.db.Baca.Schema.create(driver)
        return com.baca.shared.db.Baca(driver)
    }

    private fun store() = object : PositionStore {
        val local = mutableMapOf<String, String>()
        val positions = mutableMapOf<String, Locator>()
        override val deviceId = "test"
        override fun loadPosition(bookHash: String) = positions[bookHash]
        override fun savePosition(bookHash: String, locator: Locator) { positions[bookHash] = locator }
        override fun getLocal(key: String) = local[key]
        override fun putLocal(key: String, value: String) { local[key] = value }
    }

    @Test
    fun `sidebar settings row navigates to the settings screen`() {
        rule.setContent { App(newDb()) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Settings", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Settings").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("FONT SIZE", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `app theme choice overrides the system theme and is remembered`() {
        val db = newDb()
        rule.setContent { App(db) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Settings", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Settings").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("APP THEME").fetchSemanticsNodes().isNotEmpty() }

        // the test host is in light mode; Dark must win over it
        rule.onNodeWithText("Dark").performClick()
        rule.waitForIdle()
        assertEquals("dark", db.settings_localQueries.getLocal(AppThemeChoice.STORE_KEY).executeAsOneOrNull()?.value_)
        // (5,5) is inside the sidebar: dark surface vs white light surface
        assertEquals(Color(0xFF1A1E26).toArgb(), pixelAt(5, 5), "sidebar should use the dark theme")

        rule.onNodeWithText("Light").performClick()
        rule.waitForIdle()
        assertEquals(Tokens.Surface.toArgb(), pixelAt(5, 5))
    }

    private fun pixelAt(x: Int, y: Int): Int {
        val image = rule.onRoot().captureToImage()
        val pixel = IntArray(1)
        image.readPixels(pixel, x, y, 1, 1)
        return pixel[0]
    }

    @Test
    fun `settings edits reading display`() {
        val store = store()
        var back = false
        rule.setContent {
            AppTheme {
                SettingsScreen(store = store, onBack = { back = true })
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("FONT SIZE", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("THEME", substring = true).fetchSemanticsNodes().isNotEmpty())

        rule.onNodeWithText("Black").performClick()
        rule.waitUntil(5_000) {
            store.local["reader_display"].orEmpty().contains("\"theme\":\"black\"")
        }
        assertEquals(true, store.local["reader_display"].orEmpty().contains("\"theme\":\"black\""))

        rule.onNodeWithText("‹ Library").performClick()
        rule.waitUntil(5_000) { back }
    }
}
