package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.CampaignOutcome
import com.msa.fightandconquer.ui.CampaignRunState
import com.msa.fightandconquer.ui.CoachCard
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.campaign.StarRow
import com.msa.fightandconquer.ui.resolve

/**
 * The mission objectives, in the same slot the economy and diplomacy panels use — the
 * three are mutually exclusive, so the board is never covered by more than one.
 */
@Composable
internal fun ObjectivesPanel(run: CampaignRunState) {
    Surface(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(start = HudGutter, top = TopBarHeight + HudGutter)
            .width(264.dp),
        shape = RoundedCornerShape(16.dp),
        color = UiColors.panel,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(run.levelName),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = UiColors.ink,
                )
                run.turnLimit?.let {
                    Text(
                        stringResource(R.string.campaign_turn_limit, run.round, it),
                        fontSize = 12.sp,
                        color = if (run.round >= it - 3) UiColors.alert else UiColors.inkSecondary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            run.objectives.forEach { line ->
                Row(
                    Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .background(
                                if (line.done) UiColors.faction(0) else UiColors.ink.copy(alpha = 0.12f),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (line.done) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = UiColors.onFaction,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        line.text.resolve(),
                        fontSize = 14.sp,
                        color = if (line.done) UiColors.inkSecondary else UiColors.ink,
                        textDecoration = if (line.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                    )
                    line.counter?.let {
                        Text(it.resolve(), fontSize = 13.sp, color = UiColors.inkSecondary)
                    }
                }
            }
        }
    }
}

/**
 * The coach card. It sits above the bottom bar rather than over the board, so a hint
 * never covers the hexes it is pointing at (the ring on those hexes is the other half of
 * the same instruction).
 */
@Composable
internal fun CoachCardView(card: CoachCard, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(UiColors.faction(0), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            card.text.resolve(),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            color = UiColors.onFaction,
            modifier = Modifier.weight(1f),
        )
        if (card.dismissible) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.campaign_coach_dismiss),
                    color = UiColors.onFaction,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Replaces [GameOverOverlay] for a campaign mission: the same scrim, but reporting
 * against the mission's own terms rather than "player N wins".
 */
@Composable
internal fun CampaignOutcomeOverlay(
    outcome: CampaignOutcome,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(UiColors.bannerScrim)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(
                    if (outcome.won) R.string.outcome_victory else R.string.outcome_defeat,
                ),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = UiColors.ink,
            )
            Spacer(Modifier.height(12.dp))
            if (outcome.won) {
                StarRow(outcome.stars, size = 30)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.outcome_rounds, outcome.rounds),
                    fontSize = 13.sp,
                    color = UiColors.inkSecondary,
                )
            } else {
                outcome.reason?.let {
                    Text(
                        it.resolve(),
                        fontSize = 14.sp,
                        color = UiColors.alert,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(
                    if (outcome.won) outcome.debrief else R.string.outcome_defeat,
                ),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = UiColors.ink.copy(alpha = 0.85f),
            )
            if (outcome.won && outcome.nextLevelId == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.outcome_campaign_complete),
                    fontSize = 13.sp,
                    color = UiColors.inkSecondary,
                )
            }
            Spacer(Modifier.height(24.dp))
            if (outcome.won && outcome.nextLevelId != null) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UiColors.faction(0),
                        contentColor = UiColors.onFaction,
                    ),
                ) { Text(stringResource(R.string.outcome_next)) }
                Spacer(Modifier.height(10.dp))
            }
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.outcome_retry), color = UiColors.ink)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onMenu, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.outcome_menu), color = UiColors.ink)
            }
        }
    }
}
