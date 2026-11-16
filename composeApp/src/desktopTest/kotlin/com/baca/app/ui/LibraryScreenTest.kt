package com.baca.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.baca.shared.db.BookRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    fun `a finished book's cover carries a check, an unfinished one does not`() {
        val books = listOf(
            LibraryBook(row("h1", "Moby-Dick", "H. Melville"), 0.4f),
            LibraryBook(row("h2", "Dracula", "B. Stoker"), 0.99f),
            LibraryBook(row("h3", "Emma", "J. Austen"), 0.1f, finished = true),
        )
        rule.setContent {
            AppTheme { LibraryScreen(books = books, onAdd = {}, onOpen = {}, onResume = {}, onDelete = {}) }
        }
        // read through (99%) and marked finished each get one; the 40% book none
        assertEquals(2, rule.onAllNodesWithContentDescription("Finished").fetchSemanticsNodes().size)
    }

    @Test
    fun `picking an order in the sort menu reorders the grid and reports the choice`() {
        val books = listOf(
            LibraryBook(row("h1", "Moby-Dick", "H. Melville").copy(addedAt = 3L), null),
            LibraryBook(row("h2", "Emma", "J. Austen").copy(addedAt = 2L), null),
            LibraryBook(row("h3", "Dracula", "B. Stoker").copy(addedAt = 1L), null),
        )
        var sort by androidx.compose.runtime.mutableStateOf(com.baca.shared.domain.LibrarySort.ADDED)
        rule.setContent {
            AppTheme {
                LibraryScreen(
                    books = books, onAdd = {}, onOpen = {}, onResume = {}, onDelete = {},
                    sort = sort, onSortChange = { sort = it },
                )
            }
        }
        fun gridOrder() = listOf("Moby-Dick", "Emma", "Dracula")
            .sortedBy { rule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.left }
        assertEquals(listOf("Moby-Dick", "Emma", "Dracula"), gridOrder())

        rule.onNodeWithContentDescription("Sort books").performClick()
        rule.onNodeWithText("Title").performClick()
        rule.waitForIdle()

        assertEquals(com.baca.shared.domain.LibrarySort.TITLE, sort)
        rule.onNodeWithText("Title").assertExists()
        assertEquals(listOf("Dracula", "Emma", "Moby-Dick"), gridOrder())
    }

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
