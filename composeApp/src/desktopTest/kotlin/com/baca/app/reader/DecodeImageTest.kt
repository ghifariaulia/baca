package com.baca.app.reader

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DecodeImageTest {
    @Test
    fun `real epub cover jpeg decodes on desktop`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val tmp = okio.FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "decode-test.epub"
        okio.FileSystem.SYSTEM.write(tmp) { write(bytes) }
        val loader = com.baca.shared.parser.EpubImages(tmp)
        val jpeg = loader.imageBytes("OEBPS/1845375904528319168_cover.jpg")
        assertNotNull(jpeg)
        val bitmap = decodeImageBytes(jpeg)
        assertNotNull(bitmap, "JPEG should decode via javax.imageio")
        assertTrue(bitmap.width > 50, "decoded bitmap should have real size, got ${bitmap.width}x${bitmap.height}")
    }
}
