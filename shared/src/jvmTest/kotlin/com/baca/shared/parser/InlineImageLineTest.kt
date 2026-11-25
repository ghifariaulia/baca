package com.baca.shared.parser

import com.baca.shared.render.GridMetrics
import com.baca.shared.render.ReflowLayout
import com.baca.shared.render.ReflowSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineImageLineTest {

    @Test
    fun `inline image up to 6em stays in paragraph and grows line height`() {
        val html = """
            <html xmlns="http://www.w3.org/1999/xhtml">
            <body>
            <p>Text <img src="pic.png" style="height: 2em"/> more</p>
            </body>
            </html>
        """.trimIndent()
        val blocks = parseXhtml(html, resolveSrc = { it })
        assertEquals(1, blocks.size)
        val para = blocks.single() as Block.Text
        assertTrue(para.spans.any { it.image?.heightEm == 2f })

        val layout = ReflowLayout(
            blocks = blocks,
            metrics = GridMetrics(),
            settings = ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1.2f, imageHeightPx = 100),
            columnWidthPx = 400,
            pageHeightPx = 400,
        )
        val line = layout.lines.single()
        // 2em at 20px = 40px, taller than 20 * 1.2 = 24px
        assertTrue(line.growAbove > 0f, "expected growAbove > 0 for 2em inline image, was ${line.growAbove}")
    }
}
