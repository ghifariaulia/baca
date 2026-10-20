package com.baca.shared.backup

import okio.Sink
import okio.Source
import okio.buffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal actual fun writeZip(out: Sink, entries: List<Pair<String, () -> Source>>) {
    ZipOutputStream(out.buffer().outputStream()).use { zip ->
        entries.forEach { (name, open) ->
            zip.putNextEntry(ZipEntry(name))
            open().buffer().use { it.inputStream().copyTo(zip) }
            zip.closeEntry()
        }
    }
}
