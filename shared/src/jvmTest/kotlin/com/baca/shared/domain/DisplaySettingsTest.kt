package com.baca.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisplaySettingsTest {

    @Test
    fun `settings saved before full screen existed load with it on`() {
        val old = decodeDisplay("""{"fontSizeSp":21,"font":"sans","theme":"dim","lineSpacing":"normal","margins":"wide","tapToTurn":false}""")
        assertTrue(old.fullScreen)
    }

    @Test
    fun `settings saved as named steps load at their nearest step`() {
        val old = decodeDisplay("""{"fontSizeSp":21,"font":"sans","theme":"dim","lineSpacing":"normal","margins":"wide"}""")
        // 21sp is 131% of 16
        assertEquals(Triple(130, 17, 9), Triple(old.fontSizePercent, old.lineSpacingTenths, old.paddingPercent))
        assertEquals("sans", old.font)
        // and once saved again, as they are
        val again = decodeDisplay(old.stepLineSpacing(-2).encode())
        assertEquals(15, again.lineSpacingTenths)
    }

    @Test
    fun `each setting steps as LibreraX's do, within its range`() {
        val d = DisplaySettings(fontSizePercent = 125, lineSpacingTenths = 15, paddingPercent = 5)
        assertEquals(130, d.stepFontSize(1).fontSizePercent)
        assertEquals(20f, d.fontSizeSp)
        assertEquals(1.6f, d.stepLineSpacing(1).lineRatio())
        assertEquals(4, d.stepPadding(-1).paddingPercent)
        assertEquals(250, DisplaySettings(fontSizePercent = 250).stepFontSize(1).fontSizePercent)
        assertEquals(10, DisplaySettings(lineSpacingTenths = 10).stepLineSpacing(-1).lineSpacingTenths)
        assertEquals(0, DisplaySettings(paddingPercent = 0).stepPadding(-1).paddingPercent)
    }

    @Test
    fun `turning full screen off is kept`() {
        assertFalse(decodeDisplay(DisplaySettings(fullScreen = false).encode()).fullScreen)
    }
}
