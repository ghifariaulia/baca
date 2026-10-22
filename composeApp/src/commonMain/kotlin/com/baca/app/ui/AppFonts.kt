package com.baca.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import baca.composeapp.generated.resources.Res
import baca.composeapp.generated.resources.dmsans
import baca.composeapp.generated.resources.libron_bold
import baca.composeapp.generated.resources.libron_bolditalic
import baca.composeapp.generated.resources.libron_italic
import baca.composeapp.generated.resources.libron_regular
import baca.composeapp.generated.resources.literata
import baca.composeapp.generated.resources.literata_italic

/**
 * Bundled fonts (TECH 10.2): DM Sans for UI, Literata for book text and cover
 * titles. Both are variable TTFs (OFL, see docs/licenses) instanced per weight
 * via FontVariation.
 */
/** The app's font families, provided at the theme root. */
data class UiFonts(val ui: FontFamily, val serif: FontFamily, val sans: FontFamily, val libron: FontFamily)

val LocalAppFonts = androidx.compose.runtime.compositionLocalOf<UiFonts> {
    UiFonts(FontFamily.Default, FontFamily.Serif, FontFamily.SansSerif, FontFamily.Serif)
}

/** [opsz]: the optical size to draw at; null is the font's default. */
@Composable
fun dmSansFamily(opsz: Float? = null): FontFamily = FontFamily(
    Font(Res.font.dmsans, weight = FontWeight.Normal, variationSettings = variation(400, opsz)),
    Font(Res.font.dmsans, weight = FontWeight.Medium, variationSettings = variation(500, opsz)),
    Font(Res.font.dmsans, weight = FontWeight.Bold, variationSettings = variation(700, opsz)),
)

/** [opsz]: the optical size to draw at; null is the font's default. */
@Composable
fun literataFamily(opsz: Float? = null): FontFamily = FontFamily(
    Font(Res.font.literata, weight = FontWeight.Normal, variationSettings = variation(400, opsz)),
    Font(Res.font.literata, weight = FontWeight.SemiBold, variationSettings = variation(600, opsz)),
    Font(Res.font.literata, weight = FontWeight.Bold, variationSettings = variation(700, opsz)),
    // the font's own italic, as LibreraX loads it: a slanted roman sets lines at other widths
    Font(Res.font.literata_italic, weight = FontWeight.Normal, style = FontStyle.Italic, variationSettings = variation(400, opsz)),
    Font(Res.font.literata_italic, weight = FontWeight.SemiBold, style = FontStyle.Italic, variationSettings = variation(600, opsz)),
    Font(Res.font.literata_italic, weight = FontWeight.Bold, style = FontStyle.Italic, variationSettings = variation(700, opsz)),
)

private fun variation(weight: Int, opsz: Float?) =
    if (opsz == null) FontVariation.Settings(FontVariation.weight(weight))
    else FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz))

/**
 * The reader's own faces at the optical size a browser draws them at (`font-optical-sizing:
 * auto`): the running text's size in CSS px, [opsz], which is its size in sp. The font's
 * default optical size sets text a little wider than LibreraX's WebView does.
 */
@Composable
fun readerFonts(opsz: Float): UiFonts = UiFonts(dmSansFamily(), literataFamily(opsz), dmSansFamily(opsz), libronFamily())

/** Libron reading font: static TTFs per style (OFL, docs/licenses/OFL-libron.txt). */
@Composable
fun libronFamily(): FontFamily = FontFamily(
    Font(Res.font.libron_regular, weight = FontWeight.Normal),
    Font(Res.font.libron_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(Res.font.libron_bold, weight = FontWeight.Bold),
    Font(Res.font.libron_bolditalic, weight = FontWeight.Bold, style = FontStyle.Italic),
)

/** M3 defaults, every style set to DM Sans. */
@Composable
fun appTypography(): Typography {
    val t = Typography()
    val dm = dmSansFamily()
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = dm),
        displayMedium = t.displayMedium.copy(fontFamily = dm),
        displaySmall = t.displaySmall.copy(fontFamily = dm),
        headlineLarge = t.headlineLarge.copy(fontFamily = dm),
        headlineMedium = t.headlineMedium.copy(fontFamily = dm),
        headlineSmall = t.headlineSmall.copy(fontFamily = dm),
        titleLarge = t.titleLarge.copy(fontFamily = dm),
        titleMedium = t.titleMedium.copy(fontFamily = dm),
        titleSmall = t.titleSmall.copy(fontFamily = dm),
        bodyLarge = t.bodyLarge.copy(fontFamily = dm),
        bodyMedium = t.bodyMedium.copy(fontFamily = dm),
        bodySmall = t.bodySmall.copy(fontFamily = dm),
        labelLarge = t.labelLarge.copy(fontFamily = dm),
        labelMedium = t.labelMedium.copy(fontFamily = dm),
        labelSmall = t.labelSmall.copy(fontFamily = dm),
    )
}
