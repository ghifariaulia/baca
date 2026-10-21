package com.baca.shared.parser

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sets a book's running text in the reader's own size and font, as LibreraX does
 * (TECH 3.1): the size and face most of its body text is set in are the book's
 * running text. That size becomes the reader's, and every other size keeps its
 * ratio to it; that face becomes the reader's font, and any other one (a
 * transcript's monospace, a sign's sans-serif) is kept.
 *
 * A book with too little body text to tell keeps its sizes and faces as they are.
 */
internal fun withRunningText(chapters: List<Chapter>): List<Chapter> {
    val sizes = mutableMapOf<Int, Int>()
    val faces = mutableMapOf<BookFace?, Int>()
    var total = 0
    chapters.forEach { chapter ->
        chapter.blocks.forEach { block ->
            if (block !is Block.Text || block.kind == BlockKind.HEADING) return@forEach
            block.spans.forEach { span ->
                val chars = span.text.count { !it.isWhitespace() }
                val key = sizeKey((block.scale ?: 1f) * span.scale)
                sizes[key] = (sizes[key] ?: 0) + chars
                faces[span.face] = (faces[span.face] ?: 0) + chars
                total += chars
            }
        }
    }
    if (total < RUNNING_TEXT_ENOUGH) return chapters
    val size = sizes.maxBy { it.value }.key / SIZE_STEPS
    val face = faces.maxBy { it.value }.key
    if (size == 1f && face == null) return chapters
    return chapters.map { chapter ->
        chapter.copy(blocks = chapter.blocks.map { block -> if (block is Block.Text) block.runningIn(size, face) else block })
    }
}

private fun Block.Text.runningIn(size: Float, face: BookFace?): Block.Text = copy(
    // a heading the book leaves unsized keeps the reader's heading size
    scale = if (scale == null && kind == BlockKind.HEADING) null else ((scale ?: 1f) / size).takeIf { abs(it - 1f) > 0.001f },
    spans = if (face == null) spans else spans.map { if (it.face == face) it.copy(face = null) else it },
)

// sizes a hundredth apart are one size
private fun sizeKey(scale: Float): Int = (scale * SIZE_STEPS).roundToInt()

private const val SIZE_STEPS = 100f

// LibreraX's threshold, in characters
private const val RUNNING_TEXT_ENOUGH = 200
