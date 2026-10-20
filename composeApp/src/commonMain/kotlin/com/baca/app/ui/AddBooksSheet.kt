package com.baca.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ImportStatus { READING, ADDED, SKIPPED, UNSUPPORTED, BROKEN, PENDING }

/** One file in the "Importing N files" screen (design file, TECH 8.1). */
data class ImportItem(
    val name: String,
    val status: ImportStatus = ImportStatus.PENDING,
)

/** Sheet body for Add books → import flow. Owns no logic; App drives it. */
@Composable
fun ImportProgressSheet(
    items: List<ImportItem>,
    running: Boolean,
    folderMode: Boolean,
    onFromFiles: () -> Unit,
    onFromFolder: (() -> Unit)?,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 12.dp)) {
            Box(
                Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline),
            )
        }
        if (items.isEmpty()) {
            Text("Add books", style = Type.cardTitle, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            SheetOption(
                icon = "⎙",
                title = "From files",
                subtitle = "Pick EPUB, FB2, TXT or CBZ files",
                onClick = onFromFiles,
            )
            if (onFromFolder != null) {
                SheetOption(
                    icon = "🗀",
                    title = "From a folder",
                    subtitle = "Import every supported file in a folder",
                    onClick = onFromFolder,
                )
            }
        } else {
            val processed = items.count { it.status != ImportStatus.PENDING && it.status != ImportStatus.READING }
            Text(
                if (items.size == 1) items.first().name else "Importing ${items.size} files",
                style = Type.cardTitle,
                fontSize = 18.sp,
            )
            Text(
                "Reading metadata…",
                style = Type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            items.forEachIndexed { i, item ->
                importRow(item, i == items.lastIndex)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                // Cancel while running; Done once finished (design page).
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClick = if (running) onCancel else onDone),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (running) "Cancel" else "Done", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (running) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
                        .clickable(enabled = !running, onClick = onDone),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Done",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (running) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun importRow(item: ImportItem, last: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = Type.body, fontWeight = FontWeight.Medium)
                val subtitle = when (item.status) {
                    ImportStatus.ADDED -> "Added to your library"
                    ImportStatus.SKIPPED -> "Already in your library. Skipped."
                    ImportStatus.UNSUPPORTED -> "Unsupported format. Supported: EPUB, FB2, TXT, CBZ."
                    ImportStatus.BROKEN -> "Couldn't read this file. It may be damaged."
                    ImportStatus.READING -> "Reading metadata…"
                    ImportStatus.PENDING -> "Waiting…"
                }
                Text(subtitle, style = Type.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun SheetOption(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontSize = 16.sp)
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text(title, style = Type.body, fontWeight = FontWeight.Bold)
                Text(subtitle, style = Type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
