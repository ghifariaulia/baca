package com.baca.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class RubyParserTest {

    @Test
    fun `parses ruby base text and attaches rt annotations without polluting block text`() {
        val html = """
            <html xmlns="http://www.w3.org/1999/xhtml">
            <body>
            <p><ruby>漢<rp>(</rp><rt>かん</rt><rp>)</rp>字<rp>(</rp><rt>じ</rt><rp>)</rp></ruby></p>
            </body>
            </html>
        """.trimIndent()
        val blocks = parseXhtml(html, resolveSrc = { it })
        val para = blocks.single() as Block.Text
        assertEquals("漢字", para.text)
        assertEquals(listOf("漢" to "かん", "字" to "じ"), para.spans.map { it.text to it.ruby })
    }
}
