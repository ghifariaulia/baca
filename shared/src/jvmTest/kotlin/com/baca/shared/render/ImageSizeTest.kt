package com.baca.shared.render

import com.baca.shared.parser.CssLength
import com.baca.shared.parser.ImageBox
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageSizeTest {

    // a 1080px-wide phone at density 2.625: a 400dp column, an 800dp page
    private fun ImageBox.size(w: Int, h: Int) = sizePx(w, h, cssPx = 2.625f, fontPx = 50, columnPx = 1050, pageHeightPx = 2100)

    @Test
    fun `a picture without CSS keeps its own size in CSS px`() {
        assertEquals(ImageSize(315, 210), ImageBox().size(120, 80))
    }

    @Test
    fun `a picture wider than the column shrinks to it, keeping its shape`() {
        assertEquals(ImageSize(1050, 1506), ImageBox().size(1255, 1800))
    }

    @Test
    fun `a picture taller than the page shrinks to the page`() {
        assertEquals(ImageSize(700, 2100), ImageBox().size(400, 1200))
    }

    @Test
    fun `a percent width is of the column and the height follows`() {
        assertEquals(ImageSize(935, 467), ImageBox(width = CssLength.Percent(89f)).size(400, 200))
    }

    @Test
    fun `a vh height sets the size and the width follows`() {
        assertEquals(ImageSize(735, 1470), ImageBox(height = CssLength.Vh(70f), width = null).size(300, 600))
    }

    @Test
    fun `the book's max-width holds a picture narrower than the column`() {
        assertEquals(ImageSize(735, 735), ImageBox(maxWidth = CssLength.Percent(70f)).size(1000, 1000))
    }

    @Test
    fun `both sides given make a box the picture is contained in`() {
        assertEquals(ImageSize(105, 53), ImageBox(CssLength.Px(40f), CssLength.Px(20f)).size(9, 12))
    }

    @Test
    fun `a page not measured yet gives a picture no height, not a crash`() {
        val size = ImageBox(height = CssLength.Vh(95f)).sizePx(600, 900, cssPx = 1f, fontPx = 16, columnPx = 0, pageHeightPx = 0)
        assertEquals(ImageSize(1, 1), size)
    }
}
