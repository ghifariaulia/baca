package com.baca.shared.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HyphenatorTest {

    private val en = Hyphenator.forLanguage("en")!!

    private fun hyphenated(word: String) = en.points(word).foldRight(word) { at, w -> w.substring(0, at) + "-" + w.substring(at) }

    @Test
    fun `English words hyphenate as a WebView hyphenates them`() {
        // as LibreraX broke them in Strange Buildings
        assertEquals("in-flu-en-tial", hyphenated("influential"))
        assertEquals("fam-ily", hyphenated("family"))
        assertEquals("hang-ing", hyphenated("hanging"))
        // three letters at least after the hyphen, two before it
        assertEquals("re-port-edly", hyphenated("reportedly"))
        assertEquals("in-herit", hyphenated("inherit"))
    }

    @Test
    fun `listed exceptions win over the patterns`() {
        assertEquals("as-so-ciate", hyphenated("associate"))
    }

    @Test
    fun `short, capitalized and non-letter words stay whole`() {
        assertEquals("rapt", hyphenated("rapt"))
        assertEquals("Kiyochika", hyphenated("Kiyochika"))
        assertEquals("there’s", hyphenated("there’s"))
    }

    @Test
    fun `English of any region takes the patterns, other languages and none don't`() {
        assertEquals(en, Hyphenator.forLanguage("en-US"))
        assertNull(Hyphenator.forLanguage("de"))
        assertNull(Hyphenator.forLanguage(null))
    }
}
