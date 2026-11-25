package com.baca.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.baca.shared.db.BookRow
import com.baca.shared.db.DbAnnotationRepository
import com.baca.shared.domain.AnnotationEntry
import com.baca.shared.domain.PointDto
import com.baca.shared.domain.ReflowAnchorDto
import org.junit.Rule
import org.junit.Test

class NotesScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun sampleGroups(): List<NotesGroup> {
        val book = BookRow("h1", "Frankenstein", "Mary Shelley", "/tmp/f.epub", "epub", null, 1L, 0L, deleted = false)
        val a1 = DbAnnotationRepository.AnnotWithDevice(
            AnnotationEntry(
                id = "1",
                kind = "highlight",
                reflowAnchor = ReflowAnchorDto("ch1.xhtml", PointDto(0, 0), PointDto(0, 10), "Arctic voyage"),
                text = "Arctic voyage",
                note = "Opening theme",
                color = "yellow",
                createdAt = 1L,
                lamport = 1L,
            ),
            deviceId = "dev",
        )
        val a2 = DbAnnotationRepository.AnnotWithDevice(
            AnnotationEntry(
                id = "2",
                kind = "highlight",
                reflowAnchor = ReflowAnchorDto("ch2.xhtml", PointDto(0, 0), PointDto(0, 10), "Lightning storm"),
                text = "Lightning storm",
                note = "",
                color = "blue",
                createdAt = 2L,
                lamport = 2L,
            ),
            deviceId = "dev",
        )
        return listOf(NotesGroup(book, listOf(a1, a2)))
    }

    @Test
    fun `search query and color filter narrow visible notes`() {
        rule.setContent {
            NotesScreen(groups = sampleGroups(), onOpenAnnotation = { _, _ -> })
        }

        rule.onNodeWithText("Arctic voyage").assertExists()
        rule.onNodeWithText("Lightning storm").assertExists()

        // Filter by blue color chip
        rule.onNodeWithContentDescription("Filter color blue").performClick()
        rule.onNodeWithText("Lightning storm").assertExists()
        rule.onNodeWithText("Arctic voyage").assertDoesNotExist()

        // Reset color filter and search by note text
        rule.onNodeWithContentDescription("Filter color blue").performClick()
        rule.onNodeWithText("Search notes", substring = true).performTextInput("Opening")
        rule.onNodeWithText("Arctic voyage").assertExists()
        rule.onNodeWithText("Lightning storm").assertDoesNotExist()
    }

    @Test
    fun `export button is shown when notes exist`() {
        rule.setContent {
            NotesScreen(groups = sampleGroups(), onOpenAnnotation = { _, _ -> })
        }

        rule.onNodeWithText("Export").assertExists().performClick()
        rule.onNodeWithText("Copy Markdown").assertExists()
        rule.onNodeWithText("Save .md").assertExists()
    }
}
