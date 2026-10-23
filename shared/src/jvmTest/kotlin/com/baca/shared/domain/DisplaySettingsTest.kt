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
    fun `top and bottom margins follow the side margins until chosen, each on its own`() {
        val d = DisplaySettings(paddingPercent = 9, fullScreen = false)
        assertEquals(9 to 9, d.topMarginPercent to d.bottomMarginPercent)
        // the first step starts from what was in force
        val lowerTop = d.stepTop(-1)
        assertEquals(8 to 9, lowerTop.topMarginPercent to lowerTop.bottomMarginPercent)
        val raisedBottom = lowerTop.stepBottom(2)
        assertEquals(8 to 11, raisedBottom.topMarginPercent to raisedBottom.bottomMarginPercent)
        assertEquals(9, raisedBottom.paddingPercent)
        // once chosen they stay put when the side margins change
        assertEquals(8 to 11, raisedBottom.stepPadding(1).let { it.topMarginPercent to it.bottomMarginPercent })
        assertEquals(0, DisplaySettings(topPercent = 0).stepTop(-1).topMarginPercent)
        assertEquals(25, DisplaySettings(bottomPercent = 25).stepBottom(1).bottomMarginPercent)
    }

    @Test
    fun `with the status bar hidden the top margin is none until chosen`() {
        val d = DisplaySettings(paddingPercent = 9, fullScreen = true)
        assertEquals(0 to 9, d.topMarginPercent to d.bottomMarginPercent)
        assertEquals(1, d.stepTop(1).topMarginPercent)
        assertEquals(5, d.copy(topPercent = 5).topMarginPercent)
        // keeping the status bar, the page stays below it with the side margins' gap
        assertEquals(9, d.copy(fullScreen = false).topMarginPercent)
    }

    @Test
    fun `settings saved before separate margins existed load following the side margins`() {
        val old = decodeDisplay("""{"fontSizePercent":130,"paddingPercent":9,"fullScreen":false}""")
        assertEquals(9 to 9, old.topMarginPercent to old.bottomMarginPercent)
    }

    @Test
    fun `turning full screen off is kept`() {
        assertFalse(decodeDisplay(DisplaySettings(fullScreen = false).encode()).fullScreen)
    }
}
