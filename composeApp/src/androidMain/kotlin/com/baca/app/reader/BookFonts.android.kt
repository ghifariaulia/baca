package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File

@Composable
internal actual fun fontCacheDir(): String? = File(LocalContext.current.cacheDir, "book-fonts").path

// Android reads a font from a file, not bytes: each goes into the cache once, named by its identity
internal actual fun platformFont(identity: String, bytes: ByteArray, weight: FontWeight, style: FontStyle, cacheDir: String?): Font? {
    val dir = File(cacheDir ?: return null).apply { mkdirs() }
    val file = File(dir, identity.hashCode().toUInt().toString(16) + ".font")
    if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
    return Font(file, weight, style)
}
