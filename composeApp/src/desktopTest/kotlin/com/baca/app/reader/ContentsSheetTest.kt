package com.baca.app.reader

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.baca.shared.domain.Locator
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.onFirst
import kotlin.test.assertTrue

private class SeededStore2(position: Locator?) : PositionStore {
    val saved = mutableMapOf<String, Locator>()
    init { position?.let { saved["hash"] = it } }
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): Locator? = saved[bookHash]
    override fun savePosition(bookHash: String, locator: Locator) { saved[bookHash] = locator }
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}

class ContentsSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private fun content() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "toc-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        rule.setContent {
            ReaderScreen(book = book, bookPath = path.toString(), bookHash = "hash", store = SeededStore2(null), onBack = {})
        }
    }

    @Test
    fun `contents sheet opens and shows toc entries`() {
        content()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Contents").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Contents").fetchSemanticsNodes().isNotEmpty() }
        val tocTexts = rule.onAllNodesWithText("Letter 1", substring = true).fetchSemanticsNodes()
        assertTrue(tocTexts.isNotEmpty(), "toc entries should render in the contents sheet")
    }

    @Test
    fun `display sheet still opens via Aa`() {
        content()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("☐ none", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Aa").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Display").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `contents row click jumps to chapter`() {
        content()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Contents").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Contents").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Letter 1").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("To Mrs. Saville, England.", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
