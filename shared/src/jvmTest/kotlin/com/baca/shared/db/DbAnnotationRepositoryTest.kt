package com.baca.shared.db

import com.baca.shared.domain.AnnotationEntry
import com.baca.shared.domain.FixedAnchorDto
import com.baca.shared.domain.PointDto
import com.baca.shared.domain.ReflowAnchorDto
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

    @Test
    fun `learning an annotation's page keeps its book and the rest of it`() {
        val (db, _) = inMemoryDb()
        val repo = DbAnnotationRepository(db, now = { 1L })
        val anchor = ReflowAnchorDto("c.xhtml", PointDto(1, 2), PointDto(1, 9), "quote")
        repo.upsert(AnnotationEntry(id = "a1", kind = "highlight", reflowAnchor = anchor, text = "quote", note = "n", createdAt = 5, lamport = 0), "hash-1")

        repo.setPage("a1", 13)

        val saved = repo.forBook("hash-1").single()
        assertEquals(13, saved.reflowAnchor?.page)
        assertEquals(anchor.copy(page = 13), saved.reflowAnchor)
        assertEquals("n", saved.note)
        // an annotation that isn't reflowed, or isn't there, is left alone
        repo.setPage("nothing", 4)
    }
}
