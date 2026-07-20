package com.msa.fightandconquer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.msa.fightandconquer.ui.UiColors

/**
 * The game paints from the hand-authored neo-pastel [UiColors] palette (docs/game-idea.md
 * section 7), so the Material scheme is derived from it rather than from the template
 * purples.
 *
 * Deliberately light-only and NOT dynamic-color: the board, HUD panels and faction
 * colors are fixed, so wallpaper-derived or dark schemes would only affect the
 * Material components that don't set explicit colors (chips, buttons, cards) and
 * make them clash with everything around them.
 */
private val GameColorScheme = lightColorScheme(
    primary = UiColors.faction(0),
    onPrimary = UiColors.ink,
    primaryContainer = UiColors.faction(0).copy(alpha = 0.30f),
    onPrimaryContainer = UiColors.ink,
    secondary = UiColors.faction(3),
    onSecondary = UiColors.ink,
    secondaryContainer = UiColors.faction(0).copy(alpha = 0.30f),
    onSecondaryContainer = UiColors.ink,
    tertiary = UiColors.faction(2),
    onTertiary = UiColors.ink,
    background = UiColors.background,
    onBackground = UiColors.ink,
    surface = Color(0xFFFFFDFB),
    onSurface = UiColors.ink,
    surfaceVariant = UiColors.background,
    onSurfaceVariant = UiColors.inkSecondary,
    outline = UiColors.inkFaint,
    outlineVariant = UiColors.inkFaint.copy(alpha = 0.3f),
    error = UiColors.alert,
    onError = Color.White,
)

@Composable
fun FightAndConquerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GameColorScheme,
        typography = Typography,
        content = content,
    )
}
