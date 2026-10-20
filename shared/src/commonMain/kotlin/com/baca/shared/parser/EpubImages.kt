package com.baca.shared.parser

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip

/** Reads bytes for one image entry of a parsed EPUB. */
class EpubImages(private val bookPath: Path, private val fs: FileSystem = FileSystem.SYSTEM) {

    fun imageBytes(zipHref: String): ByteArray? = runCatching {
        val zip = fs.openZip(bookPath)
        val entry = zipHref.toPath()
        if (!zip.exists(entry)) return null
        zip.read(entry, { readByteArray() })
    }.getOrNull()
}
