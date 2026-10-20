package com.baca.shared.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The only Json instance for sync files. IgnoreUnknownKeys keeps older files
 * readable when new fields appear; encodeDefaults keeps schema_version explicit.
 */
object SyncJson {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    inline fun <reified T> decode(text: String): T = json.decodeFromString(text)
}
