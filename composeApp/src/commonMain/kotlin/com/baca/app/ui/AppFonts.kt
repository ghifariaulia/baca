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

@Composable
fun dmSansFamily(): FontFamily = FontFamily(
    Font(Res.font.dmsans, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(Res.font.dmsans, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(Res.font.dmsans, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

@Composable
fun literataFamily(): FontFamily = FontFamily(
    Font(Res.font.literata, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(Res.font.literata, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(Res.font.literata, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

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
