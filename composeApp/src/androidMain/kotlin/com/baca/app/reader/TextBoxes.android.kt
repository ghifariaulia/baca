package com.baca.app.reader

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange

// in one pass: a box per character from getBoundingBox would measure the line again each time
internal actual fun characterBoxes(layout: TextLayoutResult, length: Int): FloatArray =
    layout.multiParagraph.fillBoundingBoxes(TextRange(0, length), FloatArray(length * 4), 0)
