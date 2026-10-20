package com.baca.shared.sync

/** In-memory transport for tests; mirrors the interface exactly. */
class MemoryTransport : SyncTransport {
    val files = mutableMapOf<String, ByteArray>()
    private val written = mutableListOf<String>()

    override suspend fun push(relativePath: String, bytes: ByteArray) {
        files[relativePath] = bytes
        written.add(relativePath)
    }

    override suspend fun pull(relativePath: String): ByteArray? = files[relativePath]

    override suspend fun list(): List<String> = files.keys.toList()

    /** Device A should never write into device B's folder (TECH 6.1). */
    fun writtenPaths(): List<String> = written.toList()
}
