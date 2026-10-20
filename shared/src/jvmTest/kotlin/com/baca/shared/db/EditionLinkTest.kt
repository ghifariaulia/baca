package com.baca.shared.db

import com.baca.shared.domain.Locator
import com.baca.shared.sync.MemoryTransport
import com.baca.shared.sync.SyncBoundary
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Two devices hold different files of one edition; sync links them by work key. */
class EditionLinkTest {

    private val key = "gutenbergorg84|frankenstein"
    private fun locator(word: String) = Locator("c.xhtml", 1, 0, 0.5, quote = word)

    private fun device(id: String, hash: String): Triple<Baca, DbBookRepository, DbPositionStore> {
        val (db, _) = inMemoryDb()
        db.settings_localQueries.setLocal("device_id", id)
        val books = DbBookRepository(db, now = { 1L })
        books.upsert(hash, "Frankenstein", "M. Shelley", "/books/$hash.epub", "epub")
        books.setWorkKey(hash, key)
        return Triple(db, books, DbPositionStore(db, now = { 1L }))
    }

    @Test
    fun `peer file of the same edition links after sync`() = runBlocking {
        val transport = MemoryTransport()
        val (dbA, _, posA) = device("dev-A", "fileA")
        val (dbB, booksB, posB) = device("dev-B", "fileB")
        posB.savePosition("fileB", locator("older place on B"))
        repeat(5) { posA.savePosition("fileA", locator("newer place on A")) } // higher lamport

        SyncBoundary(dbA, "dev-A", now = { 1L }).exportAll(transport)
        SyncBoundary(dbB, "dev-B", now = { 1L }).pullAndMerge(transport)

        assertEquals(listOf("fileB", "fileA"), booksB.linkedHashes("fileB"))
        // the peer's file shows as this book, not as a second library entry
        assertEquals(listOf("fileB"), booksB.visible().map { it.bookHash })
        assertEquals("newer place on A", posB.loadLinkedPosition("fileB")?.quote)

        // reading on B afterwards makes B's own position the newest again
        repeat(10) { posB.savePosition("fileB", locator("latest on B")) }
        assertNull(posB.loadLinkedPosition("fileB"))
    }

    @Test
    fun `books without a key stay separate`() {
        val (db, books, _) = device("dev-A", "fileA")
        books.upsert("other", "Dracula", "B. Stoker", "/books/other.epub", "epub")
        books.setWorkKey("other", null)
        assertEquals(listOf("other"), books.linkedHashes("other"))
        assertEquals(2, books.visible().size)
        assertEquals(listOf("fileA"), DbBookRepository(db, now = { 1L }).linkedHashes("fileA"))
    }
}
