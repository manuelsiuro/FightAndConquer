package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.GameState

/**
 * Enumerates purposeful candidate actions for the current player. Deterministic:
 * every collection is sorted before iteration so identical states yield identical lists.
 */
object MoveGenerator {

    fun candidates(state: GameState, difficulty: Difficulty): List<GameAction> {
        val me = state.currentPlayer
        val rules = state.config.rules
        val treasury = state.player(me).treasury
        val out = ArrayList<GameAction>()

        // Frontier: non-owned hexes adjacent to funded territory, with their defense.
        // Fog of war note: everything read here (frontier hexes and every defenseOf
        // input — the hex plus its neighbors) lies within distance 2 of owned
        // territory, i.e. inside the visionRadiusOwned >= 2 guarantee. That invariant
        // is why this generator needs no fog filtering (see docs/fog-of-war.md).
        val frontier = HashMap<Hex, Int>()
        for ((hex, tile) in state.tiles) {
            if (tile.owner != me || tile.starving) continue
            HexMath.forEachNeighbor(hex) { n ->
                if (n !in frontier) {
                    val t = state.tiles[n]
                    if (t != null && t.owner != me) frontier[n] = Rules.defenseOf(state, n)
                }
            }
        }
        val frontierDefenses = frontier.values.toSet()

        // --- Unit actions ---
        val myUnits = state.units.values
            .filter { it.owner == me && !it.spent }
            .sortedBy { it.id.value }
        for (unit in myUnits) {
            val reach = Rules.reachable(state, unit.id)
            reach.captureTargets.sortedBy { it.packed }.forEach {
                out.add(GameAction.MoveUnit(unit.id, it))
            }
            // Clear trees rotting our income.
            reach.moveTargets.sortedBy { it.packed }.forEach {
                if (state.tiles.getValue(it).flora is Flora.Tree) {
                    out.add(GameAction.MoveUnit(unit.id, it))
                }
            }
            // Merge only when the merged tier would break a currently-unbreakable frontier hex.
            if (unit.tier in frontierDefenses) {
                reach.mergeTargets.sortedBy { it.packed }.forEach { targetHex ->
                    out.add(GameAction.MergeUnits(unit.id, state.tiles.getValue(targetHex).unit!!))
                }
            }
        }

        // --- Buy-capture: cheapest tier that takes each frontier hex ---
        for ((hex, defense) in frontier.entries.sortedBy { it.key.packed }) {
            val tier = defense + 1
            if (tier <= rules.maxTier && treasury >= rules.unitCost[tier - 1]) {
                out.add(GameAction.BuyUnit(tier, hex))
            }
        }

        // --- Buy a peasant onto our own tree hexes (income repair) ---
        if (treasury >= rules.unitCost[0]) {
            for ((hex, tile) in state.tiles.entries.sortedBy { it.key.packed }) {
                if (tile.owner == me && !tile.starving && tile.flora is Flora.Tree &&
                    tile.unit == null && tile.building == null
                ) {
                    out.add(GameAction.BuyUnit(1, hex))
                }
            }
        }

        // --- Structures (Easy ignores them until the economy is strong) ---
        val income = Rules.incomeOf(state, me)
        val structuresAllowed = difficulty != Difficulty.EASY || income > 15
        if (structuresAllowed) {
            // Towers on border hexes touching ENEMY territory that lack coverage.
            if (treasury >= rules.towerCost) {
                val towerSpots = state.tiles.entries
                    .filter { (hex, tile) ->
                        tile.owner == me && !tile.starving && tile.building == null &&
                            tile.unit == null && tile.flora == null &&
                            Rules.defenseOf(state, hex) < rules.towerDefense &&
                            HexMath.neighbors(hex).any { n ->
                                val t = state.tiles[n]
                                t?.owner != null && t.owner != me
                            }
                    }
                    .sortedByDescending { (hex, _) ->
                        HexMath.neighbors(hex).count { n ->
                            val t = state.tiles[n]
                            t?.owner != null && t.owner != me
                        } * 1000 - (hex.packed and 0x3FF)
                    }
                    .take(3)
                towerSpots.forEach { out.add(GameAction.BuyBuilding(BuildingType.TOWER, it.key)) }
            }
            // Farms: grow the economy when there's spare cash.
            val farmCost = Rules.nextFarmCost(state, me)
            if (treasury >= farmCost + 10) {
                val farmSpots = state.tiles.entries
                    .filter { (hex, tile) ->
                        tile.owner == me && !tile.starving && tile.building == null &&
                            tile.unit == null && tile.flora == null &&
                            HexMath.neighbors(hex).any { n ->
                                val t = state.tiles[n]
                                t?.owner == me && (t.building == com.msa.fightandconquer.core.model.Building.CAPITAL ||
                                    t.building == com.msa.fightandconquer.core.model.Building.FARM)
                            }
                    }
                    .sortedBy { it.key.packed }
                    .take(2)
                farmSpots.forEach { out.add(GameAction.BuyBuilding(BuildingType.FARM, it.key)) }
            }
        }
        return out
    }
}
