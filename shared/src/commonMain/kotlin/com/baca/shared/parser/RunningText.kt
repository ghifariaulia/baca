package com.baca.shared.parser

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sets a book's running text in the reader's own size and font, as LibreraX does
 * (TECH 3.1): the size and face most of its body text is set in are the book's
 * running text. That size becomes the reader's, and every other size keeps its
 * ratio to it; that face becomes the reader's font, and any other one (a
 * transcript's monospace, a sign's sans-serif) is kept. Spacing, which the
 * parser gives in em of the browser's 16px, is put in em of the running text,
 * and any of it 4em or more is halved, as LibreraX's `pinType` halves spacing
 * wider than a phone can spare.
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
    if (total < RUNNING_TEXT_ENOUGH) return chapters.map { chapter ->
        chapter.copy(blocks = chapter.blocks.map { if (it is Block.Text) it.runningIn(1f, null) else it })
    }
    val size = sizes.maxBy { it.value }.key / SIZE_STEPS
    val face = faces.maxBy { it.value }.key
    return chapters.map { chapter ->
        chapter.copy(
            blocks = chapter.blocks.map { block ->
                when (block) {
                    is Block.Text -> block.runningIn(size, face)
                    is Block.Image -> block.copy(gapBefore = block.gapBefore?.let { spacing(it, size) })
                    is Block.Rule -> block.copy(gapBefore = block.gapBefore?.let { spacing(it, size) }, height = block.height?.let { spacing(it, size) })
                }
            },
        )
    }
}

private fun Block.Text.runningIn(size: Float, face: BookFace?): Block.Text = copy(
    scale = ((scale ?: 1f) / size).takeIf { abs(it - 1f) > 0.001f },
    spans = if (face == null) spans else spans.map { if (it.face == face) it.copy(face = null) else it },
    gapBefore = gapBefore?.let { spacing(it, size) },
    indent = spacing(indent, size),
    inset = spacing(inset, size),
    insetEnd = spacing(insetEnd, size),
    leftRule = leftRule?.let { it.copy(at = spacing(it.at, size)) },
)

/**
 * [space]'s em (of the browser's 16px) in em of the running text; its CSS px stay
 * as they are. Spacing 4em of the running text or wider is halved and kept in em of
 * it, as LibreraX's `pinType` pins it.
 */
private fun spacing(space: Spacing, size: Float): Spacing {
    val runningPx = size * BROWSER_EM_PX
    val px = space.em * BROWSER_EM_PX + space.px
    return if (abs(px) >= SPACE_WIDE * runningPx) Spacing(em = px / 2 / runningPx) else Spacing(space.em / size, space.px)
}

// LibreraX's SPACE_WIDE: spacing this wide (in em of the running text) is halved
private const val SPACE_WIDE = 4f

private const val BROWSER_EM_PX = 16f

// sizes a hundredth apart are one size
private fun sizeKey(scale: Float): Int = (scale * SIZE_STEPS).roundToInt()

private const val SIZE_STEPS = 100f

// LibreraX's threshold, in characters
private const val RUNNING_TEXT_ENOUGH = 200
