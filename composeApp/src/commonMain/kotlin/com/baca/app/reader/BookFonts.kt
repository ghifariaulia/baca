package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.baca.shared.domain.UserFont
import com.baca.shared.domain.addUserFontEntry
import com.baca.shared.domain.loadUserFonts
import com.baca.shared.domain.removeUserFontEntry
import com.baca.shared.domain.saveUserFonts
import com.baca.shared.domain.USER_FONT_PREFIX
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.parser.BookFont
import com.baca.shared.parser.EpubImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer

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

internal fun userFontsDir(storeDir: String): Path {
    val base = storeDir.toPath()
    return (base.parent ?: base) / "fonts"
}

internal fun importUserFontFile(pickedPath: String, storeDir: String, store: PositionStore): List<UserFont> {
    val src = pickedPath.toPath()
    val rawName = src.name
    val ext = rawName.substringAfterLast('.', "").lowercase()
    if (ext != "ttf" && ext != "otf") {
        discardPickedCopy(pickedPath)
        return store.loadUserFonts()
    }
    val bytes = runCatching { FileSystem.SYSTEM.source(src).buffer().use { it.readByteArray() } }.getOrNull()
    discardPickedCopy(pickedPath)
    if (bytes == null || bytes.isEmpty()) return store.loadUserFonts()

    val dir = userFontsDir(storeDir)
    FileSystem.SYSTEM.createDirectories(dir)
    val id = "${System.currentTimeMillis()}"
    val fileName = "$id.$ext"
    FileSystem.SYSTEM.sink(dir / fileName).buffer().use { it.write(bytes) }

    val displayName = rawName.substringBeforeLast('.').ifBlank { "Custom font" }
    val updated = addUserFontEntry(store.loadUserFonts(), id = id, name = displayName, fileName = fileName)
    store.saveUserFonts(updated)
    return updated
}

internal fun deleteUserFontFile(id: String, storeDir: String, store: PositionStore): List<UserFont> {
    val current = store.loadUserFonts()
    val target = current.firstOrNull { it.id == id }
    if (target != null) {
        runCatching { FileSystem.SYSTEM.delete(userFontsDir(storeDir) / target.fileName) }
    }
    val updated = removeUserFontEntry(current, id)
    store.saveUserFonts(updated)
    return updated
}

@Composable
internal fun rememberUserFonts(storeDir: String, userFonts: List<UserFont>): State<Map<String, FontFamily>?> {
    val cacheDir = fontCacheDir()
    return produceState<Map<String, FontFamily>?>(if (userFonts.isEmpty()) emptyMap() else null, storeDir, userFonts) {
        if (userFonts.isEmpty()) {
            value = emptyMap()
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val dir = userFontsDir(storeDir)
            userFonts.mapNotNull { uf ->
                val bytes = runCatching { FileSystem.SYSTEM.source(dir / uf.fileName).buffer().use { it.readByteArray() } }.getOrNull()
                    ?: return@mapNotNull null
                val loaded = platformFont("user#${uf.id}#${uf.fileName}", bytes, FontWeight.Normal, FontStyle.Normal, cacheDir)
                    ?: return@mapNotNull null
                "$USER_FONT_PREFIX${uf.id}" to FontFamily(loaded)
            }.toMap()
        }
    }
}
