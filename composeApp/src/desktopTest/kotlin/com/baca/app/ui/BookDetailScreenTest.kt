package com.baca.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.baca.shared.db.BookRow
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
        rule.onNodeWithText("Remove from library").performClick()
        assertEquals(1, removed)
    }
}
