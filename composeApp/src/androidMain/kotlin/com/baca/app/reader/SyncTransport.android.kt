package com.baca.app.reader

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.baca.shared.sync.SyncTransport
import androidx.documentfile.provider.DocumentFile

/**
 * Android folder transport (TECH 6.6 v1): SAF tree URI with a persisted
 * permission. Reads and writes go through DocumentFile/ContentResolver.
 */
class SafTransport(
    private val context: Context,
    private val treeUri: Uri,
) : SyncTransport {

    private fun root(): DocumentFile = DocumentFile.fromTreeUri(context, treeUri)!!

    private fun fileFor(relativePath: String): DocumentFile? {
        val parts = relativePath.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        var current = root()
        for (i in 0 until parts.size - 1) {
            current = current.findFile(parts[i])?.takeIf { it.isDirectory } ?: return null
        }
        return current.findFile(parts.last())?.takeIf { it.isFile }
    }

    override suspend fun push(relativePath: String, bytes: ByteArray) {
        val parts = relativePath.split('/').filter { it.isNotEmpty() }
        var dir = root()
        for (i in 0 until parts.size - 1) {
            dir = dir.findFile(parts[i])?.takeIf { it.isDirectory }
                ?: dir.createDirectory(parts[i])
                ?: return
        }
        val name = parts.last()
        val existing = dir.findFile(name)?.takeIf { it.isFile }
        if (existing != null) {
            context.contentResolver.openOutputStream(existing.uri, "wt")?.use { it.write(bytes) }
        } else {
            val created = dir.createFile("application/octet-stream", name) ?: return
            context.contentResolver.openOutputStream(created.uri, "wt")?.use { it.write(bytes) }
        }
    }

    override suspend fun pull(relativePath: String): ByteArray? {
        val file = fileFor(relativePath) ?: return null
        return context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
    }

    override suspend fun list(): List<String> {
        val out = mutableListOf<String>()
        fun walk(dir: DocumentFile, prefix: String) {
            dir.listFiles().forEach { child ->
                val path = if (prefix.isEmpty()) child.name ?: return@forEach else "$prefix/${child.name}"
                if (child.isDirectory) walk(child, path) else out.add(path)
            }
        }
        walk(root(), "")
        return out
    }
}

@Composable
actual fun rememberSyncTransport(folderPath: String?): SyncTransport? {
    val context = LocalContext.current
    return remember(folderPath, context) {
        folderPath?.let { path ->
            runCatching { SafTransport(context, Uri.parse(path)) }.getOrNull()
        }
    }
}

actual fun folderLabel(path: String): String = path.substringAfterLast('/')

/**
 * SAF folder picker; the tree URI permission is persisted so the transport
 * keeps access across app restarts (TECH 11.1).
 */
@Composable
actual fun rememberSyncFolderPicker(onPicked: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) {
            onPicked(null)
        } else {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            onPicked(uri.toString())
        }
    }
    return remember(launcher) { { launcher.launch(null) } }
}
