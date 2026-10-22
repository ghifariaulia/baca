package com.baca.app.reader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.baca.shared.parser.BlockBox
import com.baca.shared.render.PageBox
import kotlin.test.Test
import kotlin.test.assertEquals

class BoxDrawTest {

    private val rounded = BlockBox(id = 0, border = listOf(2f, 2f, 2f, 2f), radiusPx = 15f)

    private fun draw(startsHere: Boolean, endsHere: Boolean) = ImageBitmap(100, 100).also { image ->
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(100f, 100f)) {
            drawBox(PageBox(rounded, 0, 100, 0, 100, startsHere, endsHere), Color.Black, dark = false)
        }
    }.toPixelMap()

    @Test
    fun `a rounded box split over pages keeps its rounded top on the first page`() {
        val first = draw(startsHere = true, endsHere = false)
        assertEquals(Color.Transparent, first[1, 1], "top-left corner is rounded off")
        assertEquals(Color.Black, first[1, 99], "the left side runs on to the page's foot")
        assertEquals(Color.Transparent, first[50, 99], "no bottom border where the box goes on")
    }

    @Test
    fun `a rounded box split over pages keeps its rounded bottom on the last page`() {
        val last = draw(startsHere = false, endsHere = true)
        assertEquals(Color.Black, last[1, 0], "the left side comes in from the page's head")
        assertEquals(Color.Transparent, last[50, 0], "no top border where the box came from")
        assertEquals(Color.Transparent, last[1, 98], "bottom-left corner is rounded off")
    }
}
