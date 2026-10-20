package com.baca.shared.sync

import com.baca.shared.db.Baca
import com.baca.shared.db.LOCAL_ANNOTATION_DEVICE
import com.baca.shared.db.WORK_KEY_PREFIX
import kotlinx.serialization.encodeToString

/**
 * The only IO at the sync boundary (docs/TECH.md 6.5): export this device's
 * DB through the transport, pull peers, merge (pure), persist winners.
 */
class SyncBoundary(
    private val db: Baca,
    private val deviceId: String,
    private val now: () -> Long,
) {
    private fun state() = db.sync_stateQueries.selectSyncStateAll().executeAsList()
        .firstOrNull { it.device_id == deviceId }

    /** Write this device's files as devices/<me>/… and bump the counter. */
    suspend fun exportAll(transport: SyncTransport) {
        val state = state()
        val ownCounter = (state?.own_counter ?: 0L) + 1
        val ownLamport = state?.own_lamport ?: 0L
        db.sync_stateQueries.upsertSyncState(deviceId, state?.last_seen_counter ?: 0L, ownCounter, ownLamport)

        val prefix = "devices/$deviceId"
        transport.push("$prefix/meta.json", SyncJson.encode(DeviceMeta(deviceId, counter = ownCounter, lamport = ownLamport, updatedAt = now())).encodeToByteArray())

        db.positionsQueries.selectPositions().executeAsList().forEach { row ->
            val locator = decodeLocator(row.locator) ?: return@forEach
            transport.push("$prefix/state/${row.book_hash}.json", SyncJson.encode(StateFile(locator, row.lamport, row.updated_at)).encodeToByteArray())
        }

        db.annotationsQueries.selectAnnotationsAll().executeAsList()
            .groupBy { it.book_hash }
            .forEach { (bookHash, rows) ->
                val entries = rows.mapNotNull { runCatching { SyncJson.decode<AnnotationEntry>(it.anchor) }.getOrNull() }
                transport.push("$prefix/annots/$bookHash.json", SyncJson.encode(AnnotsFile(entries)).encodeToByteArray())
            }

        transport.push("$prefix/library.json", SyncJson.encode(localLibrary()).encodeToByteArray())

        val settings = SettingsFile(
            db.settings_syncedQueries.selectSyncedAll().executeAsList().associate { row ->
                row.key to SyncedSetting(JsonString(row.value_), row.lamport)
            },
        )
        transport.push("$prefix/settings.json", SyncJson.encode(settings).encodeToByteArray())
    }

    data class MergeReport(
        val positions: Int,
        val annotations: Int,
        val library: Int,
        val settings: Int,
        /** Local position rows that a peer's record overwrote (for Undo). */
        val positionOverwrites: List<PositionOverwrite>,
    )

    data class PositionOverwrite(val bookHash: String, val previousLocator: String, val winningDevice: String)

    /** Pull changed peers, run pure merges, persist winners, update sync_state. */
    suspend fun pullAndMerge(transport: SyncTransport): MergeReport {
        val files = transport.list()
        val peers = files.mapNotNull { relative ->
            relative.substringAfter("devices/").substringBefore('/').takeIf { it.isNotEmpty() && it != deviceId }
        }.distinct()

        val stateFiles = mutableMapOf<String, MutableList<Tagged<StateFile>>>()
        val annotFiles = mutableListOf<Tagged<AnnotsFile>>()
        // AnnotationEntry carries no book hash; it comes from the file name
        // (peers) or the row (local)
        val annotBookHash = mutableMapOf<String, String>()
        val libFiles = mutableListOf<Tagged<LibraryFile>>()
        val settingFiles = mutableListOf<Tagged<SettingsFile>>()
        val overwrites = mutableListOf<PositionOverwrite>()
        var highestPeerLamport = 0L

        for (peer in peers) {
            val meta = transport.pull("devices/$peer/meta.json")
                ?.let { runCatching { SyncJson.decode<DeviceMeta>(it.decodeToString()) }.getOrNull() }
                ?: continue
            highestPeerLamport = maxOf(highestPeerLamport, meta.lamport)

            val seen = state()?.let { if (it.device_id == peer) it.last_seen_counter else 0L }
                ?: db.sync_stateQueries.selectSyncStateAll().executeAsList()
                    .firstOrNull { it.device_id == peer }?.last_seen_counter ?: 0L
            if (meta.counter <= seen) continue

            files.filter { it.startsWith("devices/$peer/state/") }.forEach { path ->
                transport.pull(path)?.let { bytes ->
                    runCatching { SyncJson.decode<StateFile>(bytes.decodeToString()) }.getOrNull()
                        ?.let { stateFile ->
                            val hash = path.substringAfterLast('/').removeSuffix(".json")
                            stateFiles.getOrPut(hash) { mutableListOf() }.add(Tagged(peer, stateFile))
                        }
                }
            }
            files.filter { it.startsWith("devices/$peer/annots/") }.forEach { path ->
                transport.pull(path)?.let { bytes ->
                    runCatching { SyncJson.decode<AnnotsFile>(bytes.decodeToString()) }.getOrNull()
                        ?.let { file ->
                            val hash = path.substringAfterLast('/').removeSuffix(".json")
                            file.annotations.forEach { annotBookHash[it.id] = hash }
                            annotFiles.add(Tagged(peer, file))
                        }
                }
            }
            transport.pull("devices/$peer/library.json")?.let { bytes ->
                runCatching { SyncJson.decode<LibraryFile>(bytes.decodeToString()) }.getOrNull()
                    ?.let { libFiles.add(Tagged(peer, it)) }
            }
            transport.pull("devices/$peer/settings.json")?.let { bytes ->
                runCatching { SyncJson.decode<SettingsFile>(bytes.decodeToString()) }.getOrNull()
                    ?.let { settingFiles.add(Tagged(peer, it)) }
            }

            val row = db.sync_stateQueries.selectSyncStateAll().executeAsList()
                .firstOrNull { it.device_id == peer }
            db.sync_stateQueries.upsertSyncState(peer, meta.counter, row?.own_counter ?: 0L, row?.own_lamport ?: 0L)
        }

        // local records join as candidates
        db.positionsQueries.selectPositions().executeAsList().forEach { row ->
            decodeLocator(row.locator)?.let { locator ->
                stateFiles.getOrPut(row.book_hash) { mutableListOf() }
                    .add(Tagged(row.device_id, StateFile(locator, row.lamport, row.updated_at)))
            }
        }
        db.annotationsQueries.selectAnnotationsAll().executeAsList().forEach { row ->
            runCatching { SyncJson.decode<AnnotationEntry>(row.anchor) }.getOrNull()
                ?.let {
                    annotBookHash[it.id] = row.book_hash
                    // peers tag our entries with our real id; tag them the same
                    // here so (lamport, device_id) ties break identically everywhere
                    val origin = if (row.device_id == LOCAL_ANNOTATION_DEVICE) deviceId else row.device_id
                    annotFiles.add(Tagged(origin, AnnotsFile(listOf(it))))
                }
        }
        libFiles.add(Tagged(deviceId, localLibrary()))
        settingFiles.add(
            Tagged(
                deviceId,
                SettingsFile(
                    db.settings_syncedQueries.selectSyncedAll().executeAsList().associate { row ->
                        row.key to SyncedSetting(JsonString(row.value_), row.lamport)
                    },
                ),
            ),
        )

        var positionWrites = 0
        mergePositions(stateFiles).forEach { (hash, winner) ->
            val local = db.positionsQueries.selectPosition(hash).executeAsOneOrNull()
            if (winner.deviceId != deviceId) {
                if (local != null && local.device_id == deviceId) {
                    // the local position lost the merge; remember it for Undo
                    overwrites.add(PositionOverwrite(hash, local.locator, winner.deviceId))
                }
                db.positionsQueries.setPosition(
                    book_hash = hash,
                    device_id = winner.deviceId,
                    locator = SyncJson.encode(winner.value.locator),
                    lamport = winner.value.lamport,
                    updated_at = winner.value.updatedAt,
                )
                positionWrites++
            }
        }

        var annotWrites = 0
        mergeAnnotations(annotFiles).forEach { (_, winner) ->
            val entry = winner.value
            db.annotationsQueries.upsertAnnotation(
                id = entry.id,
                book_hash = annotBookHash[entry.id] ?: "",
                kind = entry.kind,
                anchor = SyncJson.encode(entry),
                text = entry.text,
                note = entry.note,
                created_at = entry.createdAt,
                device_id = if (winner.deviceId == deviceId) LOCAL_ANNOTATION_DEVICE else winner.deviceId,
                lamport = entry.lamport,
                deleted = if (entry.deleted) 1L else 0L,
            )
            annotWrites++
        }

        var libWrites = 0
        mergeLibrary(libFiles).forEach { (_, winner) ->
            val entry = winner.value
            val localRow = db.booksQueries.selectBookByHash(entry.bookHash).executeAsOneOrNull()
            db.booksQueries.insertBook(
                book_hash = entry.bookHash,
                title = entry.title,
                author = localRow?.author ?: "",
                path = localRow?.path ?: "",
                format = localRow?.format ?: "remote",
                cover_path = localRow?.cover_path,
                added_at = entry.addedAt,
                lamport = entry.lamport,
                deleted = if (entry.deleted) 1L else 0L,
            )
            // an older peer's entry carries no key; keep the one we know
            entry.workKey?.let { db.settings_localQueries.setLocal(WORK_KEY_PREFIX + entry.bookHash, it) }
            libWrites++
        }

        var settingWrites = 0
        mergeSettings(settingFiles).forEach { (key, winner) ->
            db.settings_syncedQueries.putSynced(key, winner.value.value.raw, winner.value.lamport)
            settingWrites++
        }

        if (highestPeerLamport > 0L) {
            val own = state()
            if (own != null && highestPeerLamport > own.own_lamport) {
                db.sync_stateQueries.upsertSyncState(deviceId, own.last_seen_counter, own.own_counter, highestPeerLamport)
            }
        }

        return MergeReport(positionWrites, annotWrites, libWrites, settingWrites, overwrites)
    }

    private fun localLibrary() = LibraryFile(
        db.booksQueries.selectBooksAll().executeAsList().map { row ->
            val workKey = db.settings_localQueries.getLocal(WORK_KEY_PREFIX + row.book_hash).executeAsOneOrNull()
                ?.value_?.takeIf { it.isNotEmpty() }
            LibraryEntry(row.book_hash, row.title, row.added_at, row.lamport, row.deleted != 0L, workKey)
        },
    )

    private fun decodeLocator(json: String): PositionLocator? = runCatching {
        if (json.contains("\"href\"")) SyncJson.decode<ReflowLocator>(json) else SyncJson.decode<FixedPageLocator>(json)
    }.getOrNull()
}
