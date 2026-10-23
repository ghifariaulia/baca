package com.baca.app.reader

import androidx.compose.runtime.Composable
import com.baca.shared.db.BookRow
import com.baca.shared.db.DbBookRepository
import com.baca.shared.domain.computeBookHash
import com.baca.shared.domain.workKeyOf
import com.baca.shared.parser.info
import com.baca.shared.parser.BookInfo
import com.baca.shared.parser.CbzBook
import com.baca.shared.parser.parseCbz
import com.baca.shared.parser.EpubImages
import com.baca.shared.parser.ParsedEpub
import com.baca.shared.parser.parseEpub
import com.baca.shared.parser.ParseOptions
import com.baca.shared.parser.parseDocxFile
import com.baca.shared.parser.parseFb2
import com.baca.shared.parser.parseHtmlFile
import com.baca.shared.parser.parseMarkdownFile
import com.baca.shared.parser.parseRtfFile
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
        "text/html",
        "application/xhtml+xml",
        "text/markdown",
        "application/rtf",
        "text/rtf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    fun of(fileName: String): String? = when {
        fileName.endsWith(".epub", ignoreCase = true) -> "epub"
        fileName.endsWith(".fb2", ignoreCase = true) ||
            fileName.endsWith(".fb2.zip", ignoreCase = true) -> "fb2"
        fileName.endsWith(".cbz", ignoreCase = true) ||
            fileName.endsWith(".zip", ignoreCase = true) -> "cbz"
        fileName.endsWith(".txt", ignoreCase = true) -> "txt"
        fileName.endsWith(".html", ignoreCase = true) || fileName.endsWith(".htm", ignoreCase = true) ||
            fileName.endsWith(".xhtml", ignoreCase = true) -> "html"
        fileName.endsWith(".md", ignoreCase = true) || fileName.endsWith(".markdown", ignoreCase = true) -> "md"
        fileName.endsWith(".rtf", ignoreCase = true) -> "rtf"
        fileName.endsWith(".docx", ignoreCase = true) -> "docx"
        else -> null
    }

    /** Fixed-layout books bypass the Block pipeline entirely. */
    fun parseCbzFile(path: Path, fileName: String): CbzBook? = runCatching {
        parseCbz(path, fileName)
    }.getOrNull()

    /** [options]: what the reader's settings change in how the book is read. */
    fun parse(path: Path, format: String, options: ParseOptions = ParseOptions()): ParsedEpub? = runCatching {
        when (format) {
            "epub" -> parseEpub(path, options)
            "fb2" -> parseFb2(path, options)
            "html" -> parseHtmlFile(path, options)
            "md" -> parseMarkdownFile(path, options)
            "rtf" -> parseRtfFile(path, options)
            "docx" -> parseDocxFile(path, options)
            else -> parsePlainTextOrFb2(path, options)
        }
    }.getOrNull()
}

/** Import result: the library row and whether this call created it. */
data class Imported(val row: BookRow, val isNew: Boolean)

/**
 * Import use case (TECH 7.1/7.2): hash → dedupe by hash → keep the file →
 * parse metadata (+ cover for EPUB/CBZ) → upsert. The file is either copied
 * into app storage ([import]) or left where it is in the library folder
 * ([importInPlace]). Returns the row that ended up in the library (existing
 * on re-import), or null when unsupported or unreadable.
 */
