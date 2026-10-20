package com.baca.app.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Sink
import okio.sink

@Composable
actual fun rememberBackupSaver(onPicked: ((() -> Sink)?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_MIME)) { uri: Uri? ->
        onPicked(
            uri?.let {
                {
                    checkNotNull(context.contentResolver.openOutputStream(it, "wt")) { "Can't write the backup file" }.sink()
                }
            },
        )
    }
    return remember(launcher) { { launcher.launch(BACKUP_FILE_NAME) } }
}

@Composable
actual fun rememberBackupOpener(onPicked: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.Main) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            onPicked(null)
        } else {
            // the zip reader needs a real file; content URIs have no path
            scope.launch { onPicked(withContext(Dispatchers.IO) { copyToCache(context, uri) }) }
        }
    }
    return remember(launcher) { { launcher.launch(arrayOf(ZIP_MIME)) } }
}

private const val ZIP_MIME = "application/zip"
