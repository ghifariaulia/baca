package com.baca.shared.domain

import okio.Buffer
import okio.FileSystem
import okio.Path

// docs/TECH.md 5.1 — stable enough in practice; hashing a whole 200 MB file
// would be too slow on phones and e-readers alike.
private const val WINDOW = 64L * 1024

/**
 * SHA-256(file_size || first 64 KiB || last 64 KiB). Files smaller than 2*64KiB
 * overlap head and tail; that is fine — the result is still stable per file.
 */
fun computeBookHash(path: Path, fs: FileSystem = FileSystem.SYSTEM): String {
    val size = fs.metadataOrNull(path)?.size ?: return ""
    val buffer = Buffer()
    buffer.writeLong(size)
    fs.openReadOnly(path).use { h ->
        h.read(0, buffer, minOf(WINDOW, size))
        val tailOffset = (size - WINDOW).coerceAtLeast(0)
        val tailLength = size - tailOffset
        if (tailLength > 0) h.read(tailOffset, buffer, tailLength)
    }
    return buffer.readByteString().sha256().hex()
}
