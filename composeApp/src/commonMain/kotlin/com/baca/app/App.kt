package com.baca.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.baca.app.reader.BookFormats
import com.baca.app.reader.BookImporter
import com.baca.app.reader.CbzScreen
import com.baca.app.reader.LocalBookImages
import com.baca.app.reader.ReaderScreen
import com.baca.app.reader.rememberBookImages
import com.baca.app.reader.rememberBookPicker
import com.baca.app.reader.rememberBookStoreDir
import com.baca.app.reader.rememberFolderPicker
import com.baca.app.reader.rememberFolderChooser
import com.baca.app.reader.LibraryFolder
import com.baca.app.reader.folderLabel
import com.baca.app.reader.rememberLibraryFolder
import com.baca.app.reader.rememberBackupOpener
import com.baca.app.reader.SystemBarsAppearance
import com.baca.app.reader.PlatformBackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import com.baca.app.reader.rememberBackupSaver
import com.baca.shared.backup.LibraryBackup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.baca.app.reader.AnchorJump
import com.baca.app.ui.AppTheme
import com.baca.app.ui.ReaderThemes
import com.baca.app.ui.isDarkScheme
import com.baca.shared.domain.isFinished
import com.baca.app.ui.SettingsScreen
import com.baca.app.ui.BookDetailScreen
import com.baca.app.ui.BookDetails
import com.baca.app.ui.ImportItem
import com.baca.app.ui.ImportProgressSheet
import com.baca.app.ui.ImportStatus
import com.baca.app.ui.LibraryBook
import com.baca.app.ui.LibraryScreen
import com.baca.app.ui.Type
import com.baca.app.ui.NotesGroup
import com.baca.app.ui.NotesScreen
import com.baca.app.ui.BookmarksScreen
import com.baca.app.ui.NavShell
import com.baca.app.ui.AppThemeChoice
import com.baca.app.ui.NavItem
import com.baca.shared.db.BookRow
import com.baca.shared.db.DbAnnotationRepository
import com.baca.shared.db.DbBookRepository
import com.baca.shared.db.DbPositionStore
import com.baca.shared.db.Baca
import com.baca.shared.domain.loadDisplay
import com.baca.shared.domain.reader.relinkEntry
import androidx.compose.foundation.isSystemInDarkTheme
import com.baca.shared.domain.FixedLocator
import com.baca.shared.domain.Locator
import com.baca.shared.parser.BookInfo
import com.baca.shared.parser.CbzBook
import com.baca.shared.parser.ParseOptions
import com.baca.shared.parser.ParsedEpub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

sealed class OpenBook {
    abstract val path: String
    abstract val bookHash: String

    data class Reflow(val parsed: ParsedEpub, override val path: String, override val bookHash: String) : OpenBook()
    data class Fixed(val cbz: CbzBook, override val path: String, override val bookHash: String) : OpenBook()
}

/** One-screen-at-a-time navigation; the library is the home screen. */
private sealed class Screen {
    data object Library : Screen()
    data object Notes : Screen()
    data object Bookmarks : Screen()
    data object Settings : Screen()
    data class Detail(val row: BookRow) : Screen()
    data class Reader(val book: OpenBook, val jump: AnchorJump? = null, val page: Int? = null) : Screen()
}

