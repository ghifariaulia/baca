package com.baca.app.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import com.baca.shared.parser.Block
import com.baca.shared.parser.Chapter
import com.baca.shared.parser.InlineImage

/** Width over height of a book image; square until it decodes. */
internal fun BookImages.aspect(src: String): Float =
    preload(src)?.takeIf { it.height > 0 }?.let { it.width.toFloat() / it.height } ?: 1f

/** The inline-content key an image span is drawn under. */
internal val InlineImage.id: String get() = "$heightEm $src"

/**
 * What each inline image of [chapter] draws in its placeholder. The image is a
 * mask in the page's [ink]: these are letter-sized marks (black circles on
 * white), so they read like the text around them on every reading theme.
 */
@Composable
internal fun rememberInlineImages(chapter: Chapter, images: BookImages, ink: Color): Map<String, InlineTextContent> =
    remember(chapter, ink) {
        val inkMask = ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0f, 0f, 0f, 0f, ink.red * 255,
                    0f, 0f, 0f, 0f, ink.green * 255,
                    0f, 0f, 0f, 0f, ink.blue * 255,
                    // dark pixels opaque, light and transparent ones clear
                    -0.299f, -0.587f, -0.114f, 1f, 0f,
                ),
            ),
        )
        chapter.blocks.flatMap { (it as? Block.Text)?.spans.orEmpty() }.mapNotNull { it.image }.distinct()
            .associate { image ->
                image.id to InlineTextContent(inlineImagePlaceholder(image, images.aspect(image.src))) {
                    images.image(image.src)?.let { bitmap ->
                        Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, colorFilter = inkMask)
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
): List<AnnotatedString.Range<Placeholder>> {
    val out = mutableListOf<AnnotatedString.Range<Placeholder>>()
    var offset = 0
    block.spans.forEach { span ->
        val image = span.image
        if (image != null && offset >= lineStart && offset < lineEnd) {
            val at = offset - lineStart
            out += AnnotatedString.Range(inlineImagePlaceholder(image, images.aspect(image.src)), at, at + span.text.length)
        }
        offset += span.text.length
    }
    return out
}
