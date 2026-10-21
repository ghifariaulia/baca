package com.baca.shared.render

import com.baca.shared.parser.CssLength
import com.baca.shared.parser.ImageBox
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
fun ImageBox.sizePx(
    naturalWidth: Int,
    naturalHeight: Int,
    cssPx: Float,
    fontPx: Int,
    columnPx: Int,
    pageHeightPx: Int,
): ImageSize {
    if (naturalWidth <= 0 || naturalHeight <= 0) return ImageSize(columnPx, 0)
    fun resolve(length: CssLength?, percentOf: Int?): Float? = when (length) {
        null, CssLength.Auto -> null
        is CssLength.Px -> length.px * cssPx
        is CssLength.Em -> length.em * fontPx
        is CssLength.Percent -> percentOf?.let { it * length.percent / 100f }
        is CssLength.Vh -> pageHeightPx * length.percent / 100f
        is CssLength.Vw -> columnPx * length.percent / 100f
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
    val maxH = pageHeightPx.toFloat()
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
