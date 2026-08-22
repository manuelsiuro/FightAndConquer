package com.msa.fightandconquer.ui.debrief

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.record.KeyMoment
import com.msa.fightandconquer.core.record.MatchKind
import com.msa.fightandconquer.core.record.MatchRecorderState
import com.msa.fightandconquer.core.record.SeatSeries
import com.msa.fightandconquer.ui.DebriefData
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.game.PiecePlinth
import com.msa.fightandconquer.ui.game.PlinthScale
import com.msa.fightandconquer.ui.resolve
import com.msa.fightandconquer.ui.seatNameRes
import com.msa.fightandconquer.ui.setup.mapShapeLowercaseRes
import com.msa.fightandconquer.ui.setup.mapSizeLabelRes
import com.msa.fightandconquer.ui.setup.scaleClickable

/** Which stat the one timeline chart is showing. */
private enum class Lens(val labelRes: Int) {
    TERRITORY(R.string.debrief_lens_territory),
    INCOME(R.string.debrief_lens_income),
    TREASURY(R.string.debrief_lens_treasury),
}

private fun SeatSeries.valuesFor(lens: Lens): List<Int> = when (lens) {
    Lens.TERRITORY -> hexes
    Lens.INCOME -> income
    Lens.TREASURY -> treasury
}

/**
 * The Chronicle: the post-match debrief — a ceremony (verdict), a story (the timeline
 * and its turning points) and the numbers (honours). The match is already torn down;
 * everything shown comes from the captured [DebriefData].
 */
@Composable
fun DebriefScreen(data: DebriefData, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val record = data.record

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    /** Staggered section entrance: each block fades and rises ~90 ms after the last. */
    @Composable
    fun Section(index: Int, content: @Composable () -> Unit) {
        AnimatedVisibility(
            visible = entered,
            enter = fadeIn(tween(350, delayMillis = index * 90)) +
                slideInVertically(tween(350, delayMillis = index * 90)) { it / 8 },
        ) { content() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(UiColors.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Section(0) { VerdictHeader(data) }

        Spacer(Modifier.height(24.dp))
        Section(1) { WarShape(record) }

        Spacer(Modifier.height(24.dp))
        Section(2) { TurningPoints(record) }

        Spacer(Modifier.height(24.dp))
        Section(3) { Honours(record, entered) }

        Spacer(Modifier.height(28.dp))
        OutlinedButton(onBack, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.game_over_back_to_menu))
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ----- verdict -----

@Composable
private fun VerdictHeader(data: DebriefData) {
    val record = data.record
    val humanSeat = record.seats.indexOfFirst { it.isHuman }.coerceAtLeast(0)
    val heroSeat = record.winnerSeat ?: humanSeat
    // Pass-and-play always ends in *somebody's* triumph; vs the machine, defeat mutes it.
    val celebrate = data.campaignWon
        ?: (record.meta.kind == MatchKind.PASS_AND_PLAY || record.winnerSeat == humanSeat)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(72.dp).background(UiColors.faction(heroSeat), CircleShape))
        PiecePlinth(R.drawable.piece_capital, PlinthScale.L, desaturated = !celebrate)
        Text(
            when {
                data.campaignWon == true -> stringResource(R.string.outcome_victory)
                data.campaignWon == false -> stringResource(R.string.outcome_defeat)
                else -> stringResource(
                    R.string.debrief_conquers,
                    stringResource(seatNameRes(record.winnerSeat ?: heroSeat)),
                )
            },
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = UiColors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.debrief_meta, matchTitle(data), stringResource(R.string.outcome_rounds, record.rounds)),
            fontSize = 12.sp,
            color = UiColors.inkSecondary,
        )
    }
}

/** "Medium continent" for generated maps, the mission or map name for authored ones. */
@Composable
private fun matchTitle(data: DebriefData): String {
    data.levelNameText?.let { return it }
    data.levelNameRes?.let { return stringResource(it) }
    val meta = data.record.meta
    val size = meta.size
    val shape = meta.shape
    return if (size != null && shape != null) {
        stringResource(
            R.string.setup_size_shape,
            stringResource(mapSizeLabelRes(size)),
            stringResource(mapShapeLowercaseRes(shape)),
        )
    } else {
        stringResource(R.string.campaign_title)
    }
}

// ----- the shape of the war -----

