package com.msa.fightandconquer.ui

import androidx.compose.ui.graphics.Color

/** Compose-side (sRGB) mirror of the render palette in docs/game-idea.md section 7. */
object UiColors {
    val factions = listOf(
        Color(0xFF8FA89B), // Soft Sage Green
        Color(0xFFDE9B8B), // Dusty Coral
        Color(0xFFE6C594), // Muted Ochre
        Color(0xFF8FA3B5), // Slate Blue
        Color(0xFFB59BAD), // Dusty Mauve
        Color(0xFFA8B58F), // Moss Olive
    )

    fun faction(index: Int): Color = factions[index % factions.size]

    val background = Color(0xFFF4F2EF)
    val ink = Color(0xFF3E3A36)
    val panel = Color(0xF2FFFDFB)

    /**
     * Secondary text tokens. The faction pastels are far too light to carry white
     * text, so anything sitting on them uses [ink]; these three cover the muted
     * hierarchy on panels (all contrast-checked against [panel]).
     */
    val inkSecondary = ink.copy(alpha = 0.75f)
    val inkMuted = ink.copy(alpha = 0.6f)
    val inkFaint = ink.copy(alpha = 0.45f)

    /** Coin-icon tint: the render palette's gold darkened to hold on [panel]. */
    val coin = Color(0xFFB8913D)

    val positive = Color(0xFF41663F)
    val alert = Color(0xFF9C4636)
    val toastWarning = Color(0xF2EAD9B8)

    // Fully opaque: these chips float over the live 3D board, so translucency
    // would let the board bleed through and drop text contrast below 4.5:1.
    val chipCapturable = Color(0xFF3F6142)
    val chipBlocked = Color(0xFF8E3E30)
}
