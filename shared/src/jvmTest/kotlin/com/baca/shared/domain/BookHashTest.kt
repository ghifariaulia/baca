package com.baca.shared.domain

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BookHashTest {

    private val tmp = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
    private val fs = FileSystem.SYSTEM

    private fun writeTemp(name: String, bytes: ByteArray): okio.Path {
        val path = tmp / name
        fs.write(path) { write(bytes) }
        return path
    }

    @Test
    fun `hash is stable for the same file`() {
        val p = writeTemp("hash-a.bin", ByteArray(2000) { (it % 251).toByte() })
        assertEquals(computeBookHash(p), computeBookHash(p))
    }

    @Test
    fun `small file under 64KiB - overlapping windows are fine`() {
        val p = writeTemp("hash-small.bin", "hello baca".toByteArray())
        val h1 = computeBookHash(p)
        assertEquals(64, h1.length)
        assertEquals(h1, computeBookHash(p))
    }

    @Test
    fun `different content gives different hash even with same size`() {
        val a = writeTemp("hash-b.bin", ByteArray(200_000) { (it % 7).toByte() })
        val b = writeTemp("hash-c.bin", ByteArray(200_000) { ((it + 3) % 7).toByte() })
        assertNotEquals(computeBookHash(a), computeBookHash(b))
    }

    @Test
    fun `same head different tail differs - the tail window matters`() {
        val sharedHead = ByteArray(200_000) { (it % 13).toByte() }
        val a = writeTemp("hash-d.bin", sharedHead)
        val b = writeTemp("hash-e.bin", sharedHead.copyOf().also { it[199_999] = (it[199_999] + 1).toByte() })
        assertNotEquals(computeBookHash(a), computeBookHash(b))
    }

    @Test
    fun `missing file hashes to empty string`() {
        assertEquals("", computeBookHash(tmp / "no-such-file.bin"))
    }
}
