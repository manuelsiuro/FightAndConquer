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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
import com.msa.fightandconquer.ui.DiplomacyPanelState
import com.msa.fightandconquer.ui.GameViewModel
import com.msa.fightandconquer.ui.IncomingProposal
import com.msa.fightandconquer.ui.PactStatus
import com.msa.fightandconquer.ui.PactUiState
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.setup.scaleClickable

@Composable
internal fun DiplomacyPanel(state: DiplomacyPanelState, viewModel: GameViewModel, topAnchor: Dp) {
    HudSidePanel(topAnchor) {
        Column {
            PanelHeader(stringResource(R.string.diplomacy_title))
            val visible = state.rows.filter { !it.eliminated }
            visible.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = UiColors.divider)
                DiplomacyRow(row, state, viewModel)
            }
        }
        Text(
            stringResource(
                R.string.diplomacy_footer,
                state.pactDurationRounds,
                state.breakPenaltyPercent,
            ),
            fontSize = 11.sp,
            color = UiColors.inkMuted,
        )
    }
}

@Composable
private fun DiplomacyRow(row: PactStatus, panel: DiplomacyPanelState, viewModel: GameViewModel) {
    var tributeOpen by remember(row.playerIndex) { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val factionDescription = stringResource(R.string.cd_faction_color, row.playerIndex + 1)
            Box(
                Modifier
                    .size(14.dp)
                    .background(UiColors.faction(row.playerIndex), CircleShape)
                    .semantics { contentDescription = factionDescription },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                seatLabel(row.playerIndex, row.isHuman),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = UiColors.ink,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            StatusPill(row)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (row.state == PactUiState.WAR) {
                PanelButton(
                    onClick = { viewModel.proposePact(row.playerIndex) },
                ) {
                    Icon(
                        painterResource(R.drawable.ic_pact),
                        contentDescription = null,
                        Modifier.size(14.dp),
                        tint = UiColors.ink,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        stringResource(R.string.diplomacy_propose),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = UiColors.ink,
                    )
                }
            }
            PanelButton(onClick = { tributeOpen = !tributeOpen }) {
                Icon(
                    painterResource(R.drawable.ic_coin),
                    contentDescription = null,
                    Modifier.size(14.dp),
                    tint = UiColors.coin,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    stringResource(R.string.diplomacy_tribute),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = UiColors.ink,
                )
            }
        }
        if (tributeOpen) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (amount in panel.tributeChoices) {
                    val affordable = amount <= panel.treasury
                    val tributeDescription =
                        stringResource(R.string.cd_send_tribute, amount, row.playerIndex + 1)
                    TributeChip(
                        amount = amount,
                        enabled = affordable,
                        description = tributeDescription,
                        onClick = {
                            tributeOpen = false
                            viewModel.sendTribute(row.playerIndex, amount)
                        },
                    )
                }
            }
        }
    }
}

/** Status pill: 10 sp micro-label in ink over exactly one 30% tint. */
@Composable
private fun StatusPill(row: PactStatus) {
    val statusText = when (row.state) {
        PactUiState.WAR -> stringResource(R.string.diplomacy_status_war)
        PactUiState.PACT -> stringResource(R.string.diplomacy_status_pact, row.turnsRemaining ?: 0)
        PactUiState.PROPOSAL_SENT -> stringResource(R.string.diplomacy_status_proposed)
        PactUiState.PROPOSAL_RECEIVED -> stringResource(R.string.diplomacy_status_offer)
    }
    val fill = when (row.state) {
        PactUiState.WAR -> UiColors.alert.copy(alpha = 0.3f)
        PactUiState.PACT -> UiColors.positive.copy(alpha = 0.3f)
        PactUiState.PROPOSAL_SENT -> UiColors.controlFill
        PactUiState.PROPOSAL_RECEIVED -> UiColors.coin.copy(alpha = 0.3f)
    }
    Box(
        Modifier
            .background(fill, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        HudMicroLabel(statusText, color = UiColors.ink)
    }
}

/** 40 dp outlined panel action (Propose / Tribute). */
@Composable
private fun PanelButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .height(40.dp)
            .border(1.dp, UiColors.ink, shape)
            .clip(shape)
            .scaleClickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** 32 dp controlFill tribute chip; disabled = 38% container + inactiveGlyph text. */
@Composable
private fun TributeChip(
    amount: Int,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(UiColors.controlFill.copy(alpha = if (enabled) 1f else 0.38f))
            .scaleClickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_coin),
            contentDescription = null,
            Modifier.size(12.dp),
            tint = if (enabled) UiColors.coin else UiColors.inactiveGlyph,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.diplomacy_tribute_amount, amount),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) UiColors.ink else UiColors.inactiveGlyph,
        )
    }
}

@Composable
internal fun ProposalStrip(proposals: List<IncomingProposal>, viewModel: GameViewModel) {
    Column {
        for (proposal in proposals) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = HudGutter, end = HudGutter, top = HudSpacing)
                    .hudSurface(16.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val factionDescription =
                    stringResource(R.string.cd_faction_color, proposal.fromIndex + 1)
                Box(
                    Modifier
                        .size(14.dp)
                        .background(UiColors.faction(proposal.fromIndex), CircleShape)
                        .semantics { contentDescription = factionDescription },
                )
                // Neutral glyph wash — the 12% step of the tint ladder.
                Box(
                    Modifier
                        .size(18.dp)
                        .background(UiColors.ink.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_pact),
                        contentDescription = null,
                        Modifier.size(11.dp),
                        tint = UiColors.inkMuted,
                    )
                }
                Text(
                    stringResource(
                        R.string.diplomacy_proposal_text,
                        proposal.fromIndex + 1,
                        proposal.durationRounds,
                    ),
                    fontSize = 13.sp,
                    color = UiColors.ink,
                    modifier = Modifier.weight(1f),
                )
                val declineShape = RoundedCornerShape(12.dp)
                Box(
                    Modifier
                        .height(36.dp)
                        .border(1.dp, UiColors.ink, declineShape)
                        .clip(declineShape)
                        .scaleClickable { viewModel.declinePact(proposal.fromIndex) }
                        .semantics { role = Role.Button }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.diplomacy_decline),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = UiColors.ink,
                    )
                }
                Box(
                    Modifier
                        .height(36.dp)
                        .background(UiColors.filledInk, declineShape)
                        .clip(declineShape)
                        .scaleClickable { viewModel.acceptPact(proposal.fromIndex) }
                        .semantics { role = Role.Button }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.diplomacy_accept),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = UiColors.onFilledInk,
                    )
                }
            }
        }
    }
}
