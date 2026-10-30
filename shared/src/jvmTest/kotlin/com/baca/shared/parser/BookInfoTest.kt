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

    private fun epubWithTitles(opfTitles: String): okio.Path {
        val bytes = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                fun put(name: String, text: String) { zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(text.encodeToByteArray()); zip.closeEntry() }
                put("mimetype", "application/epub+zip")
                put("META-INF/container.xml", """<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="c.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
                put("c.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">$opfTitles<dc:identifier id="id">x</dc:identifier></metadata><manifest><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="a"/></spine></package>""")
                put("a.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>text</p></body></html>""")
            }
        }.toByteArray()
        return tempEpub(bytes)
    }

    @Test
    fun `the main title is the book's name, not a subtitle listed after it`() {
        val titles = """<dc:title id="maintitle">Incidents Around the House</dc:title>
            <meta property="title-type" refines="#maintitle">main</meta>
            <dc:title id="subtitle">A Novel</dc:title>
            <meta property="title-type" refines="#subtitle">subtitle</meta>"""
        assertEquals("Incidents Around the House", parse(epubWithTitles(titles)).title)
        // the marking, not the order, says which is main
        val swapped = """<dc:title id="s">A Novel</dc:title><meta property="title-type" refines="#s">subtitle</meta>
            <dc:title id="m">Incidents Around the House</dc:title><meta property="title-type" refines="#m">main</meta>"""
        assertEquals("Incidents Around the House", parse(epubWithTitles(swapped)).title)
    }

    @Test
    fun `without a main marking the first title is the book's name`() {
        val titles = "<dc:title>Incidents Around the House</dc:title><dc:title>A Novel</dc:title>"
        assertEquals("Incidents Around the House", parse(epubWithTitles(titles)).title)
        assertEquals("Only", parse(epubWithTitles("<dc:title>Only</dc:title>")).title)
    }

    @Test
    fun `a description written as escaped html is read as plain paragraphs`() {
        val titles = "<dc:title>T</dc:title><dc:description>" +
            "&lt;b&gt;&amp;ldquo;Simply put&amp;mdash;&lt;i&gt;it&lt;/i&gt; is good.&amp;rdquo;&lt;/b&gt;&lt;br&gt;&lt;b&gt; &lt;/b&gt;&lt;br&gt;" +
            "To eight-year-old Bela, her family is her world. There&amp;rsquo;s Mommy.&amp;#160;&lt;br&gt; When it grows &amp;amp; grows" +
            "</dc:description>"
        assertEquals(
            "\u201CSimply put\u2014it is good.\u201D\n\nTo eight-year-old Bela, her family is her world. There\u2019s Mommy.\nWhen it grows & grows",
            parse(epubWithTitles(titles)).meta.description,
        )
    }

    @Test
    fun `plain text and paragraph markup in a description`() {
        assertEquals("Just words.", htmlToPlainText("Just words."))
        assertEquals("One\n\nTwo", htmlToPlainText("<p>One</p><p>Two</p>"))
        assertEquals("a < b \uD83D\uDE00", htmlToPlainText("a &lt; b &#x1F600;"))
        assertEquals("&unknown;", htmlToPlainText("&unknown;"))
    }

    @Test
    fun `an ampersand in a title or author is kept`() {
        assertEquals("Pride & Prejudice", parse(epubWithTitles("<dc:title>Pride &amp; Prejudice</dc:title>")).title)
    }
}
