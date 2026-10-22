package com.baca.app.reader

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull

/** Regression: arrow keys must turn pages on desktop. */
@OptIn(ExperimentalTestApi::class)
class ArrowKeyPagingTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `right and left arrows turn pages`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "arrow-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path).openingAtText()
        val store = ArrowTestStore()
        rule.setContent {
            ReaderScreen(
                book = book,
                bookPath = path.toString(),
                bookHash = "hash",
                store = store,
                onBack = {},
            )
        }
        // where the page starts, as each turn saves it: page numbers count locations, not pages
        fun currentPage(): Pair<Int, Int>? = store.saved?.let { it.blockIndex to it.charOffset }
        fun waitForPage(test: (Pair<Int, Int>?) -> Boolean) = rule.waitUntil(10_000) { test(currentPage()) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page ", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // let the debounced first layout settle
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val start = assertNotNull(currentPage())
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        waitForPage { it != start }

        // pick a theme, then close the Display sheet: the focused chip leaves
        // composition with the sheet and used to take keyboard focus with it
        rule.onNodeWithText("Aa").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("White").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("White").performClick()
        rule.onNodeWithText("Aa").performClick()
        rule.waitForIdle()

        val before = assertNotNull(currentPage())
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        waitForPage { it != before }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        waitForPage { it == before }
    }
}

private class ArrowTestStore : com.baca.shared.domain.reader.PositionStore {
    var saved: com.baca.shared.domain.Locator? = null
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {
        saved = locator
    }
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
