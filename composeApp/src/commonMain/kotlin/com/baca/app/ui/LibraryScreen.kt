package com.baca.app.ui

import com.baca.shared.domain.isFinished
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        if (wide) {
            DesktopLibrary(books, onAdd, onOpen, onResume, onDelete, onNav)
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    PhoneLibrary(books, onAdd, onOpen, onResume, onDelete)
                }
                BottomNav(NavItem.LIBRARY, onNav)
            }
        }
    }
}

// ---------- shared bits ----------

private fun filterPredicate(query: String, filter: LibraryFilter): (LibraryBook) -> Boolean = { lib ->
    val r = lib.row
    (query.isBlank() || r.title.contains(query, true) || r.author.contains(query, true)) &&
        when (filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.READING -> lib.progress != null && !isFinished(lib.finished, lib.progress)
            LibraryFilter.FINISHED -> isFinished(lib.finished, lib.progress)
        }
}

/** Most-recently-read unfinished book, or null. */
private fun continueBook(visible: List<LibraryBook>): LibraryBook? =
    visible.filter { it.progress != null && it.progress >= 0.001f && !isFinished(it.finished, it.progress) }
        .maxByOrNull { it.lastReadAt ?: 0L }

@Composable
private fun FilterChipRow(filter: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            LibraryFilter.ALL to "All",
            LibraryFilter.READING to "Reading",
            LibraryFilter.FINISHED to "Finished",
        ).forEach { (key, label) ->
            // Filter chips: selected state is ink fill (TECH 10.3).
            val selected = filter == key
            val bg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .clickable { onSelect(key) }
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
            modifier = Modifier.width(148.dp),
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
            BookCover(lib.row, width = 104.dp)
            if (isFinished(lib.finished, lib.progress)) FinishedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(lib.row.title, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Left)
        Spacer(Modifier.height(4.dp))
        MiniProgressBar(lib.progress ?: 0f)
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
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }

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
                "Supports EPUB, FB2, HTML, Markdown, DOCX, RTF, TXT and CBZ",
                style = Type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val visible = books.filter(filterPredicate(query, filter))
    var menuOpenFor by remember { mutableStateOf<BookRow?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Library", style = Type.screenTitle)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SearchField(
                        query = query,
                        onChange = { query = it },
                        modifier = Modifier.width(180.dp),
                    )
                    AddButton(onAdd)
                }
            }
            Spacer(Modifier.height(12.dp))
            FilterChipRow(filter) { filter = it }
            Spacer(Modifier.height(16.dp))
            continueBook(visible)?.let { lib ->
                ContinueReadingCard(
                    lib = lib,
                    onClick = { onResume(lib.row) },
                )
                Spacer(Modifier.height(20.dp))
            }
            Text("ALL BOOKS", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        items(visible.chunked(3)) { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { lib ->
                    Box(Modifier.weight(1f)) {
                        BookCell(lib, onOpen, onDelete) { menuOpenFor = it }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(12.dp))
        }
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
        // long press → "Remove from library" (TECH 10.3, book detail action)
        menuOpenFor?.let { rowForMenu ->
            item {
                DropdownMenu(expanded = true, onDismissRequest = { menuOpenFor = null }) {
                    DropdownMenuItem(
                        text = { Text("Remove from library") },
                        onClick = { onDelete(rowForMenu); menuOpenFor = null },
                    )
                }
            }
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
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var menuOpenFor by remember { mutableStateOf<BookRow?>(null) }
    val visible = books.filter(filterPredicate(query, filter))

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
            FilterChipRow(filter) { filter = it }
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

            Text("ALL BOOKS", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            LazyColumn(Modifier.fillMaxWidth()) {
            items(visible.chunked(6)) { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { lib ->
                        Box(Modifier.weight(1f)) {
                            BookCell(lib, onOpen, onDelete) { menuOpenFor = it }
                        }
                    }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(20.dp))
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        "No books match.",
                        style = Type.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            menuOpenFor?.let { rowForMenu ->
                item {
                    DropdownMenu(expanded = true, onDismissRequest = { menuOpenFor = null }) {
                        DropdownMenuItem(
                            text = { Text("Remove from library") },
                            onClick = { onDelete(rowForMenu); menuOpenFor = null },
                        )
                    }
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
