package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.DiplomacyPanelState
import com.msa.fightandconquer.ui.GameViewModel
import com.msa.fightandconquer.ui.IncomingProposal
import com.msa.fightandconquer.ui.PactStatus
import com.msa.fightandconquer.ui.PactUiState
import com.msa.fightandconquer.ui.UiColors

@Composable
internal fun DiplomacyPanel(state: DiplomacyPanelState, viewModel: GameViewModel) {
    Surface(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(start = HudGutter, top = TopBarHeight + HudGutter)
            .width(270.dp),
        shape = RoundedCornerShape(16.dp),
        color = UiColors.panel,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.diplomacy_title),
                fontSize = 12.sp,
                color = UiColors.inkMuted,
                letterSpacing = 0.8.sp,
            )
            val visible = state.rows.filter { !it.eliminated }
            visible.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = UiColors.ink.copy(alpha = 0.12f))
                DiplomacyRow(row, state, viewModel)
            }
            Spacer(Modifier.height(2.dp))
            HorizontalDivider(color = UiColors.ink.copy(alpha = 0.12f))
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    R.string.diplomacy_footer,
                    state.pactDurationRounds,
                    state.breakPenaltyPercent,
                ),
                fontSize = 11.sp,
                color = UiColors.inkFaint,
            )
        }
    }
}

@Composable
private fun DiplomacyRow(row: PactStatus, panel: DiplomacyPanelState, viewModel: GameViewModel) {
    var tributeOpen by remember(row.playerIndex) { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 6.dp)) {
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
                if (row.isHuman) {
                    stringResource(R.string.hud_player, row.playerIndex + 1)
                } else {
                    stringResource(R.string.hud_ai_player, row.playerIndex + 1)
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = UiColors.ink,
            )
            Spacer(Modifier.width(10.dp))
            val statusText = when (row.state) {
                PactUiState.WAR -> stringResource(R.string.diplomacy_status_war)
                PactUiState.PACT -> stringResource(R.string.diplomacy_status_pact, row.turnsRemaining ?: 0)
                PactUiState.PROPOSAL_SENT -> stringResource(R.string.diplomacy_status_proposed)
                PactUiState.PROPOSAL_RECEIVED -> stringResource(R.string.diplomacy_status_offer)
            }
            val statusColor = when (row.state) {
                PactUiState.WAR -> UiColors.alert
                PactUiState.PACT -> UiColors.positive
                PactUiState.PROPOSAL_SENT -> UiColors.inkSecondary
                PactUiState.PROPOSAL_RECEIVED -> UiColors.ink
            }
            val statusBackground = when (row.state) {
                PactUiState.WAR -> UiColors.alert.copy(alpha = 0.10f)
                PactUiState.PACT -> UiColors.positive.copy(alpha = 0.12f)
                PactUiState.PROPOSAL_SENT -> UiColors.ink.copy(alpha = 0.06f)
                PactUiState.PROPOSAL_RECEIVED -> UiColors.toastWarning.copy(alpha = 0.5f)
            }
            Surface(shape = RoundedCornerShape(50), color = statusBackground) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.state != PactUiState.WAR) {
                        Icon(
                            painterResource(R.drawable.ic_pact),
                            contentDescription = null,
                            Modifier.size(11.dp),
                            tint = statusColor,
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.state == PactUiState.WAR) {
                OutlinedButton(
                    onClick = { viewModel.proposePact(row.playerIndex) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_pact),
                        contentDescription = null,
                        Modifier.size(14.dp),
                        tint = UiColors.ink,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.diplomacy_propose), fontSize = 13.sp, color = UiColors.ink)
                }
                Spacer(Modifier.width(8.dp))
            }
            OutlinedButton(
                onClick = { tributeOpen = !tributeOpen },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(stringResource(R.string.diplomacy_tribute), fontSize = 13.sp, color = UiColors.ink)
            }
        }
        if (tributeOpen) {
            Row {
                for (amount in panel.tributeChoices) {
                    val affordable = amount <= panel.treasury
                    val tributeDescription =
                        stringResource(R.string.cd_send_tribute, amount, row.playerIndex + 1)
                    OutlinedButton(
                        onClick = {
                            tributeOpen = false
                            viewModel.sendTribute(row.playerIndex, amount)
                        },
                        enabled = affordable,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .semantics { contentDescription = tributeDescription },
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_coin),
                            contentDescription = null,
                            Modifier.size(12.dp),
                            tint = if (affordable) UiColors.coin else UiColors.inkFaint,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.diplomacy_tribute_amount, amount),
                            fontSize = 12.sp,
                            color = if (affordable) UiColors.ink else UiColors.inkFaint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProposalStrip(proposals: List<IncomingProposal>, viewModel: GameViewModel) {
    Column {
        for (proposal in proposals) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = HudGutter, vertical = 2.dp),
                shape = RoundedCornerShape(14.dp),
                color = UiColors.panel,
                shadowElevation = 4.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(UiColors.faction(proposal.fromIndex), CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painterResource(R.drawable.ic_pact),
                        contentDescription = null,
                        Modifier.size(14.dp),
                        tint = UiColors.inkSecondary,
                    )
                    Spacer(Modifier.width(8.dp))
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
                    OutlinedButton(
                        onClick = { viewModel.declinePact(proposal.fromIndex) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text(stringResource(R.string.diplomacy_decline), fontSize = 13.sp, color = UiColors.ink)
                    }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = { viewModel.acceptPact(proposal.fromIndex) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    ) {
                        Text(stringResource(R.string.diplomacy_accept), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
