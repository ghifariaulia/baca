package com.baca.app.reader

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.baca.app.ui.LocalAppFonts
import com.baca.shared.parser.FixedPage
import com.baca.shared.parser.FixedPageText
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Draws a pre-paginated [FixedPage] scaled to fit its container (`object-fit: contain`),
 * with background pictures from [images] and positioned text runs in the book's own
 * `@font-face` [bookFonts].
 */
@Composable
internal fun FixedPageView(
    page: FixedPage,
    images: BookImages,
    bookFonts: Map<String, FontFamily>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 512)
    val serifFallback = LocalAppFonts.current.serif
    val bitmaps = remember(page, images) {
        page.images.associate { it.src to images.preload(it.src) }
    }

    Canvas(modifier = modifier) {
        val canvasW = size.width
        val canvasH = size.height
        if (canvasW <= 0f || canvasH <= 0f || page.widthPx <= 0f || page.heightPx <= 0f) return@Canvas

        val fit = minOf(canvasW / page.widthPx, canvasH / page.heightPx)
        val pageW = page.widthPx * fit
        val pageH = page.heightPx * fit
        val originX = (canvasW - pageW) / 2f
        val originY = (canvasH - pageH) / 2f

        val bg = page.backgroundColor?.let { Color(it.toInt()) } ?: Color.White
        drawRect(color = bg, topLeft = Offset(originX, originY), size = Size(pageW, pageH))

        clipRect(left = originX, top = originY, right = originX + pageW, bottom = originY + pageH) {
            for (img in page.images) {
                val bmp = bitmaps[img.src] ?: continue
                val dstX = (originX + img.xPx * fit).roundToInt()
                val dstY = (originY + img.yPx * fit).roundToInt()
                val dstW = (img.widthPx * fit).roundToInt().coerceAtLeast(1)
                val dstH = (img.heightPx * fit).roundToInt().coerceAtLeast(1)
                drawImage(
                    image = bmp,
                    dstOffset = IntOffset(dstX, dstY),
                    dstSize = IntSize(dstW, dstH),
                )
            }

            for (run in page.texts) {
                val fontSizePx = (run.fontSizePx * fit).coerceAtLeast(1f)
                val fontSizeSp = with(density) { fontSizePx.toSp() }
                val lineHeightSp = with(density) { (fontSizePx * run.lineHeightRatio.coerceAtLeast(1f)).toSp() }
                val letterSp = with(density) { (run.letterSpacingPx * fit).toSp() }
                val fontFamily = run.family?.let { bookFonts[bookFontName(it)] } ?: serifFallback
                val textColor = run.color?.let { Color(it.toInt()) } ?: Color.Black

                val style = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = fontSizeSp,
                    lineHeight = lineHeightSp,
                    fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
                    color = textColor,
                    letterSpacing = letterSp,
                )

                val annotated = annotatedRunText(run, fit, density)
                val layout = textMeasurer.measure(
                    text = annotated,
                    style = style,
                    softWrap = false,
                    maxLines = 1,
                )

                val anchorX = originX + run.xPx * fit
                val anchorY = originY + run.bottomY * fit
                val topLeft = Offset(anchorX, anchorY - layout.size.height)

                if (run.rotationDeg != 0f || run.scaleX != 1f || run.scaleY != 1f) {
                    val pivot = Offset(anchorX, anchorY)
                    withTransform({
                        if (run.rotationDeg != 0f) rotate(degrees = run.rotationDeg, pivot = pivot)
                        if (run.scaleX != 1f || run.scaleY != 1f) scale(scaleX = run.scaleX, scaleY = run.scaleY, pivot = pivot)
                    }) {
                        drawText(textLayoutResult = layout, topLeft = topLeft)
                    }
                } else {
                    drawText(textLayoutResult = layout, topLeft = topLeft)
                }
            }
        }
    }
}

private fun annotatedRunText(
    run: FixedPageText,
    fit: Float,
    density: androidx.compose.ui.unit.Density,
): AnnotatedString {
    if (abs(run.wordSpacingPx) < 0.01f || (' ' !in run.text && '\u00A0' !in run.text)) {
        return AnnotatedString(run.text)
    }
    val spaceLetterSp = with(density) { ((run.letterSpacingPx + run.wordSpacingPx) * fit).toSp() }
    val spaceStyle = SpanStyle(letterSpacing = spaceLetterSp)
    return buildAnnotatedString {
        append(run.text)
        run.text.forEachIndexed { i, ch ->
            if (ch == ' ' || ch == '\u00A0') {
                addStyle(spaceStyle, i, i + 1)
            }
        }
    }
}
