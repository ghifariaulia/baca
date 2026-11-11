package com.baca.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.baca.app.reader.DisplayContent
import com.baca.app.reader.PlatformBackHandler
import com.baca.app.reader.SegmentedRow
import com.baca.app.reader.ChoiceChip
import com.baca.app.reader.fetchLatestReleaseJson
import com.baca.shared.domain.UpdateStatus
import com.baca.shared.domain.checkForUpdate
import com.baca.shared.domain.loadDisplay
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.domain.saveDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The pages behind the settings menu. */
private enum class SettingsPage(val title: String, val caption: String, val icon: ImageVector) {
    APP("App", "Theme, library folder", Icons.Outlined.ColorLens),
    DISPLAY("Reading display", "Font, theme, spacing", Icons.Outlined.TextFields),
    BACKUP("Backup and restore", "Move to another device", Icons.Outlined.Backup),
    ABOUT("About Baca", "Version $APP_VERSION, updates", Icons.Outlined.Info),
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
    checkForUpdate: suspend () -> UpdateStatus = {
        withContext(Dispatchers.IO) { checkForUpdate(APP_VERSION) { fetchLatestReleaseJson() } }
    },
) {
    var display by remember { mutableStateOf(store.loadDisplay()) }
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    LaunchedEffect(display) { store.saveDisplay(display) }
    // back from a page returns to the menu, not to the library
    PlatformBackHandler(enabled = page != null) { page = null }
    val open = page
    SettingsFrame(
        title = open?.title ?: "Settings",
        onBack = if (open == null) onBack else ({ page = null }),
    ) {
        when (open) {
            null -> SettingsPage.entries.forEach { entry -> MenuRow(entry) { page = entry } }
            SettingsPage.APP -> {
                SettingsSection("APP THEME") {
                    SegmentedRow(
                        options = AppThemeChoice.entries.map { it.key to it.label },
                        selected = appTheme.key,
                    ) { key -> onAppThemeChange(AppThemeChoice.of(key)) }
                }
                SettingsSection("LIBRARY FOLDER") {
                    Text(
                        when {
                            scanning -> "Scanning…"
                            scanError != null -> "Can't read $libraryFolderLabel: $scanError"
                            else -> libraryFolderLabel ?: "Not set"
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
                DisplayContent(display, scrollable = false) { display = it }
            }
            SettingsPage.BACKUP -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChoiceChip("Create backup", !backupBusy, onCreateBackup)
                    ChoiceChip("Restore…", false, onRestoreBackup)
                }
                (if (backupBusy) "Working…" else backupMessage)?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(message, style = Type.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Restoring replaces this device's data, after saving the old as before-restore.zip.",
                    style = Type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsPage.ABOUT -> AboutContent(checkForUpdate)
        }
    }
}

/** Back arrow beside the page title, then a scrolling body, centred and capped in width so desktop windows stay readable. */
@Composable
private fun SettingsFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(8.dp).size(24.dp),
                )
                Text(title, style = Type.screenTitle)
            }
            Spacer(Modifier.height(20.dp))
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** A labelled group of controls: small muted label, then the controls, no box. */
@Composable
private fun SettingsSection(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(label, style = Type.sectionLabel, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** One line of the menu: tinted icon, the page and what it holds. */
@Composable
private fun MenuRow(page: SettingsPage, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(page.title, style = Type.cardTitle)
            Text(page.caption, style = Type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
