package com.baca.app.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import com.baca.shared.domain.Locator
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import okio.Path.Companion.toPath

/** Regression: reopening a book must restore the saved position, not page 1. */
class ResumePositionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun store() = object : com.baca.shared.domain.reader.PositionStore {
        val saved = mutableMapOf<String, Locator>()
        override val deviceId = "test"
        override fun loadPosition(bookHash: String): Locator? = saved[bookHash]
        override fun savePosition(bookHash: String, locator: Locator) { saved[bookHash] = locator }
        override fun getLocal(key: String): String? = null
        override fun putLocal(key: String, value: String) {}
    }

    @Test
    fun `reopening restores the saved locator`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "resume-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)

        val store = store()
        rule.setContent {
            ReaderScreen(book = book, bookPath = path.toString(), bookHash = "hash", store = store, onBack = {})
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { store.saved.containsKey("hash") }
        println("SAVED-1: " + store.saved["hash"])
        assertTrue(store.saved.containsKey("hash"), "opening the book must save a position")

        // simulate a previous session's progress: last chapter
        val mid = Locator(book.chapters.last().href, 0, 0, progression = 0.9)
        store.saved["hash"] = mid

        rule.setContent {
            ReaderScreen(book = book, bookPath = path.toString(), bookHash = "hash", store = store, onBack = {})
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Page", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // the last chapter's prose must be on screen — not chapter 1's opening
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("swallowed", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        val chapterOneOpening = rule.onAllNodesWithText("To Mrs. Saville, England.", substring = true)
            .fetchSemanticsNodes()
        assertTrue(chapterOneOpening.isEmpty(), "reader must not show chapter 1 when resuming the last chapter")
    }
}
