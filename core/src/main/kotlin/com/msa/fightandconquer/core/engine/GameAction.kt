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
     * hex (captures and arrives spent).
     */
    @Serializable
    @SerialName("buyUnit")
    data class BuyUnit(val tier: Int, val at: Hex) : GameAction

    @Serializable
    @SerialName("buyBuilding")
    data class BuyBuilding(val type: BuildingType, val at: Hex) : GameAction

    /** Merge unit [a] (the mover, must be fresh) into same-tier unit [b]. */
    @Serializable
    @SerialName("merge")
    data class MergeUnits(val a: UnitId, val b: UnitId) : GameAction

    @Serializable
    @SerialName("endTurn")
    data object EndTurn : GameAction

    @Serializable
    @SerialName("surrender")
    data object Surrender : GameAction
}
