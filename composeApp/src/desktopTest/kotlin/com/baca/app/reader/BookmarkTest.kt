package com.baca.app.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.baca.shared.parser.ParsedEpub
import com.baca.shared.parser.parseEpub
import com.baca.shared.domain.AnnotationEntry
import okio.FileSystem
import androidx.compose.ui.semantics.getOrNull
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BookmarkTest {

    @get:Rule
    val rule = createComposeRule()

    private val annotations = mutableStateListOf<AnnotationEntry>()

    private fun book(): Pair<ParsedEpub, String> {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "bookmark-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        return parseEpub(path) to path.toString()
    }

    @Composable
    private fun Reader(book: ParsedEpub, path: String) {
        ReaderScreen(
            book = book,
            bookPath = path,
            bookHash = "hash",
            store = BookmarkTestStore(),
            onBack = {},
            annotations = annotations.toList(),
            onAnnotationUpsert = { entry -> annotations.removeAll { it.id == entry.id }; annotations.add(entry) },
            onAnnotationDelete = { id -> annotations.removeAll { it.id == id } },
        )
    }

    private fun waitForReader() {
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // let the debounced first layout (and its position restore) settle
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    private fun waitForIcon(description: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `bookmark toggles per page and lists in the contents sheet`() {
        val (book, path) = book()
        rule.setContent { Box(Modifier.size(360.dp, 760.dp)) { Reader(book, path) } }
        waitForReader()

        rule.onNodeWithContentDescription("Add bookmark").performClick()
        waitForIcon("Remove bookmark")
        assertEquals(listOf("bookmark"), annotations.map { it.kind })

        // other pages are not marked; coming back shows the mark again
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        waitForIcon("Add bookmark")
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIcon("Remove bookmark")

        // listed with notes in the contents sheet
        rule.onNodeWithContentDescription("Contents").performClick()
        rule.onNodeWithText("Bookmarks (1)").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithContentDescription("Bookmark").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("Notes (0)").assertExists() // bookmarks are not counted as notes
        listOf("Contents", "Notes (0)", "Bookmarks (1)").forEach { label ->
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onAllNodesWithText(label).fetchSemanticsNodes().last().config
                .getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            assertEquals(1, results.first().lineCount, "$label tab should fit on one line")
        }
        rule.onNodeWithText("Done").performClick()

        rule.onNodeWithContentDescription("Remove bookmark").performClick()
        waitForIcon("Add bookmark")
        assertTrue(annotations.isEmpty())
    }

    @Test
    fun `desktop contents panel has a notes tab`() {
        val (book, path) = book()
        annotations.add(
            AnnotationEntry(
                id = "b1", kind = "bookmark",
                reflowAnchor = com.baca.shared.domain.ReflowAnchorDto(
                    book.chapters[0].href,
                    com.baca.shared.domain.PointDto(0, 0),
                    com.baca.shared.domain.PointDto(0, 0),
                    "",
                ),
                createdAt = 0, lamport = 1,
            ),
        )
        rule.setContent { Box(Modifier.size(1200.dp, 800.dp)) { Reader(book, path) } }
        waitForReader()

        rule.onNodeWithContentDescription("Contents").performClick()
        rule.onNodeWithText("Bookmarks (1)").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Bookmark").assertExists()
    }
}

private class BookmarkTestStore : com.baca.shared.domain.reader.PositionStore {
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
