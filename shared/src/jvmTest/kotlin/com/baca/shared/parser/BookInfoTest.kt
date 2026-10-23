package com.baca.shared.parser

import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookInfoTest {

    private fun parse(path: okio.Path) = parseEpub(path)

    private fun tempEpub(bytes: ByteArray): okio.Path {
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "baca-info-test-${System.nanoTime()}.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        return path
    }

    private fun realEpub(resourcePath: String): okio.Path =
        tempEpub(javaClass.classLoader.getResourceAsStream(resourcePath)!!.readBytes())

    @Test
    fun `an epub's own description block is read`() {
        val meta = parse(realEpub("books/frankenstein.epub")).meta
        assertEquals("Public domain in the USA.", meta.rights)
        // the first date, not the file's conversion date
        assertEquals("1993-10-01", meta.published)
        assertEquals("Science fiction", meta.subjects.first())
        assertEquals(7, meta.subjects.size)
    }

    @Test
    fun `a book with no description block has an empty one`() {
        assertEquals(BookMeta(), parse(tempEpub(tinyEpubOf(xhtmlOf("<p>x</p>")))).meta)
    }

    @Test
    fun `info counts the reading order's words and the pages the reader numbers`() {
        val info = parse(tempEpub(tinyEpubOf(xhtmlOf("<p>one two three</p>"), xhtmlOf("<p>four five</p>")))).info()
        assertEquals(2, info.chapters)
        assertEquals(5, info.words)
        assertTrue(info.pages >= 1, "a book has at least one page")
        assertEquals("", info.identifier)
    }

    @Test
    fun `info survives being stored`() {
        val info = parse(realEpub("books/frankenstein.epub")).info()
        assertEquals(info, decodeBookInfo(info.encode()))
        assertEquals("http://www.gutenberg.org/84", info.identifier)
        assertEquals("en", info.language)
    }

    @Test
    fun `an FB2 book's description is read`() {
        val fb2 = """<?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"><description>
            <title-info><genre>sf</genre><genre>adventure</genre><book-title>T</book-title>
            <annotation><p>A story.</p><p>Of two parts.</p></annotation><date>2001</date><lang>en</lang>
            <sequence name="Saga" number="2"/></title-info>
            <publish-info><publisher>Press</publisher></publish-info></description>
            <body><section><p>text</p></section></body></FictionBook>"""
        val meta = fb2Epub("t.fb2", fb2.encodeToByteArray()).meta
        assertEquals("Press", meta.publisher)
        assertEquals("2001", meta.published)
        assertEquals(listOf("sf", "adventure"), meta.subjects)
        assertEquals("Saga", meta.series)
        assertEquals("A story.\n\nOf two parts.", meta.description)
    }
}
