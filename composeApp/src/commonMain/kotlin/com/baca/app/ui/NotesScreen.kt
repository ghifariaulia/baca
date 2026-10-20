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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baca.app.reader.AnnotationCards
import com.baca.app.reader.KIND_BOOKMARK
import com.baca.shared.db.BookRow
import com.baca.shared.db.DbAnnotationRepository
import com.baca.shared.sync.AnnotationEntry

/** One book's annotations for the Notes screen (design file 'Notes'). */
data class NotesGroup(val row: BookRow, val entries: List<DbAnnotationRepository.AnnotWithDevice>)

private enum class NotesFilter { ALL, HIGHLIGHTS, WITH_NOTES }

/**
 * Notes screen (design 'Notes'): filter chips, annotations grouped by book
 * with cover headers. Tap a card → open the book at the anchor. Bookmarks
 * have their own screen.
 */
@Composable
fun NotesScreen(
    groups: List<NotesGroup>,
    onOpenAnnotation: (BookRow, AnnotationEntry) -> Unit,
) {
    var filter by remember { mutableStateOf(NotesFilter.ALL) }

    fun matches(entry: AnnotationEntry) = !entry.deleted && entry.kind != KIND_BOOKMARK && when (filter) {
        NotesFilter.ALL -> true
        NotesFilter.HIGHLIGHTS -> entry.note.isBlank()
        NotesFilter.WITH_NOTES -> entry.note.isNotBlank()
    }

    AnnotationGroupsScreen(
        title = "Notes",
        groups = groups,
        matches = ::matches,
        emptyText = if (filter == NotesFilter.WITH_NOTES) "No notes yet — select text in a book to add one."
        else "No highlights yet — press and hold a paragraph in a book.",
        onOpenAnnotation = onOpenAnnotation,
    ) {
        // chips: selected state is ink fill (TECH 10.3)
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf(
                NotesFilter.ALL to "All",
                NotesFilter.HIGHLIGHTS to "Highlights",
                NotesFilter.WITH_NOTES to "With notes",
            ).forEach { (key, label) ->
                val selected = filter == key
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface)
                        .clickable { filter = key }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Bookmarks from every book, grouped like the Notes screen. */
@Composable
fun BookmarksScreen(
    groups: List<NotesGroup>,
    onOpenAnnotation: (BookRow, AnnotationEntry) -> Unit,
) {
    AnnotationGroupsScreen(
        title = "Bookmarks",
        groups = groups,
        matches = { !it.deleted && it.kind == KIND_BOOKMARK },
        emptyText = "No bookmarks yet — tap the bookmark icon while reading.",
        onOpenAnnotation = onOpenAnnotation,
    )
}

/** Title, optional [header], then the matching annotations grouped by book. */
@Composable
private fun AnnotationGroupsScreen(
    title: String,
    groups: List<NotesGroup>,
    matches: (AnnotationEntry) -> Boolean,
    emptyText: String,
    onOpenAnnotation: (BookRow, AnnotationEntry) -> Unit,
    header: @Composable () -> Unit = { Spacer(Modifier.height(4.dp)) },
) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Text(title, style = Type.screenTitle)
            Spacer(Modifier.height(12.dp))
            header()
        }

        groups.forEach { group ->
            val visible = group.entries.filter { matches(it.entry) }
            if (visible.isEmpty()) return@forEach
            item(key = group.row.bookHash) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    BookCover(group.row, width = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(group.row.title, style = Type.cardTitle)
                        Text(
                            group.row.author,
                            style = Type.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${visible.size}",
                        style = Type.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            items(
                visible,
                key = { it.entry.id },
            ) { annot ->
                AnnotationCards(
                    entries = listOf(annot.entry),
                    book = null,
                    ink = MaterialTheme.colorScheme.onBackground,
                    deviceLabels = mapOf(annot.entry.id to annot.deviceId),
                    onTap = { onOpenAnnotation(group.row, it) },
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        if (groups.all { g -> g.entries.none { matches(it.entry) } }) {
            item {
                Text(emptyText, style = Type.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
