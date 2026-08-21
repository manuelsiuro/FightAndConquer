package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.UiColors

/**
 * The HUD's shared chrome (docs/design/game-screen-hud-handoff.md). Every
 * floating surface is opaque `surface` + 1 dp hairline + the single `boardLift`
 * shadow — the hairline separates, the shadow only lifts it off the moving board.
 */
internal val HudGutter = 12.dp

/** Vertical gap between stacked HUD surfaces. */
internal val HudSpacing = 8.dp

/** Top bar distance from the top of the immersive window. */
internal val TopBarTopInset = 16.dp

/** Opaque surface + 1 dp border (hairline by default) + boardLift — the universal HUD chrome. */
@Composable
internal fun Modifier.hudSurface(
    radius: Dp,
    fill: Color = UiColors.surface,
    border: Color = UiColors.hairline,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return shadow(2.dp, shape, ambientColor = UiColors.boardShadow, spotColor = UiColors.boardShadow)
        .background(fill, shape)
        .border(1.dp, border, shape)
        .clip(shape)
}

/** Uppercase micro-label (10 sp / 700 / wide tracking) — same idiom as setup. */
@Composable
internal fun HudMicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = UiColors.inkMuted,
) {
    Text(
        text.uppercase(),
        modifier = modifier,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        maxLines = 1,
        color = color,
    )
}

/**
 * The one plinth scale system: plinth = render + 8 dp, plinth radius =
 * render radius + 4. S for the unit strip, M for info/purchase cards,
 * L for the overlay hero renders.
 */
internal enum class PlinthScale(val plinth: Dp, val render: Dp, val radius: Dp) {
    S(40.dp, 32.dp, 11.dp),
    M(56.dp, 48.dp, 13.dp),
    L(96.dp, 80.dp, 20.dp),
}

private val DesaturateFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** A baked piece render on its controlFill + hairline plinth box. */
@Composable
internal fun PiecePlinth(iconRes: Int, scale: PlinthScale, desaturated: Boolean = false) {
    val shape = RoundedCornerShape(scale.radius)
    Box(
        Modifier
            .size(scale.plinth)
            .background(UiColors.controlFill, shape)
            .border(1.dp, UiColors.hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(iconRes),
            contentDescription = null,
            Modifier.size(scale.render),
            alpha = if (desaturated) 0.38f else 1f,
            colorFilter = if (desaturated) DesaturateFilter else null,
        )
    }
}

/**
 * Chrome for the single side-panel slot hanging off the top bar. Economy,
 * diplomacy and mission objectives are mutually exclusive occupants
 * (see GameScreen), so they must agree on this frame. [topAnchor] is the
 * measured bottom of the top chrome in root space — never a constant, so the
 * panel can't slide under a taller-than-expected bar.
 */
@Composable
internal fun HudSidePanel(
    topAnchor: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = HudGutter, top = topAnchor + HudSpacing)
                .width(264.dp)
                .hudSurface(16.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Micro-label panel header with optional right-aligned context, over a divider. */
@Composable
internal fun PanelHeader(text: String, context: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HudMicroLabel(text, Modifier.weight(1f))
            context?.let {
                Spacer(Modifier.width(8.dp))
                HudMicroLabel(it)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = UiColors.divider)
    }
}

/** "Player N" / "AI N" — one wording for the top bar, diplomacy rows and a11y. */
@Composable
internal fun seatLabel(index: Int, isHuman: Boolean): String = if (isHuman) {
    stringResource(R.string.hud_player, index + 1)
} else {
    stringResource(R.string.hud_ai_player, index + 1)
}
