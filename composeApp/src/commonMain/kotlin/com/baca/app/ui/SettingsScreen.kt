package com.baca.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import com.baca.app.reader.SegmentedRow
import com.baca.app.reader.SheetSection
import com.baca.app.reader.ChoiceChip
import com.baca.shared.domain.loadDisplay
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.domain.saveDisplay

/**
 * App settings (TECH 10.3): reading display defaults, editable without
 * opening a book. Same store/bucket as the reader's Display sheet — one
 * source of truth; the reader picks changes up on its next open.
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
) {
    var display by remember { mutableStateOf(store.loadDisplay()) }
    LaunchedEffect(display) { store.saveDisplay(display) }
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "‹ Library",
                style = Type.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .semantics { contentDescription = "Back" },
            )
        }
        Text("Settings", style = Type.screenTitle)
        Spacer(Modifier.height(16.dp))
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
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                ChoiceChip(if (libraryFolderLabel == null) "Choose folder" else "Change folder", true, onChooseLibraryFolder)
                if (libraryFolderLabel != null) ChoiceChip("Rescan", false, onRescan)
            }
        }
        // DisplayContent scrolls itself; just bound the height
        Box(Modifier.fillMaxWidth().weight(1f)) {
            DisplayContent(display) { display = it }
        }
    }
}
