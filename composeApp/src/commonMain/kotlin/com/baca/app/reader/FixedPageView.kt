package com.baca.app.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.baca.app.ui.LocalAppFonts
import com.baca.shared.domain.AnnotationEntry
import com.baca.shared.domain.reader.TextSelection
import com.baca.shared.parser.FixedPage
import com.baca.shared.parser.FixedPageText
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Draws a pre-paginated [FixedPage] scaled to fit its container (`object-fit: contain`),
 * with background pictures from [images] and positioned text runs in the book's own
 * `@font-face` [bookFonts]. Reports each run's [TextLayoutResult] via [onPlaced] so text
 * selection handles, highlights, search matches and read-aloud tinting work natively.
 */
@Composable
internal fun FixedPageView(
    page: FixedPage,
    images: BookImages,
    bookFonts: Map<String, FontFamily>,
    modifier: Modifier = Modifier,
    chapterHref: String = "",
    bookAnnotations: List<AnnotationEntry> = emptyList(),
    activeRange: TextSelection? = null,
    selectionTint: Color = Color.Unspecified,
    pageBackground: Color? = null,
    pageInk: Color? = null,
    darkTheme: Boolean = false,
    onPlaced: (blockIndex: Int, start: Int, endExclusive: Int, layout: TextLayoutResult, topLeftInRoot: Offset) -> Unit = { _, _, _, _, _ -> },
    onTapAnnotation: (AnnotationEntry) -> Unit = {},
    onLongPress: (blockIndex: Int, charOffset: Int) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 512)
    val serifFallback = LocalAppFonts.current.serif
    val bitmaps = remember(page, images) {
        page.images.associate { it.src to images.preload(it.src) }
    }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val zoom = remember(page) { mutableFloatStateOf(1f) }
    val pan = remember(page) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(page) {
        zoom.floatValue = 1f
        pan.value = Offset.Zero
    }

    BoxWithConstraints(
        modifier = modifier.onGloballyPositioned { rootOrigin = it.positionInRoot() },
    ) {
        val canvasW = constraints.maxWidth.toFloat()
        val canvasH = constraints.maxHeight.toFloat()
        if (canvasW <= 0f || canvasH <= 0f || page.widthPx <= 0f || page.heightPx <= 0f) return@BoxWithConstraints

        val fit = minOf(canvasW / page.widthPx, canvasH / page.heightPx)
        val pageW = page.widthPx * fit
        val pageH = page.heightPx * fit
        val originX = (canvasW - pageW) / 2f
        val originY = (canvasH - pageH) / 2f

        val measuredRuns = remember(
            page, fit, originX, originY, bookFonts, serifFallback, density,
            chapterHref, bookAnnotations, activeRange, selectionTint, pageInk, darkTheme,
        ) {
            page.texts.map { run ->
                val fontSizePx = (run.fontSizePx * fit).coerceAtLeast(1f)
                val fontSizeSp = with(density) { fontSizePx.toSp() }
                val lineHeightSp = with(density) { (fontSizePx * run.lineHeightRatio.coerceAtLeast(1f)).toSp() }
                val letterSp = with(density) { (run.letterSpacingPx * fit).toSp() }
                val fontFamily = run.family?.let { bookFonts[bookFontName(it)] } ?: serifFallback
                val textColor = when {
                    darkTheme && pageInk != null -> pageInk
                    run.color != null -> Color(run.color!!.toInt())
                    pageInk != null -> pageInk
                    else -> Color.Black
                }
                val style = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = fontSizeSp,
                    lineHeight = lineHeightSp,
                    fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
                    color = textColor,
                    letterSpacing = letterSp,
                )
                val ranges = if (chapterHref.isNotEmpty()) {
                    fixedHighlightRangesOf(bookAnnotations, chapterHref, run.blockIndex, run.startOffset, run.endOffset)
                } else {
                    emptyList()
                }
                val blockLen = page.blocks.getOrNull(run.blockIndex)?.text?.length ?: run.endOffset
                val selected = activeRange?.let { sel ->
                    if (sel.isEmpty) null
                    else {
                        val s = sel.start
                        val e = sel.end
                        if (run.blockIndex < s.blockIndex || run.blockIndex > e.blockIndex) null
                        else {
                            val from = if (run.blockIndex == s.blockIndex) s.charOffset else 0
                            val to = if (run.blockIndex == e.blockIndex) e.charOffset else blockLen
                            (maxOf(from, run.startOffset) until minOf(to, run.endOffset)).takeIf { !it.isEmpty() }
                        }
                    }
                }
                val annotated = annotatedRunText(run, fit, density, ranges, selected, selectionTint)
                val layout = textMeasurer.measure(
                    text = annotated,
                    style = style,
                    softWrap = false,
                    maxLines = 1,
                )
                val anchorX = originX + run.xPx * fit
                val anchorY = originY + run.bottomY * fit
                val topLeft = Offset(anchorX, anchorY - layout.size.height)
                val rect = Rect(topLeft, Size(layout.size.width * run.scaleX, layout.size.height.toFloat()))
                MeasuredFixedRun(run, ranges, layout, anchorX, anchorY, topLeft, rect)
            }
        }

        SideEffect {
            for (m in measuredRuns) {
                onPlaced(m.run.blockIndex, m.run.startOffset, m.run.endOffset, m.layout, rootOrigin + m.topLeft)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.floatValue
                    scaleY = zoom.floatValue
                    translationX = pan.value.x
                    translationY = pan.value.y
                }
                .pinchZoomAndPan(zoom, pan)
                .pointerInput(measuredRuns) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation()
                        }
                        if (up == null) {
                            val hit = hitTestFixedRun(measuredRuns, down.position, maxDistPx = 32f)
                            if (hit != null) {
                                val localX = ((down.position.x - hit.topLeft.x) / hit.run.scaleX.coerceAtLeast(0.1f))
                                    .coerceIn(0f, hit.layout.size.width.toFloat())
                                val offset = hit.layout.getOffsetForPosition(Offset(localX, hit.layout.size.height / 2f))
                                val at = (hit.run.startOffset + offset)
                                    .coerceIn(hit.run.startOffset, (hit.run.endOffset - 1).coerceAtLeast(hit.run.startOffset))
                                onLongPress(hit.run.blockIndex, at)
                            }
                            return@awaitEachGesture
                        }
                        if ((up.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
                            val hit = hitTestFixedRun(measuredRuns, up.position, maxDistPx = 6f)
                            if (hit != null && hit.ranges.isNotEmpty()) {
                                val localX = ((up.position.x - hit.topLeft.x) / hit.run.scaleX.coerceAtLeast(0.1f))
                                    .coerceIn(0f, hit.layout.size.width.toFloat())
                                val offset = hit.layout.getOffsetForPosition(Offset(localX, hit.layout.size.height / 2f))
                                hit.ranges.firstOrNull { offset >= it.start && offset < it.end }?.let { r ->
                                    onTapAnnotation(r.entry)
                                }
                            }
                        }
                    }
                },
        ) {
            val bg = when {
                pageBackground != null && (page.backgroundColor == null || page.backgroundColor == 0xFFFFFFFFL) -> pageBackground
                page.backgroundColor != null -> Color(page.backgroundColor!!.toInt())
                else -> Color.White
            }
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
                        alpha = if (darkTheme) 0.8f else 1f,
                    )
                }

                for (m in measuredRuns) {
                    val run = m.run
                    if (run.rotationDeg != 0f || run.scaleX != 1f || run.scaleY != 1f) {
                        val pivot = Offset(m.anchorX, m.anchorY)
                        withTransform({
                            if (run.rotationDeg != 0f) rotate(degrees = run.rotationDeg, pivot = pivot)
                            if (run.scaleX != 1f || run.scaleY != 1f) scale(scaleX = run.scaleX, scaleY = run.scaleY, pivot = pivot)
                        }) {
                            drawText(textLayoutResult = m.layout, topLeft = m.topLeft)
                        }
                    } else {
                        drawText(textLayoutResult = m.layout, topLeft = m.topLeft)
                    }
                }
            }
        }
    }
}

