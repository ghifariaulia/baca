package com.baca.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baca.app.reader.decodeImageBytes
import com.baca.shared.db.BookRow
import okio.FileSystem
import okio.Path.Companion.toPath

private val fallbackPalette = listOf(
    Color(0xFF7A2F3B), // maroon
    Color(0xFF2E5E4E), // green
    Color(0xFF4E3B70), // purple
    Color(0xFF8A5A1F), // ochre
    Color(0xFF31424E), // slate blue
    Color(0xFF4F3020), // brown
)

/**
 * 2:3 book cover (TECH 10.3). A imported cover image replaces the flat-color
 * fallback with title (serif, bold) and caps author.
 */
@Composable
fun BookCover(row: BookRow, width: Dp = 104.dp, modifier: Modifier = Modifier) {
    val bitmap: ImageBitmap? = remember(row.coverPath) {
        row.coverPath?.let { runCatching { decodeImageBytes(FileSystem.SYSTEM.read(it.toPath()) { readByteArray() }) }.getOrNull() }
    }
    val height = width * 3 / 2
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = row.title,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(width, height).clip(RoundedCornerShape(6.dp)),
        )
    } else {
        val color = fallbackPalette[(row.bookHash.hashCode().let { if (it < 0) -it else it }) % fallbackPalette.size]
        Box(
            modifier
                .size(width, height)
                .clip(RoundedCornerShape(6.dp))
                .background(color)
                .padding(10.dp),
        ) {
            Text(
                row.title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart),
            )
            if (row.author.isNotBlank()) {
                Text(
                    row.author.uppercase(),
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
    }
}

/** Thin 3dp progress line under the cover label (TECH 10.3). */
@Composable
fun MiniProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(track),
    ) {
        Box(
            Modifier.fillMaxWidth(if (fraction <= 0f) 0.02f else fraction)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
    }
}
