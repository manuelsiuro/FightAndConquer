package com.msa.fightandconquer.ui.campaign

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.campaign.CampaignTracker
import com.msa.fightandconquer.core.campaign.LevelDef
import com.msa.fightandconquer.core.campaign.LevelFactory
import com.msa.fightandconquer.core.campaign.Objectives
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.guide.FieldGuide
import com.msa.fightandconquer.ui.resolve

/**
 * The pre-mission card: the story, what the mission asks for, what will end it early,
 * and — for the ideas this level introduces — a way straight into the Field Guide.
 *
 * The objectives are read from the level's own opening position through the same
 * [Objectives.evaluate] the HUD uses, so the briefing can never drift from what the game
 * will actually score.
 */
@Composable
fun BriefingScreen(
    level: LevelDef,
    alreadyCleared: Boolean,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val copy = CampaignText.level(level.id)
    val rows = remember(level.id) {
        Objectives.evaluate(LevelFactory.instantiate(level), CampaignTracker(), level).rows
    }
    var guideFocus by remember { mutableStateOf<String?>(null) }
    var guideOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(UiColors.background)
                .safeDrawingPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                copy?.let { stringResource(it.name) } ?: level.id,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = UiColors.ink,
            )
            Spacer(Modifier.height(12.dp))
            copy?.let {
                Text(
                    stringResource(it.briefing),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = UiColors.ink.copy(alpha = 0.85f),
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.briefing_objectives))
            rows.forEach { row ->
                BulletLine(row.label().resolve(), UiColors.faction(0))
            }

            if (level.failures.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.briefing_constraints))
                level.failures.forEach { failure ->
                    BulletLine(failure.label().resolve(), UiColors.alert)
                }
            }

            val concepts = remember(level.id) { BriefingConcepts.forLevel(level) }
            if (concepts.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.briefing_new_here))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    concepts.forEach { concept ->
                        ConceptChip(stringResource(concept.labelRes)) {
                            guideFocus = concept.guideEntryId
                            guideOpen = true
                        }
                    }
                }
            }

            level.parRounds?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.briefing_par, it),
                    fontSize = 13.sp,
                    color = UiColors.inkSecondary,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UiColors.faction(0),
                    contentColor = UiColors.onFaction,
                ),
            ) {
                Text(
                    stringResource(
                        if (alreadyCleared) R.string.briefing_replay else R.string.briefing_start,
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_back), color = UiColors.ink)
            }
            Spacer(Modifier.height(20.dp))
        }

        if (guideOpen) {
            FieldGuide(onClose = { guideOpen = false }, focusEntryId = guideFocus)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = UiColors.inkSecondary,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun BulletLine(text: String, dot: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(dot, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, color = UiColors.ink)
    }
}

@Composable
private fun ConceptChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(UiColors.panel, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 13.sp, color = UiColors.ink)
    }
}
