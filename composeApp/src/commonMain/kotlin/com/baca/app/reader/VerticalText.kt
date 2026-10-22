package com.baca.app.reader

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity

/**
 * One line of vertical text, drawn in the page's turned frame (see [rotatedPage]), where the line runs
 * left to right: each character of an upright script (CJK, kana, fullwidth forms) is turned back to stand
 * upright, in its vertical form where the font has one (`vert`: 、。 in the corner, brackets turned, the
 * long vowel mark upright); runs of other text (Latin, digits) stay turned, lying sideways down the line.
 */
@Composable
internal fun VerticalLineText(text: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val fontPx = with(LocalDensity.current) { style.fontSize.toPx() }
    Canvas(modifier) {
        val middle = size.height / 2
        var x = 0f
        var i = 0
        while (i < text.length) {
            val upright = isUpright(text[i])
            var j = i + 1
            if (upright) {
                // a surrogate pair is one character
                if (text[i].isHighSurrogate() && j < text.length) j++
            } else {
                while (j < text.length && !isUpright(text[j])) j++
            }
            val piece = text.subSequence(i, j).let { p ->
                if (!upright) p else buildAnnotatedString {
                    append(p)
                    addStyle(SpanStyle(fontFeatureSettings = "vert"), 0, p.length)
                }
            }
            val layout = measurer.measure(piece, style, softWrap = false, maxLines = 1)
            val width = layout.size.width.toFloat()
            // the centre of the glyphs' em box, which sits on the line's middle
            val ink = layout.firstBaseline - INK_CENTER * fontPx
            val top = Offset(x, middle - ink)
            if (upright) rotate(-90f, pivot = Offset(x + width / 2, middle)) { drawText(layout, topLeft = top) }
            else drawText(layout, topLeft = top)
            x += width
            i = j
        }
    }
}

// where the middle of a glyph's em box lies above its baseline, in em: the CJK fonts' 0.88 ascent and 0.12 descent
private const val INK_CENTER = 0.38f

/** Whether [c] stands upright in vertical text: ideographs, kana, Hangul, CJK and fullwidth punctuation and forms. */
internal fun isUpright(c: Char): Boolean = when (c) {
    in '⺀'..'鿿', in '가'..'힯', in '豈'..'﫿', in '︰'..'﹏', in '＀'..'￯' -> true
    in '‘'..'‟', '‥', '…', '—', '―' -> true
    else -> c.isHighSurrogate() || c.isLowSurrogate()
}
