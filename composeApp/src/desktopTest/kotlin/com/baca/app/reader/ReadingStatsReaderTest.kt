package com.baca.app.reader

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.baca.shared.domain.stats.PageOutcome
import com.baca.shared.domain.stats.PageReading
import com.baca.shared.parser.parseEpub
import com.baca.shared.parser.parseCbz
import okio.Path.Companion.toPath
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReadingStatsReaderTest {
    @get:Rule val rule = createComposeRule()

    private val store = object : com.baca.shared.domain.reader.PositionStore {
        override val deviceId = "test"
        override fun loadPosition(bookHash: String) = null
        override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
        override fun getLocal(key: String): String? = null
        override fun putLocal(key: String, value: String) {}
    }

    @Test
    fun `turning a page reports the one left, with its locations and time, and notes a milestone`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "stats-reader.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path).openingAtText()
        val reported = mutableListOf<PageReading>()
        rule.setContent {
            ReaderScreen(
                book = book, bookPath = path.toString(), bookHash = "hash", store = store, onBack = {},
                onPageRead = { page ->
                    reported += page
                    PageOutcome(xp = 20, chapterDone = true, bookDone = false, goalMet = false, levelUp = null)
                },
            )
        }
        rule.waitUntil(20_000) { rule.onAllNodesWithText("Title:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // nothing is credited for the page shown while the book was still being laid out
        assertEquals(emptyList(), reported)
        Thread.sleep(300)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitUntil(5_000) { reported.isNotEmpty() }

        val page = reported.single()
        assertTrue(page.total > 0, "the book has locations")
        assertTrue(!page.locations.isEmpty() && page.locations.last < page.total)
        assertTrue(page.chars > 500, "the page's text was counted: ${page.chars}")
        assertTrue(page.dwellMs >= 300, "time on the page: ${page.dwellMs}")
        assertTrue(page.chapters.isNotEmpty())
        rule.onNodeWithText("Chapter done · +20 XP").assertExists()

        // the next page starts where the last one ended
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitUntil(5_000) { reported.size == 2 }
        assertTrue(reported[1].locations.first in page.locations.last..page.locations.last + 1)
        assertEquals(page.total, reported[1].total)
    }

    @Test
    fun `a comic credits each page it leaves, its pages being its locations`() {
        val zip = java.io.File.createTempFile("stats-cbz", ".cbz").apply { deleteOnExit() }
        java.util.zip.ZipOutputStream(zip.outputStream()).use { out ->
            listOf("page1.jpg", "page2.jpg", "page3.jpg").forEach { name ->
                out.putNextEntry(java.util.zip.ZipEntry(name))
                out.write(byteArrayOf(1))
                out.closeEntry()
            }
        }
        val reported = mutableListOf<PageReading>()
        rule.setContent {
            CbzScreen(
                book = parseCbz(zip.absolutePath.toPath(), zip.name), bookPath = zip.absolutePath, bookHash = "comic",
                store = store, onBack = {},
                onPageRead = { page ->
                    reported += page
                    PageOutcome(xp = 1, chapterDone = false, bookDone = false, goalMet = true, levelUp = null)
                },
            )
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Page 1 of 3").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(200)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitUntil(5_000) { reported.isNotEmpty() }

        assertEquals(1, reported.size, "$reported")
        val page = reported.single()
        assertEquals(0..0, page.locations)
        assertEquals(3, page.total)
        assertEquals(0, page.chars)
        assertTrue(page.dwellMs >= 200)
        rule.onNodeWithText("Daily goal met · +1 XP").assertExists()
    }
}