@Composable
fun App(db: Baca, initialBookPath: String? = null, openContents: Boolean = false) {
    // outside AppTheme so the shell theme can come from the stored choice
    // (System follows the OS); the reader wraps itself in its own theme from
    // the reading-theme setting
    val store = remember(db) { DbPositionStore(db, now = defaultNow()) }
    // what the reader's settings change in how a book is read: the theme's colour scheme, direction, CSS, replacements
    fun readingOptions() = store.loadDisplay().let { it.parseOptions(isDarkScheme(ReaderThemes.forTheme(it.theme).background)) }
    var appTheme by remember { mutableStateOf(AppThemeChoice.of(store.getLocal(AppThemeChoice.STORE_KEY))) }
    val dark = when (appTheme) {
        AppThemeChoice.SYSTEM -> isSystemInDarkTheme()
        AppThemeChoice.LIGHT -> false
        AppThemeChoice.DARK -> true
    }
    AppTheme(dark = dark) {
        val booksRepo = remember(db) { DbBookRepository(db, now = defaultNow()) }
        val annotRepo = remember(db) { DbAnnotationRepository(db, now = defaultNow()) }
        var annotTick by remember { mutableIntStateOf(0) }
        val storeDir = rememberBookStoreDir()
        val importer = remember(booksRepo, storeDir) { BookImporter(booksRepo, storeDir) }
        val scope = rememberCoroutineScope()
        val fs = remember { FileSystem.SYSTEM }

        var libraryTick by remember { mutableIntStateOf(0) }
        var screenState by remember { mutableStateOf<Screen>(Screen.Library) }
        // big books parse off the UI thread; show what's opening meanwhile
        var openingTitle by remember { mutableStateOf<String?>(null) }

        // --- library folder (TECH 5.4): books read in place ---
        var libraryFolderPath by remember { mutableStateOf(store.getLocal(KEY_LIBRARY_FOLDER)) }
        val libraryFolder = rememberLibraryFolder(libraryFolderPath)
        // locations seen by the last scan; folder books missing from it are hidden
        var folderBooks by remember { mutableStateOf<Set<String>?>(null) }
        var scanning by remember { mutableStateOf(false) }
        var scanError by remember { mutableStateOf<String?>(null) }

        fun scanLibraryFolder() {
            val folder = libraryFolder ?: return
            scanning = true
            scope.launch {
                try {
                    folderBooks = withContext(Dispatchers.IO) { importer.scanFolder(folder) }
                    scanError = null
                    libraryTick++
                } catch (e: Exception) {
                    // folder unplugged or access revoked: keep showing what we know
                    scanError = e.message ?: "Can't read the library folder"
                } finally {
                    scanning = false
                }
            }
        }
        LaunchedEffect(libraryFolder) { scanLibraryFolder() }

        // --- add-books sheet + import progress ---
        var addSheetOpen by remember { mutableStateOf(false) }
        val importItems = remember { mutableStateListOf<ImportItem>() }
        var importing by remember { mutableStateOf(false) }
        val importCancelled = remember { mutableStateOf(false) }

        fun runImport(paths: List<String>) {
            val usable = paths.filter { BookFormats.of(it.substringAfterLast('/')) != null }
            val unsupported = paths.size - usable.size
            if (usable.isEmpty()) {
                importItems.clear()
                repeat(unsupported) { importItems.add(ImportItem("unsupported file", ImportStatus.UNSUPPORTED)) }
                return
            }
            importItems.clear()
            importItems.addAll(usable.map { ImportItem(it.substringAfterLast('/')) })
            repeat(unsupported) { importItems.add(ImportItem("unsupported file", ImportStatus.UNSUPPORTED)) }
            importing = true
            importCancelled.value = false
            scope.launch {
                for (i in usable.indices) {
                    if (importCancelled.value) break
                    importItems[i] = importItems[i].copy(status = ImportStatus.READING)
                    val status = withContext(Dispatchers.IO) {
                        when {
                            BookFormats.of(usable[i].substringAfterLast('/')) == null -> ImportStatus.UNSUPPORTED
                            // with a library folder, the book is copied there and read in place
                            else -> (
                                libraryFolder?.let { folder ->
                                    // a folder that can't be written to shows as a broken import
                                    val location = try { folder.add(usable[i]) } catch (e: Exception) { null }
                                    location?.let { importer.importInPlace(usable[i], it, restoreRemoved = true) }
                                } ?: if (libraryFolder == null) importer.import(usable[i]) else null
                                )
                                ?.let { if (it.isNew) ImportStatus.ADDED else ImportStatus.SKIPPED }
                                ?: ImportStatus.BROKEN
                        }
                    }
                    importItems[i] = importItems[i].copy(status = status)
                }
                importing = false
                libraryTick++
            }
        }

        val pick = rememberBookPicker { paths -> runImport(paths) }
        val pickFolder = rememberFolderPicker { paths -> if (paths.isNotEmpty()) runImport(paths) }
        // --- backup / restore (Settings) ---
        val backups = remember(db) { LibraryBackup(db) }
        var backupBusy by remember { mutableStateOf(false) }
        var backupMessage by remember { mutableStateOf<String?>(null) }
        var pendingRestore by remember { mutableStateOf<String?>(null) }

        fun runBackupTask(done: String, task: () -> Unit, afterSuccess: () -> Unit = {}) {
            backupBusy = true
            scope.launch {
                backupMessage = try {
                    withContext(Dispatchers.IO) { task() }
                    afterSuccess()
                    done
                } catch (e: Exception) {
                    e.message ?: "Backup failed"
                } finally {
                    backupBusy = false
                }
            }
        }

        val saveBackup = rememberBackupSaver { open ->
            if (open != null) runBackupTask("Backup saved.", task = { open().use { backups.create(it) } })
        }
        val openBackup = rememberBackupOpener { path -> pendingRestore = path }

        val pickLibraryFolder = rememberFolderChooser(title = "Choose library folder") { path ->
            if (path != null) {
                libraryFolderPath = path
                store.putLocal(KEY_LIBRARY_FOLDER, path)
                // the new folder is scanned through the LaunchedEffect above
            }
        }
        // Open the book passed on the command line; import first so library
        // state and saved positions line up with later sessions.
        LaunchedEffect(initialBookPath) {
            if (initialBookPath != null) {
                withContext(Dispatchers.IO) {
                    importer.import(initialBookPath)
                }?.let { imported ->
                    openBookOf(imported.row, libraryFolder, readingOptions())?.let { screenState = Screen.Reader(it) }
                }
                libraryTick++
            }
        }

        // Heal rows imported before the toc/cover parser fix (missing covers)
        // or before edition linking (no work key looked up yet).
        LaunchedEffect(booksRepo, importer) {
            val healed = withContext(Dispatchers.IO) {
                val rows = booksRepo.all().filter { it.format == "epub" }
                val stale = rows.filter { it.coverPath == null || !booksRepo.hasWorkKeyEntry(it.bookHash) }
                stale.forEach { importer.backfill(it) }
                stale.isNotEmpty()
            }
            if (healed) libraryTick++
        }

        fun openAsync(row: BookRow) {
            openingTitle = row.title
            scope.launch(Dispatchers.IO) {
                val book = openBookOf(row, libraryFolder, readingOptions())
                // Compose snapshot writes are safe from any thread; Dispatchers.Main
                // doesn't exist on desktop (that's kotlinx-coroutines-android)
                openingTitle = null
                book?.let { screenState = Screen.Reader(it) }
            }
        }

        val navTo: (NavItem) -> Unit = { item ->
            screenState = when (item) {
                NavItem.LIBRARY -> Screen.Library
                NavItem.NOTES -> Screen.Notes
                NavItem.BOOKMARKS -> Screen.Bookmarks
                NavItem.SETTINGS -> Screen.Settings
            }
        }

        // every book with its annotations (Notes and Bookmarks screens filter)
        @Composable
        fun annotationGroups(): List<NotesGroup> {
            val annots = remember(annotTick) { annotRepo.allDetailed() }
            return remember(annots, libraryTick) {
                booksRepo.visible().map { row ->
                    // other files of the same edition count as this book
                    val hashes = booksRepo.linkedHashes(row.bookHash).toSet()
                    NotesGroup(row = row, entries = annots.filter { it.first in hashes }.map { it.second })
                }
            }
        }

        /**
         * This book's annotations plus those made in other files of the same
         * edition; the latter are re-found by quote (TECH 5.1 linking).
         * [only] narrows the result to those entries (Notes list tap).
         */
        fun annotationsForReader(
            book: OpenBook.Reflow,
            only: List<com.baca.shared.domain.AnnotationEntry>? = null,
        ): List<DbAnnotationRepository.AnnotWithDevice> {
            val ids = only?.map { it.id }?.toSet()
            return booksRepo.linkedHashes(book.bookHash).flatMap { hash ->
                annotRepo.forBookDetailed(hash)
                    .filter { ids == null || it.entry.id in ids }
                    .map { if (hash == book.bookHash) it else it.copy(entry = relinkEntry(it.entry, book.parsed.chapters)) }
            }
        }

        fun openAnnotation(row: BookRow, picked: com.baca.shared.domain.AnnotationEntry) {
            val page = picked.fixedAnchor?.pageIndex
            if (picked.reflowAnchor == null && page == null) return
            openBookOf(row, libraryFolder, readingOptions())?.let { book ->
                // a note from another file of this edition jumps to its re-found spot
                val anchor = (book as? OpenBook.Reflow)
                    ?.let { annotationsForReader(it, listOf(picked)).firstOrNull()?.entry?.reflowAnchor }
                    ?.takeIf { it.href.isNotEmpty() }
                screenState = Screen.Reader(
                    book,
                    anchor?.let { AnchorJump(it.href, it.start.blockIndex, it.start.charOffset) },
                    page,
                )
            }
        }

        // Android draws behind the status and navigation bars: the background
        // fills them, content stays clear of them. The reflow reader pads itself
        // inside its own surface and sets the bar icons itself (its edges take
        // the page color while its bars are hidden).
        val readingReflow = (screenState as? Screen.Reader)?.book is OpenBook.Reflow
        if (!readingReflow) SystemBarsAppearance(darkBackground = dark)
        // Back: the add-books sheet closes first; other screens return to the
        // library (the readers handle Back themselves); the library exits
        PlatformBackHandler(enabled = screenState != Screen.Library && screenState !is Screen.Reader) {
            screenState = Screen.Library
        }
        PlatformBackHandler(enabled = addSheetOpen && screenState == Screen.Library) { addSheetOpen = false }
        // the screens with the bottom nav let it fill the navigation bar's area, so it doesn't float above it
        val navBelow = screenState is Screen.Library || screenState is Screen.Notes || screenState is Screen.Bookmarks || screenState is Screen.Settings
        val safeArea = if (navBelow) WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top) else WindowInsets.safeDrawing
        Surface(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(if (readingReflow) Modifier else Modifier.windowInsetsPadding(safeArea)),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val current = screenState) {
                is Screen.Reader -> {
                    when (val book = current.book) {
                        is OpenBook.Reflow -> {
                            val annotations = remember(annotTick, book.bookHash) {
                                annotationsForReader(book)
                            }
                            CompositionLocalProvider(LocalBookImages provides rememberBookImages(book.path, book.parsed.binaries)) {
                                ReaderScreen(
                                    book = book.parsed,
                                    bookPath = book.path,
                                    bookHash = book.bookHash,
                                    store = store,
                                    onBack = {
                                        screenState = booksRepo.byHash(book.bookHash)
                                            ?.let { Screen.Detail(it) } ?: Screen.Library
                                        libraryTick++
                                    },
                                    openContents = openContents,
                                    annotations = annotations.map { it.entry },
                                    deviceLabels = annotations.associate { it.entry.id to it.deviceId },
                                    onAnnotationUpsert = { entry ->
                                        scope.launch(Dispatchers.IO) {
                                            annotRepo.upsert(entry, book.bookHash)
                                            annotTick++
                                        }
                                    },
                                    onAnnotationDelete = { id ->
                                        scope.launch(Dispatchers.IO) {
                                            annotRepo.tombstone(id)
                                            annotTick++
                                        }
                                    },
                                    jumpToAnchor = current.jump,
                                    onOptionsChange = { options ->
                                        // read again under the reader's new settings; the reader comes back where it was
                                        scope.launch(Dispatchers.IO) {
                                            BookFormats.parse(book.path.toPath(), BookFormats.of(book.path.substringAfterLast('/')) ?: "epub", options)?.let { parsed ->
                                                screenState = Screen.Reader(book.copy(parsed = parsed))
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        is OpenBook.Fixed -> {
                            val annotations = remember(annotTick, book.bookHash) {
                                annotRepo.forBookDetailed(book.bookHash)
                            }
                            CbzScreen(
                                book = book.cbz,
                                bookPath = book.path,
                                bookHash = book.bookHash,
                                store = store,
                                onBack = {
                                    screenState = booksRepo.byHash(book.bookHash)
                                        ?.let { Screen.Detail(it) } ?: Screen.Library
                                    libraryTick++
                                },
                                annotations = annotations.map { it.entry },
                                onAnnotationUpsert = { entry ->
                                    scope.launch(Dispatchers.IO) {
                                        annotRepo.upsert(entry, book.bookHash)
                                        annotTick++
                                    }
                                },
                                onAnnotationDelete = { id ->
                                    scope.launch(Dispatchers.IO) {
                                        annotRepo.tombstone(id)
                                        annotTick++
                                    }
                                },
                                startPage = current.page,
                            )
                        }
                    }
                }
                is Screen.Detail -> {
                    val progress = booksRepo.progressFor(current.row.bookHash)
                    val lastRead = booksRepo.lastReadAt(current.row.bookHash)
                    val finished = remember(current.row.bookHash, libraryTick) {
                        isFinished(booksRepo.isFinished(current.row.bookHash), progress)
                    }
                    // library-folder books on Android are document URIs, not file paths
                    val sizeBytes by produceState(0L, current.row.path, libraryFolder) {
                        value = withContext(Dispatchers.IO) {
                            val path = current.row.path
                            if (path.startsWith("content://")) libraryFolder?.size(path) ?: 0L
                            else fs.metadataOrNull(path.toPath())?.size ?: 0L
                        }
                    }
                    // what the book says about itself: read once, for books imported before it was kept
                    val info by produceState<BookInfo?>(booksRepo.info(current.row.bookHash), current.row.bookHash, libraryFolder) {
                        if (value != null) return@produceState
                        val path = current.row.path
                        value = withContext(Dispatchers.IO) {
                            val staged = path.startsWith("content://")
                            val readable = if (staged) libraryFolder?.stage(path) else path
                            readable?.let {
                                try { importer.ensureInfo(current.row, it) } finally { if (staged) libraryFolder?.unstage(it) }
                            }
                        }
                    }
                    val details = remember(current.row, libraryTick, annotTick, libraryFolder, info) {
                        val marked = annotRepo.forBookDetailed(current.row.bookHash).map { it.entry }.filter { !it.deleted }
                        val path = current.row.path
                        val inLibraryFolder = libraryFolderPath != null && (path.startsWith("content://") || !path.startsWith(storeDir))
                        BookDetails(
                            // inside Baca a book is stored under its hash, which says nothing to the reader
                            fileName = if (!inLibraryFolder) "" else path.substringAfterLast('/').let { name ->
                                // a document URI's last part is percent-encoded
                                if (path.startsWith("content://")) java.net.URLDecoder.decode(name, "UTF-8").substringAfterLast('/') else name
                            },
                            storedIn = if (inLibraryFolder) "Library folder" else "Baca storage",
                            highlights = marked.count { it.kind == "highlight" || it.kind == "underline" },
                            notes = marked.count { it.kind == "note" },
                            bookmarks = marked.count { it.kind == "bookmark" },
                            info = info,
                        )
                    }
                    BookDetailScreen(
                        row = current.row,
                        progress = progress,
                        lastReadAt = lastRead,
                        addedAt = current.row.addedAt,
                        sizeBytes = sizeBytes,
                        onBack = { screenState = Screen.Library },
                        onContinue = { openAsync(current.row) },
                        onRemove = {
                            booksRepo.tombstone(current.row.bookHash)
                            screenState = Screen.Library
                            libraryTick++
                        },
                        details = details,
                        finished = finished,
                        onToggleFinished = {
                            booksRepo.setFinished(current.row.bookHash, !finished)
                            // unread means from the start; a read-through book would count as finished again
                            if (finished) store.resetToStart(current.row.bookHash, fixedLayout = current.row.format == "cbz")
                            libraryTick++
                        },
                    )
                }
                Screen.Notes -> NavShell(NavItem.NOTES, navTo) {
                    NotesScreen(groups = annotationGroups(), onOpenAnnotation = ::openAnnotation)
                }
                Screen.Bookmarks -> NavShell(NavItem.BOOKMARKS, navTo) {
                    BookmarksScreen(groups = annotationGroups(), onOpenAnnotation = ::openAnnotation)
                }
                Screen.Settings -> NavShell(NavItem.SETTINGS, navTo) {
                    SettingsScreen(
                        store = store,
                        appTheme = appTheme,
                        onAppThemeChange = { choice ->
                            appTheme = choice
                            store.putLocal(AppThemeChoice.STORE_KEY, choice.key)
                        },
                        libraryFolderLabel = libraryFolderPath?.let { folderLabel(it) },
                        scanning = scanning,
                        scanError = scanError,
                        onChooseLibraryFolder = pickLibraryFolder,
                        onRescan = { scanLibraryFolder() },
                        backupBusy = backupBusy,
                        backupMessage = backupMessage,
                        onCreateBackup = saveBackup,
                        onRestoreBackup = openBackup,
                        onBack = { screenState = Screen.Library },
                    )
                }
                Screen.Library -> {
                    val books = remember(libraryTick, folderBooks) {
                        val present = folderBooks
                        booksRepo.visible().filter { row ->
                            present == null || libraryFolder?.contains(row.path) != true || row.path in present
                        }
                    }
                    val library = books.map { row ->
                        LibraryBook(
                            row = row,
                            progress = booksRepo.progressFor(row.bookHash),
                            lastReadAt = booksRepo.lastReadAt(row.bookHash),
                            finished = booksRepo.isFinished(row.bookHash),
                        )
                    }
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().weight(1f)) {
                        LibraryScreen(
                            books = library,
                            onAdd = { addSheetOpen = true },
                            onOpen = { row ->
                                screenState = Screen.Detail(row)
                            },
                            onResume = { row ->
                                openAsync(row)
                            },
                            onNav = navTo,
                            onDelete = { row ->
                                booksRepo.tombstone(row.bookHash)
                                libraryTick++
                            },
                        )
                        if (addSheetOpen) {
                            Box(
                                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                                    .clickable { addSheetOpen = false },
                            )
                            Column(
                                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                            ) {
                                ImportProgressSheet(
                                    items = importItems.toList(),
                                    running = importing,
                                    folderMode = false,
                                    onFromFiles = pick,
                                    onFromFolder = pickFolder,
                                    onCancel = { importCancelled.value = true },
                                    onDone = {
                                        addSheetOpen = false
                                        importItems.clear()
                                        libraryTick++
                                    },
                                )
                            }
                        }
                        }
                    }
                }
            }
            pendingRestore?.let { archive ->
                AlertDialog(
                    onDismissRequest = { pendingRestore = null },
                    title = { Text("Replace your library?") },
                    text = {
                        Text(
                            "Books, positions, notes, bookmarks and settings on this device are replaced by the backup. " +
                                "The current library is saved as ${LibraryBackup.SAFETY_COPY} first.",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingRestore = null
                            runBackupTask(
                                "Library restored.",
                                task = { backups.restore(archive.toPath(), storeDir) },
                                // screens hold the replaced library in memory
                                afterSuccess = {
                                    libraryTick++
                                    annotTick++
                                    appTheme = AppThemeChoice.of(store.getLocal(AppThemeChoice.STORE_KEY))
                                },
                            )
                        }) { Text("Replace") }
                    },
                    dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
                )
            }
            openingTitle?.let { title ->
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Opening $title…", style = Type.body, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Library tap / launcher entry: parse from the row's stored path and format.
 * Android library-folder books are document URIs; [folder] stages a local copy.
 */
private fun openBookOf(row: BookRow, folder: LibraryFolder?, options: ParseOptions): OpenBook? {
    val readable = if (row.path.startsWith("content://")) folder?.stage(row.path) ?: return null else row.path
    return when (row.format) {
        "cbz" -> BookFormats.parseCbzFile(readable.toPath(), readable.substringAfterLast('/'))
            ?.let { OpenBook.Fixed(it, readable, row.bookHash) }
        else -> BookFormats.parse(readable.toPath(), row.format, options)?.let { parsed ->
            // a fixed-layout book of pictures is read page by page, like a comic
            parsed.fixedPages?.let { OpenBook.Fixed(CbzBook(parsed.title, it), readable, row.bookHash) }
                ?: OpenBook.Reflow(parsed, readable, row.bookHash)
        }
    }
}

private const val KEY_LIBRARY_FOLDER = "library_folder"

/** Wall clock, display/debug use only (docs/TECH.md 6.1). */
expect fun defaultNow(): () -> Long
