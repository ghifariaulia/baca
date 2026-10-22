package com.baca.shared.parser

import nl.adaptivity.xmlutil.EventType
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * FB2 as LibreraX shows it: foliate's fb2.js turns each top-level part of the first
 * `<body>` into an XHTML document, with its own stylesheet, and every further body (the
 * notes) into one more, out of the reading order. The same conversion here feeds Baca's
 * XHTML pipeline, so FB2 gets the CSS handling EPUB has.
 */
internal fun fb2Epub(fileName: String, bytes: ByteArray): ParsedEpub = fb2Epub(fileName, decodeFb2(bytes))

/** FB2 bytes as text, in the encoding its XML declaration names (UTF-8 if none). */
internal fun decodeFb2(bytes: ByteArray): String {
    val head = bytes.copyOf(minOf(bytes.size, 200)).decodeToString()
    val encoding = Regex("""^\s*<\?xml[^>]*encoding\s*=\s*["']([A-Za-z0-9._-]+)["']""").find(head)?.groupValues?.get(1)
    return if (encoding == null || encoding.equals("utf-8", ignoreCase = true)) bytes.decodeToString() else decodeText(bytes, encoding)
}

/** [bytes] decoded as [charset] (`windows-1251`, `koi8-r`…). */
internal expect fun decodeText(bytes: ByteArray, charset: String): String

