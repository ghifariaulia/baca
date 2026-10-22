package com.baca.app.reader

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.roundToInt

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

/**
 * [pixels] as ARGB ints, row by row, read as LibreraX reads a picture: drawn at most
 * [PLAIN_CAP] a side in its own shape, smoothed as a canvas draws it, and every pixel of
 * that read, the border ring once.
 */
internal fun isOnPlainGround(pixels: IntArray, width: Int, height: Int): Boolean {
    val fit = minOf(1f, PLAIN_CAP.toFloat() / maxOf(width, height))
    val w = maxOf(1, (width * fit).roundToInt())
    val h = maxOf(1, (height * fit).roundToInt())
    val small = if (w == width && h == height) pixels else resampled(pixels, width, height, w, h)
    fun white(p: Int) = (p ushr 24) < 24 || (p shr 16 and 0xFF >= PLAIN_WHITE && p shr 8 and 0xFF >= PLAIN_WHITE && p and 0xFF >= PLAIN_WHITE)
    fun ink(p: Int) = (p ushr 24) >= 24 && p shr 16 and 0xFF <= PLAIN_INK && p shr 8 and 0xFF <= PLAIN_INK && p and 0xFF <= PLAIN_INK
    var all = 0
    var drawn = 0
    for (p in small) if (white(p)) all++ else if (ink(p)) drawn++
    var edge = 0
    var edges = 0
    fun ring(x: Int, y: Int) {
        edges++
        if (white(small[y * w + x])) edge++
    }
    for (x in 0 until w) {
        ring(x, 0)
        ring(x, h - 1)
    }
    for (y in 1 until h - 1) {
        ring(0, y)
        ring(w - 1, y)
    }
    val count = (w * h).toFloat()
    val share = all / count
    val between = 1f - share - drawn / count
    return share >= PLAIN_FLOOR && (edge.toFloat() / edges >= PLAIN_EDGE || share >= PLAIN_MOST || between <= PLAIN_BETWEEN)
}

/** [pixels] drawn [w] by [h] with bilinear smoothing, as a canvas's `drawImage` draws it, through premultiplied alpha. */
private fun resampled(pixels: IntArray, width: Int, height: Int, w: Int, h: Int): IntArray {
    val out = IntArray(w * h)
    val sx = width.toFloat() / w
    val sy = height.toFloat() / h
    for (y in 0 until h) for (x in 0 until w) {
        // the source point under the pixel's centre, and the four pixels round it
        val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, width - 1f)
        val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, height - 1f)
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = minOf(x0 + 1, width - 1)
        val y1 = minOf(y0 + 1, height - 1)
        val tx = fx - x0
        val ty = fy - y0
        val sum = FloatArray(4)
        for ((px, py, weight) in listOf(Triple(x0, y0, (1 - tx) * (1 - ty)), Triple(x1, y0, tx * (1 - ty)), Triple(x0, y1, (1 - tx) * ty), Triple(x1, y1, tx * ty))) {
            val p = pixels[py * width + px]
            val a = (p ushr 24) / 255f
            sum[0] += weight * a
            sum[1] += weight * a * (p shr 16 and 0xFF)
            sum[2] += weight * a * (p shr 8 and 0xFF)
            sum[3] += weight * a * (p and 0xFF)
        }
        val a = sum[0]
        fun channel(v: Float) = if (a == 0f) 0 else (v / a).roundToInt().coerceIn(0, 255)
        out[y * w + x] = ((a * 255).roundToInt() shl 24) or (channel(sum[1]) shl 16) or (channel(sum[2]) shl 8) or channel(sum[3])
    }
    return out
}

// LibreraX's readings, from its bridge.js
private const val PLAIN_CAP = 512
private const val PLAIN_WHITE = 229
private const val PLAIN_INK = 96
private const val PLAIN_FLOOR = 0.25f
private const val PLAIN_EDGE = 0.75f
private const val PLAIN_MOST = 0.45f
private const val PLAIN_BETWEEN = 0.25f
