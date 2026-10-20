package com.baca.shared.sync

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** File identity of a device inside the sync folder (docs/TECH.md 6.2). */
@Serializable
data class DeviceMeta(
    val deviceId: String,
    val schemaVersion: Int = SCHEMA_VERSION,
    val counter: Long,
    val lamport: Long,
    val updatedAt: Long,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/** One position record as written to devices/<id>/state/<hash>.json. */
@Serializable
data class StateFile(
    val locator: PositionLocator,
    val lamport: Long,
    val updatedAt: Long,
) {
    init {
        locator.validate()
    }
}

/**
 * Transport representation of a position locator. JsonClassDiscriminator keeps
 * reflow and fixed locators distinguishable in one file format.
 */
@Serializable
sealed interface PositionLocator {
    fun validate()
}

@Serializable
@SerialName("reflow")
@kotlinx.serialization.json.JsonClassDiscriminator("type")
data class ReflowLocator(
    val href: String,
    val blockIndex: Int,
    val charOffset: Int,
    val progression: Double,
    val quote: String = "",
) : PositionLocator {
    override fun validate() {
        require(href.isNotBlank()) { "href required" }
        require(blockIndex >= 0) { "blockIndex >= 0" }
        require(charOffset >= 0) { "charOffset >= 0" }
    }
}

@Serializable
@SerialName("fixed")
data class FixedPageLocator(
    val pageIndex: Int,
    val progression: Double,
) : PositionLocator {
    override fun validate() {
        require(pageIndex >= 0) { "pageIndex >= 0" }
    }
}

/** Anchor shapes for sync files (docs/TECH.md 5.3). */
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

/** One annotation this device created, edited or deleted (docs/TECH.md 6.2). */
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

@Serializable
data class AnnotsFile(val annotations: List<AnnotationEntry>)

@Serializable
data class LibraryEntry(
    val bookHash: String,
    val title: String,
    val addedAt: Long,
    val lamport: Long,
    val deleted: Boolean = false,
    /** Links other files of the same edition (workKeyOf); absent from older peers. */
    val workKey: String? = null,
)

@Serializable
data class LibraryFile(val books: List<LibraryEntry>)

/** Synced settings allowlist entry: per-key last-writer-wins. */
@Serializable
data class SyncedSetting(val value: JsonString, val lamport: Long)

@Serializable
data class SettingsFile(val settings: Map<String, SyncedSetting>)

/** Plain JSON value wrapper so settings can hold an arbitrary JSON payload. */
@Serializable(with = JsonStringSerializer::class)
@JvmInline
value class JsonString(val raw: String)

object JsonStringSerializer : KSerializer<JsonString> {
    override val descriptor: SerialDescriptor =
        kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
            "JsonString",
            kotlinx.serialization.descriptors.PrimitiveKind.STRING,
        )

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: JsonString) {
        encoder.encodeString(value.raw)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): JsonString =
        JsonString(decoder.decodeString())
}
