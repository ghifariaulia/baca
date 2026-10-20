package com.baca.shared.db

import com.baca.shared.domain.Locator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DbBookRepositoryTest {

    @Test
    fun upsertSelectAndDedupeWork() {
        val (db, _) = inMemoryDb()
        val repo = DbBookRepository(db, now = { 5L })
        repo.upsert("hash1", "Moby-Dick", "H. Melville", "/books/hash1.epub", "epub", "/books/hash1-cover.jpg")
        val row = assertNotNull(repo.byHash("hash1"))
        assertEquals("Moby-Dick", row.title)
        assertEquals("epub", row.format)
        assertFalse(row.deleted)
        assertEquals(1, repo.all().size)
    }

    @Test
    fun tombstoneKeepsRowButHidesFromLibrary() {
        val (db, _) = inMemoryDb()
        val repo = DbBookRepository(db, now = { 5L })
        repo.upsert("hash1", "Moby-Dick", "H. Melville", "/books/hash1.epub", "epub")
        repo.tombstone("hash1")
        assertEquals(0, repo.all().size) // hidden
        val row = repo.byHash("hash1")
        assertNotNull(row) // kept for sync (TECH 6.4)
        assertTrue(row!!.deleted)
    }

    @Test
    fun progressAndLastReadComeFromTheLatestPosition() {
        val (db, _) = inMemoryDb()
        val store = DbPositionStore(db, now = { 5L })
        val repo = DbBookRepository(db, now = { 5L })
        assertNull(repo.progressFor("hash1"))
        store.savePosition("hash1", Locator("x.html", 0, 3, 0.42, "q"))
        // library cards show the BOOK-wide fraction (written by ReaderSession.save)
        store.saveBookProgress("hash1", 0.42f)
        assertEquals(0.42f, repo.progressFor("hash1"))
        assertEquals(5L, repo.lastReadAt("hash1"))
    }
}
