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
}
