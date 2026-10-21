package com.baca.shared.backup

import com.baca.shared.db.Baca
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import okio.openZip
import okio.use

/**
 * Whole-library backup: one zip with [DATA_ENTRY] (every table as JSON) and the
 * imported book files and covers under [FILES_PREFIX]. Paths are stored as
 * file names only, so a backup restores on any device and platform.
 *
 * Restore replaces the library; keys in [DEVICE_ONLY_KEYS] and the sync
 * clock describe this device and are kept.
 */
class LibraryBackup(
    private val db: Baca,
    private val fs: FileSystem = FileSystem.SYSTEM,
) {

    fun create(out: Sink) {
        val data = snapshot()
        val files = db.booksQueries.selectBooksAll().executeAsList()
            .flatMap { listOfNotNull(it.path.takeIf(String::isNotEmpty), it.cover_path) }
            .distinct()
            .map { it.toPath() }
            .filter { fs.exists(it) }
        val entries = listOf<Pair<String, () -> Source>>(
            DATA_ENTRY to { Buffer().writeUtf8(json.encodeToString(BackupData.serializer(), data)) },
        ) + files.map { path -> "$FILES_PREFIX${path.name}" to { fs.source(path) } }
        writeZip(out, entries)
    }

    /**
     * Replaces the library with [archive]. The current library is first saved
     * as [SAFETY_COPY] next to [storeDir], and book files are copied before the
     * database changes, so a bad archive leaves the current library untouched.
     */
    fun restore(archive: Path, storeDir: String) {
        val zip = fs.openZip(archive)
        val dataPath = "/$DATA_ENTRY".toPath()
        require(zip.exists(dataPath)) { "Not a Baca backup" }
        val data = json.decodeFromString(BackupData.serializer(), zip.read(dataPath) { readUtf8() })
        require(data.version <= BackupData.VERSION) { "Backup is from a newer version of Baca" }

        val dir = storeDir.toPath()
        fs.createDirectories(dir)
        (dir.parent ?: dir).let { parent -> fs.sink(parent / SAFETY_COPY).use { create(it) } }
        zip.list("/$FILES_PREFIX".toPath()).forEach { entry ->
            zip.source(entry).use { input ->
                fs.sink(dir / entry.name).buffer().use { it.writeAll(input) }
            }
        }

        fun local(name: String?) = name?.takeIf { it.isNotEmpty() }?.let { (dir / it).toString() }
        db.transaction {
            db.booksQueries.deleteBooksAll()
            db.positionsQueries.deletePositionsAll()
            db.annotationsQueries.deleteAnnotationsAll()
            db.settings_syncedQueries.deleteSyncedAll()
            db.settings_localQueries.deleteLocalExcept(DEVICE_ONLY_KEYS)

            data.books.forEach {
                db.booksQueries.insertBook(
                    it.hash, it.title, it.author, local(it.file) ?: "", it.format, local(it.coverFile),
                    it.addedAt, it.lamport, if (it.deleted) 1L else 0L,
                )
            }
            data.positions.forEach { db.positionsQueries.setPosition(it.hash, it.deviceId, it.locator, it.lamport, it.updatedAt) }
            data.annotations.forEach {
                db.annotationsQueries.upsertAnnotation(
                    it.id, it.hash, it.kind, it.anchor, it.text, it.note, it.createdAt, it.deviceId, it.lamport,
                    if (it.deleted) 1L else 0L,
                )
            }
            data.settingsSynced.forEach { db.settings_syncedQueries.putSynced(it.key, it.value, it.lamport) }
            data.settingsLocal.filterKeys { it !in DEVICE_ONLY_KEYS }
                .forEach { (key, value) -> db.settings_localQueries.setLocal(key, value) }
            advanceClockPast(data)
        }
    }

    private fun snapshot() = BackupData(
        books = db.booksQueries.selectBooksAll().executeAsList().map {
            BookRecord(
                it.book_hash, it.title, it.author, fileName(it.path), it.format, it.cover_path?.let(::fileName),
                it.added_at, it.lamport, it.deleted != 0L,
            )
        },
        positions = db.positionsQueries.selectPositions().executeAsList().map {
            PositionRecord(it.book_hash, it.device_id, it.locator, it.lamport, it.updated_at)
        },
        annotations = db.annotationsQueries.selectAnnotationsAll().executeAsList().map {
            AnnotationRecord(
                it.id, it.book_hash, it.kind, it.anchor, it.text, it.note, it.created_at, it.device_id, it.lamport,
                it.deleted != 0L,
            )
        },
        settingsSynced = db.settings_syncedQueries.selectSyncedAll().executeAsList().map {
            SyncedRecord(it.key, it.value_, it.lamport)
        },
        settingsLocal = db.settings_localQueries.selectLocalAll().executeAsList()
            .filter { it.key !in DEVICE_ONLY_KEYS }
            .associate { it.key to it.value_ },
    )

    // Restored records carry their old Lamport stamps; this device's next
    // write must stamp higher or it would lose to them (TECH 6.1).
    private fun advanceClockPast(data: BackupData) {
        val deviceId = db.settings_localQueries.getLocal(DEVICE_ID_KEY).executeAsOneOrNull()?.value_ ?: return
        val highest = (data.books.map { it.lamport } + data.positions.map { it.lamport } +
            data.annotations.map { it.lamport } + data.settingsSynced.map { it.lamport }).maxOrNull() ?: return
        val own = db.sync_stateQueries.selectSyncStateAll().executeAsList().firstOrNull { it.device_id == deviceId }
        if ((own?.own_lamport ?: 0L) >= highest) return
        db.sync_stateQueries.upsertSyncState(deviceId, own?.last_seen_counter ?: 0L, own?.own_counter ?: 0L, highest)
    }

    private fun fileName(path: String) = path.substringAfterLast('/')

    companion object {
        const val DATA_ENTRY = "library.json"
        const val FILES_PREFIX = "books/"

        /** The library as it was before the last restore, next to the books folder. */
        const val SAFETY_COPY = "before-restore.zip"
        private const val DEVICE_ID_KEY = "device_id"

        /** This device's identity and its own sync folder; never taken from a backup. */
        val DEVICE_ONLY_KEYS = listOf(DEVICE_ID_KEY, "sync_folder", "last_sync_at", "library_folder")

        private val json = Json { ignoreUnknownKeys = true }
    }
}

