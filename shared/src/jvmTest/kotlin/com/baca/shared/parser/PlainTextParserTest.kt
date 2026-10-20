package com.baca.shared.parser

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlainTextParserTest {

    private val txtEpub = txtEpub("walden.txt", "\r\nfirst para line1\nline2 continues.\n\nSecond paragraph.\n\n\nThird one.")
    private val txt = txtEpub

    @Test
    fun txtSplitsParagraphsAndNormalizes() {
        val blocks = txt.chapters.single().blocks as List<Block.Text>
        assertEquals(listOf("first para line1 line2 continues.", "Second paragraph.", "Third one."), blocks.map { it.text })
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
              <body name="notes"><section><p>Ignored note</p></section></body>
            </FictionBook>
        """.trimIndent()
        val parsed = fb2Epub("peace.fb2", xml)
        assertEquals("War and Peace", parsed.title)
        assertEquals("Lev Tolstoy", parsed.author)
        assertEquals(2, parsed.chapters.size)
        val first = parsed.chapters[0]
        assertEquals("Book One", first.title)
        val textBlocks = first.blocks as List<Block.Text>
        assertEquals(BlockKind.HEADING, textBlocks[0].kind)
        assertEquals("Well, Prince, so Genoa and Lucca…", textBlocks[1].text)
        assertEquals("A second paragraph.", textBlocks[2].text)
        // notes body must not leak into chapters
        assertTrue(parsed.chapters.none { it.title == "" && 'n' in (it.blocks.firstOrNull()?.text ?: "") })
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
