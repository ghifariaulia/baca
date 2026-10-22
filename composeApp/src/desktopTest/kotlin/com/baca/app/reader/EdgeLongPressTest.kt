package com.baca.app.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Long-pressing text near the page edge highlights it instead of turning the page. */
class EdgeLongPressTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `long press near the left edge opens the highlight toolbar`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "edge-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path).openingAtText()
        rule.setContent {
            Box(Modifier.size(420.dp, 800.dp)) {
                ReaderScreen(book, path.toString(), "hash", EdgeTestStore(), onBack = {})
            }
        }
        fun pageLabel() = rule.onAllNodesWithText("Page ", substring = true).fetchSemanticsNodes()
            .single().config[SemanticsProperties.Text].joinToString()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page ", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val before = pageLabel()

        // left third, on the text block below the top bar
        rule.onRoot().performTouchInput { longClick(Offset(60f, 300f)) }
        rule.waitForIdle()

        rule.waitUntil(5_000) { rule.onAllNodesWithText("Note").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(before, pageLabel())
    }
}

private class EdgeTestStore : com.baca.shared.domain.reader.PositionStore {
    override val deviceId = "t"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