/** Writes [entries] (entry name → content) as a zip to [out] and closes it. */
internal expect fun writeZip(out: Sink, entries: List<Pair<String, () -> Source>>)

@Serializable
internal data class BackupData(
    val version: Int = VERSION,
    val books: List<BookRecord>,
    val positions: List<PositionRecord>,
    val annotations: List<AnnotationRecord>,
    val settingsSynced: List<SyncedRecord>,
    val settingsLocal: Map<String, String>,
) {
    companion object {
        const val VERSION = 1
    }
}

@Serializable
internal data class BookRecord(
    val hash: String,
    val title: String,
    val author: String,
    /** file name inside the backup's books/ folder; "" for a peer-only entry */
    val file: String,
    val format: String,
    val coverFile: String?,
    val addedAt: Long,
    val lamport: Long,
    val deleted: Boolean,
)

@Serializable
internal data class PositionRecord(
    val hash: String,
    val deviceId: String,
    val locator: String,
    val lamport: Long,
    val updatedAt: Long,
)

@Serializable
internal data class AnnotationRecord(
    val id: String,
    val hash: String,
    val kind: String,
    val anchor: String,
    val text: String,
    val note: String,
    val createdAt: Long,
    val deviceId: String,
    val lamport: Long,
    val deleted: Boolean,
)

@Serializable
internal data class SyncedRecord(val key: String, val value: String, val lamport: Long)
