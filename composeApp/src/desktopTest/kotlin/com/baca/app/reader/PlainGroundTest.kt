package com.baca.app.reader

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlainGroundTest {

    private fun picture(w: Int, h: Int, pixel: (Int, Int) -> Int) = IntArray(w * h) { pixel(it % w, it / w) }

    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test
    fun `a drawing on white is on a plain ground`() {
        // ink lines on a white sheet: a floor plan
        val plan = picture(100, 60) { x, y -> if (x % 20 == 0 || y % 20 == 0) grey(0) else grey(250) }
        assertTrue(isOnPlainGround(plan, 100, 60))
    }

    @Test
    fun `a photograph, all middle tones to its edges, is not`() {
        val photo = picture(100, 60) { x, y -> grey(100 + (x + y) % 100) }
        assertFalse(isOnPlainGround(photo, 100, 60))
    }
}
