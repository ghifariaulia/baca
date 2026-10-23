package com.baca.shared.db

import com.baca.shared.domain.Locator
import kotlin.test.Test
import kotlin.test.assertEquals

/** Files of one edition are grouped by work key; books without one stay separate. */
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
    fun `books without a key stay separate`() {
        val (db, books, _) = device("dev-A", "fileA")
        books.upsert("other", "Dracula", "B. Stoker", "/books/other.epub", "epub")
        books.setWorkKey("other", null)
        assertEquals(listOf("other"), books.linkedHashes("other"))
        assertEquals(2, books.visible().size)
        assertEquals(listOf("fileA"), DbBookRepository(db, now = { 1L }).linkedHashes("fileA"))
    }
}
