package com.baca.shared.backup

import com.baca.shared.db.Baca
import com.baca.shared.db.inMemoryDb
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryBackupTest {

    private val fs = FileSystem.SYSTEM
    private fun tempDir(name: String): Path =
        (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "baca-backup-test-${System.nanoTime()}" / name).also { fs.createDirectories(it) }

    private fun device(id: String, syncFolder: String): Baca {
        val (db, _) = inMemoryDb()
        db.settings_localQueries.setLocal("device_id", id)
        db.settings_localQueries.setLocal("sync_folder", syncFolder)
        return db
    }

    @Test
    fun `backup restores library, files and notes onto another device`() {
        val storeA = tempDir("a")
        val book = storeA / "h1.epub"
        val cover = storeA / "h1-cover.jpg"
        fs.write(book) { writeUtf8("epub bytes") }
        fs.write(cover) { writeUtf8("cover bytes") }
        val a = device("dev-A", "/a/sync")
        a.booksQueries.insertBook("h1", "Frankenstein", "M. Shelley", book.toString(), "epub", cover.toString(), 10, 7, 0)
        a.positionsQueries.setPosition("h1", "dev-A", """{"href":"c.xhtml"}""", 8, 20)
        a.annotationsQueries.upsertAnnotation("n1", "h1", "note", "{}", "text", "my note", 30, "local", 9, 0)
        a.settings_syncedQueries.putSynced("dictionary", "\"en\"", 5)
        a.settings_localQueries.setLocal("work:h1", "key")
        val archive = tempDir("out") / "baca-backup.zip"
        fs.sink(archive).use { LibraryBackup(a, fs).create(it) }

        val storeB = tempDir("b")
        val b = device("dev-B", "/b/sync")
        b.booksQueries.insertBook("old", "Old Book", "", (storeB / "old.epub").toString(), "epub", null, 1, 1, 0)
        LibraryBackup(b, fs).restore(archive, storeB.toString())

        val restored = b.booksQueries.selectBooksAll().executeAsList().single()
        assertEquals("h1", restored.book_hash)
        assertEquals((storeB / "h1.epub").toString(), restored.path)
        assertEquals("epub bytes", fs.read(restored.path.toPath()) { readUtf8() })
        assertEquals("cover bytes", fs.read(restored.cover_path!!.toPath()) { readUtf8() })
        assertEquals("my note", b.annotationsQueries.selectAnnotationsAll().executeAsOne().note)
        assertEquals("""{"href":"c.xhtml"}""", b.positionsQueries.selectPosition("h1").executeAsOne().locator)
        assertEquals("\"en\"", b.settings_syncedQueries.selectSynced("dictionary").executeAsOne().value_)
        assertEquals("key", b.settings_localQueries.getLocal("work:h1").executeAsOne().value_)
        // this device keeps its own identity and sync folder
        assertEquals("dev-B", b.settings_localQueries.getLocal("device_id").executeAsOne().value_)
        assertEquals("/b/sync", b.settings_localQueries.getLocal("sync_folder").executeAsOne().value_)
        // the replaced library is kept as a safety copy next to the books folder
        assertTrue(fs.exists(storeB.parent!! / LibraryBackup.SAFETY_COPY))
        // the next local write must out-rank restored records
        val clock = b.sync_stateQueries.selectSyncStateAll().executeAsList().single { it.device_id == "dev-B" }
        assertTrue(clock.own_lamport >= 9)
    }

    @Test
    fun `an archive that is not a backup leaves the library untouched`() {
        val other = tempDir("x") / "random.zip"
        fs.sink(other).use { writeZip(it, listOf("hello.txt" to { okio.Buffer().writeUtf8("hi") })) }
        val b = device("dev-B", "/b/sync")
        b.booksQueries.insertBook("old", "Old Book", "", "/b/old.epub", "epub", null, 1, 1, 0)

        assertFailsWith<IllegalArgumentException> { LibraryBackup(b, fs).restore(other, tempDir("b").toString()) }

        assertEquals("old", b.booksQueries.selectBooksAll().executeAsOne().book_hash)
        assertNull(b.settings_localQueries.getLocal("work:h1").executeAsOneOrNull())
    }
}
