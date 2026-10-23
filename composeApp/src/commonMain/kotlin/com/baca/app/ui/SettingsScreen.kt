package com.baca.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.baca.app.reader.DisplayContent
import com.baca.app.reader.PlatformBackHandler
import com.baca.app.reader.SegmentedRow
import com.baca.app.reader.SheetSection
import com.baca.app.reader.ChoiceChip
import com.baca.shared.domain.loadDisplay
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.domain.saveDisplay

/** The pages behind the settings menu. */
private enum class SettingsPage(val title: String, val caption: String) {
    APP("App", "Theme and library folder"),
    DISPLAY("Reading display", "Font, theme, spacing, margins and more"),
    BACKUP("Backup and restore", "Move your library to another device"),
    ABOUT("About Baca", "Version $APP_VERSION, source and acknowledgements"),
}

/**
 * App settings (TECH 10.4): a menu of pages, each opened with a tap. The reading
 * display page edits the same store as the reader's Display sheet, so there is
 * one source of truth; the reader picks changes up on its next open.
 */
@Composable
internal fun SettingsScreen(
    store: PositionStore,
    onBack: () -> Unit,
    appTheme: AppThemeChoice = AppThemeChoice.SYSTEM,
    onAppThemeChange: (AppThemeChoice) -> Unit = {},
    /** shown name of the library folder, null when books live inside Baca */
    libraryFolderLabel: String? = null,
    scanning: Boolean = false,
    scanError: String? = null,
    onChooseLibraryFolder: () -> Unit = {},
    onRescan: () -> Unit = {},
    backupBusy: Boolean = false,
    /** result of the last backup or restore, shown under the buttons */
    backupMessage: String? = null,
    onCreateBackup: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
) {
    var display by remember { mutableStateOf(store.loadDisplay()) }
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    LaunchedEffect(display) { store.saveDisplay(display) }
    // back from a page returns to the menu, not to the library
    PlatformBackHandler(enabled = page != null) { page = null }
    val open = page
    SettingsFrame(
        title = open?.title ?: "Settings",
        backLabel = if (open == null) "‹ Library" else "‹ Settings",
        onBack = if (open == null) onBack else ({ page = null }),
    ) {
        when (open) {
            null -> SettingsPage.entries.forEach { entry -> MenuRow(entry.title, entry.caption) { page = entry } }
            SettingsPage.APP -> {
                SheetSection("APP THEME") {
                    SegmentedRow(
                        options = AppThemeChoice.entries.map { it.key to it.label },
                        selected = appTheme.key,
                    ) { key -> onAppThemeChange(AppThemeChoice.of(key)) }
                }
                SheetSection("LIBRARY FOLDER") {
                    Text(
                        when {
                            scanning -> "Scanning ${libraryFolderLabel.orEmpty()}…"
                            scanError != null -> "Can't read $libraryFolderLabel: $scanError"
                            libraryFolderLabel != null -> "Books in $libraryFolderLabel and its subfolders, read where they are. Added books are copied there."
                            else -> "Not set: added books are kept inside Baca. Choose a folder to read books where they are."
                        },
                        style = Type.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ChoiceChip(if (libraryFolderLabel == null) "Choose folder" else "Change folder", true, onChooseLibraryFolder)
                        if (libraryFolderLabel != null) ChoiceChip("Rescan", false, onRescan)
                    }
                }
            }
            SettingsPage.DISPLAY -> {
                Text(
                    "Defaults for every book. The reader's Aa sheet has the same options.",
                    style = Type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DisplayContent(display, scrollable = false) { display = it }
            }
            SettingsPage.BACKUP -> {
                Text(
                    "One file with your library, positions, notes, bookmarks, settings and book files.",
                    style = Type.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChoiceChip("Create backup", !backupBusy, onCreateBackup)
                    ChoiceChip("Restore…", false, onRestoreBackup)
                }
                (if (backupBusy) "Working…" else backupMessage)?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, style = Type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SheetSection("MOVING TO ANOTHER DEVICE") {
                    Text(
                        "Create a backup here, copy the file to the other device and restore it there. " +
                            "Restoring replaces that device's library, positions and notes, after saving what it had as before-restore.zip.",
                        style = Type.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SettingsPage.ABOUT -> AboutContent()
        }
    }
}

/** Back row, title and a scrolling body: the frame of the menu and of every page. */
@Composable
private fun SettingsFrame(title: String, backLabel: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                backLabel,
                style = Type.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .semantics { contentDescription = "Back" },
            )
        }
        Text(title, style = Type.screenTitle)
        Spacer(Modifier.height(16.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

/** One line of the menu: what the page is and what it holds. */
@Composable
private fun MenuRow(title: String, caption: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.cardTitle)
                Text(caption, style = Type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = Type.cardTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
