package com.baca.app.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale

/** A book's picture over the whole screen, to pinch and pan; a tap closes it. */
@Composable
internal fun PictureViewer(src: String, onClose: () -> Unit) {
    val bitmap = LocalBookImages.current?.image(src) ?: return
    var zoom by remember(src) { mutableFloatStateOf(1f) }
    var pan by remember(src) { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(src) { detectTapGestures(onTap = { onClose() }) },
    ) {
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = "picture",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                }
                .pointerInput(src) {
                    detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                        pan = if (zoom > 1f) pan + gesturePan else Offset.Zero
                    }
                },
        )
    }
}
