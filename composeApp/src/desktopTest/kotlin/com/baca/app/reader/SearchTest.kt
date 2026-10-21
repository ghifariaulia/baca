package com.baca.app.reader

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Search the book, pick a match, land on its page. */
@OptIn(ExperimentalTestApi::class)
class SearchTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `picking a search result opens the page with the match`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "search-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        rule.setContent {
            ReaderScreen(book, path.toString(), "hash", SearchTestStore(), onBack = {})
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page ", substring = true).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithContentDescription("Search").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Clerval")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("matches", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // the first result's snippet; the sheet closes and the page shows the word
        rule.onAllNodes(hasText("Clerval", substring = true) and !hasSetTextAction())[0].performClick()
        rule.waitForIdle()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("matches", substring = true).fetchSemanticsNodes().isEmpty() }
        assertTrue(
            rule.onAllNodesWithText("Clerval", substring = true).fetchSemanticsNodes().isNotEmpty(),
            "the page should show the match",
        )
    }
}

private class SearchTestStore : com.baca.shared.domain.reader.PositionStore {
    override val deviceId = "t"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
