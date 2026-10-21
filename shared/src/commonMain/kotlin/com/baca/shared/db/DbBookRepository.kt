package com.baca.shared.db

import com.baca.shared.domain.Book
import com.baca.shared.domain.Locator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * settings_local key prefix for a book's link key ([workKeyOf]); value "" means
 * "checked, no usable identifier". Kept as a local setting like progress:<hash>
 * so linking needs no schema migration; it travels in library.json.
 */
internal const val WORK_KEY_PREFIX = "work:"

/** settings_local key prefix for "the reader marked this book finished"; it travels in library.json. */
internal const val FINISHED_PREFIX = "finished:"

/** [bookHash] first, then every other book hash sharing its work key. */
internal fun linkedBookHashes(db: Baca, bookHash: String): List<String> {
    val key = db.settings_localQueries.getLocal(WORK_KEY_PREFIX + bookHash).executeAsOneOrNull()?.value_
        ?.takeIf { it.isNotEmpty() } ?: return listOf(bookHash)
    val others = db.settings_localQueries.selectLocalByPrefix(WORK_KEY_PREFIX).executeAsList()
        .filter { it.value_ == key }
        .map { it.key.removePrefix(WORK_KEY_PREFIX) }
        .filter { it != bookHash }
    return listOf(bookHash) + others
}

/**
 * A library row as stored locally (books table). Relative paths are the
 * device's own; the synced [Book] record (TECH 6.2) carries none of them.
 */
data class BookRow(
    val bookHash: String,
    val title: String,
    val author: String,
    val path: String,
    val format: String,
    val coverPath: String?,
    val addedAt: Long,
    val lamport: Long,
    val deleted: Boolean,
)

/**
 * DB operations for the books table only (docs/TECH.md 5). Import/library
 * orchestration lives in the use cases above this; the sync merge will write
 * through [upsert] once M4 lands. Lamport bump mirrors [DbPositionStore].
 */
class DbBookRepository(
    private val db: Baca,
    private val now: () -> Long,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Same generated device id that positions use. */
    val deviceId: String by lazy {
        db.settings_localQueries.getLocal(DEVICE_ID_KEY).executeAsOneOrNull()?.value_
            ?: Uuid.random().toString().also {
                db.settings_localQueries.setLocal(DEVICE_ID_KEY, it)
            }
    }

    private fun bumpLamport(): Long {
        val current = db.sync_stateQueries.selectSyncStateAll().executeAsList()
            .firstOrNull { it.device_id == deviceId }?.own_lamport ?: 0L
        val next = current + 1
        db.sync_stateQueries.upsertSyncState(deviceId, 0, next, next)
        return next
    }

    fun upsert(
        bookHash: String,
        title: String,
        author: String,
        path: String,
        format: String,
        coverPath: String? = null,
    ) {
        db.booksQueries.insertBook(
            book_hash = bookHash,
            title = title,
            author = author,
            path = path,
            format = format,
            cover_path = coverPath,
            added_at = now(),
            lamport = bumpLamport(),
            deleted = 0L,
        )
    }

    fun byHash(bookHash: String): BookRow? {
        val row = db.booksQueries.selectBookByHash(bookHash).executeAsOneOrNull() ?: return null
        return row.toRow()
    }

    fun all(): List<BookRow> = db.booksQueries.selectBooks().executeAsList().map { it.toRow() }

    /** Where the book's file now lives; local only, not a synced change. */
    fun updatePath(bookHash: String, path: String) {
        db.booksQueries.updateBookPath(path, bookHash)
    }

    fun isFinished(bookHash: String): Boolean =
        db.settings_localQueries.getLocal(FINISHED_PREFIX + bookHash).executeAsOneOrNull()?.value_ == "true"

    /** Marks or unmarks the book finished; bumps its clock so the change syncs (TECH 6.3). */
    fun setFinished(bookHash: String, finished: Boolean) {
        db.transaction {
            db.settings_localQueries.setLocal(FINISHED_PREFIX + bookHash, finished.toString())
            db.booksQueries.updateBookLamport(bumpLamport(), bookHash)
        }
    }

    /** Stored paths of every row, removed ones included. */
    fun allPaths(): Set<String> = db.booksQueries.selectBooksAll().executeAsList().map { it.path }.toSet()

    /** Tombstone via the delete flag (TECH 6.4); the row stays for sync. */
    fun tombstone(bookHash: String) {
        db.booksQueries.tombstoneBook(lamport = bumpLamport(), book_hash = bookHash)
    }

    /** Book-wide reading fraction (see ReaderSession.save), or null. */
    fun progressFor(bookHash: String): Float? {
        db.settings_localQueries.getLocal("progress:$bookHash").executeAsOneOrNull()?.value_
            ?.let { runCatching { it.toFloat() }.getOrNull() }?.let { return it }
        // books not opened since the fix: fall back to the chapter-local
        // progression until the next open writes the true fraction
        return db.positionsQueries.selectPosition(bookHash).executeAsOneOrNull()?.let { row ->
            runCatching { json.decodeFromString<Locator>(row.locator).progression }.getOrNull()
                ?.toFloat()
        }
    }

    fun setWorkKey(bookHash: String, workKey: String?) {
        db.settings_localQueries.setLocal(WORK_KEY_PREFIX + bookHash, workKey ?: "")
    }

    fun workKey(bookHash: String): String? =
        db.settings_localQueries.getLocal(WORK_KEY_PREFIX + bookHash).executeAsOneOrNull()?.value_
            ?.takeIf { it.isNotEmpty() }

    /** False until import/backfill has looked for an identifier. */
    fun hasWorkKeyEntry(bookHash: String): Boolean =
        db.settings_localQueries.getLocal(WORK_KEY_PREFIX + bookHash).executeAsOneOrNull() != null

    /** [bookHash] plus every other file of the same edition (same work key). */
    fun linkedHashes(bookHash: String): List<String> = linkedBookHashes(db, bookHash)

    /**
     * Library rows minus peer-only entries ("remote": no file here) that are
     * another file of a book this device has; those would show as duplicates.
     */
    fun visible(): List<BookRow> {
        val rows = all()
        val localKeys = rows.filter { it.format != REMOTE_FORMAT }.mapNotNull { workKey(it.bookHash) }.toSet()
        return rows.filter { it.format != REMOTE_FORMAT || workKey(it.bookHash) !in localKeys }
    }

    /** Wall time of the latest position write, for "continue reading" ordering. */
    fun lastReadAt(bookHash: String): Long? =
        db.positionsQueries.selectPosition(bookHash).executeAsOneOrNull()?.updated_at

    private fun com.baca.shared.db.Books.toRow() = BookRow(
        bookHash = book_hash,
        title = title,
        author = author,
        path = path,
        format = format,
        coverPath = cover_path,
        addedAt = added_at,
        lamport = lamport,
        deleted = deleted != 0L,
    )

    private companion object {
        const val DEVICE_ID_KEY = "device_id"
        // format SyncBoundary gives library rows it learned from a peer
        const val REMOTE_FORMAT = "remote"
    }
}
