package com.msa.fightandconquer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Compose-side (sRGB) UI palette. The chrome tokens (paper, ink, panels, toasts)
 * come in a light and a dark instance selected by the system setting in
 * [com.msa.fightandconquer.ui.theme.FightAndConquerTheme]; the faction pastels and
 * board-overlay chips are fixed in the class body because they mirror the render
 * palette in docs/game-idea.md section 7 ([com.msa.fightandconquer.render.material.Palette]),
 * and the 3D board does not change with the theme.
 */
@Immutable
class UiColorScheme(
    val background: Color,
    val ink: Color,
    val panel: Color,
    val surface: Color,
    val coin: Color,
    val positive: Color,
    val alert: Color,
    val onAlert: Color,
    val toastWarning: Color,
    val bannerScrim: Color,
) {
    /**
     * Secondary text tokens. The faction pastels are far too light to carry white
     * text, so anything sitting on them uses [onFaction]; these three cover the
     * muted hierarchy on panels (all contrast-checked against [panel]).
     */
    val inkSecondary = ink.copy(alpha = 0.75f)
    val inkMuted = ink.copy(alpha = 0.6f)
    val inkFaint = ink.copy(alpha = 0.45f)

    val factions = listOf(
        Color(0xFF8FA89B), // Soft Sage Green
        Color(0xFFDE9B8B), // Dusty Coral
        Color(0xFFE6C594), // Muted Ochre
        Color(0xFF8FA3B5), // Slate Blue
        Color(0xFFB59BAD), // Dusty Mauve
        Color(0xFFA8B58F), // Moss Olive
    )

    fun faction(index: Int): Color = factions[index % factions.size]

    /**
     * Text/icon color on a full-strength faction pastel. Always this dark ink,
     * never [ink]: the pastels stay light in both themes while [ink] flips to
     * near-white in dark mode and would vanish on them.
     */
    val onFaction = Color(0xFF3E3A36)

    // Fully opaque: these chips float over the live 3D board, so translucency
    // would let the board bleed through and drop text contrast below 4.5:1.
    val chipCapturable = Color(0xFF3F6142)
    val chipBlocked = Color(0xFF8E3E30)
}

val LightUiColors = UiColorScheme(
    background = Color(0xFFF4F2EF),
    ink = Color(0xFF3E3A36),
    panel = Color(0xF2FFFDFB),
    surface = Color(0xFFFFFDFB),
    coin = Color(0xFFB8913D),
    positive = Color(0xFF41663F),
    alert = Color(0xFF9C4636),
    onAlert = Color.White,
    toastWarning = Color(0xF2EAD9B8),
    bannerScrim = Color(0xE6FFFFFF),
)

// Dark warm paper, light warm ink — the light aesthetic inverted. The panel is
// darker AND slightly more opaque than its light twin because in-game it sits
// over the unchanged light-pastel 3D board.
val DarkUiColors = UiColorScheme(
    background = Color(0xFF201E1B),
    ink = Color(0xFFE8E4DE),
    panel = Color(0xF7292623),
    surface = Color(0xFF262320),
    coin = Color(0xFFD4AF5C),
    positive = Color(0xFF8CBA88),
    alert = Color(0xFFE08A6F),
    onAlert = Color(0xFF2A1712),
    toastWarning = Color(0xF2554931),
    bannerScrim = Color(0xE6141210),
)

val LocalUiColors = staticCompositionLocalOf { LightUiColors }

/**
 * Theme-aware accessor keeping the historical `UiColors.x` call-site spelling.
 * Composable-only by design: capturing a token in `remember { }` or a non-UI
 * class would freeze it in one theme.
 */
val UiColors: UiColorScheme
    @Composable @ReadOnlyComposable get() = LocalUiColors.current
