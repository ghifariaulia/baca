package com.baca.app.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerticalDetailsTest {

    @Test
    fun `detects 2-digit tate-chu-yoko runs in vertical text`() {
        assertTrue(isTateChuYokoRun("24", 0, 2))
        assertFalse(isTateChuYokoRun("1234", 0, 4))
        assertFalse(isTateChuYokoRun("ab", 0, 2))
    }
}
