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

    @Test
    fun `a big picture is read smoothed down, as a canvas draws it, not one pixel in so many`() {
        // a 1024px screen of black and white dots: smoothed to 512, an even grey with no white
        // in it; every other pixel taken alone, all white
        val dots = IntArray(1024 * 1024) { i -> if ((i % 1024 + i / 1024) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        assertFalse(isOnPlainGround(dots, 1024, 1024))
    }
}
