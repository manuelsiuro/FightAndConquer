package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitType
import kotlinx.serialization.Serializable

/** One objective's live progress, in the `have / need` shape the HUD renders. */
@Serializable
data class ObjectiveRow(
    val objective: Objective,
    val progress: Int,
    val target: Int,
) {
    val done: Boolean get() = progress >= target
}

/** Why a level ended in defeat — the HUD maps each to a line of prose. */
@Serializable
enum class DefeatReason {
    /** Every hex lost: the ordinary elimination rule. */
    ELIMINATED,

    /** The mission was not complete when the turn limit ran out. */
    OUT_OF_TIME,

    /** A hex the level told you to protect changed hands. */
    LOST_PROTECTED_HEX,

    /** Ended a round with no units at all. */
    NO_UNITS_LEFT,

    /** An ally you were meant to keep alive was knocked out. */
    ALLY_LOST,

    /** Another player won the conquest outright. */
    RIVAL_VICTORY,
}

/** The verdict on a level in progress. */
@Serializable
sealed interface Verdict {
    @Serializable data object InProgress : Verdict
    @Serializable data object Won : Verdict
    @Serializable data class Lost(val reason: DefeatReason) : Verdict
}

@Serializable
data class CampaignStatus(val rows: List<ObjectiveRow>, val verdict: Verdict)

/**
 * Pure, RNG-free scoring of a campaign level. Deliberately **outside the reducer**: the
 * engine's [GamePhase] keeps meaning "conquest finished", and nothing here can perturb
 * determinism, save replay or the AI.
 *
 * Defeat is checked before victory, so a turn limit that expires on the very turn the
 * last objective completes is still a loss — the ordering is the level's promise to the
 * player, not an accident.
 */
object Objectives {

    fun evaluate(
        state: GameState,
        tracker: CampaignTracker,
        level: LevelDef,
        seat: PlayerId = level.playerSeat,
    ): CampaignStatus {
        val rows = level.objectives.mapIndexed { index, objective ->
            row(state, tracker, objective, seat, index)
        }
        return CampaignStatus(rows, verdict(state, level, seat, rows))
    }

    private fun verdict(
        state: GameState,
        level: LevelDef,
        seat: PlayerId,
        rows: List<ObjectiveRow>,
    ): Verdict {
        val me = state.player(seat)
        if (me.eliminated) return Verdict.Lost(DefeatReason.ELIMINATED)
        (state.phase as? GamePhase.Finished)?.let { finished ->
            // Total conquest settles any mission, whatever it asked for: with no rival
            // left there is no pass to hold, no clock to beat and no ally to lose. This
            // is not a courtesy — without it a "hold this ridge" level that the player
            // simply wins outright would hang, waiting for an opponent who no longer
            // exists. It sits above the failure clauses for the same reason: the board
            // is already yours.
            return if (finished.winner == seat) Verdict.Won else Verdict.Lost(DefeatReason.RIVAL_VICTORY)
        }
        for (failure in level.failures) {
            when (failure) {
                is FailCondition.TurnLimit ->
                    if (state.turnNumber >= failure.rounds) return Verdict.Lost(DefeatReason.OUT_OF_TIME)
                is FailCondition.LoseHexes ->
                    if (failure.hexes.any { state.tiles[it]?.owner != seat }) {
                        return Verdict.Lost(DefeatReason.LOST_PROTECTED_HEX)
                    }
                FailCondition.LoseAllUnits ->
                    // Only judged once the level is under way: every level starts on an
                    // empty board unless the author placed a garrison.
                    if (state.turnNumber > 0 && state.units.none { it.value.owner == seat }) {
                        return Verdict.Lost(DefeatReason.NO_UNITS_LEFT)
                    }
                is FailCondition.AllyEliminated ->
                    if (state.player(failure.seat).eliminated) return Verdict.Lost(DefeatReason.ALLY_LOST)
            }
        }
        return if (rows.all { it.done }) Verdict.Won else Verdict.InProgress
    }

    private fun row(
        state: GameState,
        tracker: CampaignTracker,
        objective: Objective,
        seat: PlayerId,
        index: Int,
    ): ObjectiveRow = when (objective) {
        Objective.ConquerAll -> {
            val rivals = state.players.count { it.id != seat && !it.eliminated }
            ObjectiveRow(objective, if (rivals == 0) 1 else 0, 1)
        }

        is Objective.CaptureHexes -> ObjectiveRow(
            objective,
            objective.hexes.count { state.tiles[it]?.owner == seat },
            objective.hexes.size,
        )

        is Objective.HoldHexes -> {
            val since = tracker.holdSince[index]
            val held = if (since == null) 0 else (state.turnNumber - since).coerceAtLeast(0)
            ObjectiveRow(objective, held, objective.rounds)
        }

        is Objective.SurviveRounds ->
            ObjectiveRow(objective, state.turnNumber, objective.rounds)

        is Objective.OwnHexCount ->
            ObjectiveRow(objective, state.tiles.count { it.value.owner == seat }, objective.count)

        is Objective.ReachTreasury ->
            ObjectiveRow(objective, state.player(seat).treasury, objective.coins)

        is Objective.ReachIncome ->
            ObjectiveRow(objective, Rules.incomeOf(state, seat), objective.coins)

        is Objective.EliminatePlayer -> ObjectiveRow(
            objective,
            if (state.player(objective.seat).eliminated) 1 else 0,
            1,
        )

        is Objective.BuildCount -> ObjectiveRow(
            objective,
            state.tiles.count { it.value.owner == seat && it.value.building == objective.building.building },
            objective.count,
        )

        is Objective.FieldUnits ->
            ObjectiveRow(objective, countUnits(state, seat, objective.type, tier = null), objective.count)

        is Objective.SinkBoats -> ObjectiveRow(objective, tracker.boatsSunk, objective.count)
    }

    /**
     * Living units of [type] owned by [seat]. A land unit stowed aboard a transport is
     * still that player's soldier — it left the units map but not the war — so cargo
     * counts, exactly as it does for upkeep.
     */
    internal fun countUnits(state: GameState, seat: PlayerId, type: UnitType, tier: Int?): Int =
        state.units.values.count { unit ->
            val standing = unit.owner == seat && unit.type == type && (tier == null || unit.tier == tier)
            val stowed = unit.owner == seat && unit.cargo?.let {
                it.type == type && (tier == null || it.tier == tier)
            } == true
            standing || stowed
        }
}
