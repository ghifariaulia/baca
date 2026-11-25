package com.baca.shared.parser

import kotlin.test.Test
import kotlin.test.assertTrue

class MathMl2dTest {

    @Test
    fun `renders mfrac as stacked fraction, msqrt with vinculum, and mtable as html table`() {
        val frac = mathmlToHtml("<math><mfrac><mi>a</mi><mi>b</mi></mfrac></math>")
        assertTrue(frac.contains("border-bottom"), "expected stacked fraction border-bottom in: $frac")
        assertTrue(frac.contains("a") && frac.contains("b"))

        val sqrt = mathmlToHtml("<math><msqrt><mi>x</mi></msqrt></math>")
        assertTrue(sqrt.contains("√") && sqrt.contains("border-top"), "expected vinculum border-top in: $sqrt")

        val matrix = mathmlToHtml("<math><mtable><mtr><mtd><mn>1</mn></mtd><mtd><mn>2</mn></mtd></mtr><mtr><mtd><mn>3</mn></mtd><mtd><mn>4</mn></mtd></mtr></mtable></math>")
        assertTrue(matrix.contains("<table") && matrix.contains("<tr>") && matrix.contains("<td"), "expected 2D table in: $matrix")
    }
}
