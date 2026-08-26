package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.GameViewModel
import com.msa.fightandconquer.ui.ResearchBranchUi
import com.msa.fightandconquer.ui.ResearchPanelState
import com.msa.fightandconquer.ui.TechNodeUi
import com.msa.fightandconquer.ui.TechUiStatus
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.resolve
import com.msa.fightandconquer.ui.setup.scaleClickable

/**
 * The research tree in the bottom sheet: one lane per branch, its three tiers
 * flowing left to right with connectors — the linearity is the reading
 * direction. Locked, busy and unaffordable cards stay tappable — the engine's
 * rejection toast explains itself, so there is no dead UI and no second rules
 * implementation here (the PurchaseCard contract).
 */
@Composable
internal fun ResearchSheetBody(
    research: ResearchPanelState,
    factionIndex: Int,
    viewModel: GameViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelHeader(
            stringResource(R.string.research_title),
            context = pluralStringResource(
                R.plurals.research_universities,
                research.universityCount,
                research.universityCount,
            ),
        )
        for (branch in research.branches) {
            BranchLane(branch, factionIndex, viewModel)
        }
    }
}

@Composable
private fun BranchLane(branch: ResearchBranchUi, factionIndex: Int, viewModel: GameViewModel) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(18.dp)
                    .background(UiColors.controlFill, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(branch.iconRes),
                    contentDescription = null,
                    Modifier.size(12.dp),
                    tint = UiColors.inkMuted,
                )
            }
            Spacer(Modifier.width(6.dp))
            HudMicroLabel(stringResource(branch.nameRes), Modifier.weight(1f))
            HudMicroLabel(
                stringResource(
                    R.string.research_branch_progress,
                    branch.nodes.count { it.status == TechUiStatus.DONE },
                    branch.nodes.size,
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            branch.nodes.forEachIndexed { index, node ->
                if (index > 0) {
                    LaneConnector(open = branch.nodes[index - 1].status == TechUiStatus.DONE)
                }
                TechCard(node, factionIndex, viewModel, Modifier.weight(1f))
            }
        }
    }
}

/** The prerequisite link between tiers: filled once the tier before is researched. */
@Composable
private fun LaneConnector(open: Boolean) {
    Box(
        Modifier
            .width(12.dp)
            .height(2.dp)
            .background(if (open) UiColors.positive else UiColors.divider),
    )
}

