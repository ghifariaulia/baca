package com.baca.app.sync

import app.cash.sqldelight.db.SqlDriver
import com.baca.shared.db.Baca
import com.baca.shared.db.DbPositionStore
import com.baca.shared.db.DbBookRepository
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.sync.SyncBoundary
import com.baca.shared.sync.SyncTransport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drives the sync loop (TECH 6.5): export own files, pull peers, merge,
 * persist. Runs only when a transport exists; guarded against overlap.
 */
class SyncCoordinator(
    db: Baca,
    deviceId: String,
    private val transport: SyncTransport?,
    private val now: () -> Long,
) {
    private val boundary = SyncBoundary(db, deviceId, now)
    private val mutex = Mutex()

    var lastReport: SyncBoundary.MergeReport? = null
        private set

    /** Runs export + pull + merge; null when no folder is set. */
    suspend fun sync(): SyncBoundary.MergeReport? {
        val t = transport ?: return null
        return mutex.withLock {
            boundary.exportAll(t)
            val report = boundary.pullAndMerge(t)
            lastReport = report
            report
        }
    }
}

/** Marks the position dirty so the app can debounce a sync (TECH 6.5). */
class SyncTriggerStore(private val delegate: PositionStore, private val onWrite: () -> Unit) :
    PositionStore by delegate {
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {
        delegate.savePosition(bookHash, locator)
        onWrite()
    }

    override fun saveFixedPosition(bookHash: String, locator: com.baca.shared.domain.FixedLocator) {
        delegate.saveFixedPosition(bookHash, locator)
        onWrite()
    }
}
