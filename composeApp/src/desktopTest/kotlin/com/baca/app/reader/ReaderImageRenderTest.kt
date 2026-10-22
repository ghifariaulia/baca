package com.baca.app.reader

import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import com.baca.shared.domain.Locator
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.parser.Block
import com.baca.shared.parser.EpubImages
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.assertTrue

private class SeededStore(position: Locator?) : PositionStore {
    private val saved = mutableMapOf<String, Locator>()
    private val local = mutableMapOf<String, String>()

    init {
        if (position != null) saved["cover-hash"] = position
    }

    override val deviceId = "test"
    override fun loadPosition(bookHash: String): Locator? = saved[bookHash]
    override fun savePosition(bookHash: String, locator: Locator) {
        saved[bookHash] = locator
    }
    override fun getLocal(key: String): String? = local[key]
    override fun putLocal(key: String, value: String) {
        local[key] = value
    }
}

class ReaderImageRenderTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `cover chapter page renders the image`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "rend-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)

        val cover = book.chapters.first { it.blocks.any { b -> b is Block.Image } }
        val store = SeededStore(
            Locator(href = cover.href, blockIndex = 0, charOffset = 0, progression = 0.0, quote = ""),
        )

        rule.setContent {
            CompositionLocalProvider(LocalBookImages provides BookImages(EpubImages(path))) {
                ReaderScreen(book, path.toString(), "cover-hash", store, onBack = {})
            }
        }

        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("image").fetchSemanticsNodes().isNotEmpty()
        }
        rule.mainClock.advanceTimeBy(2000)

        val screen = rule.onRoot().captureToImage()
        val buffer = kotlin.IntArray(screen.width * screen.height)
        screen.readPixels(buffer)
        val distinct = buffer.distinct().size
        assertTrue(distinct > 1000, "expected the cover image to draw, only $distinct distinct colors")
    }

    @Test
    fun `a long press on a picture opens it full screen, and a tap closes it`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "picture-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        rule.setContent {
            CompositionLocalProvider(LocalBookImages provides BookImages(EpubImages(path))) {
                ReaderScreen(book, path.toString(), "picture-hash", SeededStore(null), onBack = {})
            }
        }
        // Frankenstein opens on its cover
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("image").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("image").performTouchInput { longClick() }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("picture").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("picture").performTouchInput { click() }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("picture").fetchSemanticsNodes().isEmpty() }
    }
}
