package com.baca.app.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * The room [startPx] and [endPx] in from the sides, either of which may be negative: a
 * negative margin pulls a block out past its column's edge, which padding can't do.
 */
internal fun Modifier.insetBy(startPx: Int, endPx: Int): Modifier = layout { measurable, constraints ->
    val width = (constraints.maxWidth - startPx - endPx).coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(startPx, 0) }
}

/**
 * The page's content laid out turned a quarter: it is measured as wide as the page is tall and as
 * tall as it is wide, then drawn rotated 90° clockwise, so lines that run left to right and stack
 * downward come out running down the page and stacking from the right, as vertical text does.
 */
internal fun Modifier.rotatedPage(): Modifier = layout { measurable, constraints ->
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(h, w))
    layout(w, h) { placeable.placeWithLayer((w - h) / 2, (h - w) / 2) { rotationZ = 90f } }
}
