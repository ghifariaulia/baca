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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    groupBySeries: Boolean = false,
    onToggleGroupBySeries: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        if (wide) {
            DesktopLibrary(books, onAdd, onOpen, onResume, onDelete, onNav, sort, onSortChange, shelves, onToggleBookShelf, groupBySeries, onToggleGroupBySeries)
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    PhoneLibrary(books, onAdd, onOpen, onResume, onDelete, sort, onSortChange, shelves, onToggleBookShelf, groupBySeries, onToggleGroupBySeries)
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
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (groupBySeries) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable(onClick = onToggleGroupBySeries)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .semantics { contentDescription = "Group by series" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (groupBySeries) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { open = true }
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
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

@Composable
private fun FilterChipRow(
    filter: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    shelves: List<String> = emptyList(),
    selectedShelf: String? = null,
    onSelectShelf: (String?) -> Unit = {},
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .clickable {
                        onSelect(key)
                        onSelectShelf(null)
                    }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
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
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .clickable { onSelectShelf(if (selected) null else shelf) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    shelf,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    // Design pill: white bg, line border, 40dp — no Material padding that crops.
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = Type.body,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun BookCell(
    lib: LibraryBook,
    onOpen: (BookRow) -> Unit,
    onDelete: (BookRow) -> Unit,
    menuOpenFor: (BookRow?) -> Unit,
) {
    Column(
        Modifier.combinedClickable(
            onClick = { onOpen(lib.row) },
            onLongClick = { menuOpenFor(lib.row) },
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
    var menuOpenFor by remember { mutableStateOf<BookRow?>(null) }
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
                FilterChipRow(filter, { filter = it }, shelves, selectedShelf, { selectedShelf = it })
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
            bookGridContent(visible, cols, 12.dp, groupBySeries, onOpen, onDelete) { menuOpenFor = it }
        if (visible.isEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "No books match.",
                    style = Type.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    // long press → "Shelves" / "Remove from library" (TECH 10.3, book detail action)
    menuOpenFor?.let { rowForMenu ->
        val bookShelves = books.firstOrNull { it.row.bookHash == rowForMenu.bookHash }?.shelves.orEmpty()
        BookContextMenu(
            rowForMenu = rowForMenu,
            bookShelves = bookShelves,
            allShelves = shelves,
            onToggleBookShelf = onToggleBookShelf,
            onDelete = onDelete,
            onDismiss = { menuOpenFor = null },
        )
    }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.bookGridContent(
    visible: List<LibraryBook>,
    cols: Int,
    rowSpacing: androidx.compose.ui.unit.Dp,
    groupBySeries: Boolean,
    onOpen: (BookRow) -> Unit,
    onDelete: (BookRow) -> Unit,
    onMenuOpen: (BookRow?) -> Unit,
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
                BookGridRow(row, cols, rowSpacing, onOpen, onDelete, onMenuOpen)
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
                BookGridRow(row, cols, rowSpacing, onOpen, onDelete, onMenuOpen)
            }
        }
    } else {
        items(visible.chunked(cols)) { row ->
            BookGridRow(row, cols, rowSpacing, onOpen, onDelete, onMenuOpen)
        }
    }
}

@Composable
private fun BookGridRow(
    row: List<LibraryBook>,
    cols: Int,
    rowSpacing: androidx.compose.ui.unit.Dp,
    onOpen: (BookRow) -> Unit,
    onDelete: (BookRow) -> Unit,
    onMenuOpen: (BookRow?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        row.forEach { lib ->
            Box(Modifier.weight(1f)) {
                BookCell(lib, onOpen, onDelete, onMenuOpen)
            }
        }
        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
    }
    Spacer(Modifier.height(rowSpacing))
}

@Composable
private fun BookContextMenu(
    rowForMenu: BookRow,
    bookShelves: List<String>,
    allShelves: List<String>,
    onToggleBookShelf: (BookRow, String) -> Unit,
    onDelete: (BookRow) -> Unit,
    onDismiss: () -> Unit,
) {
    var showShelves by remember(rowForMenu) { mutableStateOf(false) }
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        if (showShelves) {
            allShelves.forEach { shelf ->
                val inShelf = shelf in bookShelves
                DropdownMenuItem(
                    text = { Text(shelf, fontWeight = if (inShelf) FontWeight.Bold else FontWeight.Normal) },
                    trailingIcon = if (inShelf) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = {
                        onToggleBookShelf(rowForMenu, shelf)
                        onDismiss()
                    },
                )
            }
        } else {
            if (allShelves.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Shelves") },
                    onClick = { showShelves = true },
                )
            }
            DropdownMenuItem(
                text = { Text("Remove from library") },
                onClick = {
                    onDelete(rowForMenu)
                    onDismiss()
                },
            )
        }
    }
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
    groupBySeries: Boolean,
    onToggleGroupBySeries: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var selectedShelf by remember { mutableStateOf<String?>(null) }
    var menuOpenFor by remember { mutableStateOf<BookRow?>(null) }
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
            FilterChipRow(filter, { filter = it }, shelves, selectedShelf, { selectedShelf = it })
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
                                (lib.lastReadAt?.let { "just now" } ?: "never"),
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
            bookGridContent(visible, 6, 20.dp, groupBySeries, onOpen, onDelete) { menuOpenFor = it }
            if (visible.isEmpty()) {
                item {
                    Text(
                        "No books match.",
                        style = Type.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        menuOpenFor?.let { rowForMenu ->
            val bookShelves = books.firstOrNull { it.row.bookHash == rowForMenu.bookHash }?.shelves.orEmpty()
            BookContextMenu(
                rowForMenu = rowForMenu,
                bookShelves = bookShelves,
                allShelves = shelves,
                onToggleBookShelf = onToggleBookShelf,
                onDelete = onDelete,
                onDismiss = { menuOpenFor = null },
            )
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
