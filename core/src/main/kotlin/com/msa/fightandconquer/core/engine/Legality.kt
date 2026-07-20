package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState

sealed interface LegalityResult {
    data object Ok : LegalityResult
    data class Rejected(val reason: String) : LegalityResult
}

object Legality {

    fun check(state: GameState, action: GameAction): LegalityResult {
        if (state.phase !is GamePhase.Playing) return reject("game is finished")
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
        val unit = state.units[action.unit] ?: return reject("no such unit")
        if (unit.owner != state.currentPlayer) return reject("not your unit")
        if (unit.spent) return reject("unit already acted this turn")
        val reach = Rules.reachable(state, action.unit)
        return when (action.to) {
            in reach.moveTargets, in reach.captureTargets -> LegalityResult.Ok
            in reach.mergeTargets -> reject("destination holds a unit — use MergeUnits")
            else -> reject("destination not reachable")
        }
    }

    private fun checkBuyUnit(state: GameState, action: GameAction.BuyUnit): LegalityResult {
        val rules = state.config.rules
        if (action.tier !in 1..rules.maxTier) return reject("invalid tier")
        val cost = rules.unitCost[action.tier - 1]
        val player = state.player(state.currentPlayer)
        if (player.treasury < cost) return reject("cannot afford (${player.treasury} < $cost)")
        val tile = state.tiles[action.at] ?: return reject("no such hex")

        if (tile.owner == state.currentPlayer) {
            if (tile.starving) return reject("hex is cut off from your capital")
            if (tile.building != null) return reject("hex has a building")
            val occupant = state.unitAt(action.at)
            return when {
                occupant == null -> LegalityResult.Ok
                occupant.tier == action.tier && action.tier < rules.maxTier -> LegalityResult.Ok // buy-merge
                else -> reject("hex occupied by an incompatible unit")
            }
        }
        // Not owned: must be a capture placement adjacent to funded (non-starving) territory.
        val adjacentToFunded = HexMath.neighbors(action.at).any {
            val t = state.tiles[it]
            t?.owner == state.currentPlayer && !t.starving
        }
        if (!adjacentToFunded) return reject("hex not adjacent to your funded territory")
        if (action.tier <= Rules.defenseOf(state, action.at)) return reject("defense too high")
        return LegalityResult.Ok
    }

    private fun checkBuyBuilding(state: GameState, action: GameAction.BuyBuilding): LegalityResult {
        val player = state.player(state.currentPlayer)
        val cost = Rules.buildingCost(state, state.currentPlayer, action.type)
        if (player.treasury < cost) return reject("cannot afford (${player.treasury} < $cost)")
        val tile = state.tiles[action.at] ?: return reject("no such hex")
        if (tile.owner != state.currentPlayer) return reject("not your hex")
        if (tile.starving) return reject("hex is cut off from your capital")
        if (tile.building != null) return reject("hex already has a building")
        if (tile.unit != null) return reject("hex is occupied by a unit")
        if (tile.flora != null) return reject("hex must be cleared first")
        if (action.type == BuildingType.FARM) {
            val adjacentToChain = HexMath.neighbors(action.at).any {
                val t = state.tiles[it]
                t?.owner == state.currentPlayer &&
                    (t.building == Building.CAPITAL || t.building == Building.FARM)
            }
            if (!adjacentToChain) return reject("farm must be adjacent to your capital or another farm")
        }
        return LegalityResult.Ok
    }

    private fun checkMerge(state: GameState, action: GameAction.MergeUnits): LegalityResult {
        val a = state.units[action.a] ?: return reject("no such unit (a)")
        val b = state.units[action.b] ?: return reject("no such unit (b)")
        if (a.owner != state.currentPlayer || b.owner != state.currentPlayer) return reject("not your units")
        if (a.id == b.id) return reject("cannot merge a unit with itself")
        if (a.spent) return reject("unit already acted this turn")
        if (a.tier != b.tier) return reject("units must be the same tier")
        if (a.tier >= state.config.rules.maxTier) return reject("already at max tier")
        val reach = Rules.reachable(state, action.a)
        if (b.hex !in reach.mergeTargets) return reject("units are not in the same region")
        return LegalityResult.Ok
    }

    private fun reject(reason: String) = LegalityResult.Rejected(reason)
}
