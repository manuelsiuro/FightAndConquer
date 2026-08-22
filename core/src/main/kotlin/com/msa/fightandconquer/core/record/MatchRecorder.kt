package com.msa.fightandconquer.core.record

import com.msa.fightandconquer.core.engine.DeathCause
import com.msa.fightandconquer.core.engine.GameEvent
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.map.MapShape
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind

/** How the match was set up — the debrief's byline. */
enum class MatchKind { SKIRMISH_VS_AI, PASS_AND_PLAY, CAMPAIGN, CUSTOM_MAP }

/**
 * Match facts a [GameState] cannot tell you after the fact: the setup that produced it.
 * Captured at match start because the app does not retain its setup object afterwards.
 */
data class MatchMeta(
    val kind: MatchKind,
    val seed: Long,
    val landHexes: Int,
    val fogOfWar: Boolean,
    /** Generated skirmishes only; authored maps (campaign/custom) leave these null. */
    val size: MapSize? = null,
    val shape: MapShape? = null,
    /** Campaign level id / custom map id, for the app to resolve into a title. */
    val levelId: String? = null,
    val customMapName: String? = null,
)

/** One seat's identity; index in [MatchRecorderState.seats] = [PlayerId.value]. */
data class SeatDescriptor(
    val isHuman: Boolean,
    /** AI seats only. */
    val difficulty: Difficulty? = null,
    val civ: Civilization,
)

/**
 * One seat's per-round samples as parallel arrays keyed by [rounds]. A seat that is
 * eliminated simply stops getting samples, so its series ends where it died.
 */
data class SeatSeries(
    val rounds: List<Int> = emptyList(),
    val hexes: List<Int> = emptyList(),
    val income: List<Int> = emptyList(),
    val upkeep: List<Int> = emptyList(),
    val treasury: List<Int> = emptyList(),
    val units: List<Int> = emptyList(),
)

/** A turning point worth retelling. Seat fields are [PlayerId.value] indices. */
sealed interface KeyMoment {
    val round: Int

    data class CapitalLooted(override val round: Int, val by: Int, val victim: Int, val loot: Int) : KeyMoment
    data class PactBetrayed(override val round: Int, val breaker: Int, val victim: Int, val penalty: Int) : KeyMoment
    data class WentBankrupt(override val round: Int, val seat: Int) : KeyMoment
    data class ShipSunk(override val round: Int, val owner: Int, val by: Int) : KeyMoment
    data class Eliminated(override val round: Int, val seat: Int) : KeyMoment
    data class Crowned(override val round: Int, val winner: Int) : KeyMoment
}

/** Running per-seat tallies of facts no later state reveals. */
data class SeatTotals(
    val unitsKilled: Int = 0,
    val unitsLost: Int = 0,
    val boatsSunk: Int = 0,
    val hexesCaptured: Int = 0,
    val pactsBroken: Int = 0,
)

/**
 * Everything the post-match debrief shows, recorded live because the engine keeps no
 * full-game action log (the autosave is turn-start state + the current turn only).
 *
 * Advanced by a **pure fold** — [step] over one reducer transition — exactly like
 * [com.msa.fightandconquer.core.campaign.CampaignTracker]: nothing here influences the
 * reducer; it is scoreboard, not rules. In-memory only by design — it is never
 * serialized, and a match resumed from an autosave plays unrecorded (see
 * docs/debrief.md for the trade-off).
 */
