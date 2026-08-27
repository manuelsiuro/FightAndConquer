package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.GameViewModel
import com.msa.fightandconquer.ui.HudState
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.setup.scaleClickable

/**
 * One-tap game actions as floating circles under the top bar: diplomacy,
 * research, economy, the war report, mission objectives (campaign only), and
 * jump-to-fresh-unit. Panels keep their single home — the top bar itself
 * carries no panel entry points anymore (a third circle inside the bar was
 * measured out, docs/ui-hud.md).
 */
@Composable
internal fun ActionBar(
    state: HudState,
    proposalCount: Int,
    economyOpen: Boolean,
    diplomacyOpen: Boolean,
    researchOpen: Boolean,
    statsOpen: Boolean,
    isCampaign: Boolean,
    objectivesOpen: Boolean,
    viewModel: GameViewModel,
) {
    Row(
        Modifier.padding(start = HudGutter, top = HudSpacing),
        horizontalArrangement = Arrangement.spacedBy(HudSpacing),
    ) {
        if (state.diplomacyAvailable) {
            ActionCircle(
                glyph = painterResource(R.drawable.ic_pact),
                description = stringResource(R.string.cd_open_diplomacy),
                active = diplomacyOpen,
                dotBadge = proposalCount > 0,
                onClick = { viewModel.toggleDiplomacyPanel() },
            )
        }
        if (state.researchAvailable) {
            ActionCircle(
                glyph = painterResource(R.drawable.ic_research),
                description = stringResource(R.string.cd_open_research),
                active = researchOpen,
                dotBadge = state.researchBadge,
                onClick = { viewModel.toggleResearchPanel() },
            )
        }
        ActionCircle(
            glyph = painterResource(R.drawable.ic_coin),
            description = stringResource(R.string.cd_open_economy),
            active = economyOpen,
            onClick = { viewModel.toggleEconomyPanel() },
        )
        ActionCircle(
            glyph = painterResource(R.drawable.ic_chart),
            description = stringResource(R.string.cd_open_stats),
            active = statsOpen,
            onClick = { viewModel.toggleStatsPanel() },
        )
        if (isCampaign) {
            ActionCircle(
                glyph = painterResource(R.drawable.ic_target),
                description = stringResource(R.string.cd_open_objectives),
                active = objectivesOpen,
                onClick = { viewModel.toggleObjectivesPanel() },
            )
        }
        ActionCircle(
            glyph = painterResource(R.drawable.ic_flag),
            description = stringResource(R.string.cd_fresh_units, state.freshUnitCount),
            enabled = state.freshUnitCount > 0,
            countBadge = state.freshUnitCount,
            onClick = { viewModel.focusNextFreshUnit() },
        )
    }
}

/**
 * A standalone 48 dp floating circle with the universal HUD chrome (unlike
 * [TopBarCircle], which sits on the bar's surface). Flips to filled ink while
 * its panel is open; disabled is the spec's 38 % treatment over the board.
 */
@Composable
private fun ActionCircle(
    glyph: Painter,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    dotBadge: Boolean = false,
    countBadge: Int = 0,
) {
    Box(Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.38f }) {
        Box(
            Modifier
                .size(48.dp)
                .hudSurface(24.dp, fill = if (active) UiColors.filledInk else UiColors.surface)
                .scaleClickable(enabled = enabled) { onClick() }
                .semantics(mergeDescendants = true) { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            val tint = when {
                active -> UiColors.onFilledInk
                !enabled -> UiColors.inactiveGlyph
                else -> UiColors.inkMuted
            }
            Icon(glyph, contentDescription = null, Modifier.size(20.dp), tint = tint)
        }
        if (dotBadge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(11.dp)
                    .background(UiColors.surface, CircleShape)
                    .padding(1.dp)
                    .background(UiColors.coin, CircleShape),
            )
        }
        if (countBadge > 0 && enabled) {
            // Filled ink, not coin-gold: gold dots mean "attention", and ink
            // keeps the count readable on both themes.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .background(UiColors.surface, RoundedCornerShape(50))
                    .padding(1.dp)
                    .background(UiColors.filledInk, RoundedCornerShape(50))
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    countBadge.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = UiColors.onFilledInk,
                )
            }
        }
    }
}
