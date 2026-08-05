package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A board predicate an author can attach to a teaching step ([HintStep]) or a story beat
 * ([ScriptTrigger]) — one vocabulary for both, so a level reads the same whether it is
 * coaching or narrating.
 *
 * Everything except [UiSignal] and [Acknowledged] is a pure function of the game state,
 * which is what makes whole levels testable headlessly.
 */
@Serializable
sealed interface LevelCondition {

    /** Waits for the player to dismiss the card. Never true on its own. */
    @Serializable
    @SerialName("acknowledged")
    data object Acknowledged : LevelCondition

    /**
     * Something only the UI knows happened, e.g. `"unitSelected"`. Meaningless for
     * script triggers (which run headlessly) — there it never fires.
     */
    @Serializable
    @SerialName("uiSignal")
    data class UiSignal(val name: String) : LevelCondition

    @Serializable
    @SerialName("ownHexes")
    data class OwnHexCountAtLeast(val count: Int) : LevelCondition

    /** True from round [round] onwards (`GameState.turnNumber` counts completed rounds). */
    @Serializable
    @SerialName("round")
    data class RoundAtLeast(val round: Int) : LevelCondition

    @Serializable
    @SerialName("units")
    data class UnitCountAtLeast(
        // "type" is the sealed-class discriminator — a property of that name silently
        // breaks decoding (the same trap BuyBuilding fell into; see GameAction.kt).
        @SerialName("unitType")
        val type: UnitType,
        val count: Int,
        val tier: Int? = null,
    ) : LevelCondition

    @Serializable
    @SerialName("buildings")
    data class BuildingCountAtLeast(val building: BuildingType, val count: Int) : LevelCondition

    @Serializable
    @SerialName("treasury")
    data class TreasuryAtLeast(val coins: Int) : LevelCondition

    @Serializable
    @SerialName("income")
    data class IncomeAtLeast(val coins: Int) : LevelCondition

    /** The level objective at [index] is complete. */
    @Serializable
    @SerialName("objectiveDone")
    data class ObjectiveDone(val index: Int) : LevelCondition

    /** Every hex in [hexes] belongs to the campaign seat. */
    @Serializable
    @SerialName("ownsHexes")
    data class OwnsHexes(val hexes: List<Hex>) : LevelCondition

    /** Any hex in [hexes] has slipped out of the campaign seat's hands. */
    @Serializable
    @SerialName("lostHexes")
    data class LostAnyHex(val hexes: List<Hex>) : LevelCondition

    /** [seat] is out of the game. */
    @Serializable
    @SerialName("playerEliminated")
    data class PlayerEliminated(val seat: PlayerId) : LevelCondition

    /** Every listed condition holds. */
    @Serializable
    @SerialName("all")
    data class All(val conditions: List<LevelCondition>) : LevelCondition
}

object Conditions {

    /**
     * [uiSignals] is the set of UI moments observed so far this level; pass an empty set
     * from headless callers (script triggers), where [LevelCondition.UiSignal] must
     * never fire.
     */
    fun isSatisfied(
        condition: LevelCondition,
        state: GameState,
        seat: PlayerId,
        status: CampaignStatus,
        uiSignals: Set<String> = emptySet(),
    ): Boolean = when (condition) {
        LevelCondition.Acknowledged -> false
        is LevelCondition.UiSignal -> condition.name in uiSignals
        is LevelCondition.OwnHexCountAtLeast ->
            state.tiles.count { it.value.owner == seat } >= condition.count
        is LevelCondition.RoundAtLeast -> state.turnNumber >= condition.round
        is LevelCondition.UnitCountAtLeast ->
            Objectives.countUnits(state, seat, condition.type, condition.tier) >= condition.count
        is LevelCondition.BuildingCountAtLeast ->
            state.tiles.count {
                it.value.owner == seat && it.value.building == condition.building.building
            } >= condition.count
        is LevelCondition.TreasuryAtLeast -> state.player(seat).treasury >= condition.coins
        is LevelCondition.IncomeAtLeast -> Rules.incomeOf(state, seat) >= condition.coins
        is LevelCondition.ObjectiveDone -> status.rows.getOrNull(condition.index)?.done == true
        is LevelCondition.OwnsHexes -> condition.hexes.all { state.tiles[it]?.owner == seat }
        is LevelCondition.LostAnyHex -> condition.hexes.any { state.tiles[it]?.owner != seat }
        is LevelCondition.PlayerEliminated ->
            condition.seat.value in state.players.indices && state.player(condition.seat).eliminated
        is LevelCondition.All ->
            condition.conditions.all { isSatisfied(it, state, seat, status, uiSignals) }
    }
}
