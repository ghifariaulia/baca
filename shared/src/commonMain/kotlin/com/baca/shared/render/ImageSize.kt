package com.baca.shared.render

import com.baca.shared.parser.Block

import com.baca.shared.parser.CssLength
import com.baca.shared.parser.ImageBox
import com.baca.shared.parser.Room
import kotlin.math.roundToInt

/** A picture's drawn size in px. */
data class ImageSize(val widthPx: Int, val heightPx: Int)

/**
 * A picture sized as LibreraX's page sizes it: a browser takes the picture's own
 * size in CSS px, or the book's `width`/`height`, keeping its shape where only one
 * is given; foliate then holds it to the column's width (or the book's narrower
 * `max-width`) and the page's height, and draws it `object-fit: contain`.
 *
 * [cssPx] is device px per CSS px (the screen density), [fontPx] the em.
 */
/**
 * Where a picture's containing block sits in the column: px in from the left, and its
 * width, which a picture's percent sizes and its `max-width: 100%` are of.
 */
fun Block.Image.placeIn(bodyFontPx: Float, cssPx: Float, columnPx: Int, room: Room = Room.NONE): Pair<Int, Int> {
    val start = inset.toPx(bodyFontPx, cssPx, room) + (insetShare * columnPx).roundToInt()
    val end = insetEnd.toPx(bodyFontPx, cssPx, room) + (insetEndShare * columnPx).roundToInt()
    return start to (columnPx - start - end).coerceAtLeast(1)
}

fun ImageBox.sizePx(
    naturalWidth: Int,
    naturalHeight: Int,
    cssPx: Float,
    fontPx: Float,
    columnPx: Int,
    pageHeightPx: Int,
    /** the page's top (and bottom) margin: foliate holds pictures to the page less twice it */
    marginPx: Float = 0f,
    /** the reader's whole window, which a linked stylesheet's vw and vh are of */
    windowWidthPx: Int = 0,
    windowHeightPx: Int = 0,
): ImageSize {
    if (naturalWidth <= 0 || naturalHeight <= 0) return ImageSize(columnPx, 0)
    fun resolve(length: CssLength?, percentOf: Int?): Float? = when (length) {
        null, CssLength.Auto -> null
        is CssLength.Px -> length.px * cssPx
        is CssLength.Em -> length.em * fontPx
        is CssLength.Percent -> percentOf?.let { it * length.percent / 100f }
        is CssLength.Vh -> pageHeightPx * length.percent / 100f
        is CssLength.Vw -> 0f
        is CssLength.WindowVh -> windowHeightPx * length.percent / 100f
        is CssLength.WindowVw -> windowWidthPx * length.percent / 100f
    }
    val aspect = naturalWidth.toFloat() / naturalHeight
    val givenWidth = resolve(width, columnPx)
    // a percent height is of the containing block, whose height is auto: no height
    val givenHeight = resolve(height, null)
    var w: Float
    var h: Float
    when {
        givenWidth != null && givenHeight != null -> {
            w = givenWidth
            h = givenHeight
        }
        givenWidth != null -> {
            w = givenWidth
            h = givenWidth / aspect
        }
        givenHeight != null -> {
            h = givenHeight
            w = givenHeight * aspect
        }
        else -> {
            w = naturalWidth * cssPx
            h = naturalHeight * cssPx
        }
    }
    val maxW = minOf(resolve(maxWidth, columnPx) ?: Float.MAX_VALUE, columnPx.toFloat())
    val maxH = (pageHeightPx - 2 * marginPx).coerceAtLeast(1f)
    if (givenWidth != null && givenHeight != null) {
        // both set: the box is clamped and the picture contained in it
        w = minOf(w, maxW)
        h = minOf(h, maxH)
    } else {
        // a side already 0 needs no shrinking (and 0/0 would be NaN: a vh height before the page is measured)
        fun fit(limit: Float, side: Float) = if (side > 0f) limit / side else 1f
        val scale = minOf(1f, fit(maxW, w), fit(maxH, h))
        w *= scale
        h *= scale
    }
    return ImageSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
}