@Composable
private fun TechCard(
    node: TechNodeUi,
    factionIndex: Int,
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(node.nameRes)
    val description = when (node.status) {
        TechUiStatus.DONE -> stringResource(R.string.cd_research_done, name)
        TechUiStatus.IN_PROGRESS ->
            stringResource(R.string.cd_research_in_progress, name, node.progress ?: 0, node.duration)
        TechUiStatus.LOCKED -> stringResource(R.string.cd_research_locked, name)
        TechUiStatus.BUSY -> stringResource(R.string.cd_research_busy, name, node.cost)
        TechUiStatus.AVAILABLE ->
            stringResource(R.string.cd_research_start, name, node.cost, node.duration)
    }
    val shape = RoundedCornerShape(12.dp)
    // The whole card dims while unreachable; only AVAILABLE gets the alert-red
    // affordability cue — a locked tree must not scream about money.
    val dimmed = node.status == TechUiStatus.LOCKED || node.status == TechUiStatus.BUSY
    val clickable = node.status == TechUiStatus.AVAILABLE || dimmed
    val border =
        if (node.status == TechUiStatus.IN_PROGRESS) UiColors.faction(factionIndex) else UiColors.hairline
    var cardModifier = modifier
        .height(100.dp)
        .background(UiColors.controlFill, shape)
        .border(1.dp, border, shape)
    if (clickable) {
        cardModifier = cardModifier.scaleClickable { viewModel.startResearch(node.tech) }
    }
    Column(
        cardModifier
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                name,
                Modifier.weight(1f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (dimmed) UiColors.inactiveGlyph else UiColors.ink,
                maxLines = 2,
            )
            StatusGlyph(node.status, factionIndex)
        }
        Text(
            node.effect.resolve(),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = if (dimmed) UiColors.inactiveGlyph else UiColors.inkMuted,
            maxLines = 2,
        )
        when (node.status) {
            TechUiStatus.DONE ->
                HudMicroLabel(stringResource(R.string.research_done), color = UiColors.positive)
            TechUiStatus.IN_PROGRESS -> Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(UiColors.progressTrack, CircleShape),
                ) {
                    val fraction = ((node.progress ?: 0).toFloat() / node.duration).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .background(UiColors.faction(factionIndex), CircleShape),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(R.string.research_active_progress, node.progress ?: 0, node.duration),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = UiColors.ink,
                )
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                val affordAlert = node.status == TechUiStatus.AVAILABLE && !node.affordable
                Icon(
                    painterResource(R.drawable.ic_coin),
                    contentDescription = null,
                    Modifier.size(11.dp),
                    tint = when {
                        dimmed -> UiColors.inactiveGlyph
                        affordAlert -> UiColors.alert
                        else -> UiColors.coin
                    },
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    stringResource(R.string.info_value_plain, node.cost),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        dimmed -> UiColors.inactiveGlyph
                        affordAlert -> UiColors.alert
                        else -> UiColors.ink
                    },
                )
                Spacer(Modifier.width(5.dp))
                HudMicroLabel(
                    stringResource(R.string.research_duration_short, node.duration),
                    color = if (dimmed) UiColors.inactiveGlyph else UiColors.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun StatusGlyph(status: TechUiStatus, factionIndex: Int) {
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        when (status) {
            TechUiStatus.DONE -> Box(
                Modifier
                    .size(16.dp)
                    .background(UiColors.positive.copy(alpha = 0.3f), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    Modifier.size(11.dp),
                    tint = UiColors.positive,
                )
            }
            TechUiStatus.IN_PROGRESS -> Box(
                Modifier
                    .size(9.dp)
                    .background(UiColors.faction(factionIndex), CircleShape),
            )
            TechUiStatus.LOCKED -> Icon(
                painterResource(R.drawable.ic_lock),
                contentDescription = null,
                Modifier.size(12.dp),
                tint = UiColors.inactiveGlyph,
            )
            // BUSY: reachable but the slot is taken — a muted dot, not a padlock.
            TechUiStatus.BUSY -> Box(
                Modifier
                    .size(7.dp)
                    .background(UiColors.inactiveGlyph, CircleShape),
            )
            TechUiStatus.AVAILABLE -> Box(
                Modifier
                    .size(7.dp)
                    .background(UiColors.inkMuted, CircleShape),
            )
        }
    }
}

/** Pinned foot: the active research with its progress track, or the idle guidance. */
@Composable
internal fun ResearchSheetFooter(research: ResearchPanelState, factionIndex: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(UiColors.controlFill, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        val active = research.active
        when {
            active != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(active.nameRes),
                        Modifier.weight(1f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = UiColors.ink,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.research_active_progress, active.progress ?: 0, active.duration),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = UiColors.ink,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(UiColors.progressTrack, CircleShape),
                ) {
                    val fraction = ((active.progress ?: 0).toFloat() / active.duration).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(4.dp)
                            .background(UiColors.faction(factionIndex), CircleShape),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.research_rate_line, research.ratePerTurn),
                    fontSize = 11.sp,
                    color = UiColors.inkMuted,
                )
            }
            research.universityCount == 0 -> {
                Text(
                    stringResource(R.string.research_warn_no_university),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = UiColors.ink,
                )
            }
            else -> {
                Text(
                    stringResource(R.string.research_no_active),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = UiColors.ink,
                )
                Text(
                    stringResource(R.string.research_pick_hint),
                    fontSize = 11.sp,
                    color = UiColors.inkMuted,
                )
            }
        }
    }
}
