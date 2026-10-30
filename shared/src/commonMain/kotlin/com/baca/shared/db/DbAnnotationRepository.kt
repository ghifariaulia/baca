package com.baca.shared.db

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid
import com.baca.shared.domain.AnnotationEntry

/** device_id of annotations written on this device (shown as their label). */
internal const val LOCAL_ANNOTATION_DEVICE = "local"

/**
 * DB operations for the annotations table only (docs/TECH.md 5.3). The whole
 * entry rides in the `anchor` column as JSON — the table shape predates the
 * DTO and stays frozen; everything sync-relevant is inside the entry.
 */
class DbAnnotationRepository(
    private val db: Baca,
    private val now: () -> Long,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Same generated device id that positions use. */
    private val deviceId: String by lazy {
        db.settings_localQueries.getLocal(DEVICE_ID_KEY).executeAsOneOrNull()?.value_
            ?: Uuid.random().toString().also {
                db.settings_localQueries.setLocal(DEVICE_ID_KEY, it)
            }
    }

    // sync_state also holds peer rows after a sync; only our own row is the clock
    private fun bumpLamport(): Long {
        val current = db.sync_stateQueries.selectSyncStateAll().executeAsList()
            .firstOrNull { it.device_id == deviceId }?.own_lamport ?: 0L
        val next = current + 1
        db.sync_stateQueries.upsertSyncState(deviceId, 0, next, next)
        return next
    }

    fun upsert(entry: AnnotationEntry, bookHash: String): AnnotationEntry {
        val bumped = entry.copy(lamport = bumpLamport())
        db.annotationsQueries.upsertAnnotation(
            id = bumped.id,
            book_hash = bookHash,
            kind = bumped.kind,
            anchor = json.encodeToString(bumped),
            text = bumped.text,
            note = bumped.note,
            created_at = bumped.createdAt,
            // the origin device is attached at sync read time (file folder)
            device_id = LOCAL_ANNOTATION_DEVICE,
            lamport = bumped.lamport,
            deleted = if (bumped.deleted) 1L else 0L,
        )
        return bumped
    }

    /** Records the page of a reflowed annotation made before pages were kept; its book and the rest stay as they are. */
    fun setPage(id: String, page: Int) {
        val row = db.annotationsQueries.selectAnnotationsAll().executeAsList().firstOrNull { it.id == id } ?: return
        val entry = decode(row)?.takeIf { it.reflowAnchor != null } ?: return
        val withPage = entry.copy(reflowAnchor = entry.reflowAnchor!!.copy(page = page))
        db.annotationsQueries.upsertAnnotation(
            row.id, row.book_hash, row.kind, json.encodeToString(withPage), row.text, row.note, row.created_at,
            row.device_id, row.lamport, row.deleted,
        )
    }

    /** All annotations for one book (including tombstones for sync state). */
    fun forBook(bookHash: String): List<AnnotationEntry> =
        db.annotationsQueries.selectAnnotationsByBook(bookHash).executeAsList()
            .mapNotNull { decode(it) }

    /** Annotation + the device that wrote it (the notes list's meta line). */
    data class AnnotWithDevice(val entry: AnnotationEntry, val deviceId: String)

    fun forBookDetailed(bookHash: String): List<AnnotWithDevice> =
        db.annotationsQueries.selectAnnotationsByBook(bookHash).executeAsList()
            .mapNotNull { row -> decode(row)?.let { AnnotWithDevice(it, row.device_id) } }

    fun allDetailed(): List<Pair<String, AnnotWithDevice>> =
        db.annotationsQueries.selectAnnotationsAll().executeAsList()
            .mapNotNull { row -> decode(row)?.let { row.book_hash to AnnotWithDevice(it, row.device_id) } }

    /** Every annotation, tagged with the book it belongs to. */
    fun all(): List<Pair<String, AnnotationEntry>> =
        db.annotationsQueries.selectAnnotationsAll().executeAsList()
            .mapNotNull { row -> decode(row)?.let { row.book_hash to it } }

    fun tombstone(id: String) {
        val row = db.annotationsQueries.selectAnnotationsAll().executeAsList()
            .firstOrNull { it.id == id } ?: return
        runCatching { decode(row) }.getOrNull()?.let { entry ->
            upsert(entry.copy(deleted = true), row.book_hash)
        }
    }

    private fun decode(row: com.baca.shared.db.Annotations): AnnotationEntry? =
        runCatching { json.decodeFromString<AnnotationEntry>(row.anchor) }.getOrNull()

    private companion object {
        const val DEVICE_ID_KEY = "device_id"
    }
}
