package com.baca.app.ui

import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baca.shared.db.BookRow

/**
 * Book detail (design file 'Book detail'): centered cover, title/author,
 * progress card with Continue reading, metadata list, remove action.
 * "Mark as finished"/"Highlights" wait for their backing data (annotations,
 * a finished flag) and are not built yet.
 */
@Composable
fun BookDetailScreen(
    row: BookRow,
    progress: Float?,
    lastReadAt: Long?,
    addedAt: Long,
    sizeBytes: Long,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(8.dp))
            BookCover(row, width = 150.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                row.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                textAlign = TextAlign.Center,
            )
            if (row.author.isNotBlank()) {
                Text(
                    row.author,
                    style = Type.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))

            // progress card
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                    Text(
                        "${((progress ?: 0f) * 100).toInt()}%",
                        style = Type.body,
                    )
                    Text(
                        if (lastReadAt == null) "Not started" else "Last read ${relativeTime(lastReadAt)}",
                        style = Type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline)) {
                    Box(
                        Modifier.fillMaxWidth(progress ?: 0f).height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Continue reading — accent pill
            val accent = MaterialTheme.colorScheme.primary
            val onAccent = MaterialTheme.colorScheme.onPrimary
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(accent)
                    .clickable(onClick = onContinue),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Continue reading",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = onAccent,
                )
            }

            Spacer(Modifier.height(20.dp))

            // metadata list
            val line = MaterialTheme.colorScheme.outline
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)) {
                metaRow("Format", row.format.uppercase(), line)
                metaRow("Size", humanSize(sizeBytes), line)
                metaRow("Added", relativeTime(addedAt), line)
                if (lastReadAt != null) metaRow("Last read", relativeTime(lastReadAt), line)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Remove from library",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB3261E),
                modifier = Modifier.clickable(onClick = onRemove).padding(8.dp),
            )
        }
    }
}

@Composable
private fun metaRow(label: String, value: String, line: Color) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(label, style = Type.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = Type.body, fontWeight = FontWeight.Medium)
        }
        HorizontalDivider(color = line)
    }
}

/** Display-relative wall clock strings; never used for ordering/sync. */
internal fun relativeTime(at: Long, now: () -> Long = { System.currentTimeMillis() }): String {
    val diffMinutes = ((now() - at) / 60_000L).coerceAtLeast(0)
    return when {
        diffMinutes < 1 -> "just now"
        diffMinutes < 60 -> "$diffMinutes min ago"
        diffMinutes < 60 * 24 -> "${diffMinutes / 60} hours ago"
        else -> "${diffMinutes / 60 / 24} days ago"
    }
}

internal fun humanSize(bytes: Long): String = when {
    // locale-free decimal, some JVM locales render "1,1"
    bytes >= 1 shl 20 -> "${(bytes / 104857.6).toInt() / 10.0} MB"
    bytes >= 1 shl 10 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
