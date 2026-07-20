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
    val positive = Color(0xFF5B7F5E)
    val alert = Color(0xFFB05B4C)
    val toastWarning = Color(0xF2EAD9B8)
    val chipCapturable = Color(0xE05B7F5E)
    val chipBlocked = Color(0xE0B05B4C)
}
