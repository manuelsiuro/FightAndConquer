package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.GameStatsState
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.civNameRes
import com.msa.fightandconquer.ui.debrief.ChartMarker
import com.msa.fightandconquer.ui.debrief.ChartSeries
import com.msa.fightandconquer.ui.debrief.TimelineChart
import com.msa.fightandconquer.ui.debrief.label
import com.msa.fightandconquer.ui.debrief.victimSeat
import com.msa.fightandconquer.ui.resolve
import com.msa.fightandconquer.ui.setup.scaleClickable

/** What the war report's chart is currently graphing. */
private enum class StatsLens(val labelRes: Int) {
    TERRITORY(R.string.debrief_lens_territory),
    ECONOMY(R.string.stats_lens_economy),
    TREASURY(R.string.debrief_lens_treasury),
    ARMY(R.string.stats_lens_army),
}

/**
 * The in-game war report: the viewer's own chronicle graphed live — territory,
 * economy, treasury and army over the rounds so far, this turn's numbers, the
 * running record, and the turning points the viewer was part of. Own faction
 * only by design; the cross-faction story is the post-match debrief's.
 */
@Composable
internal fun GameStatsSheet(stats: GameStatsState) {
    val faction = UiColors.faction(stats.viewerSeat)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelHeader(
            stringResource(R.string.stats_title),
            context = stringResource(R.string.hud_turn, stats.round + 1),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val factionDescription = stringResource(R.string.cd_faction_color, stats.viewerSeat + 1)
            Box(
                Modifier
                    .size(14.dp)
                    .background(faction, CircleShape)
                    .semantics { contentDescription = factionDescription },
            )
            Text(
                seatLabel(stats.viewerSeat, isHuman = true),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = UiColors.ink,
            )
            Text(
                stringResource(civNameRes(stats.civ)),
                fontSize = 11.sp,
                color = UiColors.inkMuted,
            )
        }
        NowStrip(stats)
        StatsChart(stats, faction)
        Column {
            PanelHeader(stringResource(R.string.stats_record_label))
            Spacer(Modifier.size(6.dp))
            Text(
                listOf(
                    stringResource(R.string.superlative_value_kills, stats.totals.unitsKilled),
                    stringResource(R.string.stats_total_losses, stats.totals.unitsLost),
                    stringResource(R.string.superlative_value_captured, stats.totals.hexesCaptured),
                    stringResource(R.string.superlative_value_ships, stats.totals.boatsSunk),
                    stringResource(R.string.superlative_value_pacts, stats.totals.pactsBroken),
                ).joinToString(stringResource(R.string.hud_stat_separator)),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = UiColors.ink,
            )
        }
        Column {
            PanelHeader(stringResource(R.string.stats_moments_label))
            Spacer(Modifier.size(6.dp))
            if (stats.moments.isEmpty()) {
                Text(
                    stringResource(R.string.debrief_no_moments),
                    fontSize = 12.sp,
                    color = UiColors.inkMuted,
                )
            }
            for (moment in stats.moments) {
                val suffered = moment.victimSeat == stats.viewerSeat
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        // Moments that happened TO the viewer get the 12% alert wash.
                        .background(if (suffered) UiColors.alert.copy(alpha = 0.12f) else UiColors.surface)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HudMicroLabel(stringResource(R.string.stats_moment_round, moment.round + 1))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        moment.label().resolve(),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = UiColors.ink,
                    )
                }
            }
        }
    }
}

/** This turn's live numbers — the chart only knows turn-start samples. */
@Composable
private fun NowStrip(stats: GameStatsState) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(UiColors.controlFill, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudMicroLabel(stringResource(R.string.stats_now_label))
        Spacer(Modifier.width(10.dp))
        Text(
            listOf(
                stringResource(R.string.stats_territory_value, stats.now.hexes, stats.now.territoryPercent),
                stringResource(R.string.stats_net_value, stats.now.income, stats.now.upkeep),
                stringResource(R.string.stats_treasury_value, stats.now.treasury),
                stringResource(R.string.stats_units_value, stats.now.units),
                stringResource(R.string.stats_strength_value, stats.now.strength),
            ).joinToString(stringResource(R.string.hud_stat_separator)),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = UiColors.ink,
        )
    }
}

@Composable
private fun StatsChart(stats: GameStatsState, faction: androidx.compose.ui.graphics.Color) {
    // Army needs recorded strength; a resumed pre-strength chronicle hides the lens.
    val lenses = StatsLens.entries.filter { it != StatsLens.ARMY || stats.strength.isNotEmpty() }
    var lens by remember(stats.viewerSeat) { mutableStateOf(StatsLens.TERRITORY) }

    val series = when (lens) {
        StatsLens.TERRITORY -> listOf(ChartSeries(faction, stats.rounds, stats.territory))
        StatsLens.ECONOMY -> listOf(
            ChartSeries(UiColors.positive, stats.rounds, stats.income),
            ChartSeries(UiColors.alert, stats.rounds, stats.upkeep),
        )
        StatsLens.TREASURY -> listOf(ChartSeries(faction, stats.rounds, stats.treasury))
        StatsLens.ARMY -> listOf(ChartSeries(faction, stats.strengthRounds, stats.strength))
    }
    // Own moments pinned to the lens' first curve at the nearest sample.
    val anchor = series.first()
    val markers = stats.moments.mapNotNull { moment ->
        val at = anchor.rounds.indexOfLast { it <= moment.round }
        if (at < 0) return@mapNotNull null
        ChartMarker(
            moment.round,
            anchor.values[at],
            if (moment.victimSeat == stats.viewerSeat) UiColors.alert else faction,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimelineChart(series, markers, filled = lens == StatsLens.TERRITORY)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lenses.forEach { candidate ->
                StatsLensChip(
                    text = stringResource(candidate.labelRes),
                    selected = candidate == lens,
                    onClick = { lens = candidate },
                )
            }
        }
    }
}

/** The debrief's lens chip, restated on the sheet's surface (that one is private). */
@Composable
private fun StatsLensChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .background(if (selected) UiColors.controlFill else UiColors.surface, shape)
            .border(1.dp, UiColors.hairline, shape)
            .clip(shape)
            .scaleClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) UiColors.ink else UiColors.inkSecondary,
        )
    }
}
