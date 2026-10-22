package com.baca.shared.render

import com.baca.shared.parser.INLINE_IMAGE
import kotlin.test.Test
import kotlin.test.assertEquals

class LineBreakingTest {

    // every character 10px wide, each hard line measured from its own start
    private fun edges(text: String) = FloatArray(text.length + 1).also { e ->
        var x = 0f
        for (i in 1..text.length) {
            // a soft hyphen takes no room, as a browser lays it out
            x = if (text[i - 1] == '\n') 0f else if (text[i - 1] == '\u00AD') x else x + 10f
            e[i] = x
        }
    }

    private fun lines(text: String, width: Int, indent: Int = 0, hyphenator: Hyphenator? = null, image: (Int) -> Boolean = { false }) =
        greedyLines(text, edges(text), width, indent, hyphenator, hyphenPx = 10f, image = image).map { text.substring(it.start, it.endExclusive) }

    @Test
    fun `each line takes every word that fits`() {
        assertEquals(listOf("aa bb", "cc dd", "ee"), lines("aa bb cc dd ee", 60))
        // the first line's indent counts against it
        assertEquals(listOf("aa", "bb cc", "dd ee"), lines("aa bb cc dd ee", 60, indent = 20))
    }

    @Test
    fun `the word that overflows is hyphenated where its first part and the hyphen fit`() {
        val en = Hyphenator.forLanguage("en")
        // "the influen-" is 12 characters: 120px
        assertEquals(listOf("the influen", "tial family"), lines("the influential family", 120, hyphenator = en))
        // a px short: an earlier point
        assertEquals(listOf("the influ", "ential fam", "ily home"), lines("the influential family home", 110, hyphenator = en))
        // no patterns: the word moves down whole
        assertEquals(listOf("the", "influential", "family"), lines("the influential family", 110))
    }

    @Test
    fun `a paragraph's last word isn't hyphenated, unless it's the only one`() {
        val en = Hyphenator.forLanguage("en")
        // "the influen-" would fit, but the word ends the paragraph
        assertEquals(listOf("the", "influential."), lines("the influential.", 120, hyphenator = en))
        assertEquals(listOf("influ", "ential"), lines("influential", 60, hyphenator = en))
    }

    @Test
    fun `a soft hyphen is where a word breaks, with a hyphen drawn, and the only place it does`() {
        val en = Hyphenator.forLanguage("en")
        // "the con-" is 80 with its hyphen; the mark stays at the end of the line it ends
        assertEquals(listOf("the con\u00AD", "nected", "people"), lines("the con\u00ADnected people", 80, hyphenator = en))
        // a word with soft hyphens breaks only at them, not where the patterns would
        assertEquals(listOf("abc ab\u00AD", "cde"), lines("abc ab\u00ADcde", 70, hyphenator = en))
        // unbroken, it takes no room
        assertEquals(listOf("con\u00ADnected"), lines("con\u00ADnected", 90, hyphenator = en))
    }

    @Test
    fun `punctuation around a word isn't part of what's hyphenated`() {
        val en = Hyphenator.forLanguage("en")
        assertEquals(listOf("a (fam", "ily),", "yes"), lines("a (family), yes", 70, hyphenator = en))
    }

    @Test
    fun `lines break after a hyphen between letters, around an em dash and at a hard break`() {
        assertEquals(listOf("well-", "known"), lines("well-known", 60))
        assertEquals(listOf("word—", "word"), lines("word—word", 60))
        assertEquals(listOf("word", "—", "word"), lines("word—word", 45))
        assertEquals(listOf("aa", "bb cc"), lines("aa\nbb cc", 60))
    }

    @Test
    fun `a word too long for a line is cut where the line ends`() {
        assertEquals(listOf("abcd", "efgh", "ij"), lines("abcdefghij", 40))
    }

    @Test
    fun `a line may break around an inline picture`() {
        val text = "aaa${INLINE_IMAGE}bbb"
        assertEquals(listOf("aaa$INLINE_IMAGE", "bbb"), lines(text, 50) { text[it] == INLINE_IMAGE })
        assertEquals(listOf("aaa", "$INLINE_IMAGE", "bbb"), lines(text, 35) { text[it] == INLINE_IMAGE })
    }
}
