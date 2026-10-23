package com.baca.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class PointDto(val blockIndex: Int, val charOffset: Int)

@Serializable
data class ReflowAnchorDto(
    val href: String,
    val start: PointDto,
    val end: PointDto,
    val quote: String,
    val prefix: String = "",
    val suffix: String = "",
)

@Serializable
data class FixedAnchorDto(val pageIndex: Int, val rects: List<List<Float>>)

/** One annotation: a highlight, underline, note or bookmark, anchored in a reflowed or a fixed-layout book (docs/TECH.md 5.3). */
@Serializable
data class AnnotationEntry(
    val id: String,
    val kind: String,
    val reflowAnchor: ReflowAnchorDto? = null,
    val fixedAnchor: FixedAnchorDto? = null,
    val text: String = "",
    val note: String = "",
    val color: String = "yellow",
    val createdAt: Long,
    val lamport: Long,
    val deleted: Boolean = false,
) {
    init {
        require((reflowAnchor == null) != (fixedAnchor == null)) {
            "exactly one anchor required"
        }
        require(kind in KINDS) { "unknown kind: $kind" }
    }

    companion object {
        val KINDS = setOf("highlight", "underline", "note", "bookmark")
    }
}
