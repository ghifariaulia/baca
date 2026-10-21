package com.baca.shared.parser

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EpubParserTest {

    @Test
    fun `parses metadata and spine order`() {
        val epub = parse(tempEpub(tinyEpub("Little Book", "Some Author", ch1, ch2)))
        assertEquals("Little Book", epub.title)
        assertEquals("Some Author", epub.author)
        assertEquals(listOf("OEBPS/ch0.xhtml", "OEBPS/ch1.xhtml"), epub.chapters.map { it.href })
        // nav.xhtml wins as the toc source; entries map onto spine docs
        assertEquals(listOf("Title 1", "Title 2"), epub.chapters.map { it.title })
        assertEquals(listOf("Title 1", "Title 2"), epub.toc.map { it.title })
        assertEquals("OEBPS/ch0.xhtml", epub.toc.first().href)
    }

    @Test
    fun `toc in a subdirectory still resolves against chapter paths`() {
        // Modern EPUB shape (e.g. nav at OEBPS/text/nav.xhtml): toc links are
        // relative to the TOC file's own directory. Resolving against the OPF
        // dir silently broke every contents link (the "Strange Pictures" bug).
        val epub = parse(tempEpub(tinyEpub("Sub", "A", navDir = "OEBPS/text", chapterHtml = arrayOf(ch1, ch2))))
        assertEquals(listOf("OEBPS/ch0.xhtml", "OEBPS/ch1.xhtml"), epub.chapters.map { it.href })
        assertEquals("OEBPS/ch0.xhtml", epub.toc.first().href)
        assertEquals("OEBPS/ch1.xhtml", epub.toc.last().href)
        assertEquals(listOf("Title 1", "Title 2"), epub.chapters.map { it.title })
    }

    @Test
    fun `internal links resolve to zip paths, external pass through`() {
        val html = """<p>jump to <a href="ch2.xhtml">second</a> and <a href="https://example.com">site</a> and <a href="#note">note</a> plain</p>"""
        val epub = parse(tempEpub(tinyEpub("L", "A", html)))
        val para = epub.chapters[0].blocks.filterIsInstance<Block.Text>().first()
        val links = para.spans.mapNotNull { it.href }
        // nested merge keeps distinct hrefs as separate spans
        assertEquals("second", para.spans[1].text)
        assertEquals(true, para.spans[0].href == null)
        assertEquals(true, links.contains("OEBPS/ch2.xhtml"))
        assertEquals(true, links.contains("https://example.com"))
        assertEquals(2, links.size)
    }

    @Test
    fun `source line wrapping inside a paragraph is plain whitespace`() {
        val blocks = parseXhtml(
            "<html><body><p>\nHow slowly the time passes here, Yet a\nsecond   step <i>is taken </i> towards\n</p>" +
                "<p>Your affectionate<br/>\nbrother,<br/>R.&#160;Walton</p></body></html>",
        )
        assertEquals("How slowly the time passes here, Yet a second step is taken towards", blocks[0].text)
        assertEquals("Your affectionate\nbrother,\nR.\u00A0Walton", blocks[1].text)
    }

    @Test
    fun `spaces in their own inline elements are kept`() {
        val blocks = parseXhtml(
            """<html><body><p><i>28th</i><i> </i><i>F</i><i>eb</i><i> </i><i>2023</i><i> </i><span>Tokyo</span></p></body></html>""",
        )
        assertEquals("28th Feb 2023 Tokyo", blocks.single().text)
    }

    @Test
    fun `a paragraph holding only a non-breaking space is a blank line`() {
        val blocks = parseXhtml("<html><body><p>One</p><p>\u00A0</p><p> </p><p>Two</p></body></html>")
        // &nbsp; renders an empty line in HTML; a plain space renders nothing
        assertEquals(listOf("One", "\u00A0", "Two"), blocks.map { it.text })
    }

    @Test
    fun `printed page numbers are left out of the text`() {
        val blocks = parseXhtml(
            """<html xmlns:epub="http://www.idpf.org/2007/ops"><body>""" +
                """<p>One<span class="pagenum"><a id="Page_12">[Pg 12]</a></span> two</p>""" +
                """<p><span epub:type="pagebreak" title="13">13</span>Three</p>""" +
                """<div role="doc-pagebreak" aria-label="14">14</div>""" +
                """<p class="page-number">xv</p>""" +
                """<p>Four</p></body></html>""",
        )
        assertEquals(listOf("One two", "Three", "Four"), blocks.map { it.text })
    }

    @Test
    fun `unique identifier is read from the opf`() {
        assertEquals("http://www.gutenberg.org/84", parse(realEpub("books/frankenstein.epub")).identifier)
        assertEquals(null, parse(tempEpub(tinyEpubOf(xhtmlOf("<p>x</p>")))).identifier)
    }

    @Test
    fun `real world toc - frankenstein chapter titles from ncx`() {
        val epub = parse(realEpub("books/frankenstein.epub"))
        assertTrue(epub.toc.isNotEmpty(), "gutenberg epub carries a toc.ncx")
        val titled = epub.chapters.count { it.title != null }
        assertTrue(titled > epub.chapters.size / 2, "expected most chapters titled, got $titled/${epub.chapters.size}")
    }

    @Test
    fun `page-list and landmark navs do not leak into the toc`() {
        val epub = parse(tempEpub(tinyEpub("Leak", "A", ch1, ch2)))
        assertEquals(listOf("Title 1", "Title 2"), epub.toc.map { it.title })
        assertEquals(2, epub.toc.size)
    }

    @Test
    fun `chapter blocks keep structure and styles`() {
        val epub = parse(tempEpub(tinyEpub("Fixture Book", "Fixture Author", ch1, ch2)))
        val blocks = epub.chapters[0].blocks

        val heading = assertIs<Block.Text>(blocks[0])
        assertEquals(BlockKind.HEADING, heading.kind)
        assertEquals(1, heading.headingLevel)
        assertEquals("Chapter One", heading.text)

        val para = assertIs<Block.Text>(blocks[1])
        assertEquals(BlockKind.PARAGRAPH, para.kind)
        assertEquals(
            listOf(
                FormattedSpan("plain ", false, false),
                FormattedSpan("bold", true, false),
                FormattedSpan(" and ", false, false),
                FormattedSpan("italic", false, true),
                // br appends a newline into the same span
                FormattedSpan(".\nsecond sentence after a break.", false, false),
            ),
            para.spans,
        )
        assertEquals(
            "plain bold and italic.\nsecond sentence after a break.",
            para.text,
        )

        val quote = assertIs<Block.Text>(blocks[2])
        assertEquals(BlockKind.QUOTE, quote.kind)

        val img = assertIs<Block.Image>(blocks[3])
        assertEquals("a picture", img.alt)
    }

    @Test
    fun `offsets contract - block text is spans joined, offsets index into it`() {
        val epub = parse(tempEpub(tinyEpub("Fixture Book", "Fixture Author", ch1)))
        val para = assertIs<Block.Text>(epub.chapters[0].blocks[1])
        assertEquals(para.spans.joinToString("") { it.text }, para.text)
        // offsets into text are stable across repeated parses
        val again = parse(tempEpub(tinyEpub("Fixture Book", "Fixture Author", ch1)))
        val para2 = assertIs<Block.Text>(again.chapters[0].blocks[1])
        assertEquals(para.text, para2.text)
        assertEquals(para.spans, para2.spans)
    }

    @Test
    fun `image blocks carry resolvable src`() {
        val epub = parse(tempEpub(tinyEpub("b", "a", ch1, ch2)))
        val img = epub.chapters[0].blocks.filterIsInstance<Block.Image>().first()
        assertEquals("OEBPS/cover.jpg", img.src)
        assertEquals("a picture", img.alt)
    }

    @Test
    fun `chapter-relative image src with dotdot matches the zip layout`() {
        // like real books: chapter in OEBPS/text/, image at OEBPS/images/
        // tinyEpub puts chapters at OEBPS/ch<i>.xhtml, so add a nested case via
        // an explicit src that mirrors OEBPS/text/xx.xhtml -> OEBPS/images/*.jpg
        val pixels = ByteArray(64) { 7 }
        val epubBytes = tinyEpub(
            "b", "a",
            images = mapOf("OEBPS/images/pic.jpg" to pixels),
            chapterHtml = arrayOf("<img src=\"images/pic.jpg\" alt=\"p\"/>"),
        )
        val epub = parse(tempEpub(epubBytes))
        val img = epub.chapters[0].blocks.filterIsInstance<Block.Image>().first()
        assertEquals("OEBPS/images/pic.jpg", img.src)
        val loaded = EpubImages(tempEpub(epubBytes)).imageBytes(img.src)
        assertEquals(64, loaded?.size)
    }

    @Test
    fun `src resolves against the chapter directory, like OEBPS-text chapter books`() {
        // real-world shape: chapter at OEBPS/text/x.xhtml, image at OEBPS/images/*
        val pixels = ByteArray(64) { 7 }
        val epubBytes = tinyEpub(
            "b", "a",
            images = mapOf("OEBPS/images/pic.jpg" to pixels),
            chapterDir = "OEBPS/text",
            chapterHtml = arrayOf("<img src=\"../images/pic.jpg\" alt=\"p\"/>"),
        )
        val epub = parse(tempEpub(epubBytes))
        val img = epub.chapters[0].blocks.filterIsInstance<Block.Image>().first()
        assertEquals("OEBPS/images/pic.jpg", img.src)
        val loaded = EpubImages(tempEpub(epubBytes)).imageBytes(img.src)
        assertEquals(64, loaded?.size)
    }

    @Test
    fun `real world epub image bytes load - frankenstein`() {
        val epub = parse(realEpub("books/frankenstein.epub"))
        val src = epub.chapters.asSequence()
            .flatMap { it.blocks.asSequence() }
            .filterIsInstance<Block.Image>()
            .firstNotNullOfOrNull { it.src.takeIf { s -> s.isNotBlank() } }
        if (src != null) {
            val bytes = com.baca.shared.parser.EpubImages(realEpub("books/frankenstein.epub")).imageBytes(src)
            assertTrue(bytes != null && bytes.size > 1000, "image $src should decode to bytes")
        }
    }

    @Test
    fun `real world epub parses fully - frankenstein`() {
        val epub = parse(realEpub("books/frankenstein.epub"))
        assertTrue(epub.title.contains("Frankenstein"))
        assertTrue(epub.author.contains("Shelley"))
        assertTrue("cover" in (epub.coverHref ?: ""))

        val chapters = epub.chapters
        assertTrue(chapters.size > 10, "expected many spine docs, got ${chapters.size}")
        // most spine docs are real text chapters; cover/title/image pages may be not
        val textChapters = chapters.count { ch -> ch.blocks.any { it is Block.Text } }
        assertTrue(textChapters > chapters.size / 2, "only $textChapters/${chapters.size} chapters have text")
        // offsets contract holds everywhere: spans joined == text
        chapters.forEach { ch ->
            ch.blocks.forEach { b ->
                if (b is Block.Text) assertEquals(b.spans.joinToString("") { it.text }, b.text)
            }
        }
    }

    private val ch1 = xhtmlOf(
        "<h1>Chapter One</h1>",
        "<p>plain <b>bold</b> and <i>italic</i>.<br/>second sentence after a break.</p>",
        "<blockquote><p>a quoted paragraph</p></blockquote>",
        "<img alt=\"a picture\" src=\"cover.jpg\"/>",
    )

    private val ch2 = xhtmlOf(
        "<h2>The End</h2>",
        "<p>Goodbye.</p>",
    )

    private fun parse(path: okio.Path) = parseEpub(path)

    private fun tempEpub(bytes: ByteArray): okio.Path {
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "baca-test-${System.nanoTime()}.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        return path
    }

    private fun realEpub(resourcePath: String): okio.Path {
        // load from test resources into a temp file so parseEpub(Path) applies
        val bytes = javaClass.classLoader.getResourceAsStream(resourcePath)!!.readBytes()
        return tempEpub(bytes)
    }
}
