package com.baca.app.reader

import androidx.compose.ui.graphics.ImageBitmap

/** Platform image decoding from raw bytes (JPEG/PNG/etc). */
expect fun decodeImageBytes(bytes: ByteArray): ImageBitmap?
