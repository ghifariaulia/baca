package com.baca.app.reader

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.baca.shared.domain.DisplaySettings
import com.baca.shared.domain.KEY_DISPLAY
import com.baca.shared.domain.encode
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/**
 * The running text is drawn at its exact size, as a browser draws it: 125% of 16 at a
 * density of 2.625 is 52.5px, not rounded to 53.
 */
@OptIn(ExperimentalTestApi::class)
class FractionalFontRenderTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `body text keeps a size that falls between whole pixels`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "fractional-font-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path).openingAtText()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.625f)) {
                ReaderScreen(book, path.toString(), "hash", FractionalFontStore(DisplaySettings(fontSizePercent = 125)), onBack = {})
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        fun sizes() = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
            .mapNotNull { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                results.firstOrNull()?.layoutInput?.style?.fontSize
            }
        // the book opens on its cover picture; page forward to running text (bigger than the chrome's)
        var sizes = sizes()
        repeat(5) {
            if (sizes.any { it.value > 16f }) return@repeat
            rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            rule.waitForIdle()
            sizes = sizes()
        }
        assertTrue(20.sp in sizes, "no text at 20sp (52.5px); sizes drawn: ${sizes.distinct()}")
    }
}

private class FractionalFontStore(display: DisplaySettings) : com.baca.shared.domain.reader.PositionStore {
    private val local = mutableMapOf(KEY_DISPLAY to display.encode())
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = local[key]
    override fun putLocal(key: String, value: String) {
        local[key] = value
    }
}
