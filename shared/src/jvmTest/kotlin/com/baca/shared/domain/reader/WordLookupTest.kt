package com.baca.shared.domain.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WordLookupTest {

    @Test
    fun `quotes and punctuation round the selection are dropped, inner apostrophes and hyphens kept`() {
        assertEquals("he’d", lookupTerm("“he’d,"))
        assertEquals("well-known", lookupTerm(" well-known… "))
    }

    @Test
    fun `a selection across lines is looked up as one phrase`() {
        assertEquals("tabula rasa", lookupTerm("tabula\n  rasa."))
    }

    @Test
    fun `a selection with no letter or digit has nothing to look up`() {
        assertNull(lookupTerm("—"))
        assertNull(lookupTerm(" \n"))
    }

    @Test
    fun `the web dictionary searches Wiktionary with the term percent-encoded as UTF-8`() {
        assertEquals(
            "https://en.wiktionary.org/wiki/Special:Search?search=caf%C3%A9%20au%20lait",
            webDictionaryUrl("café au lait"),
        )
    }
}
