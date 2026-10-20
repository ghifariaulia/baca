package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.baca.shared.sync.SyncTransport
import java.io.File

/**
 * Desktop folder transport (TECH 6.6 v1): a plain filesystem path chosen with
 * a directory dialog. Paths are sync-root relative with '/' separators.
 */
class FileTransport(private val root: File) : SyncTransport {
    private fun resolve(relativePath: String): File =
        File(root, relativePath.replace('\\', '/'))

    override suspend fun push(relativePath: String, bytes: ByteArray) {
        val file = resolve(relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    override suspend fun pull(relativePath: String): ByteArray? {
        val file = resolve(relativePath)
        return if (file.isFile) file.readBytes() else null
    }

    override suspend fun list(): List<String> {
        val rootPath = root.absolutePath
        val out = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.absolutePath.removePrefix(rootPath).replace('\\', '/').removePrefix("/")
            out.add(relative)
        }
        return out
    }
}

@Composable
actual fun rememberSyncTransport(folderPath: String?): SyncTransport? =
    remember(folderPath) { folderPath?.let { FileTransport(File(it)) } }

/** Sync folder label for the transport rows ("iCloud Drive / Baca" style). */
actual fun folderLabel(path: String): String = path.substringAfterLast(File.separatorChar)

/** Desktop sync folder pick: JFileChooser directories-only, returns the path. */
@Composable
actual fun rememberSyncFolderPicker(onPicked: (String?) -> Unit): () -> Unit {
    var busy by remember { mutableStateOf(false) }
    return {
        if (!busy) {
            busy = true
            try {
                val chooser = javax.swing.JFileChooser()
                chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                chooser.dialogTitle = "Choose sync folder"
                val result = chooser.showOpenDialog(null as java.awt.Frame?)
                onPicked(if (result == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath else null)
            } finally {
                busy = false
            }
        }
    }
}
