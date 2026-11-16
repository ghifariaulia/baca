package com.baca.shared.domain.reader

import com.baca.shared.parser.Block
import com.baca.shared.parser.Chapter

/** One sentence read aloud: [start, end) of [blockIndex]'s text in [chapterIndex]. */
data class Utterance(val chapterIndex: Int, val blockIndex: Int, val start: Int, val end: Int, val text: String)

/**
 * The first sentence to read from [charOffset] in [blockIndex] of [chapterIndex] on: the rest of the sentence the
 * offset is in, else the next text in the chapter, else the start of the next chapter in the reading order
 * (chapters out of it are passed over, as page turns pass them). Null at the end of the book.
 */
fun utteranceFrom(chapters: List<Chapter>, chapterIndex: Int, blockIndex: Int, charOffset: Int): Utterance? {
    var chapter = chapterIndex
    var block = blockIndex
    var offset = charOffset
    while (chapter in chapters.indices) {
        val blocks = chapters[chapter].blocks
        while (block in blocks.indices) {
            val text = (blocks[block] as? Block.Text)?.text.orEmpty()
            sentenceAt(text, offset)?.let { (start, end) ->
                return Utterance(chapter, block, start, end, text.substring(start, end))
            }
            block++
            offset = 0
        }
        chapter = nextLinear(chapters, chapter)
        block = 0
        offset = 0
    }
    return null
}

/** The sentence after [previous]. */
fun utteranceAfter(chapters: List<Chapter>, previous: Utterance): Utterance? =
    utteranceFrom(chapters, previous.chapterIndex, previous.blockIndex, previous.end)

private fun nextLinear(chapters: List<Chapter>, from: Int): Int {
    var next = from + 1
    while (next < chapters.size && !chapters[next].linear) next++
    return next
}

/** The sentence from [from] in [text] with the spaces round it trimmed, or null when only spaces are left. */
private fun sentenceAt(text: String, from: Int): Pair<Int, Int>? {
    var start = from.coerceAtLeast(0)
    while (start < text.length && text[start].isWhitespace()) start++
    if (start >= text.length) return null
    var end = sentenceEnd(text, start)
    while (end > start && text[end - 1].isWhitespace()) end--
    return start to end
}

// a sentence ends at . ! ? or … with any closing quotes and brackets after it, before a space; not where
// the text goes on in lower case ("“Is it you?” she asked.", "Then… silence."), nor after a title such as
// "Mr." or an initial such as "J.", which a name follows
private fun sentenceEnd(text: String, start: Int): Int {
    val limit = (start + MAX_UTTERANCE).coerceAtMost(text.length)
    var i = start
    while (i < limit) {
        if (text[i] in ENDS && !isAbbreviation(text, start, i)) {
            var after = i + 1
            while (after < text.length && (text[after] in ENDS || text[after] in CLOSERS)) after++
            if (after >= text.length) return after
            if (text[after].isWhitespace() && !lowercaseFollows(text, after)) return after
            i = after
            continue
        }
        i++
    }
    if (limit == text.length) return limit
    // too long for one go (text-to-speech engines cap their input): break at the last space before the cap
    val space = text.lastIndexOf(' ', limit - 1)
    return if (space > start) space else limit
}

private fun lowercaseFollows(text: String, from: Int): Boolean {
    var i = from
    while (i < text.length && text[i].isWhitespace()) i++
    return i < text.length && text[i].isLowerCase()
}

private fun isAbbreviation(text: String, start: Int, dot: Int): Boolean {
    if (text[dot] != '.') return false
    var wordStart = dot
    while (wordStart > start && text[wordStart - 1].isLetter()) wordStart--
    val word = text.substring(wordStart, dot)
    return (word.length == 1 && word[0].isUpperCase()) || word in TITLES
}

private const val ENDS = ".!?…"
private const val CLOSERS = "\"'”’»)]"
private val TITLES = setOf("Mr", "Mrs", "Ms", "Dr", "St", "Jr", "Sr", "Prof")

/** Most characters spoken in one go; Android's engine takes at most 4000. */
internal const val MAX_UTTERANCE = 1000
