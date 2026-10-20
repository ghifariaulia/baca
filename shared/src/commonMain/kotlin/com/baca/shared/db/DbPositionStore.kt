package com.baca.shared.db

import com.baca.shared.domain.FixedLocator
import com.baca.shared.domain.Locator
import com.baca.shared.domain.reader.PositionStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * The local database is the source of truth on a device (docs/TECH.md 5).
 * Positions are saved with a Lamport clock (own counter) so M4 sync can order
 * records without wall clocks; [now] is wall time for display only.
 */
class DbPositionStore(
    private val db: Baca,
    private val now: () -> Long,
) : PositionStore {

    private val json = Json { ignoreUnknownKeys = true }

    override val deviceId: String by lazy {
        getLocal(KEY_DEVICE_ID) ?: Uuid.random().toString().also { putLocal(KEY_DEVICE_ID, it) }
    }

    override fun loadPosition(bookHash: String): Locator? =
        db.positionsQueries.selectPosition(bookHash).executeAsOneOrNull()?.locator
            ?.let { runCatching { json.decodeFromString<Locator>(it) }.getOrNull() }

    override fun loadLinkedPosition(bookHash: String): Locator? {
        val own = db.positionsQueries.selectPosition(bookHash).executeAsOneOrNull()?.lamport ?: -1L
        return linkedBookHashes(db, bookHash).drop(1)
            .mapNotNull { db.positionsQueries.selectPosition(it).executeAsOneOrNull() }
            .filter { it.lamport > own }
            .maxByOrNull { it.lamport }
            ?.let { row -> runCatching { json.decodeFromString<Locator>(row.locator) }.getOrNull() }
    }

    override fun saveBookProgress(bookHash: String, fraction: Float) {
        putLocal("progress:$bookHash", fraction.toString())
    }

    override fun savePosition(bookHash: String, locator: Locator) {
        val lamport = bumpLamport()
        db.positionsQueries.setPosition(
            book_hash = bookHash,
            device_id = deviceId,
            locator = json.encodeToString(locator),
            lamport = lamport,
            updated_at = now(),
        )
    }

    override fun loadFixedPosition(bookHash: String): FixedLocator? =
        db.positionsQueries.selectPosition(bookHash).executeAsOneOrNull()?.locator
            ?.let { runCatching { json.decodeFromString<FixedLocator>(it) }.getOrNull() }

    override fun saveFixedPosition(bookHash: String, locator: FixedLocator) {
        val lamport = bumpLamport()
        db.positionsQueries.setPosition(
            book_hash = bookHash,
            device_id = deviceId,
            locator = json.encodeToString(locator),
            lamport = lamport,
            updated_at = now(),
        )
    }

    override fun getLocal(key: String): String? =
        db.settings_localQueries.getLocal(key).executeAsOneOrNull()?.value_

    override fun putLocal(key: String, value: String) {
        db.settings_localQueries.setLocal(key, value)
    }

    /**
     * own_lamport + 1 for now; M4 extends this to max(own, highest seen) + 1
     * once records from other devices are being read (TECH 6.1).
     */
    private fun bumpLamport(): Long {
        val current = db.sync_stateQueries.selectSyncStateAll().executeAsList()
            .firstOrNull { it.device_id == deviceId }?.own_lamport ?: 0L
        val next = current + 1
        db.sync_stateQueries.upsertSyncState(deviceId, 0, next, next)
        return next
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
