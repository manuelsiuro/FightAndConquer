package com.msa.fightandconquer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.msa.fightandconquer.ui.DarkUiColors
import com.msa.fightandconquer.ui.LightUiColors
import com.msa.fightandconquer.ui.LocalUiColors
import com.msa.fightandconquer.ui.UiColorScheme

/**
 * The game paints from the hand-authored neo-pastel palette (docs/game-idea.md
 * section 7), so the Material scheme is derived from it rather than from the
 * template purples. Light and dark instances follow the system setting; there is
 * deliberately NO dynamic color — the board and faction pastels are fixed art,
 * and wallpaper-derived schemes would clash with them.
 */
private fun gameColorScheme(c: UiColorScheme, darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.faction(0),
        onPrimary = c.onFaction,
        primaryContainer = c.faction(0).copy(alpha = 0.30f),
        onPrimaryContainer = c.ink,
        secondary = c.faction(3),
        onSecondary = c.onFaction,
        secondaryContainer = c.faction(0).copy(alpha = 0.30f),
        onSecondaryContainer = c.ink,
        tertiary = c.faction(2),
        onTertiary = c.onFaction,
        background = c.background,
        onBackground = c.ink,
        surface = c.surface,
        onSurface = c.ink,
        surfaceVariant = c.background,
        onSurfaceVariant = c.inkSecondary,
        outline = c.inkFaint,
        outlineVariant = c.inkFaint.copy(alpha = 0.3f),
        error = c.alert,
        onError = c.onAlert,
    )
}

@Composable
fun FightAndConquerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val uiColors = if (darkTheme) DarkUiColors else LightUiColors
    val scheme = remember(darkTheme) { gameColorScheme(uiColors, darkTheme) }
    CompositionLocalProvider(LocalUiColors provides uiColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            content = content,
        )
    }
}
