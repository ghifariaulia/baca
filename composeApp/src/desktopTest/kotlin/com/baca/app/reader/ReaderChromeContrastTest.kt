package com.baca.app.reader

import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import com.baca.app.ui.AppTheme
import com.baca.app.ui.ReaderThemes
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Reader chrome (bars, sheets) follows the app theme; only the page takes the reading theme. */
class ReaderChromeContrastTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `dark app theme gives dark chrome around a Paper page`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "contrast-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        rule.setContent {
            // same nesting as App: dark shell around the reader
            AppTheme(dark = true) {
                Surface {
                    ReaderScreen(
                        book = book,
                        bookPath = path.toString(),
                        bookHash = "hash",
                        store = ContrastTestStore(),
                        onBack = {},
                    )
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        val image = rule.onRoot().captureToImage()
        val pixel = IntArray(1)
        image.readPixels(pixel, 5, 5, 1, 1) // top bar
        assertEquals(DARK_SURFACE.toArgb(), pixel[0], "top bar should use the dark app surface")
        image.readPixels(pixel, image.width - 20, image.height / 2, 1, 1) // page margin
        assertEquals(ReaderThemes.PAPER.background.toArgb(), pixel[0], "page should keep the Paper reading color")

        rule.onNodeWithText("Aa").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Display").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(DARK_INK, textColor("Display"))
    }

    private fun textColor(text: String): Color {
        val node = rule.onNodeWithText(text).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.first().layoutInput.style.color
    }
}

private class ContrastTestStore : com.baca.shared.domain.reader.PositionStore {
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}

// dark color scheme surface and onSurface (AppTheme)
private val DARK_SURFACE = Color(0xFF1A1E26)
private val DARK_INK = Color(0xFFE7E9EE)
