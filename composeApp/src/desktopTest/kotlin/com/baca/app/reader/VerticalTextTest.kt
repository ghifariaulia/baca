package com.baca.app.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class VerticalTextTest {
    @Test
    fun `ideographs, kana and fullwidth forms stand upright in vertical text, Latin and digits lie sideways`() {
        assertEquals(listOf(true, true, true, true, true), "漢かカ。Ａ".map { isUpright(it) })
        assertEquals(listOf(false, false, false, false), "Ab1 ".map { isUpright(it) })
    }

    @Test
    fun `a line of Japanese ends without a hyphen, a line of English between letters has one`() {
        // CJK breaks anywhere; a word cut mid-way shows a hyphen
        assertEquals(false, breaksWord("吾輩は猫である", 3))
        assertEquals(true, breaksWord("wonderful", 4))
        assertEquals(false, breaksWord("two words", 3))
    }
}
