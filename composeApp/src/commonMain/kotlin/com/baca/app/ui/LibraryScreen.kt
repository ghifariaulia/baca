package com.baca.app.ui

import com.baca.shared.domain.isFinished
import com.baca.shared.domain.shownProgress
import com.baca.shared.domain.LibrarySort
import com.baca.shared.domain.SortFacts
import com.baca.shared.domain.groupBySeries
import com.baca.shared.domain.sortLibrary
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.baca.shared.db.BookRow

/** Row + its saved reading progress (0..1) and last-read wall time. */
data class LibraryBook(
    val row: BookRow,
    val progress: Float?,
    val lastReadAt: Long? = null,
    /** marked finished by the reader, whatever the progress says */
    val finished: Boolean = false,
    val series: String = "",
    val shelves: List<String> = emptyList(),
)

enum class LibraryFilter { ALL, READING, FINISHED }

/**
 * Library screen (design file 'Library', TECH 10.4): title + search + add,
 * filter chips, continue-reading card, all-books grid. Desktop (TECH 10.5,
 * width ≥ 900): 240 sidebar + 6-column grid; below that the phone layout.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    books: List<LibraryBook>,
    onAdd: () -> Unit,
    /** grid tap → book detail (design flow) */
    onOpen: (BookRow) -> Unit,
    /** continue-reading card → straight into the reader */
    onResume: (BookRow) -> Unit,
    onDelete: (BookRow) -> Unit,
    /** sidebar / bottom nav item tapped */
    onNav: (NavItem) -> Unit = {},
    sort: LibrarySort = LibrarySort.ADDED,
    onSortChange: (LibrarySort) -> Unit = {},
    shelves: List<String> = emptyList(),
    onToggleBookShelf: (BookRow, String) -> Unit = { _, _ -> },
    onCreateShelf: (String) -> Unit = {},
    onCreateBookShelf: (BookRow, String) -> Unit = { _, name -> onCreateShelf(name) },
    onRenameShelf: (String, String) -> Unit = { _, _ -> },
    onDeleteShelf: (String) -> Unit = {},
    onToggleFinished: (BookRow) -> Unit = {},
    groupBySeries: Boolean = false,
    onToggleGroupBySeries: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        if (wide) {
            DesktopLibrary(
                books, onAdd, onOpen, onResume, onDelete, onNav, sort, onSortChange,
                shelves, onToggleBookShelf, onCreateShelf, onCreateBookShelf, onRenameShelf, onDeleteShelf,
                onToggleFinished, groupBySeries, onToggleGroupBySeries,
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    PhoneLibrary(
                        books, onAdd, onOpen, onResume, onDelete, sort, onSortChange,
                        shelves, onToggleBookShelf, onCreateShelf, onCreateBookShelf, onRenameShelf, onDeleteShelf,
                        onToggleFinished, groupBySeries, onToggleGroupBySeries,
                    )
                }
                BottomNav(NavItem.LIBRARY, onNav)
            }
        }
    }
}

// ---------- shared bits ----------

private fun filterPredicate(query: String, filter: LibraryFilter, selectedShelf: String?): (LibraryBook) -> Boolean = { lib ->
    val r = lib.row
    (query.isBlank() || r.title.contains(query, true) || r.author.contains(query, true)) &&
        (selectedShelf == null || selectedShelf in lib.shelves) &&
        when (filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.READING -> lib.progress != null && !isFinished(lib.finished, lib.progress)
            LibraryFilter.FINISHED -> isFinished(lib.finished, lib.progress)
        }
}

private fun sortFacts(lib: LibraryBook) =
    SortFacts(lib.row.title, lib.row.author, lib.row.addedAt, lib.lastReadAt, if (isFinished(lib.finished, lib.progress)) 1f else lib.progress)

