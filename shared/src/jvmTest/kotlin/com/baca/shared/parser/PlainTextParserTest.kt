package com.baca.shared.parser

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlainTextParserTest {

    @Test
    fun `a text file is LibreraX's page of paragraphs, sized as foliate counts it`() {
        val raw = "The Probe Book\n\nby Somebody\n\n\nCHAPTER I\n\nIt was a dark night, and the rain fell in torrents.\n" +
            "This line follows with a single newline.\n\n    An indented paragraph with <angle> & ampersand.\n\nChapter 2. The Second\n\n" +
            "Short.\n\n* * *\n\n1. A numbered line\n2. Another one\n\nPART TWO\n\nLast paragraph here.\n"
        val book = txtEpub("probe.txt", raw)
        val chapter = book.chapters.single()
        // its lines kept apart, as <br> keeps them
        assertEquals("It was a dark night, and the rain fell in torrents.\nThis line follows with a single newline.", chapter.blocks[3].text)
        assertEquals("An indented paragraph with <angle> & ampersand.", chapter.blocks[4].text)
        // the page LibreraX made of it was 933 long, and it has no contents
        assertEquals(933, chapter.size)
        assertEquals(emptyList(), book.toc)
    }


    private val txtEpub = txtEpub("walden.txt", "\r\nfirst para line1\nline2 continues.\n\nSecond paragraph.\n\n\nThird one.")
    private val txt = txtEpub

    @Test
    fun txtSplitsParagraphsAndNormalizes() {
        val blocks = txt.chapters.single().blocks as List<Block.Text>
        assertEquals(listOf("first para line1\nline2 continues.", "Second paragraph.", "Third one."), blocks.map { it.text })
        blocks.forEach { assertEquals(BlockKind.PARAGRAPH, it.kind) }
        assertEquals("walden", txt.title)
    }

    @Test
    fun txtOffsetsAreStableAcrossParses() {
        val parsed = txtEpub("walden.txt", "\na.\n\nb.\n")
        val again = txtEpub("walden.txt", "\na.\n\nb.\n")
        assertTrue(parsed.chapters.single().blocks.textsEqual(again.chapters.single().blocks))
    }

    @Test
    fun fb2ExtractsMetadataSectionChapters() {
        val xml = """
            <FictionBook>
              <description><title-info>
                <author><first-name>Lev</first-name><last-name>Tolstoy</last-name></author>
                <book-title>War and Peace</book-title>
              </title-info></description>
              <body>
                <section>
                  <title><p>Book One</p></title>
                  <p>Well, Prince, so Genoa and Lucca…</p>
                  <p>A second paragraph.</p>
                </section>
                <section>
                  <title><p>Book Two</p></title>
                  <p>Content of the second part.</p>
                </section>
              </body>
              <body name="notes"><section><p>A note</p></section></body>
            </FictionBook>
        """.trimIndent()
        val parsed = fb2Epub("peace.fb2", xml)
        assertEquals("War and Peace", parsed.title)
        assertEquals("Lev Tolstoy", parsed.author)
        // each section of the first body is a chapter; the notes body one more, out of the reading order
        assertEquals(listOf("Book One", "Book Two", "A note"), parsed.chapters.map { it.title })
        assertEquals(0, parsed.chapters[2].size)
        val first = parsed.chapters[0].blocks as List<Block.Text>
        assertEquals(BlockKind.HEADING, first[0].kind)
        assertEquals(listOf("Book One", "Well, Prince, so Genoa and Lucca…", "A second paragraph."), first.map { it.text })
        // foliate's stylesheet: no indent on the first paragraph after a title, 1em on the next
        assertEquals(Spacing.ZERO, first[1].indent)
        assertEquals(Spacing(1f), first[2].indent)
    }

    @Test
    fun fb2KeepsWhatFoliateShows() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns:l="http://www.w3.org/1999/xlink">
              <body>
                <section>
                  <title><p>Part</p><p>One</p></title>
                  <epigraph><p>To be</p><text-author>Somebody</text-author></epigraph>
                  <subtitle>Sub</subtitle>
                  <p>With <strong>bold</strong>, <emphasis>italic</emphasis> and a note<a l:href="#n1" type="note">1</a>.</p>
                  <empty-line/>
                  <poem><stanza><v>First line</v><v>Second line</v></stanza></poem>
                  <image l:href="#pic"/>
                </section>
              </body>
              <body name="notes"><section id="n1"><p>The note</p></section></body>
              <binary id="pic" content-type="image/png">iVBORw0KGgo=</binary>
            </FictionBook>
        """.trimIndent()
        val parsed = fb2Epub("x.fb2", xml)
        val blocks = parsed.chapters[0].blocks
        val texts = blocks.map { it.text }
        // one h1 a title paragraph
        assertEquals(listOf("Part", "One"), blocks.take(2).map { it.text })
        assertTrue((blocks[0] as Block.Text).kind == BlockKind.HEADING && (blocks[1] as Block.Text).kind == BlockKind.HEADING)
        // the epigraph's author after a dash, ranged to the end
        val author = blocks.first { it.text.endsWith("Somebody") } as Block.Text
        assertEquals("—Somebody", author.text)
        assertEquals(TextAlign.END, author.align)
        assertTrue("Sub" in texts)
        // bold, italic, a raised note mark linking to its note
        val withNote = blocks.first { it.text.startsWith("With") } as Block.Text
        assertTrue(withNote.spans.any { it.bold && it.text == "bold" } && withNote.spans.any { it.italic && it.text == "italic" })
        val mark = withNote.spans.first { it.text == "1" }
        assertEquals("fb2:1", mark.href)
        assertTrue(mark.baseline > 0f)
        // an empty-line is a line of its own between the paragraphs
        assertEquals("\n", texts[texts.indexOfFirst { it.startsWith("With") } + 1])
        // a stanza's lines, each its own line (the last break adds none)
        assertTrue(blocks.any { it.text == "First line\nSecond line\n" })
        // the picture from its binary
        val image = blocks.filterIsInstance<Block.Image>().single()
        assertEquals("fb2img:pic", image.src)
        assertEquals(8, parsed.binaries.getValue("fb2img:pic").size)
    }

    @Test
    fun fb2InItsDeclaredEncoding() {
        val xml = "<?xml version=\"1.0\" encoding=\"windows-1251\"?><FictionBook><body><section><p>Привет</p></section></body></FictionBook>"
        val parsed = fb2Epub("ru.fb2", xml.toByteArray(charset("windows-1251")))
        assertEquals("Привет", parsed.chapters[0].blocks.single().text)
    }

    @Test
    fun fb2WithoutSectionsIsOneChapter() {
        val xml = """
            <FictionBook><body><p>Only body text.</p></body></FictionBook>
        """.trimIndent()
        val parsed = fb2Epub("plain.fb2", xml)
        assertEquals(1, parsed.chapters.size)
        assertEquals(0, parsed.chapters[0].blocks.size)
    }

    private companion object {
        fun List<Block>.textsEqual(other: List<Block>): Boolean {
            if (size != other.size) return false
            return zip(other).all { (a, b) ->
                a is Block.Text && b is Block.Text && a.text == b.text &&
                    a.kind == b.kind && a.headingLevel == b.headingLevel
            }
        }
    }
}
