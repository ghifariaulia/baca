package com.baca.shared.domain

import com.baca.shared.domain.reader.PositionStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val KEY_USER_FONTS = "user_fonts"
const val USER_FONT_PREFIX = "user:"

@Serializable
data class UserFont(
    val id: String,
    val name: String,
    val fileName: String,
)

private val userFontsJson = Json { ignoreUnknownKeys = true }

fun isUserFontKey(font: String): Boolean =
    font.startsWith(USER_FONT_PREFIX) && font.length > USER_FONT_PREFIX.length

fun userFontId(font: String): String? =
    font.takeIf(::isUserFontKey)?.removePrefix(USER_FONT_PREFIX)

fun PositionStore.loadUserFonts(): List<UserFont> =
    getLocal(KEY_USER_FONTS)?.let { runCatching { userFontsJson.decodeFromString<List<UserFont>>(it) }.getOrNull() }.orEmpty()

fun PositionStore.saveUserFonts(fonts: List<UserFont>) {
    putLocal(KEY_USER_FONTS, userFontsJson.encodeToString(fonts))
}

fun addUserFontEntry(existing: List<UserFont>, id: String, name: String, fileName: String): List<UserFont> =
    existing.filterNot { it.id == id } + UserFont(id = id, name = name, fileName = fileName)

fun removeUserFontEntry(existing: List<UserFont>, id: String): List<UserFont> =
    existing.filterNot { it.id == id }

fun resolveDisplayFont(display: DisplaySettings, userFonts: List<UserFont>): DisplaySettings {
    val id = userFontId(display.font) ?: return display
    return if (userFonts.any { it.id == id }) display else display.copy(font = "serif")
}
