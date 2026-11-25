package com.baca.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MathMlTest {

    private fun math(inner: String) = mathmlToHtml("""<p>a <math xmlns="http://www.w3.org/1998/Math/MathML">$inner</math> b</p>""")
        .removePrefix("<p>a ").removeSuffix(" b</p>")

    @Test
    fun `powers, subscripts, fractions and roots are laid out in 2D`() {
        assertEquals("<i>x</i><sup>2</sup> + 1", math("<msup><mi>x</mi><mn>2</mn></msup><mo>+</mo><mn>1</mn>"))
        assertEquals("<i>a</i><sub>i</sub>", math("<msub><mi>a</mi><mi>i</mi></msub>").replace("<i>i</i>", "i"))
        val frac = math("<mfrac><mi>a</mi><mi>b</mi></mfrac>")
        assertTrue(frac.contains("border-bottom") && frac.contains("<i>a</i>") && frac.contains("<i>b</i>"))
        val sqrt = math("<msqrt><mi>x</mi></msqrt>")
        assertTrue(sqrt.contains("√") && sqrt.contains("border-top") && sqrt.contains("<i>x</i>"))
        val mroot = math("<mroot><mi>x</mi><mn>3</mn></mroot>")
        assertTrue(mroot.contains("<sup>3</sup>") && mroot.contains("√") && mroot.contains("border-top"))
    }

    @Test
    fun `a sum with limits, fenced lists and a table`() {
        assertEquals("∑<sub>i=0</sub><sup>n</sup>", math("<munderover><mo>∑</mo><mrow><mi>i</mi><mo>=</mo><mn>0</mn></mrow><mi>n</mi></munderover>").replace("<i>i</i> = 0", "i=0").replace("<i>n</i>", "n"))
        assertEquals("(1,2)", math("""<mfenced><mn>1</mn><mn>2</mn></mfenced>"""))
        val tbl = math("<mtable><mtr><mtd><mn>1</mn></mtd><mtd><mn>2</mn></mtd></mtr><mtr><mtd><mn>3</mn></mtd><mtd><mn>4</mn></mtd></mtr></mtable>")
        assertTrue(tbl.contains("<table") && tbl.contains("<tr>") && tbl.contains("<td") && tbl.contains("1") && tbl.contains("4"))
    }

    @Test
    fun `a page with a formula reads it in the line, a block formula on its own`() {
        val blocks = parseXhtml("""<html><body><p>Let <math><msup><mi>x</mi><mn>2</mn></msup></math> be.</p><math display="block"><mfrac><mn>1</mn><mn>2</mn></mfrac></math></body></html>""")
        assertEquals("Let x2 be.", blocks[0].text)
        assertTrue(blocks.drop(1).joinToString("") { it.text }.contains("1") && blocks.drop(1).joinToString("") { it.text }.contains("2"))
        // the 2 is a superscript: raised and smaller
        val span = (blocks[0] as Block.Text).spans.first { it.text == "2" }
        assertEquals(true, span.baseline > 0f && span.scale < 1f)
    }
}
