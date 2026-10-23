package com.baca.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// docs/TECH.md 10.2 tokens — defined once here; screens use theme values, no literals.
object Tokens {
    val Ground = Color(0xFFF5F6F8)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF12151C)
    val Muted = Color(0xFF5B6472)
    val Line = Color(0xFFE3E6EB)
    val Accent = Color(0xFF0F766E)
    val AccentTint = Color(0xFFE6F2F0)
    val Highlight = Color(0xFFF6D860)
}

/** Reader color pairs for the display-settings themes (docs/TECH.md 10.4). */
data class ReaderColors(val background: Color, val ink: Color)

// LibreraX's themes (its Book, Day, Grey, Dark, Dusk and Black), read from its app
object ReaderThemes {
    val PAPER = ReaderColors(Color(0xFFF4ECD8), Color(0xFF5B4636))
    /** Paper's page with black text, for more contrast than Paper's brown */
    val INK = ReaderColors(Color(0xFFF4ECD8), Color(0xFF111111))
    val WHITE = ReaderColors(Color(0xFFFFFFFF), Color(0xFF111111))
    val GREY = ReaderColors(Color(0xFFE9E9E9), Color(0xFF262626))
    val DIM = ReaderColors(Color(0xFF303030), Color(0xFFDDDDDD))
    val DUSK = ReaderColors(Color(0xFF1C1A17), Color(0xFFCFC5B3))
    val BLACK = ReaderColors(Color(0xFF000000), Color(0xFFDDDDDD))

    fun forTheme(name: String): ReaderColors = when (name) {
        "white" -> WHITE
        "ink" -> INK
        "grey" -> GREY
        "dim" -> DIM
        "dusk" -> DUSK
        "black" -> BLACK
        else -> PAPER
    }
}

private val LightColors = lightColorScheme(
    primary = Tokens.Accent,
    onPrimary = Color.White,
    background = Tokens.Ground,
    onBackground = Tokens.Ink,
    surface = Tokens.Surface,
    onSurface = Tokens.Ink,
    surfaceVariant = Tokens.Surface,
    onSurfaceVariant = Tokens.Muted,
    outline = Tokens.Line,
    secondaryContainer = Tokens.AccentTint,
    onSecondaryContainer = Tokens.Accent,
)

private val DarkColors = darkColorScheme(
    primary = Tokens.Accent,
    onPrimary = Color.White,
    background = Color(0xFF12151C),
    onBackground = Color(0xFFE7E9EE),
    surface = Color(0xFF1A1E26),
    onSurface = Color(0xFFE7E9EE),
    outline = Color(0xFF31363F),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * App shell theme choice (Settings). Per device, not synced: a phone and a
 * desktop often want different modes. Stored by [key] under [STORE_KEY].
 */
enum class AppThemeChoice(val key: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        const val STORE_KEY = "app_theme"
        fun of(stored: String?): AppThemeChoice = entries.firstOrNull { it.key == stored } ?: SYSTEM
    }
}

@Composable
fun AppTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val fonts = UiFonts(dmSansFamily(), literataFamily(), dmSansFamily(), libronFamily())
    CompositionLocalProvider(LocalAppFonts provides fonts) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = appTypography(),
            shapes = AppShapes,
        ) {
            // Only the family — M3's Text merge gives the local style's specified
            // fields precedence, so a bare local injects DM Sans without
            // stomping each screen's explicit size/lineHeight
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides TextStyle(fontFamily = fonts.ui),
            ) {
                content()
            }
        }
    }
}

// Common styles (TECH 10.2 typography)
object Type {
    val screenTitle: TextStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
    val cardTitle: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
    val body: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp)
    val caption: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp)
    val sectionLabel: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
}

/** Whether a reading theme's page is dark, as LibreraX tells it: Rec.601 luminance under 0.4. */
fun isDarkScheme(page: Color): Boolean = 0.299f * page.red + 0.587f * page.green + 0.114f * page.blue < 0.4f
