package com.baca.shared.domain.reader

import com.baca.shared.parser.Block
import com.baca.shared.domain.PointDto
import com.baca.shared.domain.ReflowAnchorDto

/** A place in a chapter's text: a block and a char offset into its text (TECH 5.2). */
data class SelectionPoint(val blockIndex: Int, val charOffset: Int) : Comparable<SelectionPoint> {
    override fun compareTo(other: SelectionPoint): Int =
        compareValuesBy(this, other, { it.blockIndex }, { it.charOffset })
}

/**
 * Text the reader is selecting, possibly across blocks. [anchor] stays where the
 * selection began and [focus] follows the dragged handle, so either may come
 * first; [start] and [end] are in reading order, [end] exclusive.
 */
data class TextSelection(val anchor: SelectionPoint, val focus: SelectionPoint) {
    val start: SelectionPoint get() = minOf(anchor, focus)
    val end: SelectionPoint get() = maxOf(anchor, focus)
    val isEmpty: Boolean get() = start == end

    /** The selected part of [blockIndex]'s text as [start, end) offsets, or null when it holds none. */
    fun rangeIn(blockIndex: Int, blockLength: Int): IntRange? {
        if (blockIndex < start.blockIndex || blockIndex > end.blockIndex) return null
        val from = if (blockIndex == start.blockIndex) start.charOffset else 0
        val to = if (blockIndex == end.blockIndex) end.charOffset else blockLength
        return if (to > from) from until to else null
    }

    /** The selected text, blocks joined by line breaks. */
    fun text(blocks: List<Block>): String =
        (start.blockIndex..end.blockIndex).mapNotNull { i ->
            val block = blocks.getOrNull(i) as? Block.Text ?: return@mapNotNull null
            rangeIn(i, block.text.length)?.let { block.text.substring(it.first, it.last + 1) }
        }.joinToString("\n")

    fun anchor(chapterHref: String, blocks: List<Block>): ReflowAnchorDto = ReflowAnchorDto(
        href = chapterHref,
        start = PointDto(start.blockIndex, start.charOffset),
        end = PointDto(end.blockIndex, end.charOffset),
        quote = text(blocks).take(QUOTE_CHARS),
    )

    companion object {
        /** The word around [charOffset] in [blockIndex] (long press); one character when it's not in a word. */
        fun wordAt(blocks: List<Block>, blockIndex: Int, charOffset: Int): TextSelection {
            val text = (blocks.getOrNull(blockIndex) as? Block.Text)?.text.orEmpty()
            val at = charOffset.coerceIn(0, (text.length - 1).coerceAtLeast(0))
            if (text.isEmpty() || !text[at].isWordChar()) {
                val end = (at + 1).coerceAtMost(text.length)
                return TextSelection(SelectionPoint(blockIndex, at), SelectionPoint(blockIndex, end))
            }
            var from = at
            while (from > 0 && text[from - 1].isWordChar()) from--
            var to = at
            while (to < text.length && text[to].isWordChar()) to++
            return TextSelection(SelectionPoint(blockIndex, from), SelectionPoint(blockIndex, to))
        }

        private const val QUOTE_CHARS = 64
    }
}

// letters, digits and the apostrophes inside words ("he’d")
private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '\'' || this == '’'
