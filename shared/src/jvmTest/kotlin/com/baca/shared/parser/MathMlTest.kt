package com.baca.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class MathMlTest {

    private fun math(inner: String) = mathmlToHtml("""<p>a <math xmlns="http://www.w3.org/1998/Math/MathML">$inner</math> b</p>""")
        .removePrefix("<p>a ").removeSuffix(" b</p>")

    @Test
    fun `powers, subscripts, fractions and roots are written along the line`() {
        assertEquals("<i>x</i><sup>2</sup> + 1", math("<msup><mi>x</mi><mn>2</mn></msup><mo>+</mo><mn>1</mn>"))
        assertEquals("<i>a</i><sub>i</sub>", math("<msub><mi>a</mi><mi>i</mi></msub>").replace("<i>i</i>", "i"))
        assertEquals("<i>a</i>⁄<i>b</i>", math("<mfrac><mi>a</mi><mi>b</mi></mfrac>"))
        assertEquals("(<i>a</i> + 1)⁄2", math("<mfrac><mrow><mi>a</mi><mo>+</mo><mn>1</mn></mrow><mn>2</mn></mfrac>"))
        assertEquals("√(<i>x</i>)", math("<msqrt><mi>x</mi></msqrt>"))
        assertEquals("<sup>3</sup>√(<i>x</i>)", math("<mroot><mi>x</mi><mn>3</mn></mroot>"))
    }

    @Test
    fun `a sum with limits, fenced lists and a table`() {
        assertEquals("∑<sub>i=0</sub><sup>n</sup>", math("<munderover><mo>∑</mo><mrow><mi>i</mi><mo>=</mo><mn>0</mn></mrow><mi>n</mi></munderover>").replace("<i>i</i> = 0", "i=0").replace("<i>n</i>", "n"))
        assertEquals("(1,2)", math("""<mfenced><mn>1</mn><mn>2</mn></mfenced>"""))
        assertEquals("1 2<br/>3 4", math("<mtable><mtr><mtd><mn>1</mn></mtd><mtd><mn>2</mn></mtd></mtr><mtr><mtd><mn>3</mn></mtd><mtd><mn>4</mn></mtd></mtr></mtable>"))
    }

    @Test
    fun `a page with a formula reads it in the line, a block formula on its own`() {
        val blocks = parseXhtml("""<html><body><p>Let <math><msup><mi>x</mi><mn>2</mn></msup></math> be.</p><math display="block"><mfrac><mn>1</mn><mn>2</mn></mfrac></math></body></html>""")
        assertEquals(listOf("Let x2 be.", "1⁄2"), blocks.map { it.text })
        // the 2 is a superscript: raised and smaller
        val span = (blocks[0] as Block.Text).spans.first { it.text == "2" }
        assertEquals(true, span.baseline > 0f && span.scale < 1f)
    }
}
