package com.msa.fightandconquer.ui

import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.record.KeyMoment
import com.msa.fightandconquer.core.record.MatchRecorderState
import com.msa.fightandconquer.core.record.SeatTotals
import com.msa.fightandconquer.ui.debrief.actorSeat
import com.msa.fightandconquer.ui.debrief.victimSeat

/** The war report's live line — this turn's numbers, not the last recorded sample. */
data class StatsNow(
    val hexes: Int,
    val territoryPercent: Int,
    val income: Int,
    val upkeep: Int,
    val treasury: Int,
    val units: Int,
    val strength: Int,
)

/**
 * The in-game war report: the viewer's own chronicle, nobody else's. Enemy series
 * stay out by design — under fog they are hidden information, and comparisons
 * belong to the post-match debrief where fog has lifted.
 */
data class GameStatsState(
    val viewerSeat: Int,
    val round: Int,
    val civ: Civilization,
    /** Per-round samples (turn-start values), parallel to [rounds]. */
    val rounds: List<Int>,
    val territory: List<Int>,
    val income: List<Int>,
    val upkeep: List<Int>,
    val treasury: List<Int>,
    /**
     * Strength rides its own round axis: records that predate the field resume with
     * a shorter list, so it is tail-aligned against [rounds]. Empty = hide the lens.
     */
    val strengthRounds: List<Int>,
    val strength: List<Int>,
    val landHexes: Int,
    val now: StatsNow,
    val totals: SeatTotals,
    /** Moments the viewer acted in or suffered, newest first, capped. */
    val moments: List<KeyMoment>,
    /** Indexed by seat; drives the Player N / AI N naming in the turning points. */
    val seatIsHuman: List<Boolean>,
)

/** The turning-points feed stays glanceable — the full story is the debrief's job. */
private const val MAX_STATS_MOMENTS = 6

/**
 * The pure record+state -> war report mapping (JVM-testable; the ViewModel only
 * wraps it) — the [buildResearchPanel] idiom.
 */
fun buildGameStats(
    record: MatchRecorderState,
    state: GameState,
    viewer: PlayerId,
): GameStatsState {
    val seat = viewer.value
    val series = record.series[seat]
    val player = state.player(viewer)
    val landHexes = record.meta.landHexes
    val hexesNow = state.ownedHexCount(viewer)
    val ownUnits = state.units.values.filter { it.owner == viewer }
    return GameStatsState(
        viewerSeat = seat,
        round = state.turnNumber,
        civ = player.civ,
        rounds = series.rounds,
        territory = series.hexes,
        income = series.income,
        upkeep = series.upkeep,
        treasury = series.treasury,
        strengthRounds = series.rounds.takeLast(series.strength.size),
        strength = series.strength,
        landHexes = landHexes,
        now = StatsNow(
            hexes = hexesNow,
            territoryPercent = if (landHexes > 0) hexesNow * 100 / landHexes else 0,
            income = Rules.incomeOf(state, viewer),
            upkeep = Rules.upkeepOf(state, viewer),
            treasury = player.treasury,
            units = ownUnits.size,
            strength = ownUnits.sumOf { Rules.strengthOf(state, it) },
        ),
        totals = record.totals[seat],
        moments = record.moments
            .filter { it.actorSeat == seat || it.victimSeat == seat }
            .takeLast(MAX_STATS_MOMENTS)
            .reversed(),
        seatIsHuman = record.seats.map { it.isHuman },
    )
}
