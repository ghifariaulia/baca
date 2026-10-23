package com.baca.shared.domain.reader

import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.FormattedSpan
import com.baca.shared.domain.PointDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextSelectionTest {

    private fun para(text: String) = Block.Text(listOf(FormattedSpan(text, false, false)), BlockKind.PARAGRAPH, 0)

    private val blocks = listOf(
        para("He’d worn the last time we’d met."),
        Block.Image("", "img.png"),
        para("Mitsuru Nishiharu… The only son."),
    )

    @Test
    fun `a long press selects the word under it, apostrophes included`() {
        val word = TextSelection.wordAt(blocks, 0, 2)
        assertEquals(SelectionPoint(0, 0), word.start)
        assertEquals(SelectionPoint(0, 4), word.end)
        assertEquals("He’d", word.text(blocks))
    }

    @Test
    fun `on a space or punctuation a long press selects that one character`() {
        assertEquals(" ", TextSelection.wordAt(blocks, 0, 4).text(blocks))
    }

    @Test
    fun `dragging the focus before the anchor keeps reading order`() {
        val selection = TextSelection(anchor = SelectionPoint(2, 7), focus = SelectionPoint(0, 5))
        assertEquals(SelectionPoint(0, 5), selection.start)
        assertEquals(SelectionPoint(2, 7), selection.end)
    }

    @Test
    fun `selected text spans blocks and skips images`() {
        val selection = TextSelection(SelectionPoint(0, 24), SelectionPoint(2, 7))
        assertEquals("we’d met.\nMitsuru", selection.text(blocks))
        assertNull(selection.rangeIn(1, 0))
        assertEquals(0 until 7, selection.rangeIn(2, blocks[2].text.length))
    }

    @Test
    fun `the anchor holds exact offsets and a quote`() {
        val anchor = TextSelection(SelectionPoint(2, 8), SelectionPoint(2, 17)).anchor("c.xhtml", blocks)
        assertEquals(PointDto(2, 8), anchor.start)
        assertEquals(PointDto(2, 17), anchor.end)
        assertEquals("Nishiharu", anchor.quote)
    }
}
