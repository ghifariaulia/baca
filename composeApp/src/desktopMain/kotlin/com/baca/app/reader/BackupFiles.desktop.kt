package com.baca.app.reader

import androidx.compose.runtime.Composable
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Sink
import java.awt.FileDialog
import java.awt.Frame

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
