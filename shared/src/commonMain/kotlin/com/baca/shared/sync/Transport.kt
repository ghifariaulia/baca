package com.baca.shared.sync

/**
 * One transport interface (docs/TECH.md 6.6). Paths are the per-device file
 * layout relative to the sync root, e.g. "devices/<id>/library.json".
 * Each device writes only into its own folder; reads are unconstrained.
 */
interface SyncTransport {
    suspend fun push(relativePath: String, bytes: ByteArray)
    suspend fun pull(relativePath: String): ByteArray?

    /** All files in the sync root (relative paths). */
    suspend fun list(): List<String>
}
