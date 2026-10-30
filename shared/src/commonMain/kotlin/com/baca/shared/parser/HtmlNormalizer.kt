package com.baca.shared.parser

/**
 * An HTML page as the well-formed XHTML [parseXhtml] reads, the way a browser's HTML parser
 * would shape it: void elements closed, unquoted attributes quoted, entities decoded, the
 * end tags HTML leaves out (`<p>`, `<li>`…) put in, and whatever would run or send the reader
 * elsewhere taken out, as LibreraX does when it opens a page as a book. Headings up to h3
 * without an id get one (`h1`, `h2`…), which the contents point at.
 */
internal fun htmlToXhtml(html: String): String = HtmlNormalizer(html).run()

private val VOID = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")

// removed with what's inside them
private val DROPPED = setOf("script", "iframe", "frame", "frameset", "object", "noembed", "template")

// start tags that end an open <p>
private val CLOSES_P = setOf(
    "address", "article", "aside", "blockquote", "center", "details", "dir", "div", "dl", "fieldset", "figcaption", "figure",
    "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "li", "main", "menu", "nav", "ol", "p", "pre",
    "section", "table", "ul", "dd", "dt",
)

private val NAMED_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "copy" to "©", "reg" to "®",
    "trade" to "™", "hellip" to "…", "mdash" to "—", "ndash" to "–", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“",
    "rdquo" to "”", "sbquo" to "‚", "bdquo" to "„", "laquo" to "«", "raquo" to "»", "bull" to "•", "middot" to "·",
    "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢", "sect" to "§", "para" to "¶", "deg" to "°", "plusmn" to "±",
    "times" to "×", "divide" to "÷", "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "shy" to "­", "ensp" to " ",
    "emsp" to " ", "thinsp" to " ", "zwj" to "‍", "zwnj" to "‌", "larr" to "←", "rarr" to "→",
    "uarr" to "↑", "darr" to "↓", "dagger" to "†", "Dagger" to "‡", "permil" to "‰", "prime" to "′", "Prime" to "″",
    "iexcl" to "¡", "iquest" to "¿", "szlig" to "ß", "agrave" to "à", "aacute" to "á", "acirc" to "â", "atilde" to "ã",
    "auml" to "ä", "aring" to "å", "aelig" to "æ", "ccedil" to "ç", "egrave" to "è", "eacute" to "é", "ecirc" to "ê",
    "euml" to "ë", "igrave" to "ì", "iacute" to "í", "icirc" to "î", "iuml" to "ï", "ntilde" to "ñ", "ograve" to "ò",
    "oacute" to "ó", "ocirc" to "ô", "otilde" to "õ", "ouml" to "ö", "oslash" to "ø", "ugrave" to "ù", "uacute" to "ú",
    "ucirc" to "û", "uuml" to "ü", "yacute" to "ý", "Agrave" to "À", "Aacute" to "Á", "Acirc" to "Â", "Auml" to "Ä",
    "Eacute" to "É", "Ouml" to "Ö", "Uuml" to "Ü", "Ntilde" to "Ñ", "Ccedil" to "Ç",
)

/**
 * Markup as the plain text a person reads: line breaks and the end of a block become line breaks,
 * tags go, character references become their characters. A book's description is written this way
 * (an OPF's `dc:description` holds escaped HTML), and may be plain text with none of it.
 */
internal fun htmlToPlainText(html: String): String {
    val broken = html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</(p|div|li|h[1-6]|blockquote)\\s*>", RegexOption.IGNORE_CASE), "\n\n")
    val text = broken.replace(Regex("<[^>]*>"), "")
        .replace(Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z][A-Za-z0-9]*);")) { match ->
            val name = match.groupValues[1]
            when {
                name.startsWith("#x") -> name.drop(2).toIntOrNull(16)?.let(::charOf)
                name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::charOf)
                else -> NAMED_ENTITIES[name]
            } ?: match.value
        }
    return text.replace('\u00A0', ' ').lines()
        .map { it.replace(Regex("[ \\t]+"), " ").trim() }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private fun charOf(code: Int): String? = when (code) {
    in 1..0xFFFF -> code.toChar().toString()
    in 0x10000..0x10FFFF -> (code - 0x10000).let { (0xD800 + (it shr 10)).toChar().toString() + (0xDC00 + (it and 0x3FF)).toChar() }
    else -> null
}

private class HtmlNormalizer(private val src: String) {
    private var i = 0
    private val head = StringBuilder()
    private val body = StringBuilder()
    private var htmlAttrs = ""
    private var inHead = false
    private var bodyOpen = false
    private val open = ArrayList<String>()
    private var headings = 0

