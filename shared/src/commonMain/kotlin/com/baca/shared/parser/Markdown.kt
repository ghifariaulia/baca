package com.baca.shared.parser

/**
 * Markdown as HTML, CommonMark's blocks and spans that books use: ATX and setext headings,
 * paragraphs (their lines kept apart), emphasis, code, links and pictures, lists, quotes,
 * fenced and indented code, rules, and raw HTML passed through. (LibreraX's converter turns
 * a paragraph's line breaks into plain newlines too, probed.)
 */
internal fun markdownToHtml(markdown: String): String {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    return MarkdownBlocks(lines).blocks()
}

private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})\s*([^`]*)$""")
private val ATX = Regex("""^ {0,3}(#{1,6})(?:[ \t]+(.*?))?(?:[ \t]+#+)?[ \t]*$""")
private val RULE = Regex("""^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$""")
private val BULLET = Regex("""^( {0,3})([-+*])([ \t]+)(.*)$""")
private val ORDERED = Regex("""^( {0,3})(\d{1,9})([.)])([ \t]+)(.*)$""")
private val SETEXT = Regex("""^ {0,3}(=+|-+)[ \t]*$""")
private val HTML_BLOCK = Regex("""^ {0,3}</?(?:address|article|aside|blockquote|body|center|dd|details|div|dl|dt|fieldset|figcaption|figure|footer|form|h[1-6]|header|hr|html|li|main|nav|ol|p|pre|section|table|tbody|td|tfoot|th|thead|tr|ul)(?:\s|/?>|$)""", RegexOption.IGNORE_CASE)

private class MarkdownBlocks(private val lines: List<String>) {
    private var i = 0
    private val out = StringBuilder()

    fun blocks(): String {
        while (i < lines.size) block()
        return out.toString()
    }

    private fun blank(s: String) = s.isBlank()

    private fun block() {
        val line = lines[i]
        when {
            blank(line) -> i++
            FENCE.matches(line) -> fenced(FENCE.find(line)!!)
            ATX.matches(line) -> {
                val m = ATX.find(line)!!
                val level = m.groupValues[1].length
                out.append("<h$level>").append(inline(m.groupValues[2].trim())).append("</h$level>")
                i++
            }
            RULE.matches(line) -> { out.append("<hr>"); i++ }
            line.startsWith(">") || (line.length > 1 && line.trimStart(' ').startsWith(">") && line.takeWhile { it == ' ' }.length < 4) -> quote()
            BULLET.matches(line) || ORDERED.matches(line) -> list()
            HTML_BLOCK.containsMatchIn(line) -> html()
            line.startsWith("    ") || line.startsWith("\t") -> indented()
            else -> paragraph()
        }
    }

    private fun fenced(m: MatchResult) {
        val fence = m.groupValues[1]
        i++
        val code = StringBuilder()
        while (i < lines.size && !(lines[i].trim().startsWith(fence) && lines[i].trim().all { it == fence[0] })) {
            code.append(lines[i]).append('\n')
            i++
        }
        i++
        out.append("<pre><code>").append(escape(code.toString())).append("</code></pre>")
    }

    private fun indented() {
        val code = StringBuilder()
        while (i < lines.size && (lines[i].startsWith("    ") || lines[i].startsWith("\t") || blank(lines[i]))) {
            // a blank line ends the code unless more follows
            if (blank(lines[i]) && (i + 1 >= lines.size || !(lines[i + 1].startsWith("    ") || lines[i + 1].startsWith("\t")))) break
            code.append(lines[i].removePrefix("    ").removePrefix("\t")).append('\n')
            i++
        }
        out.append("<pre><code>").append(escape(code.toString())).append("</code></pre>")
    }

    private fun html() {
        val raw = StringBuilder()
        while (i < lines.size && !blank(lines[i])) {
            raw.append(lines[i]).append('\n')
            i++
        }
        out.append(raw.toString().trimEnd('\n'))
    }

    private fun quote() {
        val inner = ArrayList<String>()
        while (i < lines.size && !blank(lines[i])) {
            val l = lines[i]
            val stripped = l.trimStart(' ')
            // a lazy continuation line stays in the quote, a block starting one ends it
            if (stripped.startsWith(">")) inner.add(stripped.removePrefix(">").removePrefix(" "))
            else if (inner.isNotEmpty() && !RULE.matches(l) && !ATX.matches(l) && !FENCE.matches(l) && !BULLET.matches(l) && !ORDERED.matches(l)) inner.add(l)
            else break
            i++
        }
        out.append("<blockquote>").append(MarkdownBlocks(inner).blocks()).append("</blockquote>")
    }

    private fun list() {
        val ordered = ORDERED.matches(lines[i])
        val first = if (ordered) ORDERED.find(lines[i])!! else null
        val start = first?.groupValues?.get(2)?.toIntOrNull()
        val marker = if (ordered) first!!.groupValues[3] else BULLET.find(lines[i])!!.groupValues[2]
        val items = ArrayList<List<String>>()
        var loose = false
        while (i < lines.size) {
            val line = lines[i]
            val m = if (ordered) ORDERED.find(line) else BULLET.find(line)
            if (m == null || RULE.matches(line)) break
            if ((if (ordered) m.groupValues[3] else m.groupValues[2]) != marker) break
            val content = if (ordered) m.groupValues[5] else m.groupValues[4]
            val width = m.value.length - content.length
            val item = arrayListOf(content)
            i++
            // the item's following lines: indented to its content, or lazy paragraph continuation
            while (i < lines.size) {
                val next = lines[i]
                if (blank(next)) {
                    // blank lines stay in the item only if what follows is indented under it
                    var k = i
                    while (k < lines.size && blank(lines[k])) k++
                    if (k < lines.size && lines[k].takeWhile { it == ' ' }.length >= width) {
                        loose = true
                        repeat(k - i) { item.add("") }
                        i = k
                        continue
                    }
                    break
                }
                if (next.takeWhile { it == ' ' }.length >= width) item.add(next.drop(width))
                else if (!BULLET.matches(next) && !ORDERED.matches(next) && !RULE.matches(next) && !ATX.matches(next) && !FENCE.matches(next) && item.last().isNotEmpty()) item.add(next.trimStart())
                else break
                i++
            }
            items.add(item)
            // blank lines between this list's items make it loose; those before another kind of list don't
            while (i < lines.size && blank(lines[i]) && i + 1 < lines.size) {
                val after = lines[i + 1]
                val same = (if (ordered) ORDERED.find(after)?.groupValues?.get(3) else BULLET.find(after)?.groupValues?.get(2)) == marker && !RULE.matches(after)
                if (!same) break
                loose = true
                i++
            }
        }
        val tag = if (ordered) "ol" else "ul"
        out.append(if (ordered && start != null && start != 1) "<ol start=\"$start\">" else "<$tag>")
        for (item in items) {
            val inner = MarkdownBlocks(item).blocks()
            // a tight item's first paragraph is the item's text itself (a nested list may follow it)
            val end = inner.indexOf("</p>")
            val tight = !loose && inner.startsWith("<p>") && end > 0 && inner.indexOf("<p>", 1).let { it < 0 || it > end }
            out.append("<li>").append(if (tight) inner.substring(3, end) + inner.substring(end + 4) else inner).append("</li>")
        }
        out.append("</$tag>")
    }

    private fun paragraph() {
        val text = ArrayList<String>()
        while (i < lines.size && !blank(lines[i])) {
            val l = lines[i]
            if (text.isNotEmpty()) {
                // a setext underline makes the paragraph a heading
                if (SETEXT.matches(l) && !(l.trim().startsWith("-") && BULLET.matches(l) && l.trim().length > 1 && l.trim().drop(1).isNotBlank())) {
                    val level = if (l.trim().startsWith("=")) 1 else 2
                    out.append("<h$level>").append(inline(text.joinToString("\n") { it.trimStart() }.trimEnd())).append("</h$level>")
                    i++
                    return
                }
                if (FENCE.matches(l) || ATX.matches(l) || RULE.matches(l) || l.trimStart().startsWith(">") || BULLET.matches(l) || (ORDERED.find(l)?.groupValues?.get(2) == "1") || HTML_BLOCK.containsMatchIn(l)) break
            }
            text.add(l)
            i++
        }
        if (text.isNotEmpty()) out.append("<p>").append(inline(text.joinToString("\n") { it.trimStart() }.trimEnd())).append("</p>")
    }
}

/** Spans: code, links, pictures, emphasis, autolinks, escapes and raw tags. */
private fun inline(text: String): String {
    val out = StringBuilder()
    var k = 0
    fun closes(marker: String, from: Int): Int {
        var j = from
        while (j < text.length) {
            if (text[j] == '\\') { j += 2; continue }
            if (text[j] == '`') { j = text.indexOf('`', j + 1).let { if (it < 0) text.length else it + 1 }; continue }
            if (text.startsWith(marker, j) && j > from && !text[j - 1].isWhitespace()) return j
            j++
        }
        return -1
    }
    while (k < text.length) {
        val c = text[k]
        when {
            c == '\\' && k + 1 < text.length && text[k + 1] in "\\`*_{}[]()#+-.!<>|~\"'&" -> { out.append(escape(text[k + 1].toString())); k += 2 }
            c == '`' -> {
                val ticks = text.drop(k).takeWhile { it == '`' }.length
                val end = text.indexOf("`".repeat(ticks), k + ticks)
                if (end < 0) { out.append("`".repeat(ticks)); k += ticks } else {
                    out.append("<code>").append(escape(text.substring(k + ticks, end).trim())).append("</code>")
                    k = end + ticks
                }
            }
            c == '!' && text.startsWith("![", k) -> {
                val link = linkAt(text, k + 1)
                if (link == null) { out.append('!'); k++ } else {
                    out.append("<img src=\"").append(escape(link.url)).append("\" alt=\"").append(escape(link.label)).append("\">")
                    k = link.end
                }
            }
            c == '[' -> {
                val link = linkAt(text, k)
                if (link == null) { out.append('['); k++ } else {
                    out.append("<a href=\"").append(escape(link.url)).append("\">").append(inline(link.label)).append("</a>")
                    k = link.end
                }
            }
            c == '<' -> {
                val auto = Regex("""^<((?:https?|mailto):[^\s>]+)>""").find(text.substring(k))
                val tag = Regex("""^</?[A-Za-z][^>]*>""").find(text.substring(k))
                when {
                    auto != null -> { out.append("<a href=\"").append(escape(auto.groupValues[1])).append("\">").append(escape(auto.groupValues[1])).append("</a>"); k += auto.value.length }
                    tag != null -> { out.append(tag.value); k += tag.value.length }
                    else -> { out.append("&lt;"); k++ }
                }
            }
            (c == '*' || c == '_') && k + 1 < text.length -> {
                val strong = text.startsWith("$c$c", k)
                val marker = if (strong) "$c$c" else "$c"
                val intraword = c == '_' && k > 0 && text[k - 1].isLetterOrDigit()
                val end = if (intraword || text[k + marker.length].isWhitespace()) -1 else closes(marker, k + marker.length)
                if (end < 0) { out.append(c); k++ } else {
                    val tag = if (strong) "strong" else "em"
                    out.append("<$tag>").append(inline(text.substring(k + marker.length, end))).append("</$tag>")
                    k = end + marker.length
                }
            }
            c == '&' -> {
                val entity = Regex("""^&(?:#\d+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);""").find(text.substring(k))
                if (entity != null) { out.append(entity.value); k += entity.value.length } else { out.append("&amp;"); k++ }
            }
            c == '>' -> { out.append("&gt;"); k++ }
            c == '\n' -> {
                // two spaces before a newline make a hard break
                if (out.endsWith("  ")) { out.setLength(out.length - 2); out.append("<br>\n") } else {
                    while (out.endsWith(" ")) out.setLength(out.length - 1)
                    out.append('\n')
                }
                k++
            }
            else -> { out.append(c); k++ }
        }
    }
    return out.toString()
}

private class LinkAt(val label: String, val url: String, val end: Int)

/** A `[label](url "title")` at [at], or null. */
private fun linkAt(text: String, at: Int): LinkAt? {
    var depth = 0
    var j = at
    while (j < text.length) {
        when (text[j]) {
            '\\' -> j++
            '[' -> depth++
            ']' -> { depth--; if (depth == 0) break }
        }
        j++
    }
    if (j >= text.length || j + 1 >= text.length || text[j + 1] != '(') return null
    val close = text.indexOf(')', j + 2)
    if (close < 0) return null
    val target = text.substring(j + 2, close).trim()
    val url = (if (target.startsWith("<") && target.contains(">")) target.substring(1, target.indexOf('>')) else target.substringBefore(' ')).trim()
    return LinkAt(text.substring(at + 1, j), url, close + 1)
}

private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
