package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import com.baca.shared.parser.EpubImages
import okio.Path.Companion.toPath

/** Decodes EPUB image bytes into [ImageBitmap] with a per-book cache. */
class BookImages(private val loader: EpubImages) {
    private val cache = mutableMapOf<String, ImageBitmap?>()

    /** Decode-once cache; called from layout (main thread) and composition. */
    fun preload(zipHref: String): ImageBitmap? {
        if (zipHref.isBlank()) return null
        return cache.getOrPut(zipHref) { loader.imageBytes(zipHref)?.let { decodeImageBytes(it) } }
    }

    @Composable
    fun image(zipHref: String): ImageBitmap? = remember(zipHref) { preload(zipHref) }
}

@Composable
fun rememberBookImages(bookPath: String): BookImages = remember(bookPath) {
    BookImages(EpubImages(bookPath.toPath()))
}
