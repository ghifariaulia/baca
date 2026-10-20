package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

/**
 * AWT FileDialog blocks until the user picks or cancels; it runs its own event
 * pump, so calling from a Compose desktop click handler (EDT) is safe.
 * Desktop picks one file (multi-select needs the JFileChooser path later);
 * folder pick returns the supported files directly inside the chosen folder.
 */
@Composable
actual fun rememberBookPicker(onPicked: (List<String>) -> Unit): () -> Unit {
    var busy by remember { mutableStateOf(false) }
    return {
        if (!busy) {
            busy = true
            try {
                val dialog = FileDialog(null as Frame?, "Add books", FileDialog.LOAD)
                dialog.isVisible = true
                val file = dialog.file
                val dir = dialog.directory
                onPicked(if (file != null && dir != null) listOf(dir + file) else emptyList())
            } finally {
                busy = false
            }
        }
    }
}

@Composable
actual fun rememberFolderPicker(onPicked: (List<String>) -> Unit): () -> Unit {
    var busy by remember { mutableStateOf(false) }
    return {
        if (!busy) {
            busy = true
            try {
                val chooser = JFileChooser()
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.isMultiSelectionEnabled = false
                chooser.dialogTitle = "Add a folder of books"
                val result = chooser.showOpenDialog(null as Frame?)
                val dir = if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
                val books = dir?.listFiles()
                    ?.filter { it.isFile && BookFormats.of(it.name) != null }
                    ?.map { it.absolutePath }
                onPicked(books.orEmpty())
            } finally {
                busy = false
            }
        }
    }
}
