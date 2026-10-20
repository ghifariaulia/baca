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
import com.baca.app.ui.Tokens
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Regression: on a light reading theme, sheet text must not inherit the dark shell's light ink. */
class ReaderChromeContrastTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `paper theme sheet titles use dark ink under a dark system theme`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "contrast-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        rule.setContent {
            // same nesting as App: system-dark shell around the reader
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
        rule.onNodeWithText("Aa").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Display").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(Tokens.Ink, textColor("Display"))
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
