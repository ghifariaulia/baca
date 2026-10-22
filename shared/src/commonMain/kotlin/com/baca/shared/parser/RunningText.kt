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
internal fun withRunningText(chapters: List<Chapter>, embedded: Set<String> = emptySet()): List<Chapter> {
    val sizes = mutableMapOf<Int, Int>()
    val families = mutableMapOf<String, Int>()
    var total = 0
    chapters.forEach { chapter ->
        chapter.blocks.forEach { block ->
            if (block !is Block.Text || block.kind == BlockKind.HEADING) return@forEach
            block.spans.forEach { span ->
                val chars = span.text.count { !it.isWhitespace() }
                val key = sizeKey((block.scale ?: 1f) * span.scale)
                sizes[key] = (sizes[key] ?: 0) + chars
                val family = span.family ?: ""
                families[family] = (families[family] ?: 0) + chars
                total += chars
            }
        }
    }
    // too little text to tell: each chapter's body stands for it, as LibreraX takes the section's body
    if (total < RUNNING_TEXT_ENOUGH) return chapters.map { chapter ->
        runningIn(chapter, chapter.bodyScale, Running(chapter.bodyFamily ?: "", embedded))
    }
    // the size and the family most text is set in: the reader's take their place
    val size = sizes.maxBy { it.value }.key / SIZE_STEPS
    val running = Running(families.maxByOrNull { it.value }?.key ?: "", embedded)
    return chapters.map { chapter -> runningIn(chapter, size, running) }
}

/** [chapter] with [size] as the reader's size and [running]'s family as the reader's font. */
private fun runningIn(chapter: Chapter, size: Float, running: Running): Chapter =
    chapter.copy(
        blocks = chapter.blocks.map { block ->
            when (block) {
                is Block.Text -> block.runningIn(size, running)
                is Block.Image -> block.copy(
                    gapBefore = block.gapBefore?.let { spacing(it, size) },
                    marginBefore = block.marginBefore?.let { spacing(it, size) },
                    inset = spacing(block.inset, size),
                    insetEnd = spacing(block.insetEnd, size),
                    boxes = block.boxes.map { it.runningIn(size) },
                    strut = block.strut?.let { running.faceOf(it).copy(scale = it.scale / size) },
                )
                is Block.Rule -> block.copy(
                    gapBefore = block.gapBefore?.let { spacing(it, size) },
                    marginBefore = block.marginBefore?.let { spacing(it, size) },
                    height = block.height?.let { spacing(it, size) },
                    boxes = block.boxes.map { it.runningIn(size) },
                )
            }
        },
    )

/**
 * The running text's family, and the fonts the book ships: text in the running family is
 * set in the reader's font; other text keeps its family, a font the book ships by name,
 * else the system's face of its kind (a serif the book names isn't the reader's font).
 */
private class Running(val family: String, val embedded: Set<String>) {
    fun faceOf(span: FormattedSpan): FormattedSpan =
        if ((span.family ?: "") == family) span.copy(face = null, family = null)
        else span.copy(face = span.face ?: BookFace.SERIF, family = span.family?.takeIf { it in embedded })
}

private fun Block.Text.runningIn(size: Float, running: Running): Block.Text = copy(
    scale = ((scale ?: 1f) / size).takeIf { abs(it - 1f) > 0.001f },
    spans = spans.map(running::faceOf),
    gapBefore = gapBefore?.let { spacing(it, size) },
    marginBefore = marginBefore?.let { spacing(it, size) },
    indent = spacing(indent, size),
    inset = spacing(inset, size),
    insetEnd = spacing(insetEnd, size),
    boxes = boxes.map { it.runningIn(size) },
    cell = cell?.let { it.copy(ownStart = spacing(it.ownStart, size), ownEnd = spacing(it.ownEnd, size), spacing = spacing(it.spacing, size)) },
)

private fun BlockBox.runningIn(size: Float) = copy(
    left = spacing(left, size),
    right = spacing(right, size),
    topInset = spacing(topInset, size),
    bottomInset = spacing(bottomInset, size),
)

/**
 * [space]'s em (of the browser's 16px) in em of the running text; its CSS px stay
 * as they are. Spacing 4em of the running text or wider is halved and kept in em of
 * it, as LibreraX's `pinType` pins it.
 */
private fun spacing(space: Spacing, size: Float): Spacing =
    space.partsOrSelf().fold(space.relative()) { sum, part -> sum + partIn(part, size) }.let { Spacing(it.em, it.px, pageH = it.pageH, windowH = it.windowH, width = it.width, windowW = it.windowW) }

/** One element's spacing in em of the running text, halved when it's 4em of it or more. */
private fun partIn(part: Spacing, size: Float): Spacing {
    val runningPx = size * BROWSER_EM_PX
    val px = part.em * BROWSER_EM_PX + part.px
    return if (abs(px) >= SPACE_WIDE * runningPx) Spacing(em = px / 2 / runningPx) else Spacing(part.em / size, part.px)
}

// LibreraX's SPACE_WIDE: spacing this wide (in em of the running text) is halved
private const val SPACE_WIDE = 4f

private const val BROWSER_EM_PX = 16f

// sizes a hundredth apart are one size
private fun sizeKey(scale: Float): Int = (scale * SIZE_STEPS).roundToInt()

private const val SIZE_STEPS = 100f

// LibreraX's threshold, in characters
private const val RUNNING_TEXT_ENOUGH = 200
