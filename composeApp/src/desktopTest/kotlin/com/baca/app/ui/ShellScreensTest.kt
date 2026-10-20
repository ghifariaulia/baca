package com.baca.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.baca.shared.db.DbAnnotationRepository.AnnotWithDevice
import com.baca.shared.db.BookRow
import com.baca.shared.sync.AnnotationEntry
import com.baca.shared.sync.ReflowAnchorDto
import com.baca.shared.sync.PointDto
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Notes + Sync screens render and their controls work. */
class ShellScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private fun row() = BookRow(
        bookHash = "hash", title = "Frankenstein", author = "M. Shelley", path = "/x.epub",
        format = "epub", coverPath = null, addedAt = 0L, lamport = 0L, deleted = false,
    )

    private fun entry(note: String = "") = AnnotationEntry(
        id = "a1", kind = "highlight",
        reflowAnchor = ReflowAnchorDto("c1.xhtml", PointDto(0, 0), PointDto(0, 5), "quote text"),
        text = "quote text", note = note, createdAt = 0L, lamport = 1L,
    )

    @Test
    fun `notes screen renders groups and filters`() {
        val groups = listOf(
            NotesGroup(row(), listOf(AnnotWithDevice(entry(), "this-device"))),
            NotesGroup(row().copy(bookHash = "h2", title = "Strange Pictures"), emptyList()),
        )
        rule.setContent { AppTheme { NotesScreen(groups = groups, onOpenAnnotation = { _, _ -> }) } }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Frankenstein", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(rule.onAllNodesWithText("quote text", substring = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("Highlights").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("All").performClick()
        rule.waitForIdle()
    }

    @Test
    fun `bookmarks and notes screens each show only their own kind`() {
        val bookmark = entry().copy(id = "b1", kind = "bookmark", text = "bookmarked page")
        val groups = listOf(
            NotesGroup(row(), listOf(AnnotWithDevice(entry(), "d"), AnnotWithDevice(bookmark, "d"))),
        )
        var showBookmarks by mutableStateOf(true)
        rule.setContent {
            AppTheme {
                if (showBookmarks) BookmarksScreen(groups, onOpenAnnotation = { _, _ -> })
                else NotesScreen(groups, onOpenAnnotation = { _, _ -> })
            }
        }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("bookmarked page").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("quote text").fetchSemanticsNodes().isEmpty())

        showBookmarks = false
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("quote text").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("bookmarked page").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `sync screen renders status and devices`() {
        val data = SyncScreenData(
            folderPath = "/tmp/sync",
            busy = false,
            error = null,
            lastSyncAt = 123L,
            banner = MergeBanner("Frankenstein", "other-device", 42),
            devices = listOf(DeviceRow("this-device", true, 100L), DeviceRow("other-device", false, 90L)),
        )
        rule.setContent {
            AppTheme {
                SyncScreen(data, onBack = {}, onChooseFolder = {}, onSyncNow = {}, onUndo = {})
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Sync", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(rule.onAllNodesWithText("Position updated", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Undo", substring = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("Sync now").performClick()
        rule.waitForIdle()
    }

    @Test
    fun `sync screen offers backup and restore`() {
        var created = 0
        var restored = 0
        var data by mutableStateOf(
            SyncScreenData(folderPath = null, busy = false, error = null, lastSyncAt = null, banner = null, devices = emptyList()),
        )
        rule.setContent {
            AppTheme {
                SyncScreen(
                    data, onBack = {}, onChooseFolder = {}, onSyncNow = {}, onUndo = {},
                    onCreateBackup = { created++ }, onRestoreBackup = { restored++ },
                )
            }
        }
        rule.onNodeWithText("Create backup").performScrollTo().performClick()
        rule.onNodeWithText("Restore…").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(created == 1 && restored == 1)

        data = data.copy(backupMessage = "Backup saved.")
        rule.waitForIdle()
        rule.onNodeWithText("Backup saved.").assertExists()
    }
}
