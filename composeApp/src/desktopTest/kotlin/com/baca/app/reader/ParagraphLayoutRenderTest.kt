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
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Body lines are justified to the column and never wrap into a second row. */
@OptIn(ExperimentalTestApi::class)
class ParagraphLayoutRenderTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `full lines reach the column edge and every drawn line is one row`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "paragraph-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        rule.setContent {
            AppTheme {
                // phone width: 420 - 2 × 25 margin (6% of the page) = 370 column (test density 1)
                Box(Modifier.size(420.dp, 800.dp)) {
                    ReaderScreen(book, path.toString(), "hash", ParagraphTestStore(), onBack = {})
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        repeat(14) {
            rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            rule.waitForIdle()
        }

        val body = DisplaySettings().fontSizeSp.sp
        val lines = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
            .mapNotNull { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                results.firstOrNull()?.takeIf { it.layoutInput.style.fontSize == body }
            }
        assertTrue(lines.size > 10, "page should be full of body text")
        assertTrue(lines.all { it.lineCount == 1 }, "a drawn line wrapped: ${lines.filter { it.lineCount > 1 }.map { it.layoutInput.text }}")
        val justified = lines.count { it.size.width >= 370 - 4 }
        assertTrue(justified >= lines.size / 2, "most lines should be stretched to the column edge, got $justified of ${lines.size}")
    }
}

private class ParagraphTestStore : com.baca.shared.domain.reader.PositionStore {
    override val deviceId = "t"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
