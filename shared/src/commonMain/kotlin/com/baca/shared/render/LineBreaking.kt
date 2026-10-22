package com.baca.shared.render

import com.baca.shared.parser.WORD_BREAK
import kotlin.math.ceil

/**
 * A paragraph's lines as a browser breaks them: each line takes every word that fits,
 * and the word that doesn't is hyphenated at the last place where its first part and
 * the hyphen still fit; a word too long for a line on its own is cut where the line ends.
 *
 * [edgePx] is the x of each place between characters, 0 to `text.length`, measured
 * from the start of its own hard line (`\n`), as the text lies on one unbroken line.
 * [image] tells inline pictures, which a line may break before and after, from other
 * placeholders. A line ends before the spaces at its break; the next starts after them.
 */
fun greedyLines(
    text: String,
    edgePx: FloatArray,
    maxWidthPx: Int,
    firstLineIndentPx: Int = 0,
    hyphenator: Hyphenator? = null,
    hyphenPx: Float = 0f,
    image: (Int) -> Boolean = { false },
): List<TextMetrics.LineSpan> {
    val lines = mutableListOf<TextMetrics.LineSpan>()
    var hardStart = 0
    while (true) {
        val hardEnd = text.indexOf('\n', hardStart).let { if (it < 0) text.length else it }
        var start = hardStart
        while (true) {
            val indent = if (start == 0) firstLineIndentPx else 0
            val room = maxWidthPx - indent
            fun widthTo(end: Int) = edgePx[end] - edgePx[start]
            // a line that ends at a soft hyphen draws a hyphen there
            fun breakWidth(at: Int) = widthTo(contentEnd(text, start, at)) + if (endsSoft(text, start, at)) hyphenPx else 0f
            // the last break where the line still fits, and the first one where it doesn't
            var fits = -1
            var next = nextBreak(text, start, hardEnd, image)
            while (breakWidth(next) <= room) {
                fits = next
                if (next == hardEnd) break
                next = nextBreak(text, next, hardEnd, image)
            }
            val line = if (fits == hardEnd) {
                Break(contentEnd(text, start, hardEnd), hardEnd + 1)
            } else {
                val wordStart = if (fits < 0) start else fits
                val wordEnd = contentEnd(text, wordStart, next)
                // a browser leaves a paragraph's last word whole, unless it's the paragraph's only one
                val lastWord = text.substring(wordEnd).isBlank() && text.substring(0, wordStart).isNotBlank()
                hyphenated(text, wordStart, wordEnd, hyphenator.takeUnless { lastWord }) { at -> widthTo(at) + hyphenPx <= room }
                    ?.let { at -> Break(at, at, hyphen = true) }
                    ?: if (fits >= 0) Break(contentEnd(text, start, fits), fits, hyphen = endsSoft(text, start, fits))
                    else cut(start, wordEnd) { at -> widthTo(at) <= room }
            }
            val rightPx = indent + widthTo(line.end) + if (line.hyphen) hyphenPx else 0f
            lines += TextMetrics.LineSpan(start, line.end, ceil(rightPx).toInt())
            if (line.next >= hardEnd) break
            start = line.next
        }
        if (hardEnd == text.length) return lines
        hardStart = hardEnd + 1
    }
}

/** Where a line ends ([end], its spaces left out) and the next begins. */
private class Break(val end: Int, val next: Int, val hyphen: Boolean = false)

/** The next place after [from] a line may break, as the start of the line after it; [hardEnd] if none. */
private fun nextBreak(text: String, from: Int, hardEnd: Int, image: (Int) -> Boolean): Int {
    var i = from
    // a picture at the line's start is taken whole
    if (i < hardEnd && image(i)) return i + 1
    while (i < hardEnd) {
        val c = text[i]
        when {
            c == ' ' -> {
                while (i < hardEnd && text[i] == ' ') i++
                return i
            }
            c == WORD_BREAK || c == SOFT_HYPHEN -> return i + 1
            image(i) -> return i
            c in DASHES && i > from && text[i - 1].isLetter() && text.getOrNull(i + 1)?.isLetter() == true -> return i + 1
            c == EM_DASH -> {
                if (i > from) return i
                while (i < hardEnd && text[i] == EM_DASH) i++
                return i
            }
        }
        i++
    }
    return hardEnd
}

/** Whether a line from [start] broken at [at] ends at a soft hyphen, which then shows. */
private fun endsSoft(text: String, start: Int, at: Int) = at > start && at <= text.length && text[at - 1] == SOFT_HYPHEN

/** [end] with the spaces before it left out, not before [start]. */
private fun contentEnd(text: String, start: Int, end: Int): Int {
    var e = end
    while (e > start && (text[e - 1] == ' ' || text[e - 1] == WORD_BREAK)) e--
    return e
}

/**
 * The last place in the word [start, end) a hyphen may go and [fits], as a browser
 * hyphenates: the word without the punctuation around it. A word with soft hyphens
 * breaks only at them.
 */
private fun hyphenated(text: String, start: Int, end: Int, hyphenator: Hyphenator?, fits: (Int) -> Boolean): Int? {
    hyphenator ?: return null
    if ((start until end).any { text[it] == SOFT_HYPHEN }) return null
    var from = start
    while (from < end && !text[from].isLetter()) from++
    var to = end
    while (to > from && !text[to - 1].isLetter()) to--
    if (to <= from) return null
    return hyphenator.points(text.substring(from, to)).map { from + it }.lastOrNull(fits)
}

/** A word too long for any line, cut after its last character that fits (at least one). */
private fun cut(start: Int, wordEnd: Int, fits: (Int) -> Boolean): Break {
    var at = start + 1
    while (at < wordEnd && fits(at + 1)) at++
    return Break(at, at)
}

// a line may break after a hyphen or en dash between letters ("well-known"), and around an em dash
private val DASHES = setOf('-', '‐', '–')
private const val EM_DASH = '—'
// a book's own hyphenation point (`&shy;`): invisible unless a line breaks there
const val SOFT_HYPHEN = '\u00AD'
