package com.baca.app.reader

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Android library folder: a SAF tree with a persisted read/write grant.
 * Parsers need a real file, so [stage] copies a book into the cache.
 */
class SafLibraryFolder(private val context: Context, private val tree: Uri) : LibraryFolder {

    private fun root(): DocumentFile =
        checkNotNull(DocumentFile.fromTreeUri(context, tree)) { "Library folder is no longer available" }

    override fun books(): List<FolderBook> {
        val out = mutableListOf<FolderBook>()
        fun walk(dir: DocumentFile) {
            dir.listFiles().forEach { child ->
                val name = child.name ?: return@forEach
                when {
                    child.isDirectory -> walk(child)
                    child.isFile && BookFormats.of(name) != null -> out.add(FolderBook(child.uri.toString(), name))
                }
            }
        }
        walk(root())
        return out
    }

    override fun stage(location: String): String =
        checkNotNull(copyToCache(context, Uri.parse(location))) { "Can't read $location" }

    override fun unstage(path: String) {
        // copyToCache gives each copy its own folder in the cache
        File(path).parentFile?.takeIf { it.parentFile == context.cacheDir }?.deleteRecursively()
    }

    override fun add(path: String): String {
        val source = File(path)
        // SAF renames on a clash ("Book (1).epub"); the user's files are never overwritten
        val created = checkNotNull(root().createFile("application/octet-stream", source.name)) {
            "Can't write into the library folder"
        }
        checkNotNull(context.contentResolver.openOutputStream(created.uri)) { "Can't write into the library folder" }
            .use { output -> source.inputStream().use { it.copyTo(output) } }
        return created.uri.toString()
    }

    override fun contains(location: String): Boolean = location.startsWith(tree.toString())

    // DocumentFile reports 0 for a file it can't stat
    override fun size(location: String): Long? =
        DocumentFile.fromSingleUri(context, Uri.parse(location))?.length()?.takeIf { it > 0 }
}

@Composable
actual fun rememberLibraryFolder(location: String?): LibraryFolder? {
    val context = LocalContext.current
    return remember(location, context) { location?.let { SafLibraryFolder(context, Uri.parse(it)) } }
}
