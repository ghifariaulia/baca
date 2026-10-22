package com.baca.app.reader

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Density
import com.baca.app.ui.AppTheme
import com.baca.shared.domain.DisplaySettings
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Regression: the last line of a full page was drawn past the page's bottom edge. */
@OptIn(ExperimentalTestApi::class)
class PageFitRenderTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `every drawn line fits on the page, with the system font scaled`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "page-fit-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        rule.setContent {
            // a phone's density, with the system font scaled: sp and dp then differ
            CompositionLocalProvider(LocalDensity provides Density(DENSITY, fontScale = FONT_SCALE)) {
                AppTheme {
                    ReaderScreen(book, path.toString(), "hash", PageFitTestStore(), onBack = {})
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val pageHeight = rule.onRoot().fetchSemanticsNode().size.height

        val body = DisplaySettings().fontSizeSp
        var checked = 0
        repeat(20) {
            rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            rule.waitForIdle()
            val bottoms = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
                .filter { node ->
                    val results = mutableListOf<TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                    results.firstOrNull()?.layoutInput?.style?.fontSize?.value?.let { kotlin.math.abs(it - body) < 0.01f } == true
                }
                // unclipped: boundsInRoot stops at the page edge and would hide the overflow
                .map { it.positionInRoot.y + it.size.height }
            if (bottoms.isNotEmpty()) checked++
            bottoms.maxOrNull()?.let { assertTrue(it <= pageHeight + 0.5f, "a line ends at $it, below the page at $pageHeight") }
        }
        assertTrue(checked > 5, "should have checked pages of body text")
    }
}

private const val DENSITY = 2.75f
private const val FONT_SCALE = 1.3f

private class PageFitTestStore : com.baca.shared.domain.reader.PositionStore {
    override val deviceId = "t"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
