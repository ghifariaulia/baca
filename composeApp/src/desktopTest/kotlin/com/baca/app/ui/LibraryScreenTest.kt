package com.baca.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.baca.shared.db.BookRow
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun row(hash: String, title: String, author: String) = BookRow(
        bookHash = hash,
        title = title,
        author = author,
        path = "/books/$hash.epub",
        format = "epub",
        coverPath = null,
        addedAt = 0L,
        lamport = 1L,
        deleted = false,
    )

    @Test
    fun rendersContinueCardGridAndFilters() {
        val books = listOf(
            LibraryBook(row("h1", "Moby-Dick", "H. Melville"), 0.03f, lastReadAt = 1L),
            LibraryBook(row("h2", "Meditations", "M. Aurelius"), 0.55f, lastReadAt = 10L), // most recent
            LibraryBook(row("h3", "Dracula", "B. Stoker"), 0.99f),       // finished
        )
        var opened: BookRow? = null
        var resumed: BookRow? = null
        var deleted: BookRow? = null
        rule.setContent {
            AppTheme {
                LibraryScreen(
                    books = books,
                    onAdd = {},
                    onOpen = { opened = it },
                    onResume = { resumed = it },
                    onDelete = { deleted = it },
                )
            }
        }

        // continue-reading card shows the highest-progress unfinished book
        rule.onNodeWithText("CONTINUE READING").assertExists()
        rule.onNodeWithText("55%", substring = true).assertExists()
        // tap the grid title cell; 'Meditations' renders in the card and grid
        rule.onAllNodesWithText("Meditations").fetchSemanticsNodes()
        rule.onAllNodesWithText("Meditations")[1].performClick()
        assertEquals("h2", opened!!.bookHash)

        // finished filter shows only Dracula; delete sets tombstone through callback
        rule.onNodeWithText("Finished").performClick()
        val dracula = rule.onAllNodesWithText("Dracula").fetchSemanticsNodes()
        assertTrue(dracula.isNotEmpty())

        // "Meditations" hidden by the Finished filter
        assertTrue(rule.onAllNodesWithText("Meditations").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `books marked finished leave Reading and the continue card`() {
        val books = listOf(
            LibraryBook(row("h1", "Moby-Dick", "H. Melville"), 0.40f, lastReadAt = 1L),
            LibraryBook(row("h2", "Meditations", "M. Aurelius"), 0.55f, lastReadAt = 10L, finished = true),
        )
        rule.setContent { AppTheme { LibraryScreen(books = books, onAdd = {}, onOpen = {}, onResume = {}, onDelete = {}) } }

        // continue card skips the finished book even though it was read last
        rule.onNodeWithText("40%", substring = true).assertExists()
        rule.onNodeWithText("Reading").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Meditations").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Finished").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Meditations").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Moby-Dick").fetchSemanticsNodes().isEmpty())
    }
}
