package com.baca.shared.parser

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
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

internal fun txtEpub(fileName: String, raw: String): ParsedEpub {
    val paragraphs = raw.split(Regex("\\r?\\n[ \\t]*\\r?\\n"))
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotBlank() }
    val blocks = paragraphs.map {
        // LibreraX reads a text file as HTML paragraphs: a browser's 1em between them, no indent
        Block.Text(listOf(FormattedSpan(it, false, false)), BlockKind.PARAGRAPH, 0, gapBefore = Spacing(1f))
    }
    val title = fileName.substringBeforeLast('.').trim()
    return ParsedEpub(
        title = title,
        author = "",
        coverHref = null,
        chapters = listOf(Chapter(href = "txt:$fileName", title = title, blocks = blocks)),
    )
}

/** FB2, plain or zipped (a `.fb2.zip`). */
fun parseFb2(path: Path): ParsedEpub {
    val fileName = path.name
    val xml: String = when {
        fileName.endsWith(".fb2", ignoreCase = true) ->
            FileSystem.SYSTEM.read(path) { readUtf8() }
        fileName.endsWith(".zip", ignoreCase = true) -> {
            val zip = FileSystem.SYSTEM.openZip(path)
            val entry = zip.listRecursively("/".toPath())
                .first { it.name.endsWith(".fb2", ignoreCase = true) }
            zip.read(entry) { readUtf8() }
        }
        else -> error("not an fb2 file: $fileName")
    }
    return fb2Epub(fileName, xml)
}


/**
 * hrefs are opaque ids (`fb2:<n>`). [Chapter.title] comes from the section's
 * first `<title>` (or the file name when absent). The notes body
 * (`<body name="notes">`) and base64 `<binary>` images are skipped.
 */
internal fun fb2Epub(fileName: String, xml: String): ParsedEpub {
    val reader = newXmlReader(xml)

    // metadata (description/title-info)
    var descriptionDepth = 0
    var capture: StringBuilder? = null
    var captureKind: String? = null // "title" | author name part
    var title = ""
    val authorParts = mutableListOf<String>()

    // body
    var inNotesBody = false
    var sectionDepth = 0
    var chapterIndex = 0
    var titleDepth = 0
    var titleText = StringBuilder()
    var chapterTitleCollected = false
    var chapterTitle = ""
    var chapterOpen = false
    var inPara = false
    var paraText = StringBuilder()
    var inBinary = false
    val blocks = mutableListOf<Block>()
    val chapters = mutableListOf<Chapter>()

    fun flushChapter() {
        if (blocks.isNotEmpty()) {
            chapters.add(Chapter(href = "fb2:$chapterIndex", title = chapterTitle, blocks = blocks.toList()))
            chapterIndex++
        }
        blocks.clear()
        chapterTitle = ""
        chapterTitleCollected = false
        chapterOpen = false
    }

    fun flushPara() {
        val text = paraText.toString().replace(Regex("\\s+"), " ").trim()
        paraText = StringBuilder()
        if (text.isNotEmpty()) {
            // foliate's FB2 stylesheet: `p { text-indent: 1em; margin: 0 }`
            blocks.add(Block.Text(listOf(FormattedSpan(text, false, false)), BlockKind.PARAGRAPH, 0, indent = Spacing(1f), gapBefore = Spacing.ZERO))
        }
    }

    while (reader.hasNext()) {
        when (reader.next()) {
            EventType.START_ELEMENT -> when (reader.localName) {
                "description" -> descriptionDepth++
                "title-info" -> descriptionDepth++
                "author" -> if (descriptionDepth > 0) captureKind = "author"
                "book-title" -> if (descriptionDepth > 0) {
                    captureKind = "title"
                    capture = StringBuilder()
                }
                "first-name", "middle-name", "last-name", "nickname" ->
                    if (captureKind == "author") capture = StringBuilder()
                "body" -> if (reader.attrOrEmpty("name") != "") inNotesBody = true
                "binary" -> inBinary = true
                "section" -> {
                    sectionDepth++
                    if (sectionDepth == 1) {
                        flushChapter()
                        chapterOpen = true
                    }
                }
                "title" -> titleDepth++
                "p" -> if (chapterOpen && titleDepth == 0 && !inNotesBody) inPara = true
                "empty-line" -> {}
                else -> {}
            }
            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF -> when {
                inBinary || inNotesBody -> {}
                capture != null -> capture!!.append(reader.text) // description metadata
                titleDepth > 0 -> titleText.append(reader.text)
                inPara -> paraText.append(reader.text)
                else -> {}
            }
            EventType.END_ELEMENT -> when (reader.localName) {
                "description", "title-info" -> descriptionDepth--
                "author" -> captureKind = null
                "book-title", "first-name", "middle-name", "last-name", "nickname" -> {
                    val value = capture?.toString()?.trim()
                    capture = null
                    val kind = captureKind
                    captureKind = when (reader.localName) {
                        "book-title" -> null
                        else -> kind
                    }
                    when {
                        reader.localName == "book-title" -> title = value ?: ""
                        value != null -> authorParts.add(value)
                    }
                }
                "binary" -> inBinary = false
                "section" -> {
                    sectionDepth--
                    if (sectionDepth == 0) flushChapter()
                }
                "title" -> {
                    titleDepth--
                    if (titleDepth == 0 && chapterOpen && !chapterTitleCollected) {
                        val text = titleText.toString().replace(Regex("\\s+"), " ").trim()
                        if (text.isNotEmpty()) {
                            chapterTitle = text
                            chapterTitleCollected = true
                            blocks.add(
                                Block.Text(listOf(FormattedSpan(text, bold = true, italic = false)), BlockKind.HEADING, 0, align = TextAlign.CENTER, scale = 2f),
                            )
                        }
                    }
                    titleText = StringBuilder()
                }
                "p" -> if (inPara) {
                    inPara = false
                    flushPara()
                }
                "body" -> inNotesBody = false
                else -> {}
            }
            else -> {}
        }
    }
    flushChapter()

    val fallback = fileName.substringBeforeLast('.')
    if (chapters.isEmpty()) {
        chapters.add(Chapter(href = "fb2:$fallback", title = fallback, blocks = emptyList()))
    }
    return ParsedEpub(
        title = title.ifBlank { fallback },
        author = authorParts.joinToString(" ").trim(),
        coverHref = null,
        chapters = chapters,
    )
}
