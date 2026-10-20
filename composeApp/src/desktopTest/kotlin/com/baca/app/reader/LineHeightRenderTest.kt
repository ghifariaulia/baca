package com.baca.app.reader

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import com.baca.shared.domain.DisplaySettings
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Regression: each page line must render at the line height the layout
 * reserved for it. Compose trims line spacing off single-line text by
 * default, which left the bottom of every page blank.
 */
@OptIn(ExperimentalTestApi::class)
class LineHeightRenderTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `body lines render at the reserved line height`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "line-height-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        rule.setContent {
            ReaderScreen(
                book = book,
                bookPath = path.toString(),
                bookHash = "hash",
                store = LineHeightTestStore(),
                onBack = {},
            )
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()

        val display = DisplaySettings()
        val bodySize = display.fontSizeSp.sp
        val expected = display.fontSizeSp * display.lineRatio() // dp == sp at test density
        fun bodyLineHeights() = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                val layout = results.firstOrNull() ?: return@mapNotNull null
                if (layout.layoutInput.style.fontSize != bodySize || layout.lineCount != 1) return@mapNotNull null
                node.boundsInRoot.height / node.layoutInfo.density.density
            }
        // the book opens on its cover image; page forward to running text
        var bodyLines = bodyLineHeights()
        repeat(5) {
            if (bodyLines.isNotEmpty()) return@repeat
            rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            rule.waitForIdle()
            bodyLines = bodyLineHeights()
        }
        assertTrue(bodyLines.isNotEmpty(), "page should show body lines")
        bodyLines.forEach { height ->
            assertTrue(abs(height - expected) <= 1f, "line height $height, expected $expected")
        }
    }
}

private class LineHeightTestStore : com.baca.shared.domain.reader.PositionStore {
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
