package com.baca.app.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.baca.app.ui.AppTheme
import com.baca.app.ui.LocalAppFonts
import com.baca.shared.domain.DisplaySettings
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Display sheet lists fonts by name, and picking Libron re-renders the page in it. */
class FontChoiceTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `picking Libron renders page text in Libron`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "font-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        lateinit var libron: FontFamily
        rule.setContent {
            AppTheme {
                libron = LocalAppFonts.current.libron
                // phone width: the four font chips share one row
                Box(Modifier.size(360.dp, 760.dp)) {
                    ReaderScreen(
                        book = book,
                        bookPath = path.toString(),
                        bookHash = "hash",
                        store = FontTestStore(),
                        onBack = {},
                    )
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Aa").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Libron").fetchSemanticsNodes().isNotEmpty() }

        listOf("Literata", "DM Sans", "Libron", "Monospace").forEach { label ->
            assertEquals(1, layoutOf(label).lineCount, "$label chip should fit on one line")
        }

        rule.onNodeWithText("Libron").performClick()
        rule.onNodeWithText("Aa").performClick()
        rule.waitForIdle()

        val bodySize = DisplaySettings().fontSizeSp
        val pageFamilies = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .mapNotNull { node -> layoutResult(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action) }
            .filter { it.layoutInput.style.fontSize.value == bodySize.toFloat() || it.layoutInput.style.fontSize.value > bodySize }
            .map { it.layoutInput.style.fontFamily }
        assertTrue(pageFamilies.isNotEmpty(), "page should show text")
        assertTrue(pageFamilies.all { it == libron }, "page text should use Libron, got $pageFamilies")
    }

    private fun layoutOf(text: String): TextLayoutResult =
        assertNotNullLayout(layoutResult(rule.onNodeWithText(text).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action))

    private fun layoutResult(action: ((MutableList<TextLayoutResult>) -> Boolean)?): TextLayoutResult? {
        val results = mutableListOf<TextLayoutResult>()
        action?.invoke(results)
        return results.firstOrNull()
    }

    private fun assertNotNullLayout(result: TextLayoutResult?): TextLayoutResult =
        kotlin.test.assertNotNull(result)
}

private class FontTestStore : com.baca.shared.domain.reader.PositionStore {
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
