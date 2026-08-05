package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.UnitId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything a player (human or AI) can do. Actions are implicitly performed by
 * [com.msa.fightandconquer.core.model.GameState.currentPlayer].
 */
@Serializable
sealed interface GameAction {

    /** Move within the unit's region, or capture an adjacent hex (one combined action). */
    @Serializable
    @SerialName("move")
    data class MoveUnit(val unit: UnitId, val to: Hex) : GameAction

    /**
     * Buy a unit of [tier] and place it at [at]: an owned connected hex (fresh, may act),
     * an owned hex holding a same-tier unit (instant merge), or an adjacent capturable
     * hex (captures and arrives spent). Special [type]s require tier == 1 and never
     * buy-merge. Defaulted so pre-expansion action logs replay identically.
     */
    @Serializable
    @SerialName("buyUnit")
    data class BuyUnit(
        val tier: Int,
        val at: Hex,
        // Serial name must not collide with the sealed-class "type" discriminator.
        @SerialName("unitType")
        val type: com.msa.fightandconquer.core.model.UnitType = com.msa.fightandconquer.core.model.UnitType.SOLDIER,
    ) : GameAction

    @Serializable
    @SerialName("buyBuilding")
    data class BuyBuilding(
        // "type" collides with the sealed-class discriminator: serializing a mid-turn
        // save holding a BuyBuilding used to throw (and silently drop the autosave).
        // No save ever successfully contained one, so the rename is load-compatible.
        @SerialName("building")
        val type: BuildingType,
        val at: Hex,
    ) : GameAction

    /** Merge unit [a] (the mover, must be fresh) into same-tier unit [b]. */
    @Serializable
    @SerialName("merge")
    data class MergeUnits(val a: UnitId, val b: UnitId) : GameAction

    /**
     * Land [boat]'s cargo on adjacent land hex [to]: an own hex, or an amphibious
     * capture when the cargo's strength beats the defense. Boat and landed unit
     * both end spent. (Embarking is a plain [MoveUnit] onto the transport.)
     */
    @Serializable
    @SerialName("disembark")
    data class Disembark(val boat: UnitId, val to: Hex) : GameAction

    /**
     * Warship raid on adjacent hex [target]: kills the unit and destroys a
     * non-capital building when the warship's strength beats the defense.
     * Never captures ground.
     */
    @Serializable
    @SerialName("bombard")
    data class Bombard(val unit: UnitId, val target: Hex) : GameAction

    /** Offer [to] a non-aggression pact lasting [durationRounds] full rounds. */
    @Serializable
    @SerialName("proposePact")
    data class ProposePact(
        val to: com.msa.fightandconquer.core.model.PlayerId,
        val durationRounds: Int,
    ) : GameAction

    /** Accept or decline the pending proposal [from] sent to the current player. */
    @Serializable
    @SerialName("respondPact")
    data class RespondPact(
        val from: com.msa.fightandconquer.core.model.PlayerId,
        val accept: Boolean,
    ) : GameAction

    /** Gift [amount] gold to [to] (appeasement; no strings attached mechanically). */
    @Serializable
    @SerialName("sendTribute")
    data class SendTribute(
        val to: com.msa.fightandconquer.core.model.PlayerId,
        val amount: Int,
    ) : GameAction

    @Serializable
    @SerialName("endTurn")
    data object EndTurn : GameAction

    @Serializable
    @SerialName("surrender")
    data object Surrender : GameAction

    /**
     * A campaign story beat — reinforcements arriving, a patron's gold, a garrison
     * mustering — submitted by the campaign director, never by a player or the AI.
     *
     * The payload is **self-contained on purpose**: the reducer must never need the
     * level definition to apply it, so a saved action log replays a scripted event
     * exactly as it first fired. It draws no randomness and, unlike every other
     * action, is not attributed to [com.msa.fightandconquer.core.model.GameState.currentPlayer]
     * — each spawn and grant names its own owner.
     *
     * Gated by [com.msa.fightandconquer.core.model.RuleConstants.scriptedEventsEnabled]
     * (off by default), so a skirmish game can never contain one.
     *
     * [tag] is the trigger id the level author wrote; the HUD maps it to narration.
     */
    @Serializable
    @SerialName("script")
    data class RunScript(
        val tag: String,
        val spawns: List<ScriptSpawn> = emptyList(),
        val grants: List<ScriptGrant> = emptyList(),
    ) : GameAction
}

/** One unit placed by a [GameAction.RunScript], on an owned, empty land hex of [owner]. */
@Serializable
data class ScriptSpawn(
    val owner: com.msa.fightandconquer.core.model.PlayerId,
    val hex: Hex,
    @SerialName("unitType")
    val type: com.msa.fightandconquer.core.model.UnitType =
        com.msa.fightandconquer.core.model.UnitType.SOLDIER,
    val tier: Int = 1,
    /** Reinforcements normally arrive ready to act; set true for a spent arrival. */
    val spent: Boolean = false,
)

/** A treasury gift (or levy, when negative is disallowed) from a [GameAction.RunScript]. */
@Serializable
data class ScriptGrant(
    val player: com.msa.fightandconquer.core.model.PlayerId,
    val coins: Int,
)
