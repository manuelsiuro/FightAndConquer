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
    /** 1 dp card borders and drag handles — the setup screen's elevation idiom. */
    val hairline: Color,
    /** Hairline separators inside a card (slightly stronger than [hairline]). */
    val divider: Color,
    /** Fills for small controls: circular back button, switch-off tracks. */
    val controlFill: Color,
    /** Track behind thin progress indicators. */
    val progressTrack: Color,
    /** Unselected pictogram fills (the setup screen's hex-cluster glyphs). */
    val inactiveGlyph: Color,
    /**
     * The fixed "filled ink" selection treatment (dark fill, paper content in
     * light; inverted in dark) — segments, active top-bar circles, Accept.
     */
    val filledInk: Color,
    val onFilledInk: Color,
    /**
     * `boardLift` — the single shadow tint for HUD chrome floating over the live
     * 3D board (ambient and spot). Everything else elevates with hairlines only.
     */
    val boardShadow: Color,
) {
    /** Scrim behind full-screen overlays (turn banner, outcomes): paper @ 92%. */
    val overlayScrim = background.copy(alpha = 0.92f)

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

    // The selected unit's own attack badge: the palette's dark warm ink (= onFaction),
    // so "you" reads apart from the green/rust verdict chips in both themes.
    val chipAttacker = Color(0xFF3E3A36)
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
    hairline = Color(0xFFE7E1D9),
    divider = Color(0xFFEDE7DF),
    controlFill = Color(0xFFEDE9E3),
    progressTrack = Color(0xFFE4DFD8),
    inactiveGlyph = Color(0xFFD6D0C7),
    filledInk = Color(0xFF3E3A36),
    onFilledInk = Color(0xFFF7F4F0),
    boardShadow = Color(0x1A3E3A36),
)

// Dark warm paper, light warm ink — the light aesthetic inverted. The panel is
// darker AND slightly more opaque than its light twin because in-game it sits
// over the unchanged light-pastel 3D board.
val DarkUiColors = UiColorScheme(
    background = Color(0xFF201E1B),
    ink = Color(0xFFF2EEE9),
    panel = Color(0xF7292623),
    surface = Color(0xFF2A2724),
    coin = Color(0xFFD9B168),
    positive = Color(0xFF7FA97C),
    alert = Color(0xFFD2705C),
    onAlert = Color(0xFF2A1712),
    toastWarning = Color(0xF2554931),
    bannerScrim = Color(0xE6141210),
    hairline = Color(0xFF37332E),
    divider = Color(0xFF302C28),
    controlFill = Color(0xFF332F2A),
    progressTrack = Color(0xFF332F2A),
    inactiveGlyph = Color(0xFF4A4540),
    filledInk = Color(0xFFF2EEE9),
    onFilledInk = Color(0xFF201E1B),
    boardShadow = Color(0x57000000),
)

val LocalUiColors = staticCompositionLocalOf { LightUiColors }

/**
 * Theme-aware accessor keeping the historical `UiColors.x` call-site spelling.
 * Composable-only by design: capturing a token in `remember { }` or a non-UI
 * class would freeze it in one theme.
 */
val UiColors: UiColorScheme
    @Composable @ReadOnlyComposable get() = LocalUiColors.current
