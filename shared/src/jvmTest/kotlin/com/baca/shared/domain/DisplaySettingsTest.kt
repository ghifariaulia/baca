package com.baca.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisplaySettingsTest {

    @Test
    fun `settings saved before full screen existed load with it on`() {
        val old = decodeDisplay("""{"fontSizeSp":21,"font":"sans","theme":"dim","lineSpacing":"normal","margins":"wide","tapToTurn":false}""")
        assertEquals(21, old.fontSizeSp)
        assertTrue(old.fullScreen)
    }

    @Test
    fun `turning full screen off is kept`() {
        assertFalse(decodeDisplay(DisplaySettings(fullScreen = false).encode()).fullScreen)
    }
}
