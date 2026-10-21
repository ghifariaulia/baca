package com.baca.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.baca.shared.domain.reader.SelectionPoint
import com.baca.shared.domain.reader.TextSelection
import kotlin.math.abs
import kotlin.math.roundToInt

/** One drawn line of the page: its text range and where its laid-out text sits in the root. */
internal class LineGeometry(
    val blockIndex: Int,
    val start: Int,
    val endExclusive: Int,
    val layout: TextLayoutResult,
    val topLeft: Offset,
) {
    val top: Float get() = topLeft.y
    val bottom: Float get() = topLeft.y + layout.size.height

    /** The x (root) where [charOffset] of the block falls on this line. */
    fun xOf(charOffset: Int): Float =
        topLeft.x + layout.getHorizontalPosition((charOffset - start).coerceIn(0, layout.layoutInput.text.length), usePrimaryDirection = true)
}

internal const val SELECTION_HANDLE_TAG = "selection-handle"

/**
 * The selection's two handles and its toolbar, over the page. A handle drags
 * its own end of the selection through the text on this page; a tap elsewhere
 * ends the selection. [geometry] is filled by the drawn lines.
 */
@Composable
internal fun SelectionOverlay(
    selection: TextSelection,
    geometry: Map<Pair<Int, Int>, LineGeometry>,
    onChange: (TextSelection) -> Unit,
    onDismiss: () -> Unit,
    toolbar: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val current by rememberUpdatedState(selection)
    var origin by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val handleRadius = with(density) { 10.dp.toPx() }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        val widthPx = constraints.maxWidth
        val accent = MaterialTheme.colorScheme.primary
        // the anchor handle drags the anchor, the focus handle the focus, so a
        // handle keeps its end even when the two cross
        listOf(true, false).forEach { isAnchor ->
            val point = if (isAnchor) selection.anchor else selection.focus
            val isEnd = point == selection.end && !selection.isEmpty
            val line = lineAt(geometry.values, point, isEnd) ?: return@forEach
            val x = line.xOf(point.charOffset) - origin.x
            val y = line.bottom - origin.y
            Box(
                Modifier
                    // the start handle hangs left of its point, the end handle right, so short selections keep both reachable
                    .offset { IntOffset((if (isEnd) x else x - 2 * handleRadius).roundToInt(), y.roundToInt()) }
                    .size(20.dp)
                    .background(accent, CircleShape)
                    .testTag(SELECTION_HANDLE_TAG)
                    .pointerInput(isAnchor) {
                        // follow the text the handle points at, not the finger below it
                        var target = Offset.Zero
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                val p = if (isAnchor) current.anchor else current.focus
                                val l = lineAt(geometry.values, p, p == current.end) ?: return@detectDragGestures
                                target = Offset(l.xOf(p.charOffset), (l.top + l.bottom) / 2)
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, drag ->
                            change.consume()
                            target += drag
                            val hit = pointAt(geometry.values, target) ?: return@detectDragGestures
                            onChange(if (isAnchor) current.copy(anchor = hit) else current.copy(focus = hit))
                        }
                    },
            )
        }
        if (!dragging) {
            val first = lineAt(geometry.values, selection.start, isEnd = false)
            val last = lineAt(geometry.values, selection.end, isEnd = true)
            var toolbarWidth by remember { mutableIntStateOf(0) }
            val gap = with(density) { 12.dp.toPx() }
            val toolbarHeight = with(density) { 48.dp.toPx() }
            if (first != null) {
                // above the selection, or below its end handle when the page top is too close
                val above = first.top - origin.y - gap - toolbarHeight
                val y = if (above >= 0f || last == null) above.coerceAtLeast(0f) else last.bottom - origin.y + 2 * handleRadius + gap
                val x = (first.xOf(selection.start.charOffset) - origin.x - toolbarWidth / 2f)
                    .coerceIn(0f, (widthPx - toolbarWidth).coerceAtLeast(0).toFloat())
                toolbar(
                    Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .onSizeChanged { toolbarWidth = it.width },
                )
            }
        }
    }
}

/** The drawn line holding [point]; for a selection's end, the line it ends on rather than the next one's start. */
private fun lineAt(lines: Collection<LineGeometry>, point: SelectionPoint, isEnd: Boolean): LineGeometry? =
    lines.filter { it.blockIndex == point.blockIndex }.firstOrNull { line ->
        if (isEnd) point.charOffset > line.start && point.charOffset <= line.endExclusive
        else point.charOffset >= line.start && point.charOffset < line.endExclusive
    } ?: lines.filter { it.blockIndex == point.blockIndex }.minByOrNull { abs(it.start - point.charOffset) }

/** The text position under [root]: the line at that height (else the nearest), then the char there. */
private fun pointAt(lines: Collection<LineGeometry>, root: Offset): SelectionPoint? {
    val line = lines.firstOrNull { root.y >= it.top && root.y < it.bottom }
        ?: lines.minByOrNull { minOf(abs(root.y - it.top), abs(root.y - it.bottom)) }
        ?: return null
    val local = Offset(root.x - line.topLeft.x, line.layout.size.height / 2f)
    val offset = line.layout.getOffsetForPosition(local)
    return SelectionPoint(line.blockIndex, (line.start + offset).coerceIn(line.start, line.endExclusive))
}
