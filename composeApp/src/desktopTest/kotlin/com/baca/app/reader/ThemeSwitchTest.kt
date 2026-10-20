package com.baca.app.reader

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Regression: picking a theme in the Display sheet must repaint the page. */
class ThemeSwitchTest {

    @get:Rule
    val rule = createComposeRule()

    private fun content() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "theme-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        rule.setContent {
            ReaderScreen(
                book = book,
                bookPath = path.toString(),
                bookHash = "hash",
                store = ThemeTestStore(),
                onBack = {},
            )
        }
    }

    @Test
    fun `theme switch repaints the page`() {
        content()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Aa").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("THEME", substring = true).fetchSemanticsNodes().isNotEmpty() }

        fun pageSample(): Int {
            rule.waitForIdle()
            val img = rule.onRoot().captureToImage()
            val pixels = IntArray(1)
            img.readPixels(pixels, 100, 300, 1, 1)
            return pixels[0]
        }
        val before = pageSample()
        rule.onNodeWithText("White").performClick()
        rule.waitForIdle()
        val after = pageSample()
        assertNotEquals(before, after, "page background must change when the theme changes")
        rule.onNodeWithText("Black").performClick()
        rule.waitForIdle()
        val afterBlack = pageSample()
        assertNotEquals(after, afterBlack, "switching back must repaint again")
    }
}

private class ThemeTestStore : com.baca.shared.domain.reader.PositionStore {
    val saved = mutableMapOf<String, com.baca.shared.domain.Locator>()
    override val deviceId = "test"
    override fun loadPosition(bookHash: String) = saved[bookHash]
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) { saved[bookHash] = locator }
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
