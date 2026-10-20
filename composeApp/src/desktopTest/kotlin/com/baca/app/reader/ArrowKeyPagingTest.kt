package com.baca.app.reader

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
        val book = parseEpub(path)
        rule.setContent {
            ReaderScreen(
                book = book,
                bookPath = path.toString(),
                bookHash = "hash",
                store = ArrowTestStore(),
                onBack = {},
            )
        }
        // re-pagination (theme change) moves the number, so assert relative steps
        fun currentPage(): Int? = rule.onAllNodesWithText("Page ", substring = true).fetchSemanticsNodes()
            .firstNotNullOfOrNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                    ?.let { Regex("""Page (\d+) of""").find(it)?.groupValues?.get(1)?.toInt() }
            }
        fun waitForPage(n: Int) = rule.waitUntil(10_000) { currentPage() == n }
        rule.waitUntil(10_000) { currentPage() != null }
        // let the debounced first layout settle (it moves from the cover to the text)
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val start = assertNotNull(currentPage())
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        waitForPage(start + 1)

        // pick a theme, then close the Display sheet: the focused chip leaves
        // composition with the sheet and used to take keyboard focus with it
        rule.onNodeWithText("Aa").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("White").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("White").performClick()
        rule.onNodeWithText("Aa").performClick()
        rule.waitForIdle()

        val before = assertNotNull(currentPage())
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        waitForPage(before + 1)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        waitForPage(before)
    }
}

private class ArrowTestStore : com.baca.shared.domain.reader.PositionStore {
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
