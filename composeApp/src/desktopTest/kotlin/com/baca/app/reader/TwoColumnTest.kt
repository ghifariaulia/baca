package com.baca.app.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baca.app.ui.AppTheme
import com.baca.shared.domain.DisplaySettings
import com.baca.shared.domain.Locator
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.domain.saveDisplay
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Two columns: a spread of two pages side by side, a turn moves past both. Scrolled flow: pages stacked in a list. */
@OptIn(ExperimentalTestApi::class)
class TwoColumnTest {

    @get:Rule
    val rule = createComposeRule()

    private fun bodyLines(): List<Pair<Float, String>> {
        val body = DisplaySettings().fontSizeSp.sp
        return rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes().mapNotNull { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.firstOrNull()?.takeIf { it.layoutInput.style.fontSize == body }?.let { node.positionInRoot.x to it.layoutInput.text.text }
        }
    }

    private fun open(columns: Int, store: ColumnStore, flow: String = "paged") {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "two-column-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path).openingAtText()
        store.saveDisplay(DisplaySettings(columns = columns, flow = flow))
        rule.setContent {
            AppTheme {
                Box(Modifier.size(800.dp, 600.dp)) {
                    ReaderScreen(book, path.toString(), "hash", store, onBack = {})
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    @Test
    fun `two columns draw two pages side by side`() {
        open(2, ColumnStore())
        val lines = bodyLines()
        assertTrue(lines.size > 10, "page should be full of body text")
        assertTrue(lines.any { it.first < 400 } && lines.any { it.first >= 400 }, "lines should start in both halves: ${lines.map { it.first }.distinct()}")
    }

    @Test
    fun `one column stays in a single column`() {
        open(1, ColumnStore())
        val xs = bodyLines().map { it.first }.distinct()
        assertTrue(xs.size <= 2 && xs.all { it < 400 + 1 || xs.max() - xs.min() < 60 }, "one column spans: $xs")
    }

    @Test
    fun `a turn moves past both columns`() {
        val store = ColumnStore()
        open(2, store)
        val first = bodyLines().map { it.second }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitUntil(10_000) { bodyLines().map { it.second } != first }
        val next = bodyLines().map { it.second }
        assertEquals(emptySet(), first.toSet().intersect(next.toSet()) - setOf(""), "no line should show on both spreads")
    }

    @Test
    fun `scrolled flow stacks pages in one column and a turn follows the page`() {
        val store = ColumnStore()
        open(1, store, flow = "scrolled")
        val xs = bodyLines().map { it.first }.distinct()
        assertTrue(xs.all { it < 400 }, "scrolled text should stay in one column: $xs")
        val first = bodyLines().map { it.second }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitUntil(10_000) { bodyLines().map { it.second }.firstOrNull() != first.firstOrNull() }
    }
}

private class ColumnStore : PositionStore {
    private val local = HashMap<String, String>()
    override val deviceId = "t"
    override fun loadPosition(bookHash: String): Locator? = null
    override fun savePosition(bookHash: String, locator: Locator) {}
    override fun getLocal(key: String): String? = local[key]
    override fun putLocal(key: String, value: String) { local[key] = value }
}
