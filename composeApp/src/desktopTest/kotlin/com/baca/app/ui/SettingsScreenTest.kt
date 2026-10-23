package com.baca.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.baca.app.App
import com.baca.shared.domain.Locator
import com.baca.shared.domain.reader.PositionStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Reading display").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `app theme choice overrides the system theme and is remembered`() {
        val db = newDb()
        rule.setContent { App(db) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Settings", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Settings").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Reading display").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("App").performClick()
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
    fun `library folder section offers choose and rescan`() {
        var chosen = 0
        var rescans = 0
        var label by mutableStateOf<String?>(null)
        rule.setContent {
            AppTheme {
                SettingsScreen(
                    store = store(), onBack = {}, libraryFolderLabel = label,
                    onChooseLibraryFolder = { chosen++ }, onRescan = { rescans++ },
                )
            }
        }
        rule.onNodeWithText("App").performClick()
        rule.onNodeWithText("Choose folder").performClick()
        assertTrue(rule.onAllNodesWithText("Rescan").fetchSemanticsNodes().isEmpty(), "nothing to rescan without a folder")

        label = "Books"
        rule.waitForIdle()
        rule.onNodeWithText("Change folder").performClick()
        rule.onNodeWithText("Rescan").performClick()
        rule.waitForIdle()
        assertEquals(2, chosen)
        assertEquals(1, rescans)
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
        rule.onNodeWithText("Reading display").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("FONT SIZE", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("THEME", substring = true).fetchSemanticsNodes().isNotEmpty())

        rule.onNodeWithText("Black").performScrollTo().performClick()
        rule.waitUntil(5_000) {
            store.local["reader_display"].orEmpty().contains("\"theme\":\"black\"")
        }
        assertEquals(true, store.local["reader_display"].orEmpty().contains("\"theme\":\"black\""))

        rule.onNodeWithText("‹ Settings").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Backup and restore").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("‹ Library").performClick()
        rule.waitUntil(5_000) { back }
    }

    @Test
    fun `settings is a menu whose rows open their pages`() {
        rule.setContent {
            AppTheme {
                SettingsScreen(store = store(), onBack = {})
            }
        }
        listOf("App", "Reading display", "Backup and restore", "About Baca").forEach {
            rule.onNodeWithText(it).assertExists()
        }
        // the pages' own settings are not on the menu
        assertTrue(rule.onAllNodesWithText("FONT SIZE").fetchSemanticsNodes().isEmpty())

        rule.onNodeWithText("About Baca").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("VERSION").assertExists()
        rule.onNodeWithText("Baca $APP_VERSION").assertExists()
        rule.onNodeWithText("‹ Settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Reading display").assertExists()
    }

    @Test
    fun `settings offers backup and restore`() {
        var created = 0
        var restored = 0
        var message by mutableStateOf<String?>(null)
        rule.setContent {
            AppTheme {
                SettingsScreen(
                    store = store(), onBack = {}, backupMessage = message,
                    onCreateBackup = { created++ }, onRestoreBackup = { restored++ },
                )
            }
        }
        rule.onNodeWithText("Backup and restore").performClick()
        rule.onNodeWithText("Create backup").performScrollTo().performClick()
        rule.onNodeWithText("Restore…").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(1, created)
        assertEquals(1, restored)

        message = "Backup saved."
        rule.waitForIdle()
        rule.onNodeWithText("Backup saved.").performScrollTo().assertExists()
    }
}
