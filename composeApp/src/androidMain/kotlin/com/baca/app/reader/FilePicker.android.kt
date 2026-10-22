package com.baca.app.reader

import androidx.documentfile.provider.DocumentFile

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
actual fun rememberFolderPicker(onPicked: (List<String>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.Main) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree == null) {
            onPicked(emptyList())
        } else {
            scope.launch {
                val paths = withContext(Dispatchers.IO) {
                    DocumentFile.fromTreeUri(context, tree)?.listFiles().orEmpty()
                        .filter { it.isFile && it.name?.let(BookFormats::of) != null }
                        .mapNotNull { copyToCache(context, it.uri) }
                }
                onPicked(paths)
            }
        }
    }
    return remember(launcher) { { launcher.launch(null) } }
}

internal fun copyToCache(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        // keep the real name: the format comes from it (".fb2.zip" isn't ".zip")
        val name = displayName(context, uri)?.replace('/', '_') ?: "book.${guessExt(context, uri)}"
        // own folder per copy, so the file keeps its exact name
        val file = File(File(context.cacheDir, "open-${System.nanoTime()}").apply { mkdirs() }, name)
        file.outputStream().use { output -> input.copyTo(output) }
        file.absolutePath
    }
}.getOrNull()

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull()

/** Extension from the MIME type, for providers that don't report a name. */
private fun guessExt(context: Context, uri: Uri): String =
    when (context.contentResolver.getType(uri)) {
        "application/epub+zip" -> "epub"
        "application/zip" -> "fb2.zip"
        "text/plain" -> "txt"
        "text/html", "application/xhtml+xml" -> "html"
        "text/markdown" -> "md"
        "application/rtf", "text/rtf" -> "rtf"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/xml", "text/xml" -> "fb2"
        else -> "epub"
    }
