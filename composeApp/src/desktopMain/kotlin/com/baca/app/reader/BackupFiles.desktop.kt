package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Sink
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Composable
actual fun rememberBackupSaver(onPicked: ((() -> Sink)?) -> Unit): () -> Unit = {
    val dialog = FileDialog(null as Frame?, "Save backup", FileDialog.SAVE)
    dialog.file = BACKUP_FILE_NAME
    dialog.isVisible = true
    val chosen = dialog.file?.let { name -> dialog.directory + if (name.endsWith(".zip")) name else "$name.zip" }
    onPicked(chosen?.let { path -> { FileSystem.SYSTEM.sink(path.toPath()) } })
}

@Composable
actual fun rememberBackupOpener(onPicked: (String?) -> Unit): () -> Unit = {
    val dialog = FileDialog(null as Frame?, "Restore backup", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.endsWith(".zip", ignoreCase = true) }
    dialog.isVisible = true
    onPicked(if (dialog.file != null && dialog.directory != null) dialog.directory + dialog.file else null)
}

@Composable
actual fun rememberMarkdownSaver(suggestedName: String, onPicked: ((() -> Sink)?) -> Unit): () -> Unit = {
    val dialog = FileDialog(null as Frame?, "Export notes", FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true
    val chosen = dialog.file?.let { name -> dialog.directory + if (name.endsWith(".md")) name else "$name.md" }
    onPicked(chosen?.let { path -> { FileSystem.SYSTEM.sink(path.toPath()) } })
}

@Composable
actual fun rememberPngSaver(suggestedName: String, onPicked: ((() -> Sink)?) -> Unit): () -> Unit = {
    val dialog = FileDialog(null as Frame?, "Save stats card", FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true
    val chosen = dialog.file?.let { name -> dialog.directory + if (name.endsWith(".png")) name else "$name.png" }
    onPicked(chosen?.let { path -> { FileSystem.SYSTEM.sink(path.toPath()) } })
}

actual fun encodePng(bitmap: ImageBitmap): ByteArray {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.readPixels(pixels)
    val buffered = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    buffered.setRGB(0, 0, w, h, pixels, 0, w)
    val out = ByteArrayOutputStream()
    ImageIO.write(buffered, "png", out)
    return out.toByteArray()
}
