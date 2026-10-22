package com.baca.shared.parser

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip

/**
 * TXT and FB2 go through the same Block pipeline as EPUB spine documents
 * (docs/TECH.md 8.3). The offsets contract (Parser.kt) applies unchanged:
 * a block's text is its spans joined in order, and locator/anchor charOffsets
 * index into that exact string.
 */

/** Book file → parsed book, dispatched by file extension. */
fun parsePlainTextOrFb2(path: Path): ParsedEpub = when {
    path.name.endsWith(".fb2", ignoreCase = true) ||
        path.name.endsWith(".zip", ignoreCase = true) -> parseFb2(path)
    else -> parseTxt(path, path.name)
}

/** One flat chapter; [href] is an opaque id reused by locator save/restore. */
fun parseTxt(path: Path, fileName: String): ParsedEpub {
    val text = FileSystem.SYSTEM.read(path) { readUtf8() }
    return txtEpub(fileName, text)
}

/**
 * A text file as LibreraX reads it (probed): one HTML document, a `<p>` for each run of lines
 * between blank ones, its lines kept apart by `<br>`; no headings, so no contents. Its size
 * for locations is that document's length, as foliate counts a section.
 */
internal fun txtEpub(fileName: String, raw: String): ParsedEpub {
    val paragraphs = raw.split(Regex("\\r?\\n[ \\t]*\\r?\\n"))
        .map { paragraph -> paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() } }
        .filter { it.isNotEmpty() }
    val blocks = paragraphs.map { lines ->
        // a browser's 1em between paragraphs, no indent; each line its own
        val text = lines.joinToString("\n") { it.replace(Regex("\\s+"), " ") }
        Block.Text(listOf(FormattedSpan(text, false, false)), BlockKind.PARAGRAPH, 0, gapBefore = Spacing(1f))
    }
    val title = fileName.substringBeforeLast('.').trim()
    val body = paragraphs.sumOf { lines -> "<p></p>\n".length + lines.sumOf { escapedLength(it) } + "<br>".length * (lines.size - 1) }
    return ParsedEpub(
        title = title,
        author = "",
        coverHref = null,
        chapters = listOf(Chapter(href = "txt:$fileName", title = title, blocks = blocks, size = TXT_PAGE_OVERHEAD + escapedLength(title) + body)),
    )
}

/** [text]'s length as HTML writes it, `&`, `<` and `>` escaped. */
private fun escapedLength(text: String) = text.length + text.sumOf { c ->
    when (c) {
        '&' -> "&amp;".length - 1
        '<', '>' -> "&lt;".length - 1
        else -> 0
    }.toInt()
}

// what LibreraX's document of a text file holds besides its title and paragraphs: the doctype,
// its head with its stylesheet, and the body's tags (probed: a 5-letter name's page is 933 long)
private const val TXT_PAGE_OVERHEAD = 15 + 529 + 14

/** FB2, plain or zipped (a `.fb2.zip`). */
fun parseFb2(path: Path): ParsedEpub {
    val fileName = path.name
    val bytes: ByteArray = when {
        fileName.endsWith(".fb2", ignoreCase = true) ->
            FileSystem.SYSTEM.read(path) { readByteArray() }
        fileName.endsWith(".zip", ignoreCase = true) -> {
            val zip = FileSystem.SYSTEM.openZip(path)
            val entry = zip.listRecursively("/".toPath())
                .first { it.name.endsWith(".fb2", ignoreCase = true) }
            zip.read(entry) { readByteArray() }
        }
        else -> error("not an fb2 file: $fileName")
    }
    return fb2Epub(fileName, bytes)
}
