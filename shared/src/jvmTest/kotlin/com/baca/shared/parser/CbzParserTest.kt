package com.baca.shared.parser

import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class CbzParserTest {

    private fun tempZip(entries: Map<String, ByteArray>): okio.Path {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "cbz-test-${System.nanoTime()}.cbz"
        FileSystem.SYSTEM.write(path) { write(out.toByteArray()) }
        return path
    }

    private val jpg = byteArrayOf(1, 2, 3)

    @Test
    fun pagesSortNaturallyAndDropNonImages() {
        val zip = tempZip(
            mapOf(
                "page10.jpg" to jpg,
                "page2.jpg" to jpg,
                "Page1.jpg" to jpg,
                "cover.xml" to "<x/>".toByteArray(),
                "__MACOSX/page2._x.jpg" to jpg,
            ),
        )
        val book = parseCbz(zip, "My Comic.cbz")
        assertEquals("My Comic", book.title)
        assertEquals(listOf("/Page1.jpg", "/page2.jpg", "/page10.jpg"), book.pages)
    }

    @Test
    fun nestedFoldersKeepTheirOrder() {
        val zip = tempZip(
            mapOf(
                "b/2.jpg" to jpg,
                "b/1.jpg" to jpg,
                "a/0.jpg" to jpg,
            ),
        )
        val book = parseCbz(zip, "nested.cbz")
        assertEquals(listOf("/a/0.jpg", "/b/1.jpg", "/b/2.jpg"), book.pages)
    }
}
