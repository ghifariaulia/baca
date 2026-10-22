package com.baca.app.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.unit.Density
import com.baca.shared.parser.Block
import com.baca.shared.parser.Chapter
import com.baca.shared.parser.InlineImage

/** Width over height of a book image; square until it decodes. */
internal fun BookImages.aspect(src: String): Float =
    preload(src)?.takeIf { it.height > 0 }?.let { it.width.toFloat() / it.height } ?: 1f

/** The inline-content key an image span is drawn under. */
internal val InlineImage.id: String get() = "$heightEm $src"

/**
 * What each inline image of [chapter] draws in its placeholder: the picture as the book has
 * it, under the same filter as any picture on the [page] (LibreraX's `img` rules).
 */
@Composable
internal fun rememberInlineImages(chapter: Chapter, images: BookImages, page: Color): Map<String, InlineTextContent> =
    remember(chapter, page) {
        chapter.blocks.flatMap { (it as? Block.Text)?.spans.orEmpty() }.mapNotNull { it.image }.distinct()
            .associate { image ->
                image.id to InlineTextContent(inlineImagePlaceholder(image, images.aspect(image.src))) {
                    images.image(image.src)?.let { bitmap ->
                        Image(
                            bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
                            colorFilter = pictureFilter(page) { images.onPlainGround(image.src) },
                        )
                    }
                }
            }
    }

/** Placeholders for the inline images in [lineStart, lineEnd) of [block], at offsets into the line. */
internal fun inlinePlaceholders(
    block: Block.Text,
    lineStart: Int,
    lineEnd: Int,
    images: BookImages,
    /** each span's spacer width in px, from the layout ([com.baca.shared.render.ReflowLayout.spacersOf]) */
    spacers: List<Int?>,
    density: Density,
): List<AnnotatedString.Range<Placeholder>> {
    val out = mutableListOf<AnnotatedString.Range<Placeholder>>()
    var offset = 0
    block.spans.forEachIndexed { i, span ->
        val placeholder = span.image?.let { inlineImagePlaceholder(it, images.aspect(it.src)) }
            ?: spacers.getOrNull(i)?.let { spacerPlaceholder(it, density) }
        if (placeholder != null && offset >= lineStart && offset < lineEnd) {
            val at = offset - lineStart
            out += AnnotatedString.Range(placeholder, at, at + span.text.length)
        }
        offset += span.text.length
    }
    return out
}

/** The inline-content key a spacer is drawn under: spacers of one width share one. */
internal fun spacerId(px: Int) = "space $px"

/** Empty inline content for each spacer width [layoutSpacers] holds. */
internal fun spacerContent(layoutSpacers: Collection<Int>, density: Density): Map<String, InlineTextContent> =
    layoutSpacers.distinct().associate { px -> spacerId(px) to InlineTextContent(spacerPlaceholder(px, density)) {} }
