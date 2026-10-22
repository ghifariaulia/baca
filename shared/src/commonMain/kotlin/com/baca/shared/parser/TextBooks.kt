package com.baca.shared.parser

import nl.adaptivity.xmlutil.EventType
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip

/*
 * Books that arrive as one HTML page: HTML and XHTML as written, Markdown, RTF and DOCX
 * converted to it, as LibreraX does. They are one chapter, whose contents are its h1–h3
 * headings, through the same block pipeline as an EPUB's documents.
 */

/** An HTML or XHTML file, read in the encoding its meta tag or XML declaration names (UTF-8 if none). */
fun parseHtmlFile(path: Path, options: ParseOptions = ParseOptions()): ParsedEpub {
    val bytes = FileSystem.SYSTEM.read(path) { readByteArray() }
    return htmlBook(path.name, htmlToXhtml(decodeMarkup(bytes)), options)
}

fun parseMarkdownFile(path: Path, options: ParseOptions = ParseOptions()): ParsedEpub {
    val text = FileSystem.SYSTEM.read(path) { readUtf8() }
    return htmlBook(path.name, htmlToXhtml(markdownToHtml(text)), options)
}

fun parseRtfFile(path: Path, options: ParseOptions = ParseOptions()): ParsedEpub {
    val bytes = FileSystem.SYSTEM.read(path) { readByteArray() }
    return htmlBook(path.name, htmlToXhtml(rtfToHtml(bytes)), options)
}

fun parseDocxFile(path: Path, options: ParseOptions = ParseOptions()): ParsedEpub {
    val xml = FileSystem.SYSTEM.openZip(path).read("word/document.xml".toPath()) { readUtf8() }
    return htmlBook(path.name, htmlToXhtml(docxToHtml(xml)), options)
}

/** A page's bytes as text: UTF-8, or the charset the page says it is in. */
private fun decodeMarkup(bytes: ByteArray): String {
    val head = bytes.copyOf(minOf(bytes.size, 2048)).decodeToString()
    val charset = Regex("""charset\s*=\s*["']?([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
        ?: Regex("""^\s*<\?xml[^>]*encoding\s*=\s*["']([A-Za-z0-9._-]+)["']""").find(head)?.groupValues?.get(1)
    return if (charset == null || charset.equals("utf-8", true) || charset.equals("utf8", true)) bytes.decodeToString() else runCatching { decodeText(bytes, charset) }.getOrElse { bytes.decodeToString() }
}

/** [xhtml], well-formed, as a one-chapter book; the title is the page's own, else the file's name. */
internal fun htmlBook(fileName: String, xhtml: String, options: ParseOptions = ParseOptions()): ParsedEpub {
    val anchors = HashMap<String, Float>()
    val anchorBlocks = HashMap<String, Int>()
    var body = 1f to (null as String?)
    val blocks = parseXhtml(xhtml, anchors = anchors, anchorBlocks = anchorBlocks, onBody = { scale, family -> body = scale to family }, options = options, darkScheme = options.darkScheme)
    val name = fileName.substringBeforeLast('.').trim()
    val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(xhtml)?.groupValues?.get(1)
        ?.replace("&amp;", "&")?.replace("&lt;", "<")?.replace("&gt;", ">")?.trim()?.takeIf { it.isNotEmpty() } ?: name
    val href = "html:$fileName"
    val toc = Regex("""<h([123]) id="([^"]*)"[^>]*>(.*?)</h\1>""", RegexOption.DOT_MATCHES_ALL).findAll(xhtml).mapNotNull { m ->
        val label = m.groupValues[3].replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim()
        label.takeIf { it.isNotEmpty() }?.let { TocEntry(href, it, anchor = m.groupValues[2]) }
    }.toList()
    val chapter = Chapter(
        href = href, title = title, blocks = blocks, size = xhtml.length,
        anchors = anchors, anchorBlocks = anchorBlocks, bodyScale = body.first, bodyFamily = body.second,
    )
    val language = Regex("""<html[^>]*\blang="([^"]*)"""").find(xhtml)?.groupValues?.get(1)?.ifBlank { null }
    return ParsedEpub(title, "", null, withRunningText(listOf(chapter)), toc, language = language, options = options)
}

/** Word's body as paragraphs of bold and italic runs, as LibreraX converts it (probed: no headings, no alignment). */
internal fun docxToHtml(xml: String): String {
    val reader = newXmlReader(xml)
    val out = StringBuilder()
    var paragraph = StringBuilder()
    var inText = false
    var bold = false
    var italic = false
    var run = StringBuilder()
    fun flushRun() {
        if (run.isEmpty()) return
        var t = escapeHtml(run.toString())
        if (italic) t = "<i>$t</i>"
        if (bold) t = "<b>$t</b>"
        paragraph.append(t)
        run = StringBuilder()
    }
    while (reader.hasNext()) {
        when (reader.next()) {
            EventType.START_ELEMENT -> when (reader.localName) {
                "r" -> { bold = false; italic = false; run = StringBuilder() }
                // a bare <w:b/> is on; <w:b w:val="0"/> or "false" is off
                "b" -> bold = reader.attr("val").let { it != "0" && it != "false" }
                "i" -> italic = reader.attr("val").let { it != "0" && it != "false" }
                "t" -> inText = true
                "tab" -> run.append('\t')
                "br" -> { flushRun(); paragraph.append("<br>") }
            }
            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF -> if (inText) run.append(reader.text)
            EventType.END_ELEMENT -> when (reader.localName) {
                "t" -> inText = false
                "r" -> flushRun()
                "p" -> {
                    if (paragraph.isNotBlank()) out.append("<p>").append(paragraph).append("</p>\n")
                    paragraph = StringBuilder()
                }
            }
            else -> {}
        }
    }
    return out.toString()
}
