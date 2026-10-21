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
import com.baca.app.reader.rememberSyncFolderPicker
import com.baca.app.reader.LibraryFolder
import com.baca.app.reader.folderLabel
import com.baca.app.reader.rememberLibraryFolder
import com.baca.app.reader.rememberSyncTransport
import com.baca.app.reader.rememberBackupOpener
import com.baca.app.reader.SystemBarsAppearance
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import com.baca.app.reader.rememberBackupSaver
import com.baca.shared.backup.LibraryBackup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.baca.app.reader.AnchorJump
import com.baca.app.sync.SyncCoordinator
import com.baca.app.sync.SyncTriggerStore
import com.baca.app.ui.AppTheme
import com.baca.app.ui.SettingsScreen
import com.baca.app.ui.BookDetailScreen
import com.baca.app.ui.DeviceRow
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
import com.baca.app.ui.MergeBanner
import com.baca.app.ui.SyncScreen
import com.baca.app.ui.SyncScreenData
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
import com.baca.shared.parser.CbzBook
import com.baca.shared.parser.ParsedEpub
import com.baca.shared.sync.SyncBoundary
import com.baca.shared.sync.SyncJson
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
    data object Sync : Screen()
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

        // --- sync state (TECH 11) ---
        var syncFolderPath by remember { mutableStateOf(store.getLocal(KEY_SYNC_FOLDER)) }
        val transport = rememberSyncTransport(syncFolderPath)
        val coordinator = remember(transport, db) {
            SyncCoordinator(db, store.deviceId, transport, defaultNow())
        }
        var syncBusy by remember { mutableStateOf(false) }
        var syncError by remember { mutableStateOf<String?>(null) }
        var lastSyncAt by remember { mutableStateOf(store.getLocal(KEY_LAST_SYNC)?.toLongOrNull()) }
        var pendingUndo by remember { mutableStateOf<SyncBoundary.PositionOverwrite?>(null) }
        var deviceRows by remember { mutableStateOf<List<DeviceRow>>(emptyList()) }
        var dirtySinceSync by remember { mutableStateOf(false) }

        val readerStore = remember(store) {
            SyncTriggerStore(store) { dirtySinceSync = true }
        }

        fun refreshDevices() {
            val t = transport ?: return
            scope.launch(Dispatchers.IO) {
                val rows = mutableListOf<DeviceRow>()
                t.list()
                    .filter { it.startsWith("devices/") && it.endsWith("/meta.json") }
                    .forEach { path ->
                        val bytes = t.pull(path) ?: return@forEach
                        val text = bytes.decodeToString()
                        val meta = runCatching { SyncJson.decode<com.baca.shared.sync.DeviceMeta>(text) }.getOrNull()
                            ?: return@forEach
                        rows.add(
                            DeviceRow(
                                id = meta.deviceId,
                                isThisDevice = meta.deviceId == store.deviceId,
                                lastSeenAt = meta.updatedAt,
                            ),
                        )
                    }
                deviceRows = rows
            }
        }

        fun runSync() {
            if (syncFolderPath == null || syncBusy) return
            scope.launch {
                syncBusy = true
                try {
                    val report = withContext(Dispatchers.IO) { coordinator.sync() }
                    if (report != null) {
                        report.positionOverwrites.firstOrNull()?.let { pendingUndo = it }
                        val now = defaultNow()()
                        store.putLocal(KEY_LAST_SYNC, now.toString())
                        lastSyncAt = now
                        refreshDevices()
                        libraryTick++
                    }
                    syncError = null
                } catch (e: Exception) {
                    syncError = e.message ?: "sync failed"
                } finally {
                    syncBusy = false
                }
            }
        }

        // start sync when a folder exists; debounce local writes (TECH 6.5)
        LaunchedEffect(syncFolderPath) {
            if (syncFolderPath != null) runSync()
        }
        LaunchedEffect(dirtySinceSync, syncFolderPath) {
            if (dirtySinceSync && syncFolderPath != null) {
                delay(2000)
                if (dirtySinceSync) {
                    dirtySinceSync = false
                    runSync()
                }
            }
        }

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
                    dirtySinceSync = true
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
                if (syncFolderPath != null) runSync()
            }
        }

        val pick = rememberBookPicker { paths -> runImport(paths) }
        val pickFolder = rememberFolderPicker { paths -> if (paths.isNotEmpty()) runImport(paths) }
        // --- backup / restore (Sync screen) ---
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

        val pickLibraryFolder = rememberSyncFolderPicker(title = "Choose library folder") { path ->
            if (path != null) {
                libraryFolderPath = path
                store.putLocal(KEY_LIBRARY_FOLDER, path)
                // the new folder is scanned through the LaunchedEffect above
            }
        }
        val pickSyncFolder = rememberSyncFolderPicker { path ->
            if (path != null) {
                syncFolderPath = path
                store.putLocal(KEY_SYNC_FOLDER, path)
                // picker change re-syncs through the LaunchedEffect above
            }
        }

        // Open the book passed on the command line; import first so library
        // state and saved positions line up with later sessions.
        LaunchedEffect(initialBookPath) {
            if (initialBookPath != null) {
                withContext(Dispatchers.IO) {
                    importer.import(initialBookPath)
                }?.let { imported ->
                    openBookOf(imported.row, libraryFolder)?.let { screenState = Screen.Reader(it) }
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

        val banner = pendingUndo?.let { overwrite ->
            booksRepo.byHash(overwrite.bookHash)?.let { row ->
                MergeBanner(
                    bookTitle = row.title,
                    fromDevice = overwrite.winningDevice,
                    percent = ((booksRepo.progressFor(overwrite.bookHash) ?: 0f) * 100).toInt(),
                )
            }
        }

        fun undoPositionChange() {
            val overwrite = pendingUndo ?: return
            pendingUndo = null
            scope.launch(Dispatchers.IO) {
                val prevText = overwrite.previousLocator
                val isReflow = prevText.contains("\"href\"")
                val locRef: Locator? = if (isReflow) runCatching { SyncJson.decode<Locator>(prevText) }.getOrNull() else null
                val locFixed: FixedLocator? = if (!isReflow) runCatching { SyncJson.decode<FixedLocator>(prevText) }.getOrNull() else null
                if (locRef != null) readerStore.savePosition(overwrite.bookHash, locRef)
                if (locFixed != null) readerStore.saveFixedPosition(overwrite.bookHash, locFixed)
            }
        }

        fun openAsync(row: BookRow) {
            openingTitle = row.title
            scope.launch(Dispatchers.IO) {
                val book = openBookOf(row, libraryFolder)
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
                NavItem.SYNC -> Screen.Sync
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
            only: List<com.baca.shared.sync.AnnotationEntry>? = null,
        ): List<DbAnnotationRepository.AnnotWithDevice> {
            val ids = only?.map { it.id }?.toSet()
            return booksRepo.linkedHashes(book.bookHash).flatMap { hash ->
                annotRepo.forBookDetailed(hash)
                    .filter { ids == null || it.entry.id in ids }
                    .map { if (hash == book.bookHash) it else it.copy(entry = relinkEntry(it.entry, book.parsed.chapters)) }
            }
        }

        fun openAnnotation(row: BookRow, picked: com.baca.shared.sync.AnnotationEntry) {
            val page = picked.fixedAnchor?.pageIndex
            if (picked.reflowAnchor == null && page == null) return
            openBookOf(row, libraryFolder)?.let { book ->
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
        Surface(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(if (readingReflow) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val current = screenState) {
                is Screen.Reader -> {
                    when (val book = current.book) {
                        is OpenBook.Reflow -> {
                            val annotations = remember(annotTick, book.bookHash) {
                                annotationsForReader(book)
                            }
                            CompositionLocalProvider(LocalBookImages provides rememberBookImages(book.path)) {
                                ReaderScreen(
                                    book = book.parsed,
                                    bookPath = book.path,
                                    bookHash = book.bookHash,
                                    store = readerStore,
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
                                            dirtySinceSync = true
                                        }
                                    },
                                    onAnnotationDelete = { id ->
                                        scope.launch(Dispatchers.IO) {
                                            annotRepo.tombstone(id)
                                            annotTick++
                                            dirtySinceSync = true
                                        }
                                    },
                                    jumpToAnchor = current.jump,
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
                                store = readerStore,
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
                                        dirtySinceSync = true
                                    }
                                },
                                onAnnotationDelete = { id ->
                                    scope.launch(Dispatchers.IO) {
                                        annotRepo.tombstone(id)
                                        annotTick++
                                        dirtySinceSync = true
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
                    BookDetailScreen(
                        row = current.row,
                        progress = progress,
                        lastReadAt = lastRead,
                        addedAt = current.row.addedAt,
                        sizeBytes = runCatching { fs.metadata(current.row.path.toPath()).size ?: 0L }.getOrDefault(0L),
                        onBack = { screenState = Screen.Library },
                        onContinue = { openAsync(current.row) },
                        onRemove = {
                            booksRepo.tombstone(current.row.bookHash)
                            dirtySinceSync = true
                            screenState = Screen.Library
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
                Screen.Sync -> NavShell(NavItem.SYNC, navTo) {
                    SyncScreen(
                        data = SyncScreenData(
                            folderPath = syncFolderPath,
                            busy = syncBusy,
                            error = syncError,
                            lastSyncAt = lastSyncAt,
                            banner = banner,
                            devices = deviceRows,
                            backupBusy = backupBusy,
                            backupMessage = backupMessage,
                        ),
                        onBack = { screenState = Screen.Library },
                        onChooseFolder = pickSyncFolder,
                        onSyncNow = { dirtySinceSync = false; runSync() },
                        onUndo = { undoPositionChange() },
                        onCreateBackup = saveBackup,
                        onRestoreBackup = openBackup,
                    )
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
                                dirtySinceSync = true
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
                                    dirtySinceSync = true
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
private fun openBookOf(row: BookRow, folder: LibraryFolder?): OpenBook? {
    val readable = if (row.path.startsWith("content://")) folder?.stage(row.path) ?: return null else row.path
    return when (row.format) {
        "cbz" -> BookFormats.parseCbzFile(readable.toPath(), readable.substringAfterLast('/'))
            ?.let { OpenBook.Fixed(it, readable, row.bookHash) }
        else -> BookFormats.parse(readable.toPath(), row.format)
            ?.let { OpenBook.Reflow(it, readable, row.bookHash) }
    }
}

private const val KEY_SYNC_FOLDER = "sync_folder"
private const val KEY_LIBRARY_FOLDER = "library_folder"
private const val KEY_LAST_SYNC = "last_sync_at"

/** Wall clock, display/debug use only (docs/TECH.md 6.1). */
expect fun defaultNow(): () -> Long
