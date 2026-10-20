package com.baca.app.reader

import android.content.Context
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
import java.io.File

/**
 * SAF multi-document picker. Picked content URIs have no filesystem path, so
 * each file is copied into the app cache and those paths are handed to the
 * caller; [BookImporter] then copies them into permanent storage by hash.
 */
@Composable
actual fun rememberBookPicker(onPicked: (List<String>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.Main) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) {
            onPicked(emptyList())
        } else {
            scope.launch {
                val paths = withContext(Dispatchers.IO) { uris.mapNotNull { copyToCache(context, it) } }
                onPicked(paths)
            }
        }
    }
    return remember(launcher) {
        { launcher.launch(BookFormats.pickMimeTypes) }
    }
}

@Composable
actual fun rememberFolderPicker(onPicked: (List<String>) -> Unit): () -> Unit =
    // SAF tree enumeration needs DocumentFile; ships with step 11 (sync folder).
    remember { { onPicked(emptyList()) } }

internal fun copyToCache(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val ext = guessExt(context, uri)
        val file = File(context.cacheDir, "open-${System.nanoTime()}.$ext")
        file.outputStream().use { output -> input.copyTo(output) }
        file.absolutePath
    }
}.getOrNull()

private fun guessExt(context: Context, uri: Uri): String {
    val name = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst() && cursor.getString(idx) != null) {
                val n = cursor.getString(idx)
                n.substringAfterLast('.', "").lowercase()
            } else null
        }
    }.getOrNull()
    if (name != null && name.length in 1..5) return name
    return when (context.contentResolver.getType(uri)) {
        "application/epub+zip" -> "epub"
        "application/zip" -> "fb2.zip"
        "text/plain" -> "txt"
        "application/xml", "text/xml" -> "fb2"
        else -> "epub"
    }
}
