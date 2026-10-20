package com.baca.shared.sync

/**
 * Pure merge core (docs/TECH.md 6.3): records in, records out. No IO, no clock
 * reads. Peer files are tagged with their originating device at READ time (the
 * file's own folder is the origin), so ordering is (lamport, device_id) with
 * the highest winning.
 */
data class Tagged<T>(val deviceId: String, val value: T)

internal fun <T> wins(a: Tagged<T>, b: Tagged<T>, lamport: (T) -> Long): Tagged<T> {
    val la = lamport(a.value)
    val lb = lamport(b.value)
    return when {
        la != lb -> if (la > lb) a else b
        a.deviceId != b.deviceId -> if (a.deviceId > b.deviceId) a else b
        // same device, same clock: normally the same record; if content differs
        // (corrupt input), break deterministically so order never matters
        else -> {
            val ha = a.value.hashCode()
            val hb = b.value.hashCode()
            if (ha > hb) a else b
        }
    }
}

/** Position rule: per book, highest (lamport, device_id) wins. */
fun mergePositions(
    perBook: Map<String, List<Tagged<StateFile>>>,
): Map<String, Tagged<StateFile>> =
    perBook.mapValues { (_, candidates) ->
        candidates.reduce { a, b -> wins(a, b) { it.lamport } }
    }

/** Annotation rule: union by id across all devices; per id, highest wins. */
fun mergeAnnotations(
    files: List<Tagged<AnnotsFile>>,
): Map<String, Tagged<AnnotationEntry>> {
    val byId = mutableMapOf<String, Tagged<AnnotationEntry>>()
    files.forEach { (device, file) ->
        file.annotations.forEach { entry ->
            val tagged = Tagged(device, entry)
            val current = byId[entry.id]
            byId[entry.id] = if (current == null) tagged else wins(current, tagged) { it.lamport }
        }
    }
    return byId
}

/** Library rule: union by book_hash; add and delete both resolve by clock. */
fun mergeLibrary(
    files: List<Tagged<LibraryFile>>,
): Map<String, Tagged<LibraryEntry>> {
    val byHash = mutableMapOf<String, Tagged<LibraryEntry>>()
    files.forEach { (device, file) ->
        file.books.forEach { entry ->
            val tagged = Tagged(device, entry)
            val current = byHash[entry.bookHash]
            byHash[entry.bookHash] = if (current == null) tagged else wins(current, tagged) { it.lamport }
        }
    }
    return byHash
}

/** Synced settings rule: per-key last-writer-wins by (lamport, device_id). */
fun mergeSettings(
    files: List<Tagged<SettingsFile>>,
): Map<String, Tagged<SyncedSetting>> {
    val byKey = mutableMapOf<String, Tagged<SyncedSetting>>()
    files.forEach { (device, file) ->
        file.settings.forEach { (key, setting) ->
            val tagged = Tagged(device, setting)
            val current = byKey[key]
            byKey[key] = if (current == null) tagged else wins(current, tagged) { it.lamport }
        }
    }
    return byKey
}

/**
 * Tombstone GC (TECH 6.4): a tombstone may be dropped once every known device
 * has a last-seen counter at or past the write that produced it, and it is at
 * least [minAgeDays] old. Returns the tombstones that are still needed.
 */
data class TombstoneRef(val id: String, val createdAt: Long, val writeCounter: Long)

fun keepTombstones(
    tombstones: List<TombstoneRef>,
    lastSeenCounter: Map<String, Long>,
    nowMs: Long,
    minAgeDays: Int = 90,
): List<TombstoneRef> {
    val minAgeMs = minAgeDays.toLong() * 24 * 3600 * 1000
    return tombstones.filter { tombstone ->
        tombstone.createdAt + minAgeMs > nowMs ||
            lastSeenCounter.any { (_, seen) -> seen < tombstone.writeCounter }
    }
}
