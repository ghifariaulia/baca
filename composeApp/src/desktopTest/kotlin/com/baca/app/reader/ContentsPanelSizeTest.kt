package com.baca.app.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import com.baca.app.ui.ReaderThemes
import com.baca.shared.parser.ParsedEpub
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Desktop contents panel is as tall as its content, not the whole window. */
class ContentsPanelSizeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun openPanel(): ParsedEpub {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "panel-size-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                ReaderScreen(book = book, bookPath = path.toString(), bookHash = "hash", store = PanelTestStore(), onBack = {})
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Contents").performClick()
        rule.waitForIdle()
        return book
    }

    @Test
    fun `short panel ends below its content, not at the window bottom`() {
        openPanel()
        rule.onNodeWithText("Notes (0)").performClick() // empty tab: short panel
        rule.waitForIdle()

        // right side, halfway down: inside the old full-height panel, below
        // the new one; outside the centered text column either way
        val image = rule.onRoot().captureToImage()
        val pixel = IntArray(1)
        image.readPixels(pixel, image.width - 100, image.height / 2, 1, 1)
        assertEquals(ReaderThemes.PAPER.background.toArgb(), pixel[0], "page background should show below the panel")
    }

    @Test
    fun `long contents still scroll inside the window`() {
        val book = openPanel()
        rule.onNodeWithText(book.toc.last().title).performScrollTo().assertIsDisplayed()
    }
}

private class PanelTestStore : com.baca.shared.domain.reader.PositionStore {
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
