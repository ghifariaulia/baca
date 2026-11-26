package com.baca.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.baca.shared.db.BookRow
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class LibraryOrganizationTest {

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
    fun `filtering by shelf chip shows only books in that shelf`() {
        val books = listOf(
            LibraryBook(row("h1", "Dune", "Frank Herbert"), 0.2f, shelves = listOf("Sci-Fi")),
            LibraryBook(row("h2", "Dracula", "Bram Stoker"), 0.4f, shelves = listOf("Classics")),
        )
        rule.setContent {
            AppTheme {
                LibraryScreen(
                    books = books,
                    shelves = listOf("Sci-Fi", "Classics"),
                    onAdd = {},
                    onOpen = {},
                    onResume = {},
                    onDelete = {},
                )
            }
        }
        rule.onNodeWithText("Sci-Fi").performClick()
        assertEquals(true, rule.onAllNodesWithText("Dune").fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, rule.onAllNodesWithText("Dracula").fetchSemanticsNodes().size)
    }

    @Test
    fun `toggling group by series displays series section header`() {
        val books = listOf(
            LibraryBook(row("h1", "Dune", "Frank Herbert"), null, series = "Dune"),
            LibraryBook(row("h2", "Dune Messiah", "Frank Herbert"), null, series = "Dune"),
            LibraryBook(row("h3", "Frankenstein", "Mary Shelley"), null, series = ""),
        )
        var grouped by mutableStateOf(false)
        rule.setContent {
            AppTheme {
                LibraryScreen(
                    books = books,
                    groupBySeries = grouped,
                    onToggleGroupBySeries = { grouped = !grouped },
                    onAdd = {},
                    onOpen = {},
                    onResume = {},
                    onDelete = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Group by series").performClick()
        rule.waitForIdle()
        assertEquals(true, grouped)
        rule.onNodeWithText("Dune (2)").assertExists()
        rule.onNodeWithText("Standalone").assertExists()
    }

    @Test
    fun `long pressing book lets user add it to a shelf`() {
        val books = listOf(
            LibraryBook(row("h1", "Dune", "Frank Herbert"), null, shelves = emptyList()),
        )
        val toggled = mutableListOf<Pair<String, String>>()
        rule.setContent {
            AppTheme {
                LibraryScreen(
                    books = books,
                    shelves = listOf("Favorites"),
                    onToggleBookShelf = { book, shelf -> toggled += book.bookHash to shelf },
                    onAdd = {},
                    onOpen = {},
                    onResume = {},
                    onDelete = {},
                )
            }
        }
        rule.onAllNodesWithText("Dune")[0].performTouchInput { longClick() }
        rule.onNodeWithText("Shelves").performClick()
        rule.onAllNodesWithText("Favorites")[1].performClick()
        assertEquals(listOf("h1" to "Favorites"), toggled)
    }

    @Test
    fun `creating shelf from library chip invokes onCreateShelf`() {
        val books = listOf(LibraryBook(row("h1", "Dune", "Frank Herbert"), null))
        val created = mutableListOf<String>()
        rule.setContent {
            AppTheme {
                LibraryScreen(
                    books = books,
                    shelves = emptyList(),
                    onCreateShelf = { created += it },
                    onAdd = {},
                    onOpen = {},
                    onResume = {},
                    onDelete = {},
                )
            }
        }
        rule.onNodeWithText("+ New shelf").performClick()
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("Sci-Fi")
        rule.onNodeWithText("Create").performClick()
        assertEquals(listOf("Sci-Fi"), created)
    }

    @Test
    fun `removing book from library context menu asks for confirmation`() {
        val books = listOf(LibraryBook(row("h1", "Dune", "Frank Herbert"), null))
        val deleted = mutableListOf<String>()
        rule.setContent {
            AppTheme {
                LibraryScreen(
                    books = books,
                    onAdd = {},
                    onOpen = {},
                    onResume = {},
                    onDelete = { deleted += it.bookHash },
                )
            }
        }
        rule.onAllNodesWithText("Dune")[0].performTouchInput { longClick() }
        rule.onNodeWithText("Remove from library").performClick()
        assertEquals(emptyList(), deleted)
        rule.onNodeWithText("Remove").performClick()
        assertEquals(listOf("h1"), deleted)
    }

    @Test
    fun `book detail edits metadata and toggles shelves`() {
        var edited: Triple<String, String, String>? = null
        val toggled = mutableListOf<String>()
        val created = mutableListOf<String>()
        rule.setContent {
            AppTheme {
                BookDetailScreen(
                    row = row("h1", "Old Title", "Old Author"),
                    progress = 0.2f,
                    lastReadAt = null,
                    addedAt = 0L,
                    sizeBytes = 1024L,
                    details = BookDetails(series = "Old Series"),
                    shelves = listOf("Sci-Fi", "Read Next"),
                    bookShelves = listOf("Sci-Fi"),
                    onToggleShelf = { toggled += it },
                    onCreateShelf = { created += it },
                    onEditMetadata = { t, a, s -> edited = Triple(t, a, s) },
                    onBack = {},
                    onContinue = {},
                    onRemove = {},
                )
            }
        }
        rule.onNodeWithText("Edit details").performScrollTo()
        rule.onNodeWithText("Read Next").performClick()
        assertEquals(listOf("Read Next"), toggled)

        rule.onNodeWithText("Edit details").performClick()
        rule.onAllNodes(hasSetTextAction())[0].performTextClearance()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("New Title")
        rule.onAllNodes(hasSetTextAction())[1].performTextClearance()
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("New Author")
        rule.onAllNodes(hasSetTextAction())[2].performTextClearance()
        rule.onAllNodes(hasSetTextAction())[2].performTextInput("New Series")
        rule.onNodeWithText("Save").performClick()

        assertEquals(Triple("New Title", "New Author", "New Series"), edited)
    }
}
