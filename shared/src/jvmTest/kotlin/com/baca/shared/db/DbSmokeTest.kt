package com.baca.shared.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DbSmokeTest {

    @Test
    fun booksInsertSelectDelete() {
        val (db, _) = inMemoryDb()
        db.booksQueries.insertBook(
            book_hash = "h1", title = "Frankenstein", author = "Mary Shelley",
            path = "/books/84.epub", format = "epub", cover_path = null,
            added_at = 1712345678, lamport = 700, deleted = 0,
        )
        val book = db.booksQueries.selectBookByHash("h1").executeAsOne()
        assertEquals("Frankenstein", book.title)
        assertEquals(0L, book.deleted)

        db.booksQueries.insertBook(
            "h1", "Frankenstein (edited)", "Mary Shelley", "84.epub", "epub",
            null, 1712345678, 701, 1,
        )
        assertEquals("Frankenstein (edited)", db.booksQueries.selectBookByHash("h1").executeAsOne().title)
        assertEquals(0, db.booksQueries.selectBooks().executeAsList().size)
    }

    @Test
    fun positionsRoundTrip() {
        val (db, _) = inMemoryDb()
        val locator = """{"href":"OEBPS/ch12.xhtml","blockIndex":41,"charOffset":128}"""
        db.positionsQueries.setPosition("h1", "phone-a", locator, 903, 1712345678)
        val row = db.positionsQueries.selectPosition("h1").executeAsOne()
        assertEquals("phone-a", row.device_id)
        assertEquals(903L, row.lamport)
        assertNull(db.positionsQueries.selectPosition("missing").executeAsOneOrNull())
    }

    @Test
    fun annotationsFilterDeleted() {
        val (db, _) = inMemoryDb()
        listOf(annotationsRow("a1", deleted = 0), annotationsRow("a2", deleted = 1)).forEach {
            db.annotationsQueries.upsertAnnotation(
                it[0] as String, it[1] as String, it[2] as String, it[3] as String,
                it[4] as String, it[5] as String, it[6] as Long, it[7] as String,
                it[8] as Long, it[9] as Long,
            )
        }
        val rows = db.annotationsQueries.selectAnnotationsByBook("h1").executeAsList()
        assertEquals(listOf("a1"), rows.map { it.id })
    }

    @Test
    fun settingsAndSyncStateRoundTrip() {
        val (db, _) = inMemoryDb()
        db.settings_syncedQueries.putSynced("dictionary", """{"lang":"en"}""", 650)
        assertEquals(650L, db.settings_syncedQueries.selectSynced("dictionary").executeAsOne().lamport)

        db.sync_stateQueries.upsertSyncState("phone-a", 41, 42, 903)
        val state = db.sync_stateQueries.selectSyncStateAll().executeAsOne()
        assertEquals(41L, state.last_seen_counter)
        assertEquals(903L, state.own_lamport)
    }

    private fun annotationsRow(
        id: String,
        deleted: Long,
    ): Array<Any?> = arrayOf(
        id, "h1", "highlight", """{"href":"x"}""", "text", "", 1712345678L, "phone-a", 880L, deleted,
    )
}
