package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.CampaignOutcome
import com.msa.fightandconquer.ui.CampaignRunState
import com.msa.fightandconquer.ui.CoachCard
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.campaign.StarRow
import com.msa.fightandconquer.ui.resolve
import com.msa.fightandconquer.ui.setup.scaleClickable

/**
 * The mission objectives, in the same slot the economy and diplomacy panels use — the
 * three are mutually exclusive, so the board is never covered by more than one.
 */
@Composable
internal fun ObjectivesPanel(run: CampaignRunState, topAnchor: Dp) {
    HudSidePanel(topAnchor) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    run.levelNameText ?: stringResource(run.levelName),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = UiColors.ink,
                    modifier = Modifier.weight(1f),
                )
                run.turnLimit?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.campaign_turn_limit, run.round, it),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (run.round >= it - 3) UiColors.alert else UiColors.inkMuted,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            run.objectives.forEach { line ->
                Row(
                    Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 18 dp check circle: filled positive when done, hairline-weight
                    // inactiveGlyph ring while pending.
                    Box(
                        Modifier
                            .size(18.dp)
                            .then(
                                if (line.done) {
                                    Modifier.background(UiColors.positive, CircleShape)
                                } else {
                                    Modifier.border(1.dp, UiColors.inactiveGlyph, CircleShape)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (line.done) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = UiColors.surface,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        line.text.resolve(),
                        fontSize = 13.sp,
                        color = if (line.done) UiColors.inkMuted else UiColors.ink,
                        textDecoration = if (line.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                    )
                    line.counter?.let {
                        Text(
                            it.resolve(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = UiColors.inkMuted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The coach card — the HUD's only solid-pastel surface. It sits above the bottom
 * bar rather than over the board, so a hint never covers the hexes it is pointing
 * at (the ring on those hexes is the other half of the same instruction).
 */
@Composable
internal fun CoachCardView(card: CoachCard, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = HudGutter)
            .background(UiColors.faction(0), shape)
            .border(1.dp, androidx.compose.ui.graphics.Color(0x243E3A36), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            HudMicroLabel(
                stringResource(R.string.campaign_coach_hint),
                color = UiColors.onFaction.copy(alpha = 0.7f),
            )
            Text(
                card.text.resolve(),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = UiColors.onFaction,
            )
        }
        if (card.dismissible) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .scaleClickable(onClick = onDismiss)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.campaign_coach_dismiss),
                    color = UiColors.onFaction,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Replaces [GameOverOverlay] for a campaign mission: the same overlay card, but
 * reporting against the mission's own terms rather than "player N wins".
 */
@Composable
internal fun CampaignOutcomeOverlay(
    outcome: CampaignOutcome,
    missionName: String,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
) {
    OverlayScrim {
        HudMicroLabel(missionName)
        Text(
            stringResource(
                if (outcome.won) R.string.outcome_victory else R.string.outcome_defeat,
            ),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = UiColors.ink,
            textAlign = TextAlign.Center,
        )
        if (outcome.won) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StarRow(outcome.stars, size = 22)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.outcome_rounds, outcome.rounds),
                    fontSize = 13.sp,
                    color = UiColors.inkMuted,
                )
            }
        } else {
            outcome.reason?.let {
                Text(it.resolve(), fontSize = 14.sp, color = UiColors.alert, textAlign = TextAlign.Center)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(if (outcome.won) outcome.debrief else R.string.outcome_defeat),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = UiColors.ink,
                textAlign = TextAlign.Center,
            )
            if (outcome.won && outcome.nextLevelId == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.outcome_campaign_complete),
                    fontSize = 13.sp,
                    color = UiColors.inkMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (outcome.won && outcome.nextLevelId != null) {
                OverlayButton(
                    text = stringResource(R.string.outcome_next),
                    fill = UiColors.faction(0),
                    textColor = UiColors.onFaction,
                    onClick = onNext,
                )
            }
            OverlayButton(
                text = stringResource(R.string.outcome_retry),
                fill = null,
                textColor = UiColors.ink,
                onClick = onRetry,
            )
            OverlayButton(
                text = stringResource(R.string.outcome_menu),
                fill = UiColors.controlFill,
                textColor = UiColors.inkMuted,
                onClick = onMenu,
            )
        }
    }
}
