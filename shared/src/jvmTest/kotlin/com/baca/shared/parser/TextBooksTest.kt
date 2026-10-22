package com.baca.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextBooksTest {

    private fun body(xhtml: String) = xhtml.substringAfter("<body>").substringBefore("</body>")

    @Test
    fun `HTML as a browser reads it, with void tags, implied ends, no scripts and ids on headings`() {
        val xhtml = htmlToXhtml("""<html lang="en"><head><title>Probe Page</title><style>p { color: red }</style></head><body><h1>Head</h1><p>One<br>two <b>bold</b><p>Unclosed paragraph<ul><li>a<li>b</ul><script>alert(1)</script><img src="x.png" onclick="evil()"></body></html>""")
        assertEquals("""<h1 id="h1">Head</h1><p>One<br/>two <b>bold</b></p><p>Unclosed paragraph</p><ul><li>a</li><li>b</li></ul><img src="x.png"/>""", body(xhtml))
        assertTrue(xhtml.startsWith("""<html lang="en"><head><title>Probe Page</title>"""))
    }

    @Test
    fun `HTML entities are decoded and text with angle brackets is kept`() {
        assertEquals("<p>a b &amp; c &lt; d — ©</p>", body(htmlToXhtml("<p>a&nbsp;b &amp; c < d &mdash; &#169;")))
    }

    @Test
    fun `Markdown as CommonMark, LibreraX's paragraphs with their lines kept`() {
        val md = "# Main Title\n\nA paragraph with *emphasis*, **strong**, `code` and a [link](http://example.com).\nSecond line of the same paragraph.\n\n" +
            "## Section Two\n\n- first item\n- second item\n\n1. one\n2. two\n\n> A quoted line.\n\n```\nfenced code\n```\n\n---\n\nLast paragraph & <b>raw</b> html.\n"
        assertEquals(
            "<h1>Main Title</h1><p>A paragraph with <em>emphasis</em>, <strong>strong</strong>, <code>code</code> and a <a href=\"http://example.com\">link</a>.\n" +
                "Second line of the same paragraph.</p><h2>Section Two</h2><ul><li>first item</li><li>second item</li></ul><ol><li>one</li><li>two</li></ol>" +
                "<blockquote><p>A quoted line.</p></blockquote><pre><code>fenced code\n</code></pre><hr><p>Last paragraph &amp; <b>raw</b> html.</p>",
            markdownToHtml(md),
        )
    }

    @Test
    fun `Markdown setext headings, nested lists and hard breaks`() {
        assertEquals("<h1>Title</h1><h2>Sub</h2>", markdownToHtml("Title\n=====\n\nSub\n---\n"))
        assertEquals("<ul><li>a<ul><li>b</li></ul></li><li>c</li></ul>", markdownToHtml("- a\n  - b\n- c\n"))
        assertEquals("<p>one<br>\ntwo</p>", markdownToHtml("one  \ntwo"))
    }

    @Test
    fun `RTF as paragraphs of bold and italic, as LibreraX converts it`() {
        val rtf = "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24 First paragraph with {\\b bold} and {\\i italic} text.\\par\nSecond paragraph.\\par\n\\pard\\qc Centred line.\\par\n}"
        assertEquals("<p>First paragraph with <b>bold</b> and <i>italic</i> text.</p>\n<p>Second paragraph.</p>\n<p>Centred line.</p>\n", rtfToHtml(rtf.encodeToByteArray()))
    }

    @Test
    fun `RTF escapes and unicode`() {
        assertEquals("<p>café “q” é {x}</p>\n", rtfToHtml("{\\rtf1\\ansi caf\\'e9 \\ldblquote q\\rdblquote  \\u233? \\{x\\}\\par}".encodeToByteArray()))
    }

    @Test
    fun `DOCX as paragraphs of bold and italic runs`() {
        val xml = """<w:document xmlns:w="w"><w:body><w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Docx Heading</w:t></w:r></w:p>""" +
            """<w:p><w:r><w:t xml:space="preserve">Plain and </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>bold</w:t></w:r><w:r><w:rPr><w:i/></w:rPr><w:t xml:space="preserve"> italic</w:t></w:r></w:p></w:body></w:document>"""
        assertEquals("<p>Docx Heading</p>\n<p>Plain and <b>bold</b><i> italic</i></p>\n", docxToHtml(xml))
    }

    @Test
    fun `an HTML book is one chapter, its contents the h1 to h3 headings, the title the page's`() {
        val book = htmlBook("page.html", htmlToXhtml("<html><head><title>Probe Page</title></head><body><h1>Head</h1><p>Text</p><h2>Sub</h2><p>More text</p></body></html>"))
        assertEquals("Probe Page", book.title)
        assertEquals(listOf("Head" to "h1", "Sub" to "h2"), book.toc.map { it.title to it.anchor })
        val chapter = book.chapters.single()
        assertEquals(listOf("Head", "Text", "Sub", "More text"), chapter.blocks.map { it.text })
        assertEquals(mapOf("h1" to 0, "h2" to 2), chapter.anchorBlocks.filterKeys { it.startsWith("h") })
    }
}
