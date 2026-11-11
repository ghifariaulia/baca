package com.baca.app.reader

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Readable full path: the tree URI is percent-encoded and its volume reads `primary:` for internal storage. */
actual fun folderLabel(path: String): String {
    val treeId = Uri.decode(path).substringAfter("/tree/").substringBefore("/document/")
    val volume = treeId.substringBefore(':')
    val rest = treeId.substringAfter(':', "")
    val root = if (volume == "primary") "Internal storage" else volume
    return if (rest.isEmpty()) root else "$root/$rest"
}

/** SAF folder picker; the tree URI permission is persisted so access survives app restarts. */
@Composable
actual fun rememberFolderChooser(title: String, onPicked: (String?) -> Unit): () -> Unit {
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