private data class FixedHighlightRange(val start: Int, val end: Int, val colorName: String, val entry: AnnotationEntry)

private class MeasuredFixedRun(
    val run: FixedPageText,
    val ranges: List<FixedHighlightRange>,
    val layout: TextLayoutResult,
    val anchorX: Float,
    val anchorY: Float,
    val topLeft: Offset,
    val rect: Rect,
)

private fun hitTestFixedRun(runs: List<MeasuredFixedRun>, pos: Offset, maxDistPx: Float): MeasuredFixedRun? {
    runs.firstOrNull { pos in it.rect }?.let { return it }
    return runs.minByOrNull { m ->
        val dx = when {
            pos.x < m.rect.left -> m.rect.left - pos.x
            pos.x > m.rect.right -> pos.x - m.rect.right
            else -> 0f
        }
        val dy = when {
            pos.y < m.rect.top -> m.rect.top - pos.y
            pos.y > m.rect.bottom -> pos.y - m.rect.bottom
            else -> 0f
        }
        hypot(dx, dy)
    }?.takeIf { m ->
        val dx = maxOf(0f, m.rect.left - pos.x, pos.x - m.rect.right)
        val dy = maxOf(0f, m.rect.top - pos.y, pos.y - m.rect.bottom)
        hypot(dx, dy) <= maxDistPx
    }
}

