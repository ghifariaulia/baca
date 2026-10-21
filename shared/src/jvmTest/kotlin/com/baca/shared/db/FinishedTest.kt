package com.baca.shared.db

import com.baca.shared.sync.MemoryTransport
import com.baca.shared.sync.SyncBoundary
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinishedTest {

    private fun device(id: String): Pair<Baca, DbBookRepository> {
        val (db, _) = inMemoryDb()
        db.settings_localQueries.setLocal("device_id", id)
        val books = DbBookRepository(db, now = { 1L })
        books.upsert("h1", "Frankenstein", "M. Shelley", "/books/h1.epub", "epub")
        return db to books
    }

    @Test
    fun `marking finished is stored and undoable`() {
        val (_, books) = device("dev-A")
        assertFalse(books.isFinished("h1"))
        books.setFinished("h1", true)
        assertTrue(books.isFinished("h1"))
        books.setFinished("h1", false)
        assertFalse(books.isFinished("h1"))
    }

    @Test
    fun `finished travels to another device and the newer change wins`() = runBlocking {
        val transport = MemoryTransport()
        val (dbA, booksA) = device("dev-A")
        val (dbB, booksB) = device("dev-B")
        val a = SyncBoundary(dbA, "dev-A", now = { 1L })
        val b = SyncBoundary(dbB, "dev-B", now = { 1L })

        booksA.setFinished("h1", true)
        a.exportAll(transport)
        b.pullAndMerge(transport)
        assertTrue(booksB.isFinished("h1"))

        // B changes its mind later: the higher clock wins back on A
        booksB.setFinished("h1", false)
        b.exportAll(transport)
        a.pullAndMerge(transport)
        assertFalse(booksA.isFinished("h1"))
    }
}
