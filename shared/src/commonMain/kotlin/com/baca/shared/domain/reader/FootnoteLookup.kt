package com.baca.shared.domain.reader

import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.Chapter

/** Resolved footnote/endnote content for inline preview without leaving the current page. */
data class FootnoteTarget(
    val title: String,
    val text: String,
    val href: String,
)

/**
 * Resolves [href] to a [FootnoteTarget] when it points into a non-linear chapter (`linear="no"`,
 * FB2 notes) or a fragment id that looks like a footnote/endnote. Returns null for ordinary links.
 */
fun resolveFootnote(chapters: List<Chapter>, href: String): FootnoteTarget? {
    val target = indexOfChapter(href, chapters)
    if (target < 0) return null
    val chapter = chapters[target]
    val fragment = href.substringAfter('#', "")
    if (chapter.linear && !looksLikeFootnoteId(fragment)) return null

    val startBlock = fragment.takeIf { it.isNotEmpty() }?.let { chapter.anchorBlocks[it] } ?: 0
    if (startBlock !in chapter.blocks.indices) return null

    val stopBlocks = chapter.anchorBlocks.values.filter { it > startBlock }.toSet()
    val parts = mutableListOf<String>()
    var totalChars = 0
    for (i in startBlock until chapter.blocks.size) {
        if (i > startBlock && (i in stopBlocks || parts.size >= 6 || totalChars >= 1200)) break
        val block = chapter.blocks[i]
        if (i > startBlock && block is Block.Text && block.kind == BlockKind.HEADING) break
        if (block is Block.Text) {
            val trimmed = block.text.trim()
            if (trimmed.isNotEmpty()) {
                parts += trimmed
                totalChars += trimmed.length
            }
        }
    }
    val body = parts.joinToString("\n\n")
    if (body.isEmpty()) return null
    val title = chapter.title?.takeIf { it.isNotBlank() } ?: "Footnote"
    return FootnoteTarget(title = title, text = body, href = href)
}

private fun looksLikeFootnoteId(fragment: String): Boolean {
    if (fragment.isEmpty()) return false
    val lower = fragment.lowercase()
    return lower.startsWith("fn") ||
        lower.startsWith("ftn") ||
        lower.startsWith("note") ||
        lower.contains("footnote") ||
        lower.contains("endnote")
}
