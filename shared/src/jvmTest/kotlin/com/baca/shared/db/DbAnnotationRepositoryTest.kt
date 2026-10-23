package com.baca.shared.db

import com.baca.shared.domain.AnnotationEntry
import com.baca.shared.domain.FixedAnchorDto
import kotlin.test.Test
import kotlin.test.assertEquals

class DbAnnotationRepositoryTest {

    @Test
    fun lamportAdvancesFromOwnClockNotPeerRows() {
        val (db, _) = inMemoryDb()
        db.settings_localQueries.setLocal("device_id", "dev-me")
        // a peer row listed before ours, as pullAndMerge leaves it
        db.sync_stateQueries.upsertSyncState("dev-peer", 3, 0, 0)
        db.sync_stateQueries.upsertSyncState("dev-me", 0, 10, 10)
        val repo = DbAnnotationRepository(db, now = { 1L })

        val saved = repo.upsert(AnnotationEntry(id = "a1", kind = "highlight", fixedAnchor = FixedAnchorDto(0, emptyList()), createdAt = 1, lamport = 0), "hash-1")

        assertEquals(11, saved.lamport)
    }
}
