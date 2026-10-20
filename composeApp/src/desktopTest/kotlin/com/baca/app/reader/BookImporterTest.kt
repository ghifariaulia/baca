package com.baca.app.reader

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.baca.shared.db.DbBookRepository
import com.baca.shared.db.Baca
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookImporterTest {

    private fun temporaryFb2(): String {
        val file = java.io.File.createTempFile("import-fb2", ".fb2")
        file.writeText(
            """
            <FictionBook>
              <description><title-info>
                <author><first-name>M.</first-name><last-name>Shelley</last-name></author>
                <book-title>Imported FB2</book-title>
              </title-info></description>
              <body><section><title><p>Ch 1</p></title><p>Hello FB2 import.</p></section></body>
            </FictionBook>
            """.trimIndent(),
        )
        file.deleteOnExit()
        return file.absolutePath
    }

    private fun importer(): Pair<BookImporter, DbBookRepository> {
        val driver = JdbcSqliteDriver("jdbc:sqlite:")
        Baca.Schema.create(driver)
        val repo = DbBookRepository(Baca(driver), now = { 1L })
        val dir = java.nio.file.Files.createTempDirectory("import-store").toFile().absolutePath
        return BookImporter(repo, dir) to repo
    }

    @Test
    fun importTwiceYieldsOneRow() {
        val (importer, repo) = importer()
        val path = temporaryFb2()
        val first = assertNotNull(importer.import(path))
        assertTrue(first.isNew)
        val second = assertNotNull(importer.import(path))
        assertTrue(!second.isNew)
        assertEquals(1, repo.all().size)
        assertEquals("Imported FB2", second.row.title)
        assertEquals("M. Shelley", second.row.author)
    }

    @Test
    fun reimportAfterRemoveRestoresBook() {
        val (importer, repo) = importer()
        val path = temporaryFb2()
        val first = assertNotNull(importer.import(path))
        repo.tombstone(first.row.bookHash)
        assertEquals(0, repo.all().size)

        val again = assertNotNull(importer.import(path))

        assertTrue(again.isNew)
        assertTrue(!again.row.deleted)
        assertEquals(1, repo.all().size)
    }

    @Test
    fun txtTitleComesFromTheOriginalFileName() {
        val (importer, _) = importer()
        val dir = java.nio.file.Files.createTempDirectory("import-txt").toFile()
        val file = java.io.File(dir, "Short Story.txt").apply { writeText("Once upon a time.") }

        val imported = assertNotNull(importer.import(file.absolutePath))

        assertEquals("Short Story", imported.row.title)
    }

    @Test
    fun cbzImportUsesFirstPageAsCover() {
        val (importer, repo) = importer()
        val zip = java.io.File.createTempFile("import-cbz", ".cbz")
        val out = java.util.zip.ZipOutputStream(zip.outputStream())
        out.putNextEntry(java.util.zip.ZipEntry("page1.jpg"))
        out.write(byteArrayOf(-1, -40, -1, -32, 0, 16)) // tiny jpeg-ish bytes
        out.closeEntry()
        out.putNextEntry(java.util.zip.ZipEntry("page2.jpg"))
        out.write(byteArrayOf(1))
        out.closeEntry()
        out.close()
        zip.deleteOnExit()
        val first = assertNotNull(importer.import(zip.absolutePath))
        assertTrue(first.isNew)
        val row = first.row
        assertEquals("cbz", row.format)
        assertNotNull(row.coverPath)
        assertEquals(1, repo.all().size)
    }

    @Test
    fun unsupportedFilesReturnNull() {
        val (importer, repo) = importer()
        val file = java.io.File.createTempFile("import-x", ".docx")
        file.writeText("junk")
        file.deleteOnExit()
        assertNull(importer.import(file.absolutePath))
        assertEquals(0, repo.all().size)
    }
}
