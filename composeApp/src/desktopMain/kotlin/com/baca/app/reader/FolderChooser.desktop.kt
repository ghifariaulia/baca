package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File


actual fun folderLabel(path: String): String = path.substringAfterLast(File.separatorChar)

/** Desktop folder pick: JFileChooser directories-only, returns the path. */
@Composable
actual fun rememberFolderChooser(title: String, onPicked: (String?) -> Unit): () -> Unit {
    var busy by remember { mutableStateOf(false) }
    return {
        if (!busy) {
            busy = true
            try {
                val chooser = javax.swing.JFileChooser()
                chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                chooser.dialogTitle = title
                val result = chooser.showOpenDialog(null as java.awt.Frame?)
                onPicked(if (result == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath else null)
            } finally {
                busy = false
            }
        }
    }
}
