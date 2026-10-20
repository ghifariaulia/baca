package com.baca.shared.domain

import kotlinx.serialization.Serializable

/**
 * Viewport/font-independent position in a book — the only position format stored
 * or synced (docs/TECH.md 5.2). `progression` is display/fallback only.
 */
@Serializable
data class Locator(
    val href: String,
    val blockIndex: Int,
    val charOffset: Int,
    val progression: Double,
    val quote: String = "",
)

/** Viewport-independent position for fixed-layout books (docs/TECH.md 5.2). */
@Serializable
data class FixedLocator(
    val pageIndex: Int,
    val progression: Double,
)

@Serializable
data class AnchorPoint(val blockIndex: Int, val charOffset: Int)

/**
 * Viewport-independent annotation anchor (docs/TECH.md 5.3). If a parse change
 * invalidates offsets, re-anchor by searching for prefix + quote + suffix.
 */
@Serializable
data class ReflowAnchor(
    val href: String,
    val start: AnchorPoint,
    val end: AnchorPoint,
    val quote: String,
    val prefix: String = "",
    val suffix: String = "",
)

@Serializable
data class FixedAnchor(val pageIndex: Int, val rects: List<List<Float>>)

/** Annotation kinds: highlight, underline, note, bookmark. */
@Serializable
enum class AnnotationKind { HIGHLIGHT, UNDERLINE, NOTE, BOOKMARK }

/** One annotation, as stored and synced (docs/TECH.md 6.2). */
@Serializable
data class AnnotationRecord(
    val id: String,
    val bookHash: String,
    val kind: AnnotationKind = AnnotationKind.HIGHLIGHT,
    val reflowAnchor: ReflowAnchor? = null,
    val fixedAnchor: FixedAnchor? = null,
    val text: String = "",
    val note: String = "",
    val createdAt: Long,
    val lamport: Long,
    val deviceId: String,
    val deleted: Boolean = false,
) {
    init {
        require((reflowAnchor == null) != (fixedAnchor == null)) {
            "exactly one of reflowAnchor/fixedAnchor must be set"
        }
    }
}

@Serializable
data class Book(
    val bookHash: String,
    val title: String,
    val author: String,
    val format: String,
    val addedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class PositionRecord(
    val bookHash: String,
    val deviceId: String,
    val reflow: Locator? = null,
    val fixed: FixedLocator? = null,
    val lamport: Long,
    val updatedAt: Long,
) {
    init {
        require((reflow == null) != (fixed == null)) {
            "exactly one of reflow/fixed must be set"
        }
    }
}

data class Device(val deviceId: String, val lastSeenCounter: Long, val lastSeenAt: Long?)
