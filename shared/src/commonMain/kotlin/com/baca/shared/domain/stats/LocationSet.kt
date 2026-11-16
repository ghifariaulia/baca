package com.baca.shared.domain.stats

/** A set of locations kept as sorted, separate ranges: reading is mostly in runs, so this stays short. */
class LocationSet private constructor(private val ranges: MutableList<IntRange>) {

    fun add(range: IntRange) {
        if (range.isEmpty()) return
        var first = range.first
        var last = range.last
        val kept = mutableListOf<IntRange>()
        for (r in ranges) {
            if (r.last + 1 < first || r.first > last + 1) kept += r
            else {
                first = minOf(first, r.first)
                last = maxOf(last, r.last)
            }
        }
        kept += first..last
        ranges.clear()
        ranges += kept.sortedBy { it.first }
    }

    fun count(): Int = ranges.sumOf { it.count() }

    fun countIn(range: IntRange): Int = ranges.sumOf { r ->
        (minOf(r.last, range.last) - maxOf(r.first, range.first) + 1).coerceAtLeast(0)
    }

    /** "0-119,130,140-141" */
    fun encode(): String = ranges.joinToString(",") { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" }

    companion object {
        fun of(vararg ranges: IntRange) = LocationSet(mutableListOf()).apply { ranges.forEach(::add) }

        fun decode(text: String): LocationSet = LocationSet(mutableListOf()).apply {
            text.split(',').filter { it.isNotBlank() }.forEach { part ->
                val (from, to) = part.split('-').map { it.trim().toInt() }.let { it.first() to it.last() }
                add(from..to)
            }
        }
    }
}
