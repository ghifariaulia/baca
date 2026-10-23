package com.baca.shared.db

import com.baca.shared.domain.Locator
import com.baca.shared.domain.isFinished
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
    fun `a read-through book counts as finished without the mark`() {
        assertTrue(isFinished(markedFinished = false, progress = 1f))
        assertTrue(isFinished(markedFinished = true, progress = 0.2f))
        assertFalse(isFinished(markedFinished = false, progress = 0.5f))
        assertFalse(isFinished(markedFinished = false, progress = null))
    }

    @Test
    fun `reset to start makes a read-through book unread`() {
        val (db, books) = device("dev-A")
        val store = DbPositionStore(db, now = { 5L })
        store.savePosition("h1", Locator("OEBPS/end.xhtml", 3, 10, 1.0))
        store.saveBookProgress("h1", 1f)
        assertTrue(isFinished(books.isFinished("h1"), books.progressFor("h1")))

        store.resetToStart("h1", fixedLayout = false)

        assertFalse(isFinished(books.isFinished("h1"), books.progressFor("h1")))
        assertEquals(Locator("", 0, 0, 0.0), store.loadPosition("h1"))
    }
}