class BookImporter(
    private val repo: DbBookRepository,
    private val storeDir: String,
    private val fs: FileSystem = FileSystem.SYSTEM,
) {
    /** Copies [path] into app storage. */
    fun import(path: String): Imported? = register(path.toPath(), location = null, restoreRemoved = true)

    /**
     * Registers a library-folder book without copying it. [readable] is a local
     * file with its content (the file itself on desktop, a temporary copy on
     * Android); [location] is what the library stores as its path. A scan
     * passes [restoreRemoved] false so books the user removed stay hidden.
     */
    fun importInPlace(readable: String, location: String, restoreRemoved: Boolean): Imported? =
        register(readable.toPath(), location, restoreRemoved)

    /**
     * Adds the folder's books that the library doesn't know yet. Returns the
     * locations found, so the library can hide books whose file is gone.
     */
    fun scanFolder(folder: LibraryFolder): Set<String> {
        val books = folder.books()
        val known = repo.allPaths()
        books.filter { it.location !in known }.forEach { book ->
            val readable = folder.stage(book.location)
            try {
                importInPlace(readable, book.location, restoreRemoved = false)
            } finally {
                folder.unstage(readable)
            }
        }
        return books.map { it.location }.toSet()
    }

    private fun register(source: Path, location: String?, restoreRemoved: Boolean): Imported? = runCatching {
        val fileName = source.name
        val format = BookFormats.of(fileName) ?: return null
        val bookHash = computeBookHash(source, fs)

        // Same file imported twice yields one library row (TECH 7). A removed
        // (tombstoned) row falls through so re-importing brings the book back,
        // unless a folder scan found it: then it stays removed.
        repo.byHash(bookHash)?.takeIf { !it.deleted || !restoreRemoved }?.let { existing ->
            // a library-folder file that moved keeps its reading data
            if (!existing.deleted && location != null && existing.path != location) repo.updatePath(bookHash, location)
            backfill(existing)
            return Imported(repo.byHash(bookHash) ?: existing, isNew = false)
        }

        val dir = storeDir.toPath()
        fs.createDirectories(dir)
        val readable = if (location != null) source else (dir / "$bookHash.${fileName.substringAfterLast('.').lowercase()}").also { dest ->
            if (!fs.exists(dest)) {
                fs.source(source).use { input ->
                    fs.sink(dest).use { output ->
                        output.buffer().use { buffered -> buffered.writeAll(input) }
                    }
                }
            }
        }
        val storedPath = location ?: readable.toString()

        // CBZ: fixed layout — no Block pipeline; cover = first page image.
        if (format == "cbz") {
            val cbz = BookFormats.parseCbzFile(readable, fileName) ?: error("unreadable $fileName")
            val coverPath: String? = cbz.pages.firstOrNull()
                ?.let { first -> EpubImages(readable).imageBytes(first) }
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
                path = storedPath,
                format = format,
                coverPath = coverPath,
            )
            repo.setInfo(bookHash, BookInfo(pages = cbz.pages.size))
            return repo.byHash(bookHash)?.let { Imported(it, isNew = true) }
        }

        // the original, not the hash-named copy: TXT titles come from the file name
        val parsed = BookFormats.parse(source, format) ?: error("unreadable $fileName")

        // Real cover when the EPUB manifest names one; null otherwise (design
        // fallback: flat color + title/author). Some EPUBs point the cover meta
        // at a wrapper XHTML page instead of the image itself.
        val coverPath: String? = if (format == "epub") {
            coverBytes(parsed, EpubImages(readable))?.let { bytes ->
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
            path = storedPath,
            format = format,
            coverPath = coverPath,
        )
        repo.setWorkKey(bookHash, if (format == "epub") workKeyOf(parsed.identifier, parsed.title) else null)
        repo.setInfo(bookHash, parsed.info())
        repo.byHash(bookHash)?.let { Imported(it, isNew = true) }
    }.getOrNull()

    /**
     * One-time heal: rows imported before the toc/cover parser fix carry a
     * null cover path (or lost toc entries), and rows imported before edition
     * linking have no work key. Re-read the stored copy and fill in what is
     * missing; no-op when nothing is.
     */
    /**
     * The details page's facts for a book imported before they were stored: read its copy
     * and keep what it says. [readable] is a local file with the book's content.
     */
    fun ensureInfo(existing: BookRow, readable: String): BookInfo? {
        repo.info(existing.bookHash)?.let { return it }
        val info = runCatching {
            if (existing.format == "cbz") BookInfo(pages = BookFormats.parseCbzFile(readable.toPath(), existing.title)?.pages?.size ?: 0)
            else BookFormats.parse(readable.toPath(), existing.format)?.info()
        }.getOrNull() ?: return null
        repo.setInfo(existing.bookHash, info)
        return info
    }

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
