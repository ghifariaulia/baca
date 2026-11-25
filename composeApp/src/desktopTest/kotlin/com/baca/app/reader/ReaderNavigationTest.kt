package com.baca.app.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import com.baca.shared.domain.Locator
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.Chapter
import com.baca.shared.parser.FormattedSpan
import com.baca.shared.parser.ParsedEpub
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test

private class NavTestStore : PositionStore {
    val saved = mutableMapOf<String, Locator>()
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): Locator? = saved[bookHash]
    override fun savePosition(bookHash: String, locator: Locator) { saved[bookHash] = locator }
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}

class ReaderNavigationTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `back pill appears after toc jump and returns to previous page`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "nav-back-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        rule.setContent {
            ReaderScreen(book = book, bookPath = path.toString(), bookHash = "hash", store = NavTestStore(), onBack = {})
        }

        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Back", useUnmergedTree = true).assertDoesNotExist()

        rule.onNodeWithContentDescription("Contents").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Letter 1").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Letter 1").performClick()

        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("To Mrs. Saville, England.", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Back").fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText("Back").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Back").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `tapping a footnote link opens inline popup without leaving page`() {
        val book = ParsedEpub(
            title = "Footnote Book",
            author = "Author",
            coverHref = null,
            chapters = listOf(
                Chapter(
                    href = "ch1.xhtml",
                    title = "Chapter 1",
                    blocks = listOf(
                        Block.Text(
                            listOf(
                                FormattedSpan("[1]", false, false, href = "notes.xhtml#n1"),
                                FormattedSpan(" See note.", false, false),
                            ),
                            BlockKind.PARAGRAPH,
                            0,
                        ),
                    ),
                ),
                Chapter(
                    href = "notes.xhtml",
                    title = "Notes",
                    blocks = listOf(
                        Block.Text(listOf(FormattedSpan("1. Explanatory footnote text.", false, false)), BlockKind.PARAGRAPH, 0),
                    ),
                    linear = false,
                    anchorBlocks = mapOf("n1" to 0),
                ),
            ),
        )
        rule.setContent {
            ReaderScreen(book = book, bookPath = "", bookHash = "fn-hash", store = NavTestStore(), onBack = {})
        }

        rule.waitUntil(10_000) { rule.onAllNodesWithText("[1] See note.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("[1] See note.", substring = true).performTouchInput { click(Offset(8f, centerY)) }

        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("1. Explanatory footnote text.").fetchSemanticsNodes().isNotEmpty()
        }
        // Still on Chapter 1
        rule.onNodeWithText("[1] See note.", substring = true).assertExists()
        rule.onNodeWithText("Go to page").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Back").fetchSemanticsNodes().isNotEmpty() }
    }
}
