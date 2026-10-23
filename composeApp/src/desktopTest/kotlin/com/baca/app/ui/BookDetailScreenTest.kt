package com.baca.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.baca.shared.db.BookRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class BookDetailScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun row() = BookRow(
        bookHash = "hash",
        title = "Strange Pictures",
        author = "Uketsu",
        path = "/books/h.epub",
        format = "epub",
        coverPath = null,
        addedAt = System.currentTimeMillis() - 3L * 24 * 3600 * 1000,
        lamport = 1L,
        deleted = false,
    )

    @Test
    fun showsMetaContinueAndRemove() {
        val r = row()
        var continued = 0
        var removed = 0
        rule.setContent {
            AppTheme {
                BookDetailScreen(
                    row = r,
                    progress = 0.12f,
                    lastReadAt = System.currentTimeMillis() - 5L * 60_000,
                    addedAt = r.addedAt,
                    sizeBytes = 1_200_000,
                    onBack = {},
                    onContinue = { continued++ },
                    onRemove = { removed++ },
                )
            }
        }
        rule.onNodeWithText("EPUB").assertExists()
        rule.onNodeWithText("1.1 MB").assertExists()
        rule.onNodeWithText("3 days ago").assertExists()
        rule.onNodeWithText("5 min ago").assertExists()
        rule.onNodeWithText("12%").assertExists()
        rule.onNodeWithText("Continue reading").performClick()
        assertEquals(1, continued)
        rule.onNodeWithText("Remove from library").performScrollTo().performClick()
        assertEquals(1, removed)
    }

    @Test
    fun `unread book says Start reading and can be marked finished`() {
        val r = row()
        var finished by mutableStateOf(false)
        rule.setContent {
            AppTheme {
                BookDetailScreen(
                    row = r, progress = null, lastReadAt = null, addedAt = r.addedAt, sizeBytes = 1,
                    onBack = {}, onContinue = {}, onRemove = {},
                    finished = finished, onToggleFinished = { finished = !finished },
                )
            }
        }
        rule.onNodeWithText("Start reading").assertExists()
        rule.onNodeWithText("Not started").assertExists()

        rule.onNodeWithText("Mark as finished").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Finished").assertExists()
        rule.onNodeWithText("Mark as unread").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Mark as finished").assertExists()
    }

    @Test
    fun `lists what the book says about itself and how long it is`() {
        val r = row()
        val info = com.baca.shared.parser.BookInfo(
            meta = com.baca.shared.parser.BookMeta(
                publisher = "Lackington", published = "1818", subjects = listOf("Gothic fiction", "Science fiction"),
                rights = "Public domain", series = "Classics", description = "A scientist makes a creature.",
            ),
            identifier = "urn:isbn:1", language = "en", chapters = 24, words = 74_000, pages = 318,
        )
        rule.setContent {
            AppTheme {
                BookDetailScreen(
                    row = r, progress = null, lastReadAt = null, addedAt = r.addedAt, sizeBytes = 1,
                    onBack = {}, onContinue = {}, onRemove = {}, details = BookDetails(info = info),
                )
            }
        }
        listOf(
            "Lackington", "1818", "Gothic fiction, Science fiction", "Public domain", "Classics", "urn:isbn:1", "en", "24",
            "318 pages · 74,000 words", "A scientist makes a creature.",
        ).forEach { rule.onNodeWithText(it).performScrollTo().assertExists() }
    }

    @Test
    fun `rows the book has nothing for are left out`() {
        val r = row()
        rule.setContent {
            AppTheme {
                BookDetailScreen(
                    row = r, progress = null, lastReadAt = null, addedAt = r.addedAt, sizeBytes = 1,
                    onBack = {}, onContinue = {}, onRemove = {}, details = BookDetails(info = com.baca.shared.parser.BookInfo()),
                )
            }
        }
        listOf("Publisher", "Published", "Language", "Series", "Subjects", "Identifier", "Rights", "Length", "Chapters", "ABOUT THIS BOOK").forEach {
            assertEquals(0, rule.onAllNodesWithText(it).fetchSemanticsNodes().size, "$it should not show")
        }
    }

    @Test
    fun `lists where the file is and what is marked in it`() {
        val r = row()
        rule.setContent {
            AppTheme {
                BookDetailScreen(
                    row = r, progress = 0.5f, lastReadAt = null, addedAt = r.addedAt, sizeBytes = 1,
                    onBack = {}, onContinue = {}, onRemove = {},
                    details = BookDetails(fileName = "frankenstein.epub", storedIn = "Library folder", highlights = 3, notes = 2, bookmarks = 1),
                )
            }
        }
        rule.onNodeWithText("frankenstein.epub").performScrollTo().assertExists()
        rule.onNodeWithText("Library folder").performScrollTo().assertExists()
        rule.onNodeWithText("Highlights").performScrollTo().assertExists()
        rule.onNodeWithText("3").performScrollTo().assertExists()
    }
}
