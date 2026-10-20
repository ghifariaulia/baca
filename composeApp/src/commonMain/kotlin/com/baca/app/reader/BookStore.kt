package com.baca.app.reader

import androidx.compose.runtime.Composable
import com.baca.shared.db.BookRow
import com.baca.shared.db.DbBookRepository
import com.baca.shared.domain.computeBookHash
import com.baca.shared.domain.workKeyOf
import com.baca.shared.parser.CbzBook
import com.baca.shared.parser.parseCbz
import com.baca.shared.parser.EpubImages
import com.baca.shared.parser.ParsedEpub
import com.baca.shared.parser.parseEpub
import com.baca.shared.parser.parseFb2
import com.baca.shared.parser.parsePlainTextOrFb2
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.sink
import okio.source
import okio.use

/**
 * App-private folder for imported book files and cover images (TECH 7.2).
 * Android needs Context, so this is a composable; desktop resolves a stable
 * home-directory location.
 */
@Composable
expect fun rememberBookStoreDir(): String

/** Formats accepted by import; the picker filters to these too. */
object BookFormats {
    val pickMimeTypes = arrayOf(
        "application/epub+zip",
        "application/zip", // .fb2.zip
        "application/xml", // .fb2
        "text/xml",
        "text/plain",
    )

    fun of(fileName: String): String? = when {
        fileName.endsWith(".epub", ignoreCase = true) -> "epub"
        fileName.endsWith(".fb2", ignoreCase = true) ||
            fileName.endsWith(".fb2.zip", ignoreCase = true) -> "fb2"
        fileName.endsWith(".cbz", ignoreCase = true) ||
            fileName.endsWith(".zip", ignoreCase = true) -> "cbz"
        fileName.endsWith(".txt", ignoreCase = true) -> "txt"
        else -> null
    }

    /** Fixed-layout books bypass the Block pipeline entirely. */
    fun parseCbzFile(path: Path, fileName: String): CbzBook? = runCatching {
        parseCbz(path, fileName)
    }.getOrNull()

    fun parse(path: Path, format: String): ParsedEpub? = runCatching {
        when (format) {
            "epub" -> parseEpub(path)
            "fb2" -> parseFb2(path)
            else -> parsePlainTextOrFb2(path)
        }
    }.getOrNull()
}

/** Import result: the library row and whether this call created it. */
data class Imported(val row: BookRow, val isNew: Boolean)

/**
 * Import use case (TECH 7.1/7.2): hash → dedupe by hash → copy into app
 * storage → parse metadata (+ cover for EPUB) → upsert. Returns the row that
 * ended up in the library (existing on re-import), or null when unsupported
 * or unreadable.
 */