    // where output goes: the head until the body's content starts
    private val out get() = if (bodyOpen) body else head

    fun run(): String {
        while (i < src.length) {
            if (src[i] != '<') {
                text()
            } else if (src.startsWith("<!--", i)) {
                i = src.indexOf("-->", i + 4).let { if (it < 0) src.length else it + 3 }
            } else if (src.startsWith("<![CDATA[", i)) {
                val end = src.indexOf("]]>", i).let { if (it < 0) src.length else it }
                emitText(src.substring(i + 9, end))
                i = minOf(end + 3, src.length)
            } else if (src.startsWith("<!", i) || src.startsWith("<?", i)) {
                i = src.indexOf('>', i).let { if (it < 0) src.length else it + 1 }
            } else if (src.startsWith("</", i) && i + 2 < src.length && src[i + 2].isLetter()) {
                endTag()
            } else if (i + 1 < src.length && src[i + 1].isLetter()) {
                startTag()
            } else {
                emitText("<")
                i++
            }
        }
        while (open.isNotEmpty()) closeTop()
        val lang = Regex("""(?:xml:)?lang="[^"]*"""").find(htmlAttrs)?.value?.let { " $it" }.orEmpty()
        return "<html$lang><head>$head</head><body>$body</body></html>"
    }

    private fun text() {
        val end = src.indexOf('<', i).let { if (it < 0) src.length else it }
        emitText(decode(src.substring(i, end)))
        i = end
    }

    private fun emitText(t: String) {
        if (t.isEmpty()) return
        // text before any element is body text; whitespace between head elements isn't
        if (!bodyOpen && t.isBlank()) return
        if (!bodyOpen) openBody()
        body.append(t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
    }

    private fun openBody() {
        bodyOpen = true
        inHead = false
    }

    private fun startTag() {
        var j = i + 1
        while (j < src.length && (src[j].isLetterOrDigit() || src[j] == '-' || src[j] == ':')) j++
        val name = src.substring(i + 1, j).lowercase()
        val attrs = LinkedHashMap<String, String>()
        var selfClosing = false
        while (j < src.length && src[j] != '>') {
            when {
                src[j].isWhitespace() -> j++
                src[j] == '/' -> { selfClosing = true; j++ }
                else -> {
                    selfClosing = false
                    val s = j
                    while (j < src.length && !src[j].isWhitespace() && src[j] != '=' && src[j] != '>' && src[j] != '/') j++
                    val key = src.substring(s, j).lowercase()
                    while (j < src.length && src[j].isWhitespace()) j++
                    var value = key
                    if (j < src.length && src[j] == '=') {
                        j++
                        while (j < src.length && src[j].isWhitespace()) j++
                        if (j < src.length && (src[j] == '"' || src[j] == '\'')) {
                            val quote = src[j]
                            val e = src.indexOf(quote, j + 1).let { if (it < 0) src.length else it }
                            value = src.substring(j + 1, e)
                            j = minOf(e + 1, src.length)
                        } else {
                            val vs = j
                            while (j < src.length && !src[j].isWhitespace() && src[j] != '>') j++
                            value = src.substring(vs, j)
                        }
                    }
                    if (key.isNotEmpty()) attrs.putIfAbsent(key, decode(value))
                }
            }
        }
        i = minOf(j + 1, src.length)
        if (name in DROPPED) {
            // everything up to its end tag goes with it
            if (!selfClosing) skipRaw(name)
            return
        }
        if (name == "style" || name == "title") {
            val raw = rawText(name)
            if (name == "style") head.append("<style><![CDATA[").append(raw.replace("]]>", "")).append("]]></style>")
            else head.append("<title>").append(escape(decode(raw))).append("</title>")
            return
        }
        when (name) {
            "html" -> { htmlAttrs = attrs.entries.joinToString("") { " ${it.key}=\"${escapeAttr(it.value)}\"" }; return }
            "head" -> { inHead = true; return }
            "body" -> { openBody(); return }
            "meta" -> if (attrs["http-equiv"] != null) return
            "link", "base" -> if (name == "base") return
            "noscript" -> return
        }
        if (name !in VOID && name != "link" && name != "meta" && !bodyOpen) openBody()
        if (name == "link" || name == "meta") {
            // head-like elements keep to the head unless the body has begun
            out.append(tag(name, attrs, close = true))
            return
        }
        if (!bodyOpen) openBody()
        impliedEnds(name)
        // as LibreraX numbers them: only the headings that have no id of their own take one
        if (name in setOf("h1", "h2", "h3") && attrs["id"] == null) attrs["id"] = "h${++headings}"
        if (name in VOID || selfClosing) {
            body.append(tag(name, attrs, close = true))
        } else {
            body.append(tag(name, attrs, close = false))
            open.add(name)
        }
    }

    private fun tag(name: String, attrs: Map<String, String>, close: Boolean): String {
        val a = attrs.filter { (k, v) -> !k.startsWith("on") && !v.trimStart().startsWith("javascript:", ignoreCase = true) && k.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == ':' } }
            .entries.joinToString("") { (k, v) -> " $k=\"${escapeAttr(v)}\"" }
        return if (close) "<$name$a/>" else "<$name$a>"
    }

    private fun endTag() {
        var j = i + 2
        while (j < src.length && (src[j].isLetterOrDigit() || src[j] == '-' || src[j] == ':')) j++
        val name = src.substring(i + 2, j).lowercase()
        i = src.indexOf('>', j).let { if (it < 0) src.length else it + 1 }
        if (name == "head") { inHead = false; return }
        if (name == "p" && "p" !in open) {
            // a stray </p> makes an empty paragraph, as HTML does
            if (!bodyOpen) openBody()
            body.append("<p/>")
            return
        }
        val at = open.lastIndexOf(name)
        if (at < 0) return
        // an end tag closes what's open inside the element too
        while (open.size > at) closeTop()
    }

    private fun closeTop() {
        val name = open.removeAt(open.lastIndex)
        body.append("</").append(name).append('>')
    }

    /** What a start tag closes: a `<p>` by a block, an `<li>` by the next one, cells and rows likewise. */
    private fun impliedEnds(name: String) {
        fun closeThrough(target: String, stopAt: Set<String>) {
            val at = open.lastIndexOfAny(target, stopAt)
            if (at >= 0) while (open.size > at) closeTop()
        }
        if (name in CLOSES_P) closeThrough("p", setOf("table", "td", "th", "button", "li").minus(if (name == "li") setOf("li") else emptySet()))
        when (name) {
            "li" -> closeThrough("li", setOf("ul", "ol"))
            "dt", "dd" -> { closeThrough("dt", setOf("dl")); closeThrough("dd", setOf("dl")) }
            "tr" -> { closeThrough("td", setOf("table")); closeThrough("th", setOf("table")); closeThrough("tr", setOf("table")) }
            "td", "th" -> { closeThrough("td", setOf("tr", "table")); closeThrough("th", setOf("tr", "table")) }
            "thead", "tbody", "tfoot" -> { closeThrough("td", setOf("table")); closeThrough("th", setOf("table")); closeThrough("tr", setOf("table")); closeThrough("thead", setOf("table")); closeThrough("tbody", setOf("table")) }
        }
    }

    private fun ArrayList<String>.lastIndexOfAny(target: String, stopAt: Set<String>): Int {
        for (k in lastIndex downTo 0) {
            if (this[k] == target) return k
            if (this[k] in stopAt) return -1
        }
        return -1
    }

    private fun skipRaw(name: String) {
        val end = Regex("</$name\\s*>", RegexOption.IGNORE_CASE).find(src, i)
        i = end?.range?.last?.plus(1) ?: src.length
    }

    private fun rawText(name: String): String {
        val end = Regex("</$name\\s*>", RegexOption.IGNORE_CASE).find(src, i)
        val text = src.substring(i, end?.range?.first ?: src.length)
        i = end?.range?.last?.plus(1) ?: src.length
        return text
    }

    private fun decode(s: String): String {
        if ('&' !in s) return s
        val sb = StringBuilder()
        var k = 0
        while (k < s.length) {
            val c = s[k]
            if (c != '&') { sb.append(c); k++; continue }
            val semi = s.indexOf(';', k)
            val body = if (semi in k + 2..k + 12) s.substring(k + 1, semi) else null
            val decoded = body?.let { b ->
                when {
                    b.startsWith("#x") || b.startsWith("#X") -> b.drop(2).toIntOrNull(16)?.let(::codePoint)
                    b.startsWith("#") -> b.drop(1).toIntOrNull()?.let(::codePoint)
                    else -> NAMED_ENTITIES[b]
                }
            }
            if (decoded != null) { sb.append(decoded); k = semi + 1 } else { sb.append('&'); k++ }
        }
        return sb.toString()
    }

    private fun codePoint(n: Int): String? = if (n in 1..0x10FFFF && n !in 0xD800..0xDFFF) {
        if (n < 0x10000) n.toChar().toString() else {
            val v = n - 0x10000
            charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
        }
    } else null

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun escapeAttr(s: String) = escape(s).replace("\"", "&quot;")
}
