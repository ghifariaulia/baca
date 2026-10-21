package com.baca.app.reader

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Whether a picture is drawn on a white sheet (or on nothing) rather than carrying
 * a ground of its own, read as LibreraX's `onPlainGround` reads it: a picture framed
 * in white, or mostly white, or made of nothing but white and ink is a drawing on a
 * sheet; a photograph is none of these. Such a picture is multiplied into a tinted
 * page, so its white takes the page's colour.
 */
internal fun ImageBitmap.isOnPlainGround(): Boolean {
    if (width <= 0 || height <= 0) return false
    val pixels = IntArray(width * height)
    readPixels(pixels)
    return isOnPlainGround(pixels, width, height)
}

/** [pixels] as ARGB ints, row by row; read at most [PLAIN_CAP] a side, as LibreraX reads it. */
internal fun isOnPlainGround(pixels: IntArray, width: Int, height: Int): Boolean {
    val step = maxOf(1, (maxOf(width, height) + PLAIN_CAP - 1) / PLAIN_CAP)
    fun at(x: Int, y: Int) = pixels[y * width + x]
    fun white(p: Int) = (p ushr 24) < 24 || (p shr 16 and 0xFF >= PLAIN_WHITE && p shr 8 and 0xFF >= PLAIN_WHITE && p and 0xFF >= PLAIN_WHITE)
    fun ink(p: Int) = (p ushr 24) >= 24 && p shr 16 and 0xFF <= PLAIN_INK && p shr 8 and 0xFF <= PLAIN_INK && p and 0xFF <= PLAIN_INK
    var all = 0
    var drawn = 0
    var count = 0
    for (y in 0 until height step step) for (x in 0 until width step step) {
        val p = at(x, y)
        count++
        if (white(p)) all++ else if (ink(p)) drawn++
    }
    var edge = 0
    var edges = 0
    for (x in 0 until width step step) {
        edges += 2
        if (white(at(x, 0))) edge++
        if (white(at(x, height - 1))) edge++
    }
    for (y in 0 until height step step) {
        edges += 2
        if (white(at(0, y))) edge++
        if (white(at(width - 1, y))) edge++
    }
    val share = all.toFloat() / count
    val between = 1f - share - drawn.toFloat() / count
    return share >= PLAIN_FLOOR && (edge.toFloat() / edges >= PLAIN_EDGE || share >= PLAIN_MOST || between <= PLAIN_BETWEEN)
}

// LibreraX's readings, from its bridge.js
private const val PLAIN_CAP = 512
private const val PLAIN_WHITE = 229
private const val PLAIN_INK = 96
private const val PLAIN_FLOOR = 0.25f
private const val PLAIN_EDGE = 0.75f
private const val PLAIN_MOST = 0.45f
private const val PLAIN_BETWEEN = 0.25f
