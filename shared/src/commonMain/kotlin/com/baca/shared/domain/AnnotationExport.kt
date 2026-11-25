package com.baca.shared.domain

/** A book and its annotations for Markdown export. */
data class ExportBook(
    val title: String,
    val author: String,
    val entries: List<AnnotationEntry>,
)

/** Formats [books]'s non-deleted annotations as Markdown grouped by book and chapter/page. */
fun formatAnnotationsMarkdown(
    books: List<ExportBook>,
    includeBookmarks: Boolean = false,
): String {
    val sections = books.mapNotNull { book ->
        val visible = book.entries.filter { entry ->
            !entry.deleted && (includeBookmarks || entry.kind != "bookmark")
        }
        if (visible.isEmpty()) return@mapNotNull null
        buildString {
            val heading = if (book.author.isNotBlank()) "# ${book.title} — ${book.author}" else "# ${book.title}"
            appendLine(heading)
            val bySection = visible.groupBy { entry ->
                entry.reflowAnchor?.href ?: entry.fixedAnchor?.let { "Page ${it.pageIndex + 1}" } ?: "Notes"
            }
            bySection.forEach { (section, items) ->
                appendLine()
                appendLine("## $section")
                items.forEach { entry ->
                    appendLine()
                    val page = entry.reflowAnchor?.page?.let { " (Page $it)" } ?: ""
                    val quote = entry.text.trim()
                    if (quote.isNotEmpty()) {
                        quote.lines().forEach { line -> appendLine("> $line") }
                    } else if (entry.kind == "bookmark") {
                        appendLine("- Bookmark$page")
                    }
                    val note = entry.note.trim()
                    if (note.isNotEmpty()) {
                        appendLine()
                        appendLine(note)
                    }
                    if (quote.isNotEmpty() && page.isNotEmpty()) {
                        appendLine()
                        appendLine("*$page*".replace(" (", "(").trim())
                    }
                }
            }
        }.trimEnd()
    }
    return sections.joinToString("\n\n---\n\n")
}
