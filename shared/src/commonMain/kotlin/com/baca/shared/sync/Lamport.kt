package com.baca.shared.sync

/**
 * Lamport clock (docs/TECH.md 6.1): local writes get max(own, highest seen) + 1;
 * observing a peer's lamport only raises the ceiling. Pure counter; persistence
 * is the caller's job (sync_state.own_lamport).
 */
class LamportClock(initial: Long = 0) {
    var value: Long = initial
        private set

    /** Highest lamport observed from any record or meta.json. */
    fun observe(seen: Long) {
        if (seen > value) value = seen
    }

    /** Clock for a new local record. */
    fun bump(): Long {
        value += 1
        return value
    }
}
