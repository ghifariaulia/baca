package com.baca.app.reader

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.baca.shared.db.Baca
import com.baca.shared.db.DbBookRepository
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LibraryFolderTest {

    private fun repo(): DbBookRepository {
        val driver = JdbcSqliteDriver("jdbc:sqlite:")
        Baca.Schema.create(driver)
        return DbBookRepository(Baca(driver), now = { 1L })
    }

    private fun tempDir(name: String): File = Files.createTempDirectory(name).toFile()

    private fun epubBytes(): ByteArray = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()

    @Test
    fun `scan reads books in place, subfolders included, and stores only covers`() {
        val folder = tempDir("library")
        File(folder, "Shelley").mkdirs()
        val epub = File(folder, "Shelley/frankenstein.epub").apply { writeBytes(epubBytes()) }
        val txt = File(folder, "Short Story.txt").apply { writeText("Once upon a time.") }
        File(folder, "notes.pdf").writeText("not a book")
        val store = tempDir("store")
        val repo = repo()

        val found = BookImporter(repo, store.absolutePath).scanFolder(FileLibraryFolder(folder))

        assertEquals(setOf(epub.absolutePath, txt.absolutePath), found)
        assertEquals(setOf(epub.absolutePath, txt.absolutePath), repo.all().map { it.path }.toSet())
        assertEquals("Short Story", repo.all().single { it.format == "txt" }.title)
        // no book copies in app storage, only the EPUB's cover image
        assertTrue(store.listFiles()!!.all { it.name.endsWith("-cover.jpg") })
    }

    @Test
    fun `rescan keeps removed books hidden and follows moved files`() {
        val folder = tempDir("library")
        val epub = File(folder, "frankenstein.epub").apply { writeBytes(epubBytes()) }
        val txt = File(folder, "story.txt").apply { writeText("Once upon a time.") }
        val repo = repo()
        val importer = BookImporter(repo, tempDir("store").absolutePath)
        val library = FileLibraryFolder(folder)
        importer.scanFolder(library)

        repo.tombstone(repo.all().single { it.format == "txt" }.bookHash)
        val moved = File(folder, "Classics/frankenstein.epub").apply { parentFile.mkdirs() }
        epub.renameTo(moved)
        importer.scanFolder(library)

        assertEquals(listOf(moved.absolutePath), repo.all().map { it.path })
        assertTrue(txt.exists(), "removing a book never deletes its file")
    }

    @Test
    fun `adding copies into the folder without overwriting`() {
        val folder = tempDir("library")
        File(folder, "Book.epub").writeText("existing")
        val picked = File(tempDir("downloads"), "Book.epub").apply { writeBytes(epubBytes()) }
        val repo = repo()
        val library = FileLibraryFolder(folder)

        val location = library.add(picked.absolutePath)
        val imported = assertNotNull(BookImporter(repo, tempDir("store").absolutePath).importInPlace(picked.absolutePath, location, restoreRemoved = true))

        assertEquals(File(folder, "Book (2).epub").absolutePath, location)
        assertEquals("existing", File(folder, "Book.epub").readText())
        assertEquals(location, imported.row.path)
    }

    @Test
    fun `size reads a book in the folder, null for a missing one`() {
        val folder = tempDir("library")
        val book = File(folder, "Book.epub").apply { writeBytes(epubBytes()) }
        val library = FileLibraryFolder(folder)
        assertEquals(book.length(), library.size(book.absolutePath))
        assertEquals(null, library.size(File(folder, "gone.epub").absolutePath))
    }
}
