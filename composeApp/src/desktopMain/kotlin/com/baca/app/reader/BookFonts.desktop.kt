package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

@Composable
internal actual fun fontCacheDir(): String? = null

internal actual fun platformFont(identity: String, bytes: ByteArray, weight: FontWeight, style: FontStyle, cacheDir: String?): Font? =
    androidx.compose.ui.text.platform.Font(identity, bytes, weight, style)