class BookImporter(
    private val repo: DbBookRepository,
    private val storeDir: String,
    private val fs: FileSystem = FileSystem.SYSTEM,
) {
    fun import(path: String): Imported? = runCatching {
        val source = path.toPath()
        val fileName = source.name
        val format = BookFormats.of(fileName) ?: return null
        val bookHash = computeBookHash(source, fs)

        // Same file imported twice yields one library row (TECH 7). A removed
        // (tombstoned) row falls through so re-importing brings the book back.
        repo.byHash(bookHash)?.takeIf { !it.deleted }?.let { existing ->
            backfill(existing)
            return Imported(repo.byHash(bookHash) ?: existing, isNew = false)
        }

        val dir = storeDir.toPath()
        fs.createDirectories(dir)
        val dest = dir / "$bookHash.${fileName.substringAfterLast('.').lowercase()}"
        if (!fs.exists(dest)) {
            fs.source(source).use { input ->
                fs.sink(dest).use { output ->
                    output.buffer().use { buffered -> buffered.writeAll(input) }
                }
            }
        }

        // CBZ: fixed layout — no Block pipeline; cover = first page image.
        if (format == "cbz") {
            val cbz = BookFormats.parseCbzFile(dest, fileName) ?: error("unreadable $fileName")
            val coverPath: String? = cbz.pages.firstOrNull()
                ?.let { first -> EpubImages(dest).imageBytes(first) }
                ?.let { bytes ->
                    val coverFile = dir / "$bookHash-cover.jpg"
                    fs.sink(coverFile).use { sink ->
                        sink.buffer().use { buffered -> buffered.write(bytes) }
                    }
                    coverFile.toString()
                }
            repo.upsert(
                bookHash = bookHash,
                title = cbz.title,
                author = "",
                path = dest.toString(),
                format = format,
                coverPath = coverPath,
            )
            return repo.byHash(bookHash)?.let { Imported(it, isNew = true) }
        }

        val parsed = BookFormats.parse(dest, format) ?: error("unreadable $fileName")

        // Real cover when the EPUB manifest names one; null otherwise (design
        // fallback: flat color + title/author). Some EPUBs point the cover meta
        // at a wrapper XHTML page instead of the image itself.
        val coverPath: String? = if (format == "epub") {
            coverBytes(parsed, EpubImages(dest))?.let { bytes ->
                val coverFile = dir / "$bookHash-cover.jpg"
                fs.sink(coverFile).use { sink ->
                    sink.buffer().use { buffered -> buffered.write(bytes) }
                }
                coverFile.toString()
            }
        } else {
            null
        }

        repo.upsert(
            bookHash = bookHash,
            title = parsed.title,
            author = parsed.author,
            path = dest.toString(),
            format = format,
            coverPath = coverPath,
        )
        repo.setWorkKey(bookHash, if (format == "epub") workKeyOf(parsed.identifier, parsed.title) else null)
        repo.byHash(bookHash)?.let { Imported(it, isNew = true) }
    }.getOrNull()

    /**
     * One-time heal: rows imported before the toc/cover parser fix carry a
     * null cover path (or lost toc entries), and rows imported before edition
     * linking have no work key. Re-read the stored copy and fill in what is
     * missing; no-op when nothing is.
     */
    fun backfill(existing: BookRow) {
        if (existing.format != "epub") return
        if (existing.coverPath != null && repo.hasWorkKeyEntry(existing.bookHash)) return
        runCatching {
            val parsed = BookFormats.parse(existing.path.toPath(), "epub") ?: return
            if (!repo.hasWorkKeyEntry(existing.bookHash)) {
                repo.setWorkKey(existing.bookHash, workKeyOf(parsed.identifier, parsed.title))
            }
            if (existing.coverPath != null) return
            val bytes = coverBytes(parsed, EpubImages(existing.path.toPath())) ?: return
            val coverFile = storeDir.toPath() / "${existing.bookHash}-cover.jpg"
            fs.sink(coverFile).use { sink ->
                sink.buffer().use { buffered -> buffered.write(bytes) }
            }
            repo.upsert(
                bookHash = existing.bookHash,
                title = existing.title,
                author = existing.author,
                path = existing.path,
                format = existing.format,
                coverPath = coverFile.toString(),
            )
        }
    }
}

private fun coverBytes(parsed: ParsedEpub, images: EpubImages): ByteArray? {
    val href = parsed.coverHref ?: return null
    if (href.endsWith(".xhtml", true) || href.endsWith(".html", true)) {
        // Wrapper page (Gutenberg-style cover.xhtml): pull its <img>/<image> src.
        val page = images.imageBytes(href) ?: return null
        val src = findCoverImageSrc(okio.Buffer().apply { write(page) }.readUtf8()) ?: return null
        val resolved = href.substringBeforeLast('/').toPath() / src.toPath()
        return images.imageBytes(resolved.toString())
    }
    return images.imageBytes(href)
}

private fun findCoverImageSrc(pageHtml: String): String? {
    val img = Regex("<img[^>]*src=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    return img.find(pageHtml)?.groupValues?.get(1)
        ?: Regex("<image[^>]*xlink:href=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(pageHtml)?.groupValues?.get(1)
}
