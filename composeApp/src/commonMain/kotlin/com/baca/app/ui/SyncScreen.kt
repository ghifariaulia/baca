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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baca.app.reader.folderLabel

/** What the Sync screen shows (design pages 'Sync' and 'Sync errors'). */
data class SyncScreenData(
    val folderPath: String?,
    val busy: Boolean,
    val error: String?,
    val lastSyncAt: Long?,
    val banner: MergeBanner?,
    val devices: List<DeviceRow>,
    val backupBusy: Boolean = false,
    /** result of the last backup or restore, shown under the buttons */
    val backupMessage: String? = null,
)

data class MergeBanner(val bookTitle: String, val fromDevice: String, val percent: Int)

data class DeviceRow(val id: String, val isThisDevice: Boolean, val lastSeenAt: Long?)

@Composable
fun SyncScreen(
    data: SyncScreenData,
    onBack: () -> Unit,
    onChooseFolder: () -> Unit,
    onSyncNow: () -> Unit,
    onUndo: () -> Unit,
    onCreateBackup: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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
            Text("Sync", style = Type.screenTitle)
            Spacer(Modifier.height(16.dp))

            // merge banner: a peer moved the local position (design 'Sync')
            data.banner?.let { banner ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Position updated from ${banner.fromDevice}: ${banner.bookTitle}, ${banner.percent}%",
                                style = Type.body,
                            )
                        }
                        Text(
                            "Undo",
                            style = Type.body,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onUndo).padding(start = 12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // status card
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            data.busy -> "Syncing…"
                            data.error != null -> "Can't reach the sync folder"
                            data.folderPath == null -> "No sync folder yet"
                            else -> "Up to date"
                        },
                        style = Type.cardTitle,
                    )
                    Text(
                        when {
                            data.error != null -> data.error
                            data.folderPath == null -> "Pick a folder that your file syncer shares"
                            else -> "Last synced " + (data.lastSyncAt?.let { relativeTime(it) } ?: "never")
                        },
                        style = Type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (data.folderPath != null) {
                    val accent = MaterialTheme.colorScheme.primary
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (data.busy) MaterialTheme.colorScheme.outline else accent)
                            .clickable(enabled = !data.busy, onClick = onSyncNow)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Sync now",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (data.busy) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                } else {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.onSurface)
                            .clickable(onClick = onChooseFolder)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Choose folder",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.background,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("TRANSPORT", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onChooseFolder).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Shared folder", style = Type.body, fontWeight = FontWeight.Bold)
                        Text(
                            data.folderPath?.let { folderLabel(it) } ?: "Not set up — choose a folder",
                            style = Type.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("DEVICES", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (data.devices.isEmpty()) {
                Text(
                    "No other devices have synced yet.",
                    style = Type.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    data.devices.forEachIndexed { index, device ->
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.outline),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (device.isThisDevice) "${device.id} (this device)" else device.id,
                                    style = Type.body,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    device.lastSeenAt?.let { "Last seen ${relativeTime(it)}" } ?: "Last seen unknown",
                                    style = Type.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index != data.devices.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("BACKUP", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                "One file with your library, positions, notes, bookmarks, settings and book files.",
                style = Type.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Create backup", filled = true, enabled = !data.backupBusy, onClick = onCreateBackup)
                PillButton("Restore…", filled = false, enabled = !data.backupBusy, onClick = onRestoreBackup)
            }
            (if (data.backupBusy) "Working…" else data.backupMessage)?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(message, style = Type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val background = when {
        !enabled -> MaterialTheme.colorScheme.outline
        filled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (filled && enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