@Composable
private fun WarShape(record: MatchRecorderState) {
    var lens by remember { mutableStateOf(Lens.TERRITORY) }

    Column(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.debrief_section_shape))
        Column(
            Modifier
                .fillMaxWidth()
                .background(UiColors.panel, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val series = record.series.mapIndexed { seat, s ->
                ChartSeries(UiColors.faction(seat), s.rounds, s.valuesFor(lens))
            }
            // Turning points ride their actor's curve at the nearest sample.
            val markers = record.moments.mapNotNull { moment ->
                val s = record.series.getOrNull(moment.actorSeat) ?: return@mapNotNull null
                val at = s.rounds.indexOfLast { it <= moment.round }
                if (at < 0) return@mapNotNull null
                ChartMarker(moment.round, s.valuesFor(lens)[at], UiColors.faction(moment.actorSeat))
            }
            TimelineChart(series, markers, filled = lens == Lens.TERRITORY)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Lens.entries.forEach { candidate ->
                    LensChip(
                        text = stringResource(candidate.labelRes),
                        selected = candidate == lens,
                        onClick = { lens = candidate },
                    )
                }
            }
            Legend(record)
        }
    }
}

@Composable
private fun LensChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .background(if (selected) UiColors.controlFill else UiColors.panel, shape)
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

@Composable
private fun Legend(record: MatchRecorderState) {
    val humanSeat = record.seats.indexOfFirst { it.isHuman }
    record.seats.indices.chunked(3).forEach { rowSeats ->
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            rowSeats.forEach { seat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(UiColors.faction(seat), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    val name = stringResource(seatNameRes(seat))
                    Text(
                        if (seat == humanSeat && record.meta.kind != MatchKind.PASS_AND_PLAY) {
                            stringResource(R.string.debrief_legend_you, name)
                        } else {
                            name
                        },
                        fontSize = 12.sp,
                        color = UiColors.inkSecondary,
                    )
                }
            }
        }
    }
}

// ----- turning points -----

@Composable
private fun TurningPoints(record: MatchRecorderState) {
    val humanSeat = record.seats.indexOfFirst { it.isHuman }
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.debrief_section_moments))
        if (record.moments.isEmpty()) {
            Text(stringResource(R.string.debrief_no_moments), fontSize = 14.sp, color = UiColors.inkMuted)
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            record.moments.forEach { moment ->
                MomentRow(
                    moment = moment,
                    // A blow against the human seat carries the alert tint (12 % step);
                    // hot-seat games have no single "you" to mourn for.
                    struckYou = moment.victimSeat == humanSeat &&
                        record.meta.kind != MatchKind.PASS_AND_PLAY &&
                        humanSeat >= 0,
                )
            }
        }
    }
}

@Composable
private fun MomentRow(moment: KeyMoment, struckYou: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (struckYou) {
                    Modifier.background(UiColors.alert.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.background(UiColors.controlFill, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                (moment.round + 1).toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = UiColors.inkSecondary,
            )
        }
        Box(Modifier.size(8.dp).background(UiColors.faction(moment.actorSeat), CircleShape))
        Text(moment.label().resolve(), fontSize = 14.sp, color = UiColors.ink, modifier = Modifier.weight(1f))
    }
}

// ----- honours -----

private data class Honour(val labelRes: Int, val valueRes: Int, val seat: Int, val value: Int)

@Composable
private fun Honours(record: MatchRecorderState, entered: Boolean) {
    // Judged here, not recorded — the chronicle stores facts, the ceremony picks winners.
    fun best(values: List<Int>): Pair<Int, Int>? =
        values.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.let { it.index to it.value }

    val honours = listOfNotNull(
        best(record.series.map { it.hexes.maxOrNull() ?: 0 })?.let {
            Honour(R.string.superlative_realm, R.string.superlative_value_hexes, it.first, it.second)
        },
        best(record.series.map { it.treasury.maxOrNull() ?: 0 })?.let {
            Honour(R.string.superlative_hoard, R.string.superlative_value_gold, it.first, it.second)
        },
        best(record.totals.map { it.hexesCaptured })?.let {
            Honour(R.string.superlative_conqueror, R.string.superlative_value_captured, it.first, it.second)
        },
        best(record.totals.map { it.unitsKilled })?.let {
            Honour(R.string.superlative_butcher, R.string.superlative_value_kills, it.first, it.second)
        },
        best(record.totals.map { it.boatsSunk })?.let {
            Honour(R.string.superlative_admiral, R.string.superlative_value_ships, it.first, it.second)
        },
        best(record.totals.map { it.pactsBroken })?.let {
            Honour(R.string.superlative_oathbreaker, R.string.superlative_value_pacts, it.first, it.second)
        },
    )
    if (honours.isEmpty()) return

    Column(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.debrief_section_honours))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            honours.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { honour -> HonourCard(honour, entered, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HonourCard(honour: Honour, entered: Boolean, modifier: Modifier = Modifier) {
    val counted by animateIntAsState(if (entered) honour.value else 0, tween(700), label = "honour")
    Column(
        modifier
            .background(UiColors.panel, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(honour.labelRes), fontSize = 12.sp, color = UiColors.inkSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(UiColors.faction(honour.seat), CircleShape))
            Text(
                stringResource(honour.valueRes, counted),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = UiColors.ink,
            )
        }
    }
}

// ----- shared -----

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = UiColors.inkSecondary)
    Spacer(Modifier.height(6.dp))
}
