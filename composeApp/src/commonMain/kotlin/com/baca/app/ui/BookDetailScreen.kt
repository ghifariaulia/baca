package com.baca.app.ui

import com.baca.shared.domain.shownProgress

import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.baca.app.reader.KIND_BOOKMARK
import com.baca.app.reader.rememberCopyToClipboard
import com.baca.app.reader.rememberMarkdownSaver
import com.baca.shared.db.BookRow
import com.baca.shared.domain.AnnotationEntry
import com.baca.shared.domain.ExportBook
import com.baca.shared.domain.formatAnnotationsMarkdown
import com.baca.shared.parser.BookInfo
import okio.buffer

/** What the detail screen lists beyond the book's row: where the file is and what the reader has marked in it. */
data class BookDetails(
    val fileName: String = "",
    /** "Library folder" or "Baca storage" */
    val storedIn: String = "",
    val highlights: Int = 0,
    val notes: Int = 0,
    val bookmarks: Int = 0,
    val series: String = "",
    /** what the book says about itself and its length; null while unread */
    val info: BookInfo? = null,
)

/**
 * Book detail (design file 'Book detail'): centered cover, title/author,
 * progress card with Start/Continue reading, metadata list, mark-finished and
 * remove actions.
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
    finished: Boolean = false,
    onToggleFinished: () -> Unit = {},
    details: BookDetails = BookDetails(),
    exportEntries: List<AnnotationEntry> = emptyList(),
    shelves: List<String> = emptyList(),
    bookShelves: List<String> = emptyList(),
    onToggleShelf: (String) -> Unit = {},
    onCreateShelf: (String) -> Unit = {},
    onEditMetadata: (title: String, author: String, series: String) -> Unit = { _, _, _ -> },
    onChangeCover: () -> Unit = {},
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
            val shown = shownProgress(finished, progress)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                    Text(
                        "${(shown * 100).toInt()}%",
                        style = Type.body,
                    )
                    Text(
                        when {
                            finished -> "Finished"
                            lastReadAt == null -> "Not started"
                            else -> "Last read ${relativeTime(lastReadAt)}"
                        },
                        style = Type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline)) {
                    Box(
                        Modifier.fillMaxWidth(shown).height(3.dp)
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
                    if (lastReadAt == null || (progress ?: 0f) == 0f) "Start reading" else "Continue reading",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = onAccent,
                )
            }

            Spacer(Modifier.height(20.dp))

            // metadata list
            val line = MaterialTheme.colorScheme.outline
            MetaList(
                buildList {
                    val info = details.info
                    val meta = info?.meta
                    val shownSeries = details.series.ifEmpty { meta?.series.orEmpty() }
                    if (row.author.isNotBlank()) add("Author" to row.author)
                    if (shownSeries.isNotEmpty()) add("Series" to shownSeries)
                    if (meta != null && meta.publisher.isNotEmpty()) add("Publisher" to meta.publisher)
                    if (meta != null && meta.published.isNotEmpty()) add("Published" to meta.published)
                    if (info != null && info.language.isNotEmpty()) add("Language" to info.language)
                    if (meta != null && meta.subjects.isNotEmpty()) add("Subjects" to meta.subjects.joinToString(", "))
                    if (info != null && info.identifier.isNotEmpty()) add("Identifier" to info.identifier)
                    if (meta != null && meta.rights.isNotEmpty()) add("Rights" to meta.rights)
                    add("Format" to row.format.uppercase())
                    add("Size" to humanSize(sizeBytes))
                    if (info != null && info.pages > 0) add((if (row.format == "cbz") "Pages" else "Length") to lengthOf(info, row.format))
                    if (info != null && info.chapters > 0) add("Chapters" to info.chapters.toString())
                    if (details.fileName.isNotEmpty()) add("File" to details.fileName)
                    if (details.storedIn.isNotEmpty()) add("Stored in" to details.storedIn)
                    add("Added" to relativeTime(addedAt))
                    if (lastReadAt != null) add("Last read" to relativeTime(lastReadAt))
                },
                line,
            )

            details.info?.meta?.description?.takeIf { it.isNotEmpty() }?.let { about ->
                Spacer(Modifier.height(16.dp))
                Text("ABOUT THIS BOOK", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    about,
                    style = Type.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("MARKED IN THIS BOOK", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            MetaList(
                listOf(
                    "Highlights" to details.highlights.toString(),
                    "Notes" to details.notes.toString(),
                    "Bookmarks" to details.bookmarks.toString(),
                ),
                line,
            )

            val canExport = remember(exportEntries) {
                exportEntries.any { !it.deleted && it.kind != KIND_BOOKMARK }
            }
            if (canExport) {
                val copyToClipboard = rememberCopyToClipboard()
                var exportStatus by remember { mutableStateOf<String?>(null) }
                val bookMarkdown = remember(row, exportEntries) {
                    formatAnnotationsMarkdown(listOf(ExportBook(row.title, row.author, exportEntries)))
                }
                val suggested = remember(row.title) {
                    val clean = row.title.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifEmpty { "notes" }
                    "$clean-notes.md"
                }
                val saveMd = rememberMarkdownSaver(suggested) { sinkFactory ->
                    if (sinkFactory != null) {
                        runCatching { sinkFactory().buffer().use { it.writeUtf8(bookMarkdown) } }
                            .onSuccess { exportStatus = "Saved" }
                            .onFailure { exportStatus = "Save failed" }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = {
                        copyToClipboard(bookMarkdown)
                        exportStatus = "Copied"
                    }) {
                        Text("Copy notes", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = saveMd) {
                        Text("Save .md", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    exportStatus?.let {
                        Text(it, style = Type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            var editOpen by remember { mutableStateOf(false) }
            var newShelfOpen by remember { mutableStateOf(false) }

            Spacer(Modifier.height(16.dp))
            Text("SHELVES", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                shelves.forEach { shelf ->
                    val selected = shelf in bookShelves
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface)
                            .clickable { onToggleShelf(shelf) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            shelf,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { newShelfOpen = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("+ New shelf", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { editOpen = true }) {
                    Text("Edit details", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onChangeCover) {
                    Text("Change cover", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            if (editOpen) {
                val initialSeries = details.series.ifEmpty { details.info?.meta?.series.orEmpty() }
                var titleInput by remember(row) { mutableStateOf(row.title) }
                var authorInput by remember(row) { mutableStateOf(row.author) }
                var seriesInput by remember(initialSeries) { mutableStateOf(initialSeries) }
                AlertDialog(
                    onDismissRequest = { editOpen = false },
                    title = { Text("Edit details") },
                    text = {
                        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = titleInput, onValueChange = { titleInput = it }, label = { Text("Title") }, singleLine = true)
                            OutlinedTextField(value = authorInput, onValueChange = { authorInput = it }, label = { Text("Author") }, singleLine = true)
                            OutlinedTextField(value = seriesInput, onValueChange = { seriesInput = it }, label = { Text("Series") }, singleLine = true)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            onEditMetadata(titleInput.trim(), authorInput.trim(), seriesInput.trim())
                            editOpen = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { editOpen = false }) { Text("Cancel") }
                    },
                )
            }

            if (newShelfOpen) {
                var shelfName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { newShelfOpen = false },
                    title = { Text("New shelf") },
                    text = {
                        OutlinedTextField(value = shelfName, onValueChange = { shelfName = it }, label = { Text("Shelf name") }, singleLine = true)
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val trimmed = shelfName.trim()
                            if (trimmed.isNotEmpty()) onCreateShelf(trimmed)
                            newShelfOpen = false
                        }) { Text("Create") }
                    },
                    dismissButton = {
                        TextButton(onClick = { newShelfOpen = false }) { Text("Cancel") }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                if (finished) "Mark as unread" else "Mark as finished",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onToggleFinished).padding(8.dp),
            )
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

/** Label and value rows in a card, divided from each other but not closed by a line at the end. */
@Composable
private fun MetaList(rows: List<Pair<String, String>>, line: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(color = line)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text(label, style = Type.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(value, style = Type.body, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
            }
        }
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

/** "440 pages · 61,200 words": the length the reader numbers, and the words counted. */
internal fun lengthOf(info: BookInfo, format: String): String {
    if (format == "cbz") return info.pages.toString()
    val words = if (info.words > 0) " · ${groupThousands(info.words)} words" else ""
    return "${groupThousands(info.pages)} pages$words"
}

/** 61200 as "61,200", locale-free like [humanSize]. */
private fun groupThousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

internal fun humanSize(bytes: Long): String = when {
    // locale-free decimal, some JVM locales render "1,1"
    bytes >= 1 shl 20 -> "${(bytes / 104857.6).toInt() / 10.0} MB"
    bytes >= 1 shl 10 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
