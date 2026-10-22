package com.baca.shared.render

import kotlin.test.Test
import kotlin.test.assertEquals

class HybHyphenatorTest {

    // Android's own files, as /system/usr/hyphen-data holds them
    private fun system(locale: String) =
        HybHyphenator(javaClass.getResourceAsStream("/hyphen/hyph-$locale.hyb")!!.readBytes(), capitalized = !locale.startsWith("en"))

    private fun Hyphenator.hyphenated(word: String) = points(word).foldRight(word) { at, w -> w.substring(0, at) + "-" + w.substring(at) }

    @Test
    fun `German hyphenates by the system's patterns, capitals and all`() {
        val de = system("de-1996")
        assertEquals("Sil-ben-tren-nung", de.hyphenated("Silbentrennung"))
        assertEquals("Do-nau-dampf-schiff-fahrt", de.hyphenated("Donaudampfschifffahrt"))
        assertEquals("Bü-cher-re-gal", de.hyphenated("Bücherregal"))
    }

    @Test
    fun `the system's English patterns break as the bundled ones do`() {
        val en = system("en-us")
        val bundled = Hyphenator.forLanguage("en")!!
        listOf("influential", "family", "reportedly", "inherit", "hanging", "Influential", "it's").forEach { word ->
            assertEquals(bundled.points(word), en.points(word), word)
        }
    }

    @Test
    fun `a language falls back to the patterns Chromium takes for it`() {
        assertEquals("en-us", hyphenLocaleOf("en"))
        assertEquals("en-us", hyphenLocaleOf("en-US"))
        assertEquals("en-gb", hyphenLocaleOf("en-AU"))
        assertEquals("de-1996", hyphenLocaleOf("de-DE"))
        assertEquals("nb", hyphenLocaleOf("no"))
        assertEquals("fr", hyphenLocaleOf("fr-CA"))
    }
}
