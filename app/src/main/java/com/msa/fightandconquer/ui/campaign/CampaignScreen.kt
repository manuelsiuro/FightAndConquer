package com.msa.fightandconquer.ui.campaign

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.campaign.CampaignDef
import com.msa.fightandconquer.core.campaign.LevelDef
import com.msa.fightandconquer.ui.UiColors

/**
 * The campaign picker: a row of campaign chips over the selected campaign's mission list.
 *
 * Follows `SetupScreen`'s idiom (sections of chips, one scrolling column, back through
 * the host's `backToMenu`) rather than introducing a second navigation style for one
 * screen.
 */
@Composable
fun CampaignScreen(
    campaigns: List<CampaignDef>,
    progress: CampaignProgressStore,
    onLevel: (campaignId: String, levelId: String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    if (campaigns.isEmpty()) return

    var selectedId by rememberSaveable { mutableStateOf(campaigns.first().id) }
    val selected = remember(selectedId, campaigns) {
        campaigns.firstOrNull { it.id == selectedId } ?: campaigns.first()
    }
    val copy = CampaignText.campaign(selected.id)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.campaign_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = UiColors.ink,
        )
        Spacer(Modifier.height(12.dp))

        // Three campaign names do not fit a phone width, and a chip that wraps its
        // label vertically is unreadable — scroll the row instead.
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            campaigns.forEach { campaign ->
                CampaignChip(
                    campaign = campaign,
                    selected = campaign.id == selected.id,
                    unlocked = progress.isCampaignUnlocked(campaign),
                    onClick = { selectedId = campaign.id },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        copy?.let {
            Text(stringResource(it.blurb), fontSize = 14.sp, color = UiColors.inkSecondary)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            stringResource(
                R.string.campaign_progress,
                progress.completedCount(selected),
                selected.levels.size,
            ),
            fontSize = 13.sp,
            color = UiColors.inkSecondary,
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            itemsIndexed(selected.levels) { index, level ->
                LevelRow(
                    level = level,
                    number = index + 1,
                    unlocked = progress.isUnlocked(selected, index),
                    stars = progress.resultFor(level.id)?.stars ?: 0,
                    bestRounds = progress.resultFor(level.id)?.bestRounds,
                    lockedReason = if (index == 0) {
                        R.string.campaign_locked_academy
                    } else {
                        R.string.campaign_locked_previous
                    },
                    onClick = { onLevel(selected.id, level.id) },
                )
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.common_back), color = UiColors.ink)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CampaignChip(
    campaign: CampaignDef,
    selected: Boolean,
    unlocked: Boolean,
    onClick: () -> Unit,
) {
    val name = CampaignText.campaign(campaign.id)?.name
    Box(
        Modifier
            .background(
                if (selected) UiColors.faction(0) else UiColors.panel,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!unlocked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = stringResource(R.string.campaign_locked),
                    tint = if (selected) UiColors.onFaction else UiColors.inkSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                name?.let { stringResource(it) } ?: campaign.id,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) UiColors.onFaction else UiColors.ink,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun LevelRow(
    level: LevelDef,
    number: Int,
    unlocked: Boolean,
    stars: Int,
    bestRounds: Int?,
    lockedReason: Int,
    onClick: () -> Unit,
) {
    val copy = CampaignText.level(level.id)
    val title = copy?.let { stringResource(it.name) } ?: level.id
    Box(
        Modifier
            .fillMaxWidth()
            .background(UiColors.panel, RoundedCornerShape(14.dp))
            .then(if (unlocked) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp)
            .semantics { contentDescription = title },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(
                        if (unlocked) UiColors.faction(0) else UiColors.ink.copy(alpha = 0.15f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (unlocked) {
                    Text(
                        "$number",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = UiColors.onFaction,
                    )
                } else {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = UiColors.inkSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (unlocked) UiColors.ink else UiColors.inkSecondary,
                )
                Text(
                    when {
                        !unlocked -> stringResource(lockedReason)
                        bestRounds != null -> stringResource(R.string.campaign_best_rounds, bestRounds)
                        else -> stringResource(R.string.campaign_no_progress)
                    },
                    fontSize = 12.sp,
                    color = UiColors.inkSecondary,
                )
            }
            if (unlocked) StarRow(stars)
        }
    }
}

@Composable
internal fun StarRow(stars: Int, size: Int = 16) {
    val starsDescription = stringResource(R.string.campaign_stars, stars)
    Row(
        Modifier.semantics { contentDescription = starsDescription },
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        repeat(3) { index ->
            // One glyph, two weights: an earned star takes the coin gold the HUD already
            // uses for money, an unearned one the same shape at low alpha — no second
            // icon asset to keep in sync, and it reads in both themes.
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = if (index < stars) UiColors.coin else UiColors.progressTrack,
                modifier = Modifier.size(size.dp),
            )
        }
    }
}
