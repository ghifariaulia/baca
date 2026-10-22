package com.baca.shared.parser

import nl.adaptivity.xmlutil.EventType

/**
 * MathML as text that flows in a line, so a page doesn't lose its formulas: the structure
 * Chromium lays out in two dimensions is written out along the line, with powers and
 * subscripts as raised and lowered spans, fractions as `a⁄b`, roots as `√(…)` and tables
 * as rows. A formula set as a block (`display="block"`) is a centred paragraph of its own.
 */
internal fun mathmlToHtml(xhtml: String): String {
    if (!MATH_START.containsMatchIn(xhtml)) return xhtml
    return MATH.replace(xhtml) { match ->
        // a formula that won't parse (an entity XML doesn't define) is still read, as its text
        val tree = runCatching { MathNode.parse(match.value) }.getOrNull()
            ?: return@replace match.value.replace(Regex("<[^>]*>"), "").replace("&", "&amp;").replace("<", "&lt;")
        val html = MathWriter().write(tree)
        if (tree.attrs["display"] == "block") "<div style=\"text-align:center\">$html</div>" else html
    }
}

private val MATH_START = Regex("""<(?:\w+:)?math\b""")
private val MATH = Regex("""<((?:\w+:)?)math\b[^>]*?(?:/>|>.*?</\1math\s*>)""", RegexOption.DOT_MATCHES_ALL)

private class MathNode(val name: String, val attrs: Map<String, String>, val children: MutableList<Any> = mutableListOf()) {
    val elements get() = children.filterIsInstance<MathNode>()
    fun text(): String = children.joinToString("") { if (it is MathNode) it.text() else it as String }

    companion object {
        fun parse(xml: String): MathNode {
            val reader = newXmlReader(xml)
            val root = MathNode("#root", emptyMap())
            val open = ArrayDeque<MathNode>().apply { add(root) }
            while (reader.hasNext()) {
                when (reader.next()) {
                    EventType.START_ELEMENT -> {
                        val node = MathNode(reader.localName, (0 until reader.attributeCount).associate { reader.getAttributeLocalName(it) to reader.getAttributeValue(it) })
                        open.last().children.add(node)
                        open.addLast(node)
                    }
                    EventType.END_ELEMENT -> if (open.size > 1) open.removeLast()
                    EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF -> open.last().children.add(reader.text)
                    else -> {}
                }
            }
            return root.elements.first()
        }
    }
}

// binary operators and relations Chromium sets with space on both sides (0.2222em)
private const val OPERATORS = "+−-×÷±∓·∗=≠≈≡<>≤≥≪≫∈∉⊂⊃⊆⊇→←↔⇒⇔∧∨∩∪"

private class MathWriter {
    fun write(node: MathNode): String = when (node.name) {
        "math", "mrow", "mstyle", "mpadded", "merror", "mphantom", "menclose", "maction" -> all(node)
        "semantics" -> node.elements.firstOrNull()?.let(::write).orEmpty()
        "annotation", "annotation-xml" -> ""
        "mi" -> identifier(node)
        "mn" -> escape(node.text().trim())
        "mtext" -> escape(node.text())
        "ms" -> "\"" + escape(node.text()) + "\""
        "mspace" -> " "
        "mo" -> operator(node)
        "mfrac" -> fraction(node)
        "msqrt" -> "√(" + all(node) + ")"
        "mroot" -> node.elements.let { e -> if (e.size < 2) all(node) else "<sup>${write(e[1])}</sup>√(${write(e[0])})" }
        "msup" -> node.elements.let { e -> write(e[0]) + e.getOrNull(1)?.let { "<sup>${write(it)}</sup>" }.orEmpty() }
        "msub" -> node.elements.let { e -> write(e[0]) + e.getOrNull(1)?.let { "<sub>${write(it)}</sub>" }.orEmpty() }
        "msubsup" -> node.elements.let { e -> write(e[0]) + e.getOrNull(1)?.let { "<sub>${write(it)}</sub>" }.orEmpty() + e.getOrNull(2)?.let { "<sup>${write(it)}</sup>" }.orEmpty() }
        "munder" -> node.elements.let { e -> write(e[0]) + e.getOrNull(1)?.let { "<sub>${write(it)}</sub>" }.orEmpty() }
        "mover" -> node.elements.let { e -> write(e[0]) + e.getOrNull(1)?.let { "<sup>${write(it)}</sup>" }.orEmpty() }
        "munderover" -> node.elements.let { e -> write(e[0]) + e.getOrNull(1)?.let { "<sub>${write(it)}</sub>" }.orEmpty() + e.getOrNull(2)?.let { "<sup>${write(it)}</sup>" }.orEmpty() }
        "mmultiscripts" -> node.elements.let { e -> write(e[0]) + e.drop(1).chunked(2).joinToString("") { (s, p) -> "<sub>${write(s)}</sub><sup>${write(p)}</sup>" } }
        "mfenced" -> fenced(node)
        "mtable" -> node.elements.joinToString("<br/>") { row -> write(row) }
        "mtr", "mlabeledtr" -> node.elements.joinToString(" ") { write(it) }
        "mtd" -> all(node)
        else -> all(node).ifEmpty { escape(node.text()) }
    }

    private fun all(node: MathNode) = node.children.joinToString("") { child -> if (child is MathNode) write(child) else escape((child as String).trim()) }

    // a single letter is set in italic, as MathML's default for it
    private fun identifier(node: MathNode): String {
        val text = node.text().trim()
        val variant = node.attrs["mathvariant"]
        return if (text.length == 1 && text[0].isLetter() && variant == null) "<i>${escape(text)}</i>" else escape(text)
    }

    private fun operator(node: MathNode): String {
        val text = node.text().trim()
        // invisible operators and function application take no room
        if (text in setOf("⁡", "⁢", "⁣", "⁤", "")) return ""
        val spaced = text.length == 1 && text in OPERATORS && node.attrs["form"] != "prefix"
        return if (spaced) " " + escape(text) + " " else escape(text)
    }

    private fun fraction(node: MathNode): String {
        val (num, den) = node.elements.let { it.getOrNull(0) to it.getOrNull(1) }
        if (num == null || den == null) return all(node)
        fun wrapped(n: MathNode): String {
            val text = write(n)
            // a lone token needs no brackets
            return if (n.name in setOf("mi", "mn") || (n.name == "mrow" && n.elements.size == 1)) text else "($text)"
        }
        return wrapped(num) + "⁄" + wrapped(den)
    }

    private fun fenced(node: MathNode): String {
        val open = node.attrs["open"] ?: "("
        val close = node.attrs["close"] ?: ")"
        val separators = (node.attrs["separators"] ?: ",").filter { !it.isWhitespace() }
        val parts = node.elements.map { write(it) }
        val body = parts.withIndex().joinToString("") { (i, p) -> p + if (i < parts.lastIndex) separators.getOrElse(i) { separators.lastOrNull() ?: ',' }.toString() else "" }
        return escape(open) + body + escape(close)
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
