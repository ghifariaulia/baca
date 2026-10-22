package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.baca.shared.parser.BookFont
import com.baca.shared.parser.EpubImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

/**
 * The fonts a book ships (`@font-face`), each family by [bookFontName], read from the book
 * off the main thread; null until they're read, so pages aren't measured without them. A
 * file that can't be read leaves its family out: that text falls back to its face.
 */
@Composable
internal fun rememberBookFonts(bookPath: String, fonts: List<BookFont>): State<Map<String, FontFamily>?> {
    val cacheDir = fontCacheDir()
    return produceState<Map<String, FontFamily>?>(if (fonts.isEmpty()) emptyMap() else null, bookPath, fonts) {
        if (fonts.isEmpty()) return@produceState
        value = withContext(Dispatchers.IO) {
            val book = EpubImages(bookPath.toPath())
            fonts.mapNotNull { font ->
                val bytes = book.imageBytes(font.path) ?: return@mapNotNull null
                val style = if (font.italic) FontStyle.Italic else FontStyle.Normal
                platformFont("$bookPath#${font.path}", bytes, FontWeight(font.weight), style, cacheDir)?.let { font.family to it }
            }.groupBy({ it.first }, { it.second }).mapKeys { bookFontName(it.key) }.mapValues { FontFamily(it.value) }
        }
    }
}

/** Where a platform that reads fonts from files keeps them; null where fonts load from bytes. */
@Composable
internal expect fun fontCacheDir(): String?

/** A font from a file's bytes, [identity] naming it uniquely. Blocking: call off the main thread. */
internal expect fun platformFont(identity: String, bytes: ByteArray, weight: FontWeight, style: FontStyle, cacheDir: String?): Font?
