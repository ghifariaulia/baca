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
    fun `margins default to 6 percent at the sides and 7 at the top and bottom, each on its own`() {
        val d = DisplaySettings()
        assertEquals(Triple(6, 7, 7), Triple(d.paddingPercent, d.topPercent, d.bottomPercent))
        val lowerTop = d.stepTop(-1)
        assertEquals(Triple(6, 6, 7), Triple(lowerTop.paddingPercent, lowerTop.topPercent, lowerTop.bottomPercent))
        val raisedBottom = lowerTop.stepBottom(2)
        assertEquals(Triple(6, 6, 9), Triple(raisedBottom.paddingPercent, raisedBottom.topPercent, raisedBottom.bottomPercent))
        // the side margins don't move them
        assertEquals(6 to 9, raisedBottom.stepPadding(1).let { it.topPercent to it.bottomPercent })
        assertEquals(0, DisplaySettings(topPercent = 0).stepTop(-1).topPercent)
        assertEquals(25, DisplaySettings(bottomPercent = 25).stepBottom(1).bottomPercent)
    }

    @Test
    fun `settings saved before the top and bottom margins existed load with the default ones`() {
        val old = decodeDisplay("""{"fontSizePercent":130,"paddingPercent":9}""")
        assertEquals(9, old.paddingPercent)
        assertEquals(7 to 7, old.topPercent to old.bottomPercent)
    }

    @Test
    fun `turning full screen off is kept`() {
        assertFalse(decodeDisplay(DisplaySettings(fullScreen = false).encode()).fullScreen)
    }
}
