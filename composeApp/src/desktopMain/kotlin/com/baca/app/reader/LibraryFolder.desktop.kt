package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

/** Desktop library folder: plain file paths, read where they are. */
class FileLibraryFolder(private val root: File) : LibraryFolder {

    override fun books(): List<FolderBook> =
        root.walkTopDown()
            .filter { it.isFile && BookFormats.of(it.name) != null }
            .map { FolderBook(it.absolutePath, it.name) }
            .toList()

    override fun stage(location: String): String = location

    override fun unstage(path: String) {}

    override fun add(path: String): String {
        val source = File(path)
        val target = freeName(source.name)
        source.copyTo(target)
        return target.absolutePath
    }

    override fun contains(location: String): Boolean =
        location.startsWith(root.absolutePath + File.separator)

    // "Book.epub" taken → "Book (2).epub"; never overwrite the user's files
    private fun freeName(name: String): File {
        val ext = if (name.endsWith(".fb2.zip", ignoreCase = true)) ".fb2.zip" else "." + name.substringAfterLast('.')
        val base = name.dropLast(ext.length)
        return generateSequence(1) { it + 1 }
            .map { n -> File(root, if (n == 1) name else "$base ($n)$ext") }
            .first { !it.exists() }
    }
}

@Composable
actual fun rememberLibraryFolder(location: String?): LibraryFolder? =
    remember(location) { location?.let { FileLibraryFolder(File(it)) } }