internal fun fb2Epub(fileName: String, xml: String): ParsedEpub {
    // <FictionBook>
    val root = parseXmlTree(xml).children().firstOrNull() ?: XNode("FictionBook", emptyMap())
    val bodies = root.children().filter { it.name == "body" }
    val binaries = root.children().filter { it.name == "binary" }.mapNotNull { bin ->
        bin.attrs["id"]?.let { id -> decodeBase64(bin.text())?.let { BINARY_PREFIX + id to it } }
    }.toMap()
    // the first body's parts and each further body (the notes), as foliate makes them sections
    val parts = bodies.firstOrNull()?.children().orEmpty().filter { BODY.containsKey(it.name) }
    val sections = parts.map { it to false } + bodies.drop(1).map { it to true }
    // which section holds each id, for links into the notes
    val sectionOf = HashMap<String, Int>()
    sections.forEachIndexed { index, (node, _) -> node.walk { it.attrs["id"]?.let { id -> sectionOf.getOrPut(id) { index } } } }
    val converter = Fb2Converter(sectionOf)
    val chapters = sections.mapIndexed { index, (node, notes) ->
        val html = if (notes) converter.convert(node, mapOf("body" to Spec.El("body", children = { BODY })), extraClass = "notesBodyType")
        else converter.convert(node, BODY)
        val document = xhtmlOf(html)
        // foliate's own section size, image data aside: what LibreraX counts locations in
        val size = (foliateTemplate(html).encodeToByteArray().size - Regex(""" src="([^"]*)"""").findAll(html).sumOf { it.groupValues[1].length })
        Chapter(
            href = "fb2:$index",
            title = sectionTitle(node),
            blocks = parseXhtml(document),
            size = if (notes) 0 else size,
            linear = !notes,
        )
    }.ifEmpty { listOf(Chapter(href = "fb2:0", title = fileName, blocks = emptyList())) }
    val info = root.find("description")?.find("title-info")
    val title = info?.find("book-title")?.text()?.normalized().orEmpty().ifEmpty { fileName.substringBeforeLast('.') }
    val authors = info?.children()?.filter { it.name == "author" }.orEmpty().map(::personOf).filter { it.isNotEmpty() }
    val toc = chapters.mapIndexedNotNull { index, chapter ->
        chapter.title?.takeIf { it.isNotEmpty() && sections.getOrNull(index)?.second == false }?.let { TocEntry(chapter.href, it) }
    }
    return ParsedEpub(
        title = title,
        author = authors.joinToString(", "),
        coverHref = null,
        chapters = withRunningText(chapters),
        toc = toc,
        language = info?.find("lang")?.text()?.normalized()?.ifEmpty { null },
        binaries = binaries,
    )
}

private const val BINARY_PREFIX = "fb2img:"

/** An image `href` (`#id`) as the src Baca loads it by: one of the book's binaries. */
private fun imageSrc(href: String): String = if (href.startsWith("#")) BINARY_PREFIX + href.substring(1) else href

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64(text: String): ByteArray? = runCatching { Base64.Mime.decode(text.filterNot { it.isWhitespace() }) }.getOrNull()

private fun personOf(node: XNode): String =
    node.find("nickname")?.text()?.normalized()?.takeIf { it.isNotEmpty() }
        ?: listOf("first-name", "middle-name", "last-name").mapNotNull { node.find(it)?.text()?.normalized()?.ifEmpty { null } }.joinToString(" ")

/**
 * The section's name for the contents, as foliate picks it (`.title, .subtitle, p`): its first title,
 * subtitle or element converted to a `<p>`; a body-level title is its own name.
 */
private fun sectionTitle(node: XNode): String {
    if (node.name == "title") return node.text().normalized()
    fun first(of: XNode): XNode? = of.children().firstNotNullOfOrNull { child ->
        when (child.name) {
            in TITLE_LIKE -> child
            // an annotation's content isn't converted
            "annotation" -> null
            else -> first(child)
        }
    }
    return first(node)?.text()?.normalized().orEmpty()
}

private val TITLE_LIKE = setOf("title", "subtitle", "p", "text-author", "date", "stanza")

private fun String.normalized() = replace(Regex("[\\t\\n\\f\\r ]+"), " ").trim()

// --- foliate's element mapping (fb2.js): FB2 element → XHTML element, and what's inside it ---

private sealed class Spec {
    /** [tag], class from the FB2 name; children by [children] (a function: sections and poems hold each other), or by the same mapping when [self] */
    class El(val tag: String, val children: () -> Map<String, Spec>? = { null }, val self: Boolean = false, val attrs: List<String> = emptyList()) : Spec()
    object Image : Spec()
    object Anchor : Spec()
    object Stanza : Spec()
}

private val STYLE: Map<String, Spec> = mapOf(
    "strong" to Spec.El("strong", self = true),
    "emphasis" to Spec.El("em", self = true),
    "style" to Spec.El("span", self = true),
    "a" to Spec.Anchor,
    "strikethrough" to Spec.El("s", self = true),
    "sub" to Spec.El("sub", self = true),
    "sup" to Spec.El("sup", self = true),
    "code" to Spec.El("code", self = true),
    "image" to Spec.Image,
)

private val CELL_ATTRS = listOf("colspan", "rowspan", "align", "valign")

private val TABLE: Map<String, Spec> = mapOf(
    "tr" to Spec.El("tr", { mapOf("th" to Spec.El("th", { STYLE }, attrs = CELL_ATTRS), "td" to Spec.El("td", { STYLE }, attrs = CELL_ATTRS)) }, attrs = listOf("align")),
)

private val TITLE: Map<String, Spec> = mapOf("p" to Spec.El("h1", { STYLE }), "empty-line" to Spec.El("br"))

private val SECTION: Map<String, Spec> = mapOf(
    "title" to Spec.El("header", { TITLE }),
    "epigraph" to Spec.El("blockquote", self = true),
    "image" to Spec.Image,
    "annotation" to Spec.El("aside"),
    "section" to Spec.El("section", self = true),
    "p" to Spec.El("p", { STYLE }),
    "poem" to Spec.El("blockquote", { POEM }),
    "subtitle" to Spec.El("h2", { STYLE }),
    "cite" to Spec.El("blockquote", self = true),
    "empty-line" to Spec.El("br"),
    "table" to Spec.El("table", { TABLE }),
    "text-author" to Spec.El("p", { STYLE }),
)

private val POEM: Map<String, Spec> = mapOf(
    "epigraph" to Spec.El("blockquote", { SECTION }),
    "subtitle" to Spec.El("h2", { STYLE }),
    "text-author" to Spec.El("p", { STYLE }),
    "date" to Spec.El("p", { STYLE }),
    "stanza" to Spec.Stanza,
)

private val BODY: Map<String, Spec> = mapOf(
    "image" to Spec.Image,
    "title" to Spec.El("section", { TITLE }),
    "epigraph" to Spec.El("section", { SECTION }),
    "section" to Spec.El("section", { SECTION }),
)

private class Fb2Converter(private val sectionOf: Map<String, Int>) {
    private val xhtml = " xmlns=\"http://www.w3.org/1999/xhtml\""

    /** [node] as XHTML by [mapping]; the outermost element carries the namespace, as foliate's serializer writes it. */
    fun convert(node: XNode, mapping: Map<String, Spec>, extraClass: String? = null): String =
        StringBuilder().also { out -> element(node, mapping, out, xhtml, extraClass) }.toString()

    private fun element(node: XNode, mapping: Map<String, Spec>, out: StringBuilder, ns: String = "", extraClass: String? = null) {
        when (val spec = mapping[node.name] ?: return) {
            is Spec.Image -> {
                out.append("<img").append(ns)
                node.attrs["alt"]?.let { out.attr("alt", it) }
                node.attrs["title"]?.let { out.attr("title", it) }
                out.attr("src", node.href()?.let(::imageSrc) ?: "data:,").append("/>")
            }
            is Spec.Anchor -> {
                val href = node.href().orEmpty()
                // a link into the notes: the note's section, which Baca jumps to
                val target = if (href.startsWith("#")) sectionOf[href.substring(1)]?.let { "fb2:$it" } ?: href else href
                out.append("<a").append(ns)
                node.attrs["id"]?.let { out.attr("id", it) }
                out.attr("class", "a").attr("href", target)
                if (node.attrs["type"] == "note") out.attr("epub:type", "noteref")
                out.append(">")
                node.children.forEach { child -> content(child, STYLE, out) }
                out.append("</a>")
            }
            is Spec.Stanza -> {
                out.append("<p").append(ns)
                node.attrs["id"]?.let { out.attr("id", it) }
                out.attr("class", "stanza").append(">")
                node.children.forEach { child ->
                    if (child is XNode && child.name != "v") {
                        content(child, mapOf("title" to Spec.El("header", { mapOf("p" to Spec.El("strong", { STYLE }), "empty-line" to Spec.El("br")) }), "subtitle" to Spec.El("p", { STYLE })), out)
                    }
                }
                node.children.filterIsInstance<XNode>().filter { it.name == "v" }.forEach { out.text(it.text()).append("<br/>") }
                out.append("</p>")
            }
            is Spec.El -> {
                out.append('<').append(spec.tag).append(ns)
                node.attrs["id"]?.let { out.attr("id", it) }
                out.attr("class", listOfNotNull(node.name, extraClass).joinToString(" "))
                spec.attrs.forEach { name -> node.attrs[name]?.takeIf { it.isNotEmpty() }?.let { out.attr(name, it) } }
                val inner = if (spec.self) mapping else spec.children()
                if (spec.tag == "br") {
                    out.append("/>")
                    return
                }
                out.append('>')
                // foliate's `.text-author:before { content: "—" }`
                if (node.name == "text-author") out.append('—')
                node.children.forEach { child -> content(child, inner, out) }
                out.append("</").append(spec.tag).append('>')
            }
        }
    }

    private fun content(child: Any, mapping: Map<String, Spec>?, out: StringBuilder) {
        when (child) {
            is String -> out.text(child)
            is XNode -> if (mapping != null) element(child, mapping, out)
        }
    }

    private fun StringBuilder.attr(name: String, value: String) = apply {
        append(' ').append(name).append("=\"").append(value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")).append('"')
    }

    private fun StringBuilder.text(value: String) = append(value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
}

/** A section as a document Baca parses: foliate's stylesheet in its head. */
private fun xhtmlOf(body: String) =
    """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><style>$FB2_CSS</style></head><body>$body</body></html>"""

/** The document foliate makes of a section, its stylesheet a blob URL: what it counts the section's size by. */
private fun foliateTemplate(body: String) = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
    <head><link href="blob:https://appassets.androidplatform.net/00000000-0000-0000-0000-000000000000" rel="stylesheet" type="text/css"/></head>
    <body>$body</body>
</html>"""

// foliate's FB2 stylesheet (fb2.js, Copyright (c) 2022 John Factotum, MIT: docs/licenses/MIT-foliate-js.txt),
// less the `:before` dash, which the conversion writes
private const val FB2_CSS = """
body > img, section > img { display: block; margin: auto; }
.title h1 { text-align: center; }
body > section > .title, body.notesBodyType > .title { margin: 3em 0; }
body.notesBodyType > section .title h1 { text-align: start; }
body.notesBodyType > section .title { margin: 1em 0; }
p { text-indent: 1em; margin: 0; }
:not(p) + p, p:first-child { text-indent: 0; }
.poem p { text-indent: 0; margin: 1em 0; }
.text-author, .date { text-align: end; }
table { border-collapse: collapse; }
td, th { padding: .25em; }
a[epub|type~="noteref"] { font-size: .75em; vertical-align: super; }
body:not(.notesBodyType) > .title, body:not(.notesBodyType) > .epigraph { margin: 3em 0; }
"""

// --- a small element tree of the FB2 file ---

private class XNode(val name: String, val attrs: Map<String, String>, val children: MutableList<Any> = mutableListOf()) {
    fun children(): List<XNode> = children.filterIsInstance<XNode>()
    fun find(child: String): XNode? = children().firstOrNull { it.name == child }
    fun text(): String = children.joinToString("") { if (it is XNode) it.text() else it as String }
    fun href(): String? = attrs["href"]
    fun walk(visit: (XNode) -> Unit) {
        visit(this)
        children().forEach { it.walk(visit) }
    }
}

private fun parseXmlTree(xml: String): XNode {
    val reader = newXmlReader(xml)
    val root = XNode("#document", emptyMap())
    val open = ArrayDeque<XNode>().apply { add(root) }
    while (reader.hasNext()) {
        when (reader.next()) {
            EventType.START_ELEMENT -> {
                // attributes by local name: `l:href` and `xlink:href` alike are `href`
                val attrs = (0 until reader.attributeCount).associate { reader.getAttributeLocalName(it) to reader.getAttributeValue(it) }
                val node = XNode(reader.localName, attrs)
                open.last().children.add(node)
                open.addLast(node)
            }
            EventType.END_ELEMENT -> if (open.size > 1) open.removeLast()
            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF, EventType.IGNORABLE_WHITESPACE -> open.last().children.add(reader.text)
            else -> {}
        }
    }
    return root
}