data class MatchRecorderState(
    val meta: MatchMeta,
    val seats: List<SeatDescriptor>,
    /** Indexed like [seats]. */
    val series: List<SeatSeries>,
    val moments: List<KeyMoment>,
    /** Indexed like [seats]. */
    val totals: List<SeatTotals>,
    val rounds: Int = 0,
    /** Conquest winner seat; null while playing, and for campaign verdicts settled off-board. */
    val winnerSeat: Int? = null,
    val finished: Boolean = false,
) {

    /** Marks the record complete; further [step]s become no-ops. */
    fun finish(finalState: GameState): MatchRecorderState = copy(
        rounds = finalState.turnNumber,
        winnerSeat = (finalState.phase as? GamePhase.Finished)?.winner?.value ?: winnerSeat,
        finished = true,
    )

    companion object {

        /**
         * High-frequency moments stop accumulating past this bound so a 400-round naval
         * slugfest cannot grow the feed without limit; the rare kinds always land.
         */
        private const val MAX_FREQUENT_MOMENTS = 300

        fun start(initial: GameState, meta: MatchMeta): MatchRecorderState {
            val seats = initial.players.map { player ->
                SeatDescriptor(
                    isHuman = player.kind is PlayerKind.Human,
                    difficulty = (player.kind as? PlayerKind.Ai)?.difficulty,
                    civ = player.civ,
                )
            }
            val series = initial.players.map { player ->
                if (player.eliminated) SeatSeries() else sample(SeatSeries(), initial, player.id)
            }
            return MatchRecorderState(
                meta = meta,
                seats = seats,
                series = series,
                moments = emptyList(),
                totals = List(seats.size) { SeatTotals() },
            )
        }

        /**
         * Folds one reducer transition into the record. [before]/[after] bracket the
         * action so event facts can be attributed to an owner — events carry unit ids
         * and no round stamp, so the dead are looked up in [before] and every moment is
         * stamped with `after.turnNumber` (the CampaignTracker conventions).
         */
        fun step(
            prev: MatchRecorderState,
            before: GameState,
            after: GameState,
            events: List<GameEvent>,
        ): MatchRecorderState {
            if (prev.finished) return prev
            var series = prev.series
            var moments = prev.moments
            var totals = prev.totals
            val round = after.turnNumber
            val actor = before.currentPlayer

            fun addMoment(moment: KeyMoment, frequent: Boolean = false) {
                if (frequent && moments.size >= MAX_FREQUENT_MOMENTS) return
                moments = moments + moment
            }
            fun tally(seat: PlayerId, mutate: (SeatTotals) -> SeatTotals) {
                totals = totals.toMutableList().also { it[seat.value] = mutate(it[seat.value]) }
            }

            for (event in events) {
                when (event) {
                    is GameEvent.TurnStarted -> {
                        val seat = event.player.value
                        // A round is sampled once per seat, income/upkeep straight off
                        // the event (the values the turn actually started with).
                        if (series[seat].rounds.lastOrNull() != round) {
                            series = series.toMutableList().also {
                                it[seat] = sample(it[seat], after, event.player, event.income, event.upkeep, round)
                            }
                        }
                    }
                    is GameEvent.UnitDied -> {
                        val dead = before.units[event.unit] ?: continue
                        if (event.cause != DeathCause.DISBANDED) {
                            tally(dead.owner) { it.copy(unitsLost = it.unitsLost + 1) }
                        }
                        if (event.cause == DeathCause.KILLED && dead.owner != actor) {
                            tally(actor) { it.copy(unitsKilled = it.unitsKilled + 1) }
                        }
                        // A lost warship duel sinks the actor's own boat; no event field
                        // names the defender, so only enemy sinkings are credited.
                        if (event.cause == DeathCause.SUNK && Rules.isNaval(dead.type) && dead.owner != actor) {
                            tally(actor) { it.copy(boatsSunk = it.boatsSunk + 1) }
                            addMoment(KeyMoment.ShipSunk(round, dead.owner.value, actor.value), frequent = true)
                        }
                    }
                    is GameEvent.HexCaptured ->
                        tally(event.newOwner) { it.copy(hexesCaptured = it.hexesCaptured + 1) }
                    is GameEvent.CapitalMoved ->
                        // Relocations without loot are routine; only a fallen, looted
                        // capital is a turning point.
                        if (event.loot > 0) {
                            addMoment(KeyMoment.CapitalLooted(round, actor.value, event.player.value, event.loot))
                        }
                    is GameEvent.PactBroken -> {
                        tally(event.breaker) { it.copy(pactsBroken = it.pactsBroken + 1) }
                        addMoment(KeyMoment.PactBetrayed(round, event.breaker.value, event.victim.value, event.penalty))
                    }
                    is GameEvent.Bankruptcy ->
                        addMoment(KeyMoment.WentBankrupt(round, event.player.value))
                    is GameEvent.PlayerEliminated ->
                        addMoment(KeyMoment.Eliminated(round, event.player.value))
                    is GameEvent.GameOver ->
                        addMoment(KeyMoment.Crowned(round, event.winner.value))
                    else -> Unit
                }
            }

            if (series === prev.series && moments === prev.moments && totals === prev.totals) return prev
            return prev.copy(series = series, moments = moments, totals = totals)
        }

        private fun sample(
            prev: SeatSeries,
            state: GameState,
            id: PlayerId,
            incomeNow: Int = Rules.incomeOf(state, id),
            upkeepNow: Int = Rules.upkeepOf(state, id),
            round: Int = state.turnNumber,
        ): SeatSeries = SeatSeries(
            rounds = prev.rounds + round,
            hexes = prev.hexes + state.ownedHexCount(id),
            income = prev.income + incomeNow,
            upkeep = prev.upkeep + upkeepNow,
            treasury = prev.treasury + state.player(id).treasury,
            units = prev.units + state.units.values.count { it.owner == id },
        )
    }
}