/** "ALL BOOKS" with series-grouping toggle and sort order on the right. */
@Composable
private fun AllBooksHeader(
    sort: LibrarySort,
    onSelect: (LibrarySort) -> Unit,
    groupBySeries: Boolean,
    onToggleGroupBySeries: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("ALL BOOKS", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (groupBySeries) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable(onClick = onToggleGroupBySeries)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { contentDescription = "Group by series" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (groupBySeries) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Series",
                    fontSize = 14.sp,
                    fontWeight = if (groupBySeries) FontWeight.Bold else FontWeight.Normal,
                    color = if (groupBySeries) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                Row(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { open = true }
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(sort.label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Sort books")
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    LibrarySort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(option.label, fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal)
                            },
                            onClick = {
                                open = false
                                onSelect(option)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Most-recently-read unfinished book, or null. */
private fun continueBook(visible: List<LibraryBook>): LibraryBook? =
    visible.filter { it.progress != null && it.progress >= 0.001f && !isFinished(it.finished, it.progress) }
        .maxByOrNull { it.lastReadAt ?: 0L }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterChipRow(
    filter: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    shelves: List<String> = emptyList(),
    selectedShelf: String? = null,
    onSelectShelf: (String?) -> Unit = {},
    onCreateShelf: (String) -> Unit = {},
    onRenameShelf: (String, String) -> Unit = { _, _ -> },
    onDeleteShelf: (String) -> Unit = {},
) {
    var newShelfOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            LibraryFilter.ALL to "All",
            LibraryFilter.READING to "Reading",
            LibraryFilter.FINISHED to "Finished",
        ).forEach { (key, label) ->
            // Filter chips: selected state is ink fill (TECH 10.3).
            val selected = filter == key && selectedShelf == null
            val bg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(bg)
                    .clickable {
                        onSelect(key)
                        onSelectShelf(null)
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        shelves.forEach { shelf ->
            val selected = selectedShelf == shelf
            val bg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
            var shelfMenuOpen by remember(shelf) { mutableStateOf(false) }
            Box {
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(bg)
                        .pointerInput(shelf) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                        shelfMenuOpen = true
                                    }
                                }
                            }
                        }
                        .combinedClickable(
                            onClick = {
                                if (selected) {
                                    onSelectShelf(null)
                                } else {
                                    onSelect(LibraryFilter.ALL)
                                    onSelectShelf(shelf)
                                }
                            },
                            onLongClick = { shelfMenuOpen = true },
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        shelf,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(expanded = shelfMenuOpen, onDismissRequest = { shelfMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename shelf") },
                        onClick = {
                            shelfMenuOpen = false
                            renameTarget = shelf
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete shelf", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            shelfMenuOpen = false
                            deleteTarget = shelf
                        },
                    )
                }
            }
        }
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { newShelfOpen = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("+ New shelf", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }

    if (newShelfOpen) {
        NewShelfDialog(
            onDismiss = { newShelfOpen = false },
            onCreate = { name ->
                onCreateShelf(name)
                newShelfOpen = false
            },
        )
    }

    renameTarget?.let { oldName ->
        NewShelfDialog(
            initialName = oldName,
            title = "Rename shelf",
            confirmLabel = "Save",
            onDismiss = { renameTarget = null },
            onCreate = { newName ->
                onRenameShelf(oldName, newName)
                if (selectedShelf.equals(oldName, ignoreCase = true)) {
                    onSelectShelf(newName)
                }
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete shelf?") },
            text = { Text("Delete \"$target\"? Books in this shelf stay in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteShelf(target)
                    if (selectedShelf.equals(target, ignoreCase = true)) onSelectShelf(null)
                    deleteTarget = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    // Design pill: white bg, line border, 44dp touch target — no Material padding that crops.
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = Type.body.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).semantics { contentDescription = "Search library" },
        ) { inner ->
            if (query.isEmpty()) {
                Text(
                    "Search library",
                    style = Type.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                inner()
            }
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onChange("") }
                    .semantics { contentDescription = "Clear search" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun AddButton(onAdd: () -> Unit, label: String? = null) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val shape = RoundedCornerShape(22.dp)
    if (label == null) {
        Box(
            Modifier
                .size(44.dp)
                .clip(shape)
                .background(accent)
                .clickable(onClick = onAdd)
                .semantics { contentDescription = "Add books" },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = onAccent,
            )
        }
    } else {
        Row(
            Modifier
                .height(44.dp)
                .clip(shape)
                .background(accent)
                .clickable(onClick = onAdd)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = onAccent,
            )
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = onAccent)
        }
    }
}

/** A check on a cover: the book is finished. */
@Composable
private fun FinishedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Finished",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCell(
    lib: LibraryBook,
    allShelves: List<String>,
    onOpen: (BookRow) -> Unit,
    onResume: (BookRow) -> Unit,
    onToggleBookShelf: (BookRow, String) -> Unit,
    onCreateBookShelf: (BookRow, String) -> Unit,
    onToggleFinished: (BookRow) -> Unit,
    onDelete: (BookRow) -> Unit,
) {
    var menuOpen by remember(lib.row.bookHash) { mutableStateOf(false) }
    var shelvesDialogOpen by remember(lib.row.bookHash) { mutableStateOf(false) }
    var removeConfirmOpen by remember(lib.row.bookHash) { mutableStateOf(false) }

    Box {
        Column(
            Modifier
                .pointerInput(lib.row.bookHash) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                menuOpen = true
                            }
                        }
                    }
                }
                .combinedClickable(
                    onClick = { onOpen(lib.row) },
                    onLongClick = { menuOpen = true },
                ),
        ) {
            Box {
                BookCover(lib.row, fillWidth = true)
                if (isFinished(lib.finished, lib.progress)) FinishedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(lib.row.title, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Left)
            Spacer(Modifier.height(4.dp))
            MiniProgressBar(shownProgress(lib.finished, lib.progress))
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (lib.lastReadAt == null || (lib.progress ?: 0f) == 0f) "Start reading" else "Continue reading",
                        fontWeight = FontWeight.Medium,
                    )
                },
                onClick = {
                    menuOpen = false
                    onResume(lib.row)
                },
            )
            DropdownMenuItem(
                text = { Text("Book details") },
                onClick = {
                    menuOpen = false
                    onOpen(lib.row)
                },
            )
            DropdownMenuItem(
                text = { Text("Shelves") },
                onClick = {
                    menuOpen = false
                    shelvesDialogOpen = true
                },
            )
            DropdownMenuItem(
                text = { Text(if (lib.finished) "Mark as unread" else "Mark as finished") },
                onClick = {
                    menuOpen = false
                    onToggleFinished(lib.row)
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            DropdownMenuItem(
                text = { Text("Remove from library", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    removeConfirmOpen = true
                },
            )
        }
    }

    if (shelvesDialogOpen) {
        BookShelvesDialog(
            bookTitle = lib.row.title,
            bookShelves = lib.shelves,
            allShelves = allShelves,
            onToggleShelf = { shelf -> onToggleBookShelf(lib.row, shelf) },
            onCreateShelf = { name -> onCreateBookShelf(lib.row, name) },
            onDismiss = { shelvesDialogOpen = false },
        )
    }

    if (removeConfirmOpen) {
        ConfirmRemoveBookDialog(
            title = lib.row.title,
            onDismiss = { removeConfirmOpen = false },
            onConfirm = {
                removeConfirmOpen = false
                onDelete(lib.row)
            },
        )
    }
}

@Composable
private fun BookShelvesDialog(
    bookTitle: String,
    bookShelves: List<String>,
    allShelves: List<String>,
    onToggleShelf: (String) -> Unit,
    onCreateShelf: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newShelfOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shelves") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    bookTitle,
                    style = Type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (allShelves.isEmpty()) {
                    Text(
                        "No shelves yet. Create one to organize your books.",
                        style = Type.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    allShelves.forEach { shelf ->
                        val inShelf = shelf in bookShelves
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleShelf(shelf) }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = inShelf,
                                onCheckedChange = { onToggleShelf(shelf) },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                shelf,
                                style = Type.body,
                                fontWeight = if (inShelf) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { newShelfOpen = true },
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("+ New shelf", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
    if (newShelfOpen) {
        NewShelfDialog(
            onDismiss = { newShelfOpen = false },
            onCreate = { name ->
                onCreateShelf(name)
                newShelfOpen = false
            },
        )
    }
}

// ---------- phone layout ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneLibrary(
    books: List<LibraryBook>,
    onAdd: () -> Unit,
    onOpen: (BookRow) -> Unit,
    onResume: (BookRow) -> Unit,
    onDelete: (BookRow) -> Unit,
    sort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
    shelves: List<String>,
    onToggleBookShelf: (BookRow, String) -> Unit,
    onCreateShelf: (String) -> Unit,
    onCreateBookShelf: (BookRow, String) -> Unit,
    onRenameShelf: (String, String) -> Unit,
    onDeleteShelf: (String) -> Unit,
    onToggleFinished: (BookRow) -> Unit,
    groupBySeries: Boolean,
    onToggleGroupBySeries: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var selectedShelf by remember { mutableStateOf<String?>(null) }

    if (books.isEmpty()) {
        // "Empty library" page from the design file: fanned covers, copy, add
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BookFanIllustration()
            Spacer(Modifier.height(24.dp))
            Text("Your library is empty", style = Type.cardTitle, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add books from this device. To move your library, positions and notes " +
                    "to another device, create a backup in Settings.",
                style = Type.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(24.dp))
            val accent = MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(accent)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Add books",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Supports EPUB, FB2, PDF, HTML, Markdown, DOCX, RTF, TXT and CBZ",
                style = Type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val visible = sortLibrary(books.filter(filterPredicate(query, filter, selectedShelf)), sort, ::sortFacts)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = ((maxWidth - 40.dp) / 116.dp).toInt().coerceIn(2, 5)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Library", style = Type.screenTitle)
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SearchField(
                            query = query,
                            onChange = { query = it },
                            modifier = Modifier.weight(1f, fill = false).widthIn(min = 100.dp, max = 180.dp),
                        )
                        AddButton(onAdd)
                    }
                }
                Spacer(Modifier.height(12.dp))
                FilterChipRow(
                    filter = filter,
                    onSelect = { filter = it },
                    shelves = shelves,
                    selectedShelf = selectedShelf,
                    onSelectShelf = { selectedShelf = it },
                    onCreateShelf = onCreateShelf,
                    onRenameShelf = onRenameShelf,
                    onDeleteShelf = onDeleteShelf,
                )
                Spacer(Modifier.height(16.dp))
                continueBook(visible)?.let { lib ->
                    ContinueReadingCard(
                        lib = lib,
                        onClick = { onResume(lib.row) },
                    )
                    Spacer(Modifier.height(20.dp))
                }
                AllBooksHeader(sort, onSortChange, groupBySeries, onToggleGroupBySeries)
                Spacer(Modifier.height(8.dp))
            }
            bookGridContent(
                visible = visible,
                cols = cols,
                rowSpacing = 12.dp,
                groupBySeries = groupBySeries,
                allShelves = shelves,
                onOpen = onOpen,
                onResume = onResume,
                onToggleBookShelf = onToggleBookShelf,
                onCreateBookShelf = onCreateBookShelf,
                onToggleFinished = onToggleFinished,
                onDelete = onDelete,
            )
            if (visible.isEmpty()) {
                item {
                    EmptyFilteredState(
                        onClear = {
                            query = ""
                            filter = LibraryFilter.ALL
                            selectedShelf = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFilteredState(onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "No books match.",
            style = Type.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 44.dp)) {
            Text("Clear filters", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.bookGridContent(
    visible: List<LibraryBook>,
    cols: Int,
    rowSpacing: androidx.compose.ui.unit.Dp,
    groupBySeries: Boolean,
    allShelves: List<String>,
    onOpen: (BookRow) -> Unit,
    onResume: (BookRow) -> Unit,
    onToggleBookShelf: (BookRow, String) -> Unit,
    onCreateBookShelf: (BookRow, String) -> Unit,
    onToggleFinished: (BookRow) -> Unit,
    onDelete: (BookRow) -> Unit,
) {
    if (groupBySeries && visible.isNotEmpty()) {
        val (seriesGroups, standalones) = groupBySeries(visible, seriesOf = { it.series }, titleOf = { it.row.title })
        seriesGroups.forEach { group ->
            item {
                Text(
                    "${group.name} (${group.books.size})",
                    style = Type.cardTitle,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
            items(group.books.chunked(cols)) { row ->
                BookGridRow(row, cols, rowSpacing, allShelves, onOpen, onResume, onToggleBookShelf, onCreateBookShelf, onToggleFinished, onDelete)
            }
        }
        if (standalones.isNotEmpty()) {
            item {
                Text(
                    "Standalone",
                    style = Type.cardTitle,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
            items(standalones.chunked(cols)) { row ->
                BookGridRow(row, cols, rowSpacing, allShelves, onOpen, onResume, onToggleBookShelf, onCreateBookShelf, onToggleFinished, onDelete)
            }
        }
    } else {
        items(visible.chunked(cols)) { row ->
            BookGridRow(row, cols, rowSpacing, allShelves, onOpen, onResume, onToggleBookShelf, onCreateBookShelf, onToggleFinished, onDelete)
        }
    }
}

@Composable
private fun BookGridRow(
    row: List<LibraryBook>,
    cols: Int,
    rowSpacing: androidx.compose.ui.unit.Dp,
    allShelves: List<String>,
    onOpen: (BookRow) -> Unit,
    onResume: (BookRow) -> Unit,
    onToggleBookShelf: (BookRow, String) -> Unit,
    onCreateBookShelf: (BookRow, String) -> Unit,
    onToggleFinished: (BookRow) -> Unit,
    onDelete: (BookRow) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        row.forEach { lib ->
            Box(Modifier.weight(1f)) {
                BookCell(lib, allShelves, onOpen, onResume, onToggleBookShelf, onCreateBookShelf, onToggleFinished, onDelete)
            }
        }
        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
    }
    Spacer(Modifier.height(rowSpacing))
}

@Composable
private fun ContinueReadingCard(lib: LibraryBook, onClick: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookCover(lib.row, width = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "CONTINUE READING",
                    style = Type.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(lib.row.title, style = Type.cardTitle)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${((lib.progress ?: 0f) * 100).toInt()}%",
                    style = Type.body,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(outline)) {
            Box(
                Modifier.fillMaxWidth(lib.progress ?: 0f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
        }
    }
}

// ---------- desktop layout (TECH 10.4/10.5) ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopLibrary(
    books: List<LibraryBook>,
    onAdd: () -> Unit,
    onOpen: (BookRow) -> Unit,
    onResume: (BookRow) -> Unit,
    onDelete: (BookRow) -> Unit,
    onNav: (NavItem) -> Unit,
    sort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
    shelves: List<String>,
    onToggleBookShelf: (BookRow, String) -> Unit,
    onCreateShelf: (String) -> Unit,
    onCreateBookShelf: (BookRow, String) -> Unit,
    onRenameShelf: (String, String) -> Unit,
    onDeleteShelf: (String) -> Unit,
    onToggleFinished: (BookRow) -> Unit,
    groupBySeries: Boolean,
    onToggleGroupBySeries: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var selectedShelf by remember { mutableStateOf<String?>(null) }
    val visible = sortLibrary(books.filter(filterPredicate(query, filter, selectedShelf)), sort, ::sortFacts)

    Row(Modifier.fillMaxSize()) {
        Sidebar(active = NavItem.LIBRARY, onNav = onNav)
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(32.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Library", style = Type.screenTitle, fontSize = 32.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SearchField(query = query, onChange = { query = it }, modifier = Modifier.width(220.dp))
                    AddButton(onAdd, label = "Add book")
                }
            }
            Spacer(Modifier.height(12.dp))
            FilterChipRow(
                filter = filter,
                onSelect = { filter = it },
                shelves = shelves,
                selectedShelf = selectedShelf,
                onSelectShelf = { selectedShelf = it },
                onCreateShelf = onCreateShelf,
                onRenameShelf = onRenameShelf,
                onDeleteShelf = onDeleteShelf,
            )
            Spacer(Modifier.height(20.dp))

            // wide continue card with a Resume action (design 'Desktop Library')
            continueBook(visible)?.let { lib ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onResume(lib.row) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BookCover(lib.row, width = 64.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "CONTINUE READING",
                            style = Type.sectionLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(lib.row.title, style = Type.cardTitle, fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${((lib.progress ?: 0f) * 100).toInt()}% · last read " +
                                (lib.lastReadAt?.let { relativeTime(it) } ?: "never"),
                            style = Type.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline)) {
                            Box(
                                Modifier.fillMaxWidth(lib.progress ?: 0f).height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                    // secondary Resume button: ink fill (TECH 10.3)
                    Box(
                        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.onSurface)
                            .clickable { onResume(lib.row) }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Resume",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.background,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            AllBooksHeader(sort, onSortChange, groupBySeries, onToggleGroupBySeries)
            Spacer(Modifier.height(12.dp))

            LazyColumn(Modifier.fillMaxWidth()) {
                bookGridContent(
                    visible = visible,
                    cols = 6,
                    rowSpacing = 20.dp,
                    groupBySeries = groupBySeries,
                    allShelves = shelves,
                    onOpen = onOpen,
                    onResume = onResume,
                    onToggleBookShelf = onToggleBookShelf,
                    onCreateBookShelf = onCreateBookShelf,
                    onToggleFinished = onToggleFinished,
                    onDelete = onDelete,
                )
                if (visible.isEmpty()) {
                    item {
                        EmptyFilteredState(
                            onClear = {
                                query = ""
                                filter = LibraryFilter.ALL
                                selectedShelf = null
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 240dp sidebar (TECH 10.3): one row per [NavItem]. */
@Composable
internal fun Sidebar(active: NavItem = NavItem.LIBRARY, onNav: (NavItem) -> Unit = {}) {
    val accentTint = MaterialTheme.colorScheme.secondaryContainer
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(com.baca.shared.AppInfo.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        NavItem.entries.forEach { item -> navRow(item, item.label, active, onNav, accentTint, accent) }
    }
}

@Composable
private fun navRow(
    item: NavItem,
    label: String,
    active: NavItem,
    onNav: (NavItem) -> Unit,
    accentTint: Color,
    accent: Color,
) {
    val isActive = item == active
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) accentTint else Color.Transparent)
            .clickable { onNav(item) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NavIcon(item, if (isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant, 18.dp)
        Text(
            label,
            style = Type.body,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Three fanned covers; "Your library is empty" illustration in the design file. */
@Composable
private fun BookFanIllustration() {
    Row(
        horizontalArrangement = Arrangement.spacedBy((-28).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colors = listOf(
            Color(0xFF2E5E4E),
            Color(0xFF7A2F3B),
            Color(0xFF4E3B70),
        )
        val angles = listOf(-14f, 0f, 14f)
        colors.forEachIndexed { i, color ->
            Box(
                Modifier
                    .size(width = 64.dp, height = 92.dp)
                    .zIndex(if (i == 1) 2f else 1f)
                    .graphicsLayer { rotationZ = angles[i] }
                    .clip(RoundedCornerShape(6.dp))
                    .background(color),
            )
        }
    }
}
