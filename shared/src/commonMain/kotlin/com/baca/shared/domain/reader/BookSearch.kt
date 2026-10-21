package com.baca.shared.domain.reader

import com.baca.shared.parser.Block
import com.baca.shared.parser.Chapter

/**
 * One match of an in-book search: where it is ([blockIndex], [charOffset],
 * [length] into the block's text) and a [snippet] of the text around it, with
 * the match at [matchInSnippet].
 */
data class SearchHit(
    val chapterIndex: Int,
    val blockIndex: Int,
    val charOffset: Int,
    val length: Int,
    val snippet: String,
    val matchInSnippet: IntRange,
)

/**
 * Every match of [query] in the book's text, in reading order, at most [limit].
 * Ignores case and treats curly and straight apostrophes alike, so "he'd" finds
 * "he’d". A query shorter than two characters finds nothing.
 */
fun searchBook(chapters: List<Chapter>, query: String, limit: Int = SEARCH_LIMIT): List<SearchHit> {
    val needle = foldQuotes(query.trim())
    if (needle.length < 2) return emptyList()
    val hits = mutableListOf<SearchHit>()
    chapters.forEachIndexed { chapterIndex, chapter ->
        chapter.blocks.forEachIndexed { blockIndex, block ->
            if (block !is Block.Text) return@forEachIndexed
            val text = block.text
            val haystack = foldQuotes(text)
            var from = 0
            while (true) {
                val at = haystack.indexOf(needle, from, ignoreCase = true)
                if (at < 0) break
                hits += hitAt(chapterIndex, blockIndex, text, at, needle.length)
                if (hits.size >= limit) return hits
                from = at + needle.length
            }
        }
    }
    return hits
}

private fun hitAt(chapterIndex: Int, blockIndex: Int, text: String, at: Int, length: Int): SearchHit {
    var start = (at - SNIPPET_BEFORE).coerceAtLeast(0)
    var end = (at + length + SNIPPET_AFTER).coerceAtMost(text.length)
    // whole words at the edges
    if (start > 0) start = text.indexOf(' ', start).takeIf { it in start until at }?.plus(1) ?: start
    if (end < text.length) end = text.lastIndexOf(' ', end).takeIf { it >= at + length } ?: end
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < text.length) "…" else ""
    val snippet = prefix + text.substring(start, end).replace('\n', ' ') + suffix
    val matchStart = prefix.length + at - start
    return SearchHit(chapterIndex, blockIndex, at, length, snippet, matchStart until matchStart + length)
}

// same length in and out, so offsets in the folded text are offsets in the original
private fun foldQuotes(s: String): String = s.replace('’', '\'').replace('‘', '\'')

const val SEARCH_LIMIT = 200
private const val SNIPPET_BEFORE = 40
private const val SNIPPET_AFTER = 60
