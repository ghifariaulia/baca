package com.baca.shared.parser

/**
 * Rich Text Format as HTML paragraphs with bold and italic, as LibreraX converts it (probed):
 * a `<p>` for each `\par`, `<b>` and `<i>` round runs, nothing else of the formatting. Text
 * in a header, a font or colour table, the stylesheet or a picture isn't text.
 */
internal fun rtfToHtml(rtf: ByteArray): String {
    val text = rtf.decodeToString(throwOnInvalidSequence = false)
    val body = StringBuilder()
    var paragraph = StringBuilder()
    var bold = false
    var italic = false
    var skipDepth = -1
    var depth = 0
    // the states to go back to at each `}`
    val stack = ArrayList<Triple<Boolean, Boolean, Int>>()  // bold, italic and the skip depth before the group
    var unicodeSkip = 1
    var skipChars = 0
    var codepage = "windows-1252"
    var bytes = ArrayList<Byte>()

    fun flushBytes() {
        if (bytes.isNotEmpty()) {
            paragraph.append(escapeHtml(decodeText(bytes.toByteArray(), codepage)).let { styled(it, bold, italic) })
            bytes = ArrayList()
        }
    }
    fun add(s: String) {
        flushBytes()
        if (skipDepth < 0 && s.isNotEmpty()) paragraph.append(styled(escapeHtml(s), bold, italic))
    }
    fun endParagraph() {
        flushBytes()
        if (paragraph.isNotBlank()) body.append("<p>").append(paragraph).append("</p>\n")
        paragraph = StringBuilder()
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            skipChars > 0 && c != '\\' && c != '{' && c != '}' -> { skipChars--; i++ }
            c == '{' -> {
                stack.add(Triple(bold, italic, skipDepth))
                depth++
                // `{\*\x ...}` and the groups that hold no text are skipped whole
                val rest = text.substring(i + 1, minOf(text.length, i + 30))
                if (skipDepth < 0 && (rest.startsWith("\\*") || SKIPPED_GROUPS.any { rest.startsWith("\\$it") })) skipDepth = depth
                i++
            }
            c == '}' -> {
                flushBytes()
                if (skipDepth == depth) skipDepth = -1
                stack.removeLastOrNull()?.let { (b, i2, _) -> bold = b; italic = i2 }
                depth = (depth - 1).coerceAtLeast(0)
                i++
            }
            c == '\\' -> {
                i++
                if (i >= text.length) break
                val n = text[i]
                when {
                    n == '\\' || n == '{' || n == '}' -> { if (skipChars > 0) skipChars-- else add(n.toString()); i++ }
                    n == '~' -> { add(" "); i++ }
                    n == '_' -> { add("‑"); i++ }
                    n == '-' -> { add("­"); i++ }
                    n == '\'' -> {
                        val hex = text.substring(i + 1, minOf(text.length, i + 3))
                        hex.toIntOrNull(16)?.let { if (skipDepth < 0) bytes.add(it.toByte()) }
                        i += 3
                        // the byte ends a run of them that decode together
                        if (!(i + 1 < text.length && text[i] == '\\' && text[i + 1] == '\'')) flushBytes()
                    }
                    n == '\n' || n == '\r' -> { endParagraph(); i++ }
                    n.isLetter() -> {
                        var j = i
                        while (j < text.length && text[j].isLetter()) j++
                        val word = text.substring(i, j)
                        var k = j
                        if (k < text.length && text[k] == '-') k++
                        while (k < text.length && text[k].isDigit()) k++
                        val param = text.substring(j, k).toIntOrNull()
                        // one space after a control word belongs to it
                        i = if (k < text.length && text[k] == ' ') k + 1 else k
                        when (word) {
                            "par", "sect" -> endParagraph()
                            "line" -> { flushBytes(); if (skipDepth < 0) paragraph.append("<br>") }
                            "tab" -> add("\t")
                            "b" -> { flushBytes(); bold = param != 0 }
                            "i" -> { flushBytes(); italic = param != 0 }
                            "plain" -> { flushBytes(); bold = false; italic = false }
                            "uc" -> unicodeSkip = param ?: 1
                            "u" -> {
                                if (param != null && skipDepth < 0) add(String(charArrayOf((if (param < 0) param + 65536 else param).toChar())))
                                skipChars = unicodeSkip
                            }
                            "ansicpg" -> codepage = param?.let { "windows-$it" } ?: codepage
                            "emdash" -> add("—")
                            "endash" -> add("–")
                            "lquote" -> add("‘")
                            "rquote" -> add("’")
                            "ldblquote" -> add("“")
                            "rdblquote" -> add("”")
                            "bullet" -> add("•")
                        }
                    }
                    else -> i++
                }
            }
            c == '\n' || c == '\r' -> i++
            else -> {
                // a run of plain text
                var j = i
                while (j < text.length && text[j] != '\\' && text[j] != '{' && text[j] != '}' && text[j] != '\n' && text[j] != '\r') j++
                add(text.substring(i, j))
                i = j
            }
        }
    }
    endParagraph()
    return body.toString()
}

private val SKIPPED_GROUPS = listOf("fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "listtable", "listoverridetable", "generator", "filetbl", "revtbl", "rsidtbl", "themedata", "colorschememapping", "datastore", "latentstyles")

private fun styled(text: String, bold: Boolean, italic: Boolean): String {
    var t = text
    if (italic) t = "<i>$t</i>"
    if (bold) t = "<b>$t</b>"
    return t
}

internal fun escapeHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
