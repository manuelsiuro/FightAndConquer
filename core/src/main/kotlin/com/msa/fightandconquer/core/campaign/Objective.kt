package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A mission goal. A level is won when **every** objective is complete.
 *
 * Objectives are evaluated by [Objectives.evaluate] — a pure function of
 * `(GameState, CampaignTracker)` — deliberately *outside* the reducer: the engine's
 * [com.msa.fightandconquer.core.model.GamePhase] keeps meaning "conquest finished" and
 * nothing about campaign scoring can perturb determinism, saves or the AI.
 *
 * Every variant reports progress as `have / need` so the HUD renders one row shape for
 * all of them (see [ObjectiveRow]).
 */
@Serializable
sealed interface Objective {

    /** The classic win: be the last player standing. */
    @Serializable
    @SerialName("conquerAll")
    data object ConquerAll : Objective

    /** Own every hex in [hexes] at once. The board marks them for the player. */
    @Serializable
    @SerialName("captureHexes")
    data class CaptureHexes(val hexes: List<Hex>) : Objective

    /** Own every hex in [hexes] continuously for [rounds] full rounds. */
    @Serializable
    @SerialName("holdHexes")
    data class HoldHexes(val hexes: List<Hex>, val rounds: Int) : Objective

    /** Still be playing at the start of round [rounds]. */
    @Serializable
    @SerialName("survive")
    data class SurviveRounds(val rounds: Int) : Objective

    /** Own at least [count] hexes (starving ones count — they are still yours). */
    @Serializable
    @SerialName("ownHexes")
    data class OwnHexCount(val count: Int) : Objective

    /** Hold at least [coins] in the treasury. */
    @Serializable
    @SerialName("treasury")
    data class ReachTreasury(val coins: Int) : Objective

    /** Reach a gross income of at least [coins] per turn. */
    @Serializable
    @SerialName("income")
    data class ReachIncome(val coins: Int) : Objective

    /** Knock [seat] out of the game. */
    @Serializable
    @SerialName("eliminate")
    data class EliminatePlayer(val seat: PlayerId) : Objective

    /** Have at least [count] standing buildings of [building]. */
    @Serializable
    @SerialName("build")
    data class BuildCount(val building: BuildingType, val count: Int) : Objective

    /** Have at least [count] living units of [type] (cargo aboard a boat counts). */
    @Serializable
    @SerialName("field")
    data class FieldUnits(
        // "type" is the sealed-class discriminator — see LevelCondition.UnitCountAtLeast.
        @SerialName("unitType")
        val type: UnitType,
        val count: Int,
    ) : Objective

    /** Sink at least [count] enemy boats over the level (cumulative, tracked). */
    @Serializable
    @SerialName("sink")
    data class SinkBoats(val count: Int) : Objective
}

/**
 * How a level can be lost. Losing every hex (elimination) is *always* a defeat and needs
 * no entry here; these are the extra, authored ways to fail.
 */
@Serializable
sealed interface FailCondition {

    /** The mission must be complete before round [rounds] begins. */
    @Serializable
    @SerialName("turnLimit")
    data class TurnLimit(val rounds: Int) : FailCondition

    /** Losing ownership of any of [hexes] ends the level — the "protect this" clause. */
    @Serializable
    @SerialName("loseHexes")
    data class LoseHexes(val hexes: List<Hex>) : FailCondition

    /** Ending a round with no units left at all. */
    @Serializable
    @SerialName("loseAllUnits")
    data object LoseAllUnits : FailCondition

    /** [seat] is an ally you were meant to keep alive. */
    @Serializable
    @SerialName("allyLost")
    data class AllyEliminated(val seat: PlayerId) : FailCondition
}
