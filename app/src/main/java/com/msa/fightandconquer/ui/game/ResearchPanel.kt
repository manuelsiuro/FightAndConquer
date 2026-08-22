package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.Dp
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
 * The research tree in the shared side-panel slot: branch groups of three
 * linear nodes, the active research pinned below. Locked and unaffordable rows
 * stay tappable — the engine's rejection toast explains itself, so there is no
 * dead UI and no second rules implementation here (the PurchaseCard contract).
 */
@Composable
internal fun ResearchPanel(
    research: ResearchPanelState,
    factionIndex: Int,
    topAnchor: Dp,
    viewModel: GameViewModel,
) {
    HudSidePanel(topAnchor, pinned = { ActiveResearchCard(research, factionIndex) }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelHeader(
                stringResource(R.string.research_title),
                context = pluralStringResource(
                    R.plurals.research_universities,
                    research.universityCount,
                    research.universityCount,
                ),
            )
            for (branch in research.branches) {
                BranchGroup(branch, factionIndex, viewModel)
            }
        }
    }
}

@Composable
private fun BranchGroup(branch: ResearchBranchUi, factionIndex: Int, viewModel: GameViewModel) {
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
            HudMicroLabel(stringResource(branch.nameRes))
        }
        Spacer(Modifier.height(4.dp))
        branch.nodes.forEachIndexed { index, node ->
            if (index > 0) HorizontalDivider(color = UiColors.divider)
            TechNodeRow(node, factionIndex, viewModel)
        }
    }
}

@Composable
private fun TechNodeRow(node: TechNodeUi, factionIndex: Int, viewModel: GameViewModel) {
    val name = stringResource(node.nameRes)
    val effect = node.effect.resolve()
    val description = when (node.status) {
        TechUiStatus.DONE -> stringResource(R.string.cd_research_done, name)
        TechUiStatus.IN_PROGRESS ->
            stringResource(R.string.cd_research_in_progress, name, node.progress ?: 0, node.duration)
        TechUiStatus.LOCKED -> stringResource(R.string.cd_research_locked, name)
        TechUiStatus.AVAILABLE ->
            stringResource(R.string.cd_research_start, name, node.cost, node.duration)
    }
    val rowShape = RoundedCornerShape(10.dp)
    val clickable = node.status == TechUiStatus.AVAILABLE || node.status == TechUiStatus.LOCKED
    var modifier = Modifier
        .fillMaxWidth()
        .let { if (node.status == TechUiStatus.IN_PROGRESS) it.background(UiColors.controlFill, rowShape) else it }
    if (clickable) {
        modifier = modifier.scaleClickable { viewModel.startResearch(node.tech) }
    }
    Row(
        modifier
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            when (node.status) {
                TechUiStatus.DONE -> Box(
                    Modifier
                        .size(18.dp)
                        .background(UiColors.positive.copy(alpha = 0.3f), RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        Modifier.size(12.dp),
                        tint = UiColors.positive,
                    )
                }
                TechUiStatus.IN_PROGRESS -> Box(
                    Modifier
                        .size(10.dp)
                        .background(UiColors.faction(factionIndex), CircleShape),
                )
                TechUiStatus.LOCKED -> Icon(
                    painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    Modifier.size(13.dp),
                    tint = UiColors.inactiveGlyph,
                )
                TechUiStatus.AVAILABLE -> Box(
                    Modifier
                        .size(8.dp)
                        .background(UiColors.inkMuted, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (node.status == TechUiStatus.LOCKED) UiColors.inactiveGlyph else UiColors.ink,
                maxLines = 1,
            )
            Text(
                effect,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = if (node.status == TechUiStatus.LOCKED) UiColors.inactiveGlyph else UiColors.inkMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))
        when (node.status) {
            TechUiStatus.DONE -> HudMicroLabel(stringResource(R.string.research_done), color = UiColors.positive)
            TechUiStatus.IN_PROGRESS -> Text(
                stringResource(R.string.research_active_progress, node.progress ?: 0, node.duration),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = UiColors.ink,
            )
            TechUiStatus.LOCKED -> Unit
            TechUiStatus.AVAILABLE -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_coin),
                    contentDescription = null,
                    Modifier.size(12.dp),
                    tint = if (node.affordable) UiColors.coin else UiColors.alert,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    stringResource(R.string.info_value_plain, node.cost),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (node.affordable) UiColors.ink else UiColors.alert,
                )
                Spacer(Modifier.width(5.dp))
                HudMicroLabel(stringResource(R.string.research_duration_short, node.duration))
            }
        }
    }
}

/** Pinned foot: the active research with its progress track, or the idle guidance. */
@Composable
private fun ActiveResearchCard(research: ResearchPanelState, factionIndex: Int) {
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
