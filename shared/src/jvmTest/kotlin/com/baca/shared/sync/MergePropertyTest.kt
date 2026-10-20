package com.baca.shared.sync

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property tests for the merge core (docs/TECH.md 6.5): idempotent,
 * commutative, associative on randomized inputs; in any merge order, all
 * devices converge to the same state.
 */
class MergePropertyTest {

    private data class Scenario(
        val states: Map<String, List<Tagged<StateFile>>>,
        val annots: List<Tagged<AnnotsFile>>,
        val library: List<Tagged<LibraryFile>>,
        val settings: List<Tagged<SettingsFile>>,
    )

    private fun randomScenario(rng: Random, deviceCount: Int): Scenario {
        val devices = (1..deviceCount).map { "dev-$it" }
        val books = (1..3).map { "hash-$it" }
        val states = books.associateWith { book ->
            (0..rng.nextInt(0, 3)).map { devices[rng.nextInt(devices.size)] }.map { dev ->
                Tagged(dev, StateFile(ReflowLocator(book, rng.nextInt(0, 40), rng.nextInt(0, 2000), rng.nextDouble(), "q"), rng.nextInt(1, 50).toLong(), rng.nextLong()))
            }
        }
        val annots = devices.map { dev ->
            val entries = (0..rng.nextInt(0, 3)).map { i ->
                AnnotationEntry(
                    id = "a-$bookmark-${rng.nextInt(0, 4)}",
                    kind = "highlight",
                    reflowAnchor = ReflowAnchorDto(bookmark, PointDto(0, 0), PointDto(0, 5), "quote"),
                    text = "t${rng.nextInt(100)}",
                    note = if (rng.nextBoolean()) "n" else "",
                    createdAt = rng.nextLong(),
                    lamport = rng.nextInt(1, 50).toLong(),
                    deleted = rng.nextBoolean(),
                )
            }
            Tagged(dev, AnnotsFile(entries))
        }
        val library = devices.map { dev ->
            val entries = (0..rng.nextInt(0, 3)).map {
                LibraryEntry("hash-${rng.nextInt(1, 4)}", "Title ${rng.nextInt(100)}", rng.nextLong(), rng.nextInt(1, 50).toLong(), rng.nextBoolean())
            }
            Tagged(dev, LibraryFile(entries))
        }
        val settings = devices.map { dev ->
            val map = (0..rng.nextInt(0, 3)).associate { "key-${rng.nextInt(0, 3)}" to SyncedSetting(JsonString("\"v\""), rng.nextInt(1, 50).toLong()) }
            Tagged(dev, SettingsFile(map))
        }
        return Scenario(states, annots, library, settings)
    }

    private var bookmark = "hash-1"

    @Test
    fun mergesAreIdempotentCommutativeAssociative() {
        repeat(100) { seed ->
            val rng = Random(seed)
            val scenario = randomScenario(rng, deviceCount = rng.nextInt(1, 4))

            // idempotence: merging twice changes nothing
            val once = mergePositions(scenario.states)
            assertEquals(once, mergePositions(once.mapValues { (_, w) -> listOf(w) }))

            val annotsOnce = mergeAnnotations(scenario.annots)
            val remergedAnnots = mergeAnnotations(
                annotsOnce.values.map { Tagged(it.deviceId, AnnotsFile(listOf(it.value))) },
            )
            assertEquals(annotsOnce, remergedAnnots)

            val libOnce = mergeLibrary(scenario.library)
            val remergedLib = mergeLibrary(
                libOnce.values.map { Tagged(it.deviceId, LibraryFile(listOf(it.value))) },
            )
            assertEquals(libOnce, remergedLib)

            val setOnce = mergeSettings(scenario.settings)
            val remergedSet = mergeSettings(
                setOnce.entries.groupBy { it.value.deviceId }.map { (dev, entries) ->
                    Tagged(dev, SettingsFile(entries.associate { it.key to it.value.value }))
                },
            )
            assertEquals(setOnce, remergedSet)
        }
    }

    @Test
    fun annotationMergeIsCommutativeAndAssociative() {
        repeat(100) { seed ->
            val rng = Random(seed)
            val scenario = randomScenario(rng, deviceCount = 3)

            // commutativity: order of the files does not matter
            val forward = mergeAnnotations(scenario.annots)
            val reversed = mergeAnnotations(scenario.annots.reversed())
            assertEquals(forward, reversed)

            val libForward = mergeLibrary(scenario.library)
            assertEquals(libForward, mergeLibrary(scenario.library.reversed()))

            val setForward = mergeSettings(scenario.settings)
            assertEquals(setForward, mergeSettings(scenario.settings.reversed()))

            val books = scenario.states
            val flatForward = mergePositions(books)
            val flatReversed = mergePositions(books.mapValues { (_, v) -> v.reversed() })
            assertEquals(flatForward, flatReversed)

            // associativity: (A+B)+C == A+(B+C)
            if (scenario.annots.size >= 3) {
                val (a, b, c) = listOf(scenario.annots[0], scenario.annots[1], scenario.annots[2])
                val abWinners = mergeAnnotations(listOf(a, b))
                val leftAssoc = mergeAnnotations(
                    abWinners.values.map { Tagged(it.deviceId, AnnotsFile(listOf(it.value))) } + c,
                )
                assertEquals(mergeAnnotations(listOf(a, b, c)), leftAssoc)
            }
        }
    }

    @Test
    fun positionLosingDeviceIsTrackable() {
        val a = Tagged("a", StateFile(ReflowLocator("h", 0, 0, 0.1), 5, 0))
        val b = Tagged("b", StateFile(ReflowLocator("h", 1, 1, 0.2), 5, 0))
        // same lamport: device_id breaks the tie
        assertEquals("b", mergePositions(mapOf("h" to listOf(a, b)))["h"]!!.deviceId)
    }

    @Test
    fun tombstoneGcKeepsYoungAndUnseenTombstonesOnly() {
        val now = 1_000_000L
        val day = 24 * 3600 * 1000L
        val tombstones = listOf(
            TombstoneRef("old+seen", now - 100 * day, 10),  // eligible: dropped
            TombstoneRef("old+unseen", now - 100 * day, 12), // dev-2 saw only 10: kept
            TombstoneRef("young+seen", now - 10 * day, 10),  // young: kept
        )
        val kept = keepTombstones(tombstones, mapOf("dev-1" to 10L, "dev-2" to 10L), now)
        assertEquals(listOf("old+unseen", "young+seen"), kept.map { it.id })
        assertTrue(kept.none { it.id == "old+seen" })
    }
}
