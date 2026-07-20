package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState

sealed interface LegalityResult {
    data object Ok : LegalityResult

    /** [amount] carries the relevant number when the reason has one (cost, defense). */
    data class Rejected(val reason: RejectionReason, val amount: Int? = null) : LegalityResult
}

object Legality {

    fun check(state: GameState, action: GameAction): LegalityResult {
        if (state.phase !is GamePhase.Playing) return reject(RejectionReason.GAME_FINISHED)
        return when (action) {
            is GameAction.MoveUnit -> checkMove(state, action)
            is GameAction.BuyUnit -> checkBuyUnit(state, action)
            is GameAction.BuyBuilding -> checkBuyBuilding(state, action)
            is GameAction.MergeUnits -> checkMerge(state, action)
            GameAction.EndTurn -> LegalityResult.Ok
            GameAction.Surrender -> LegalityResult.Ok
        }
    }

    private fun checkMove(state: GameState, action: GameAction.MoveUnit): LegalityResult {
        val unit = state.units[action.unit] ?: return reject(RejectionReason.NO_SUCH_UNIT)
        if (unit.owner != state.currentPlayer) return reject(RejectionReason.NOT_YOUR_UNIT)
        if (unit.spent) return reject(RejectionReason.UNIT_ALREADY_ACTED)
        val reach = Rules.reachable(state, action.unit)
        return when (action.to) {
            in reach.moveTargets, in reach.captureTargets -> LegalityResult.Ok
            in reach.mergeTargets -> reject(RejectionReason.DESTINATION_HAS_UNIT)
            else -> reject(RejectionReason.DESTINATION_UNREACHABLE)
        }
    }

    private fun checkBuyUnit(state: GameState, action: GameAction.BuyUnit): LegalityResult {
        val rules = state.config.rules
        if (action.tier !in 1..rules.maxTier) return reject(RejectionReason.INVALID_TIER)
        val cost = rules.unitCost[action.tier - 1]
        val player = state.player(state.currentPlayer)
        if (player.treasury < cost) return reject(RejectionReason.CANNOT_AFFORD, cost)
        val tile = state.tiles[action.at] ?: return reject(RejectionReason.NO_SUCH_HEX)

        if (tile.owner == state.currentPlayer) {
            if (tile.starving) return reject(RejectionReason.HEX_CUT_OFF)
            if (tile.building != null) return reject(RejectionReason.HEX_HAS_BUILDING)
            val occupant = state.unitAt(action.at)
            return when {
                occupant == null -> LegalityResult.Ok
                occupant.tier == action.tier && action.tier < rules.maxTier -> LegalityResult.Ok // buy-merge
                else -> reject(RejectionReason.HEX_OCCUPIED_INCOMPATIBLE)
            }
        }
        // Not owned: must be a capture placement adjacent to funded (non-starving) territory.
        val adjacentToFunded = HexMath.neighbors(action.at).any {
            val t = state.tiles[it]
            t?.owner == state.currentPlayer && !t.starving
        }
        if (!adjacentToFunded) return reject(RejectionReason.NOT_ADJACENT_TO_TERRITORY)
        val defense = Rules.defenseOf(state, action.at)
        if (action.tier <= defense) return reject(RejectionReason.DEFENSE_TOO_HIGH, defense)
        return LegalityResult.Ok
    }

    private fun checkBuyBuilding(state: GameState, action: GameAction.BuyBuilding): LegalityResult {
        val player = state.player(state.currentPlayer)
        val cost = Rules.buildingCost(state, state.currentPlayer, action.type)
        if (player.treasury < cost) return reject(RejectionReason.CANNOT_AFFORD, cost)
        val tile = state.tiles[action.at] ?: return reject(RejectionReason.NO_SUCH_HEX)
        if (tile.owner != state.currentPlayer) return reject(RejectionReason.NOT_YOUR_HEX)
        if (tile.starving) return reject(RejectionReason.HEX_CUT_OFF)
        if (tile.building != null) return reject(RejectionReason.HEX_HAS_BUILDING)
        if (tile.unit != null) return reject(RejectionReason.HEX_HAS_UNIT)
        if (tile.flora != null) return reject(RejectionReason.HEX_NEEDS_CLEARING)
        if (action.type == BuildingType.FARM) {
            val adjacentToChain = HexMath.neighbors(action.at).any {
                val t = state.tiles[it]
                t?.owner == state.currentPlayer &&
                    (t.building == Building.CAPITAL || t.building == Building.FARM)
            }
            if (!adjacentToChain) return reject(RejectionReason.FARM_NEEDS_ADJACENCY)
        }
        return LegalityResult.Ok
    }

    private fun checkMerge(state: GameState, action: GameAction.MergeUnits): LegalityResult {
        val a = state.units[action.a] ?: return reject(RejectionReason.NO_SUCH_UNIT)
        val b = state.units[action.b] ?: return reject(RejectionReason.NO_SUCH_UNIT)
        if (a.owner != state.currentPlayer || b.owner != state.currentPlayer) {
            return reject(RejectionReason.NOT_YOUR_UNITS)
        }
        if (a.id == b.id) return reject(RejectionReason.CANNOT_MERGE_WITH_SELF)
        if (a.spent) return reject(RejectionReason.UNIT_ALREADY_ACTED)
        if (a.tier != b.tier) return reject(RejectionReason.TIER_MISMATCH)
        if (a.tier >= state.config.rules.maxTier) return reject(RejectionReason.ALREADY_MAX_TIER)
        val reach = Rules.reachable(state, action.a)
        if (b.hex !in reach.mergeTargets) return reject(RejectionReason.NOT_IN_SAME_REGION)
        return LegalityResult.Ok
    }

    private fun reject(reason: RejectionReason, amount: Int? = null) =
        LegalityResult.Rejected(reason, amount)
}
