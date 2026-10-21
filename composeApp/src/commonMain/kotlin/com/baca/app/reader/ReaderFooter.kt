package com.baca.app.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Height the page gives up to the footer, so text never runs under it. */
internal val READER_FOOTER_HEIGHT = 34.dp

/**
 * Always-on status line under the page, in the reading theme (LibreraX's
 * footer): book progress with a tick where each chapter starts, pages left
 * in the chapter, the time, the battery and the book page.
 */
@Composable
internal fun ReaderFooter(
    fraction: Float,
    chapterStarts: List<Float>,
    chapterPagesLeft: Int,
    page: Int,
    totalPages: Int,
    ink: Color,
    horizontalMargin: Dp,
    modifier: Modifier = Modifier,
) {
    val faint = ink.copy(alpha = 0.45f)
    val text = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, color = faint)
    // read when the page changes; a page turn is often enough for a clock
    val clock = remember(page) { clockText() }
    val battery = remember(page) { batteryPercent() }
    Column(modifier.fillMaxWidth().height(READER_FOOTER_HEIGHT).padding(horizontal = horizontalMargin)) {
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val stroke = 1.dp.toPx()
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(faint, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = radius, alpha = 0.5f)
            chapterStarts.forEach { start ->
                val x = size.width * start
                drawLine(faint, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
            }
            drawRoundRect(faint, cornerRadius = radius, style = Stroke(stroke))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Pages left: $chapterPagesLeft", style = text, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(clock, style = text)
                battery?.let { BatteryBadge(it, text, faint) }
                Text("$page / $totalPages", style = text)
            }
        }
    }
}

@Composable
private fun BatteryBadge(percent: Int, style: TextStyle, color: Color) {
    Box(
        Modifier.border(1.dp, color, RoundedCornerShape(2.dp)).padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$percent", style = style.copy(fontSize = 9.sp, lineHeight = 12.sp))
    }
}
