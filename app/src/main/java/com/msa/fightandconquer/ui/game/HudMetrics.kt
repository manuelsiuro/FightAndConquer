package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.UiColors

/** HUD metrics — the overlays hang off the top bar, so its height is shared. */
internal val TopBarHeight = 56.dp
internal val HudGutter = 12.dp

/**
 * Chrome for the single left side-panel slot below the top bar. Economy,
 * diplomacy and mission objectives are mutually exclusive occupants
 * (see GameScreen), so they must agree on this frame.
 */
@Composable
internal fun HudSidePanel(
    contentPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(start = HudGutter, top = TopBarHeight + HudGutter)
            .width(264.dp),
        shape = RoundedCornerShape(16.dp),
        color = UiColors.panel,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** The muted all-caps-style section header the side panels share. */
@Composable
internal fun PanelHeader(text: String) {
    Text(text, fontSize = 12.sp, color = UiColors.inkMuted, letterSpacing = 0.8.sp)
}

/** "Player N" / "AI N" — one wording for the top bar, diplomacy rows and a11y. */
@Composable
internal fun seatLabel(index: Int, isHuman: Boolean): String = if (isHuman) {
    stringResource(R.string.hud_player, index + 1)
} else {
    stringResource(R.string.hud_ai_player, index + 1)
}
