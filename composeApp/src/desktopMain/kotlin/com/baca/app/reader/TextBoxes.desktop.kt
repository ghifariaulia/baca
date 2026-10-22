package com.baca.app.reader

import androidx.compose.ui.text.TextLayoutResult

// Skia's fillBoundingBoxes leaves every box empty; one character at a time does work
internal actual fun characterBoxes(layout: TextLayoutResult, length: Int): FloatArray =
    FloatArray(length * 4).also { boxes ->
        for (i in 0 until length) {
            val box = layout.getBoundingBox(i)
            boxes[i * 4] = box.left
            boxes[i * 4 + 1] = box.top
            boxes[i * 4 + 2] = box.right
            boxes[i * 4 + 3] = box.bottom
        }
    }
