package com.msa.fightandconquer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R

/**
 * The UI face: Figtree, every weight the composables use. It reaches every `Text`
 * through [Typography] (Material's `LocalTextStyle` is `bodyLarge`), so call sites
 * never name it — only text drawn outside that chain (a `TextStyle` built from
 * scratch for a `TextMeasurer`) sets it explicitly.
 *
 * Static instances baked from `art/fonts/` by `tools/bake_fonts.py`.
 */
val UiFontFamily = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_extrabold, FontWeight.ExtraBold),
)

/**
 * The display face: Fraunces (soft, opsz 28), for screen and overlay titles at
 * 22 sp and up — the menu title, turn banner, outcome and debrief headlines, screen
 * headers. Everything smaller, including every HUD surface, stays [UiFontFamily].
 */
val DisplayFontFamily = FontFamily(
    Font(R.font.fraunces_bold, FontWeight.Bold),
    Font(R.font.fraunces_extrabold, FontWeight.ExtraBold),
)

private val Base = Typography()

val Typography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = DisplayFontFamily),
    displayMedium = Base.displayMedium.copy(fontFamily = DisplayFontFamily),
    displaySmall = Base.displaySmall.copy(fontFamily = DisplayFontFamily),
    headlineLarge = Base.headlineLarge.copy(fontFamily = DisplayFontFamily),
    headlineMedium = Base.headlineMedium.copy(fontFamily = DisplayFontFamily),
    headlineSmall = Base.headlineSmall.copy(fontFamily = DisplayFontFamily),
    titleLarge = Base.titleLarge.copy(fontFamily = UiFontFamily),
    titleMedium = Base.titleMedium.copy(fontFamily = UiFontFamily),
    titleSmall = Base.titleSmall.copy(fontFamily = UiFontFamily),
    bodyLarge = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = Base.bodyMedium.copy(fontFamily = UiFontFamily),
    bodySmall = Base.bodySmall.copy(fontFamily = UiFontFamily),
    labelLarge = Base.labelLarge.copy(fontFamily = UiFontFamily),
    labelMedium = Base.labelMedium.copy(fontFamily = UiFontFamily),
    labelSmall = Base.labelSmall.copy(fontFamily = UiFontFamily),
)