private fun fixedHighlightRangesOf(
    annotations: List<AnnotationEntry>,
    chapterHref: String,
    blockIndex: Int,
    runStart: Int,
    runEnd: Int,
): List<FixedHighlightRange> {
    val out = mutableListOf<FixedHighlightRange>()
    for (ann in annotations) {
        val anchor = ann.reflowAnchor ?: continue
        if (anchor.href != chapterHref) continue
        if (anchor.start.blockIndex > blockIndex || anchor.end.blockIndex < blockIndex) continue
        val start = if (anchor.start.blockIndex == blockIndex) anchor.start.charOffset else 0
        val end = if (anchor.end.blockIndex == blockIndex) anchor.end.charOffset else Int.MAX_VALUE
        val s = maxOf(start, runStart)
        val e = minOf(end, runEnd)
        if (e > s) out.add(FixedHighlightRange(s - runStart, e - runStart, ann.color, ann))
    }
    return out
}

private fun annotatedRunText(
    run: FixedPageText,
    fit: Float,
    density: Density,
    ranges: List<FixedHighlightRange>,
    selected: IntRange?,
    selectionTint: Color,
): AnnotatedString {
    val hasWordSpacing = abs(run.wordSpacingPx) >= 0.01f && (' ' in run.text || '\u00A0' in run.text)
    if (!hasWordSpacing && ranges.isEmpty() && selected == null) {
        return AnnotatedString(run.text)
    }
    val spaceLetterSp = if (hasWordSpacing) with(density) { ((run.letterSpacingPx + run.wordSpacingPx) * fit).toSp() } else null
    return buildAnnotatedString {
        append(run.text)
        if (spaceLetterSp != null) {
            val spaceStyle = SpanStyle(letterSpacing = spaceLetterSp)
            run.text.forEachIndexed { i, ch ->
                if (ch == ' ' || ch == '\u00A0') addStyle(spaceStyle, i, i + 1)
            }
        }
        for (r in ranges) {
            addStyle(
                SpanStyle(background = HighlightColors.of(r.colorName)),
                r.start.coerceIn(0, run.text.length),
                r.end.coerceIn(0, run.text.length),
            )
        }
        selected?.let { sel ->
            val s = (sel.first - run.startOffset).coerceIn(0, run.text.length)
            val e = (sel.last + 1 - run.startOffset).coerceIn(0, run.text.length)
            if (e > s) addStyle(SpanStyle(background = selectionTint), s, e)
        }
    }
}

/**
 * Pinch-zoom and pan that only consumes pointer events when 2+ fingers are down or the page
 * is already zoomed in ([zoom] > 1f), leaving 1-finger horizontal swipes free to turn pages.
 */
internal fun Modifier.pinchZoomAndPan(
    zoom: MutableFloatState,
    pan: MutableState<Offset>,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2 || zoom.floatValue > 1f) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (zoomChange != 1f || panChange != Offset.Zero) {
                    val newZoom = (zoom.floatValue * zoomChange).coerceIn(1f, 6f)
                    zoom.floatValue = newZoom
                    pan.value = if (newZoom > 1f) pan.value + panChange else Offset.Zero
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
