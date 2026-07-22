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

    /** AI market cap: markets are an economy garnish, not a wall-to-wall strategy. */
    private const val MAX_AI_MARKETS = 3

    fun candidates(state: GameState, difficulty: Difficulty): List<GameAction> {
        val me = state.currentPlayer
        val rules = state.config.rules
        val treasury = state.player(me).treasury
        val out = ArrayList<GameAction>()

        // Pact partners are never capture targets (regardless of what the engine
        // would allow — attacking auto-breaks with a penalty). Hard lifts the filter
        // for partners the betrayal policy has marked.
        val partners: Set<com.msa.fightandconquer.core.model.PlayerId> =
            if (rules.diplomacyEnabled && state.diplomacy.pacts.isNotEmpty()) {
                val all = state.diplomacy.pacts.mapNotNull {
                    when (me) {
                        it.a -> it.b
                        it.b -> it.a
                        else -> null
                    }
                }.toSet()
                if (difficulty == Difficulty.HARD) all - DiplomacyPolicy.betrayalTargets(state, me) else all
            } else {
                emptySet()
            }

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
                    if (t != null && t.owner != me && t.owner !in partners) {
                        frontier[n] = Rules.defenseOf(state, n)
                    }
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
                if (state.tiles.getValue(it).owner !in partners) {
                    out.add(GameAction.MoveUnit(unit.id, it))
                }
            }
            // Clear trees rotting our income (managed camp trees are income, keep them).
            reach.moveTargets.sortedBy { it.packed }.forEach {
                if (state.tiles.getValue(it).flora is Flora.Tree && !managedByOwnCamp(state, it, me)) {
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
                    tile.unit == null && tile.building == null && !managedByOwnCamp(state, hex, me)
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

            // Mines: a vein without a mine is dead weight at every difficulty.
            if (treasury >= rules.mineCost) {
                state.tiles.entries
                    .filter { (_, tile) ->
                        tile.owner == me && !tile.starving && tile.building == null &&
                            tile.unit == null && tile.flora == null &&
                            tile.deposit == com.msa.fightandconquer.core.model.Deposit.GOLD_VEIN
                    }
                    .sortedBy { it.key.packed }
                    .forEach { out.add(GameAction.BuyBuilding(BuildingType.MINE, it.key)) }
            }

            if (difficulty != Difficulty.EASY) {
                // Markets: interior hexes only — a frontier market is a gift to the
                // attacker — and capped, or a rich AI paves its interior with them
                // and turtles instead of fighting (observed stalemate mode).
                val myMarkets = state.tiles.values.count {
                    it.owner == me && it.building == com.msa.fightandconquer.core.model.Building.MARKET
                }
                if (myMarkets < MAX_AI_MARKETS && treasury >= rules.marketCost + 10) {
                    state.tiles.entries
                        .filter { (hex, tile) ->
                            tile.owner == me && !tile.starving && tile.building == null &&
                                tile.unit == null && tile.flora == null && tile.deposit == null &&
                                HexMath.neighbors(hex).all { state.tiles[it]?.owner == me }
                        }
                        .sortedBy { it.key.packed }
                        .take(2)
                        .forEach { out.add(GameAction.BuyBuilding(BuildingType.MARKET, it.key)) }
                }
                // Lumber camps where at least two own trees make them beat clearing.
                if (treasury >= rules.lumberCampCost + 10) {
                    state.tiles.entries
                        .filter { (hex, tile) ->
                            tile.owner == me && !tile.starving && tile.building == null &&
                                tile.unit == null && tile.flora == null && tile.deposit == null &&
                                adjacentOwnTrees(state, hex, me) >= 2
                        }
                        .sortedWith(
                            compareByDescending<Map.Entry<Hex, com.msa.fightandconquer.core.model.Tile>> {
                                adjacentOwnTrees(state, it.key, me)
                            }.thenBy { it.key.packed },
                        )
                        .take(2)
                        .forEach { out.add(GameAction.BuyBuilding(BuildingType.LUMBER_CAMP, it.key)) }
                }
            }

            // --- Special units (Normal/Hard) ---
            if (rules.specialUnitsEnabled && difficulty != Difficulty.EASY) {
                // Catapults where BUILDING defense is the blocker: the cheapest-tier
                // logic can't crack defense >= maxTier, a catapult ignores it.
                if (treasury >= rules.catapultCost) {
                    frontier.entries
                        .filter { (hex, defense) ->
                            val siegeDefense = Rules.defenseOf(state, hex, com.msa.fightandconquer.core.model.UnitType.CATAPULT)
                            defense > siegeDefense && siegeDefense < rules.catapultStrength
                        }
                        .sortedWith(
                            compareByDescending<Map.Entry<Hex, Int>> { it.value }.thenBy { it.key.packed },
                        )
                        .take(4)
                        .forEach {
                            out.add(GameAction.BuyUnit(1, it.key, com.msa.fightandconquer.core.model.UnitType.CATAPULT))
                        }
                }
                // Archers to harden threatened borders: rank by how many own hexes the
                // aura would actually raise.
                if (treasury >= rules.archerCost) {
                    state.tiles.entries
                        .filter { (hex, tile) ->
                            tile.owner == me && !tile.starving && tile.building == null &&
                                tile.unit == null && tile.flora == null &&
                                HexMath.neighbors(hex).any { n ->
                                    val t = state.tiles[n]
                                    t?.owner != null && t.owner != me
                                }
                        }
                        .map { it.key to auraGain(state, it.key, me) }
                        .filter { it.second >= 2 }
                        .sortedWith(compareByDescending<Pair<Hex, Int>> { it.second }.thenBy { it.first.packed })
                        .take(3)
                        .forEach {
                            out.add(GameAction.BuyUnit(1, it.first, com.msa.fightandconquer.core.model.UnitType.ARCHER))
                        }
                }
            }

            // Watchtowers: Hard only, fog games only, and only with a healthy economy.
            if (difficulty == Difficulty.HARD && rules.fogOfWar &&
                treasury >= rules.watchtowerCost + 10 && income - Rules.upkeepOf(state, me) >= 4
            ) {
                val discovered = state.player(me).discovered
                // Score by never-seen POSITIONS in range, from pure hex geometry: probing
                // state.tiles for undiscovered hexes would leak the coastline through fog.
                fun unseen(hex: Hex): Int =
                    HexMath.range(hex, rules.watchtowerVisionRadius).count { it !in discovered }
                state.tiles.entries
                    .filter { (_, tile) ->
                        tile.owner == me && !tile.starving && tile.building == null &&
                            tile.unit == null && tile.flora == null && tile.deposit == null
                    }
                    .map { it.key to unseen(it.key) }
                    .filter { it.second > 0 }
                    .sortedWith(compareByDescending<Pair<Hex, Int>> { it.second }.thenBy { it.first.packed })
                    .take(2)
                    .forEach { out.add(GameAction.BuyBuilding(BuildingType.WATCHTOWER, it.first)) }
            }
        }
        return out
    }

    /** How many hexes (self + adjacent own) an archer's aura would raise above their current defense. */
    private fun auraGain(state: GameState, hex: Hex, me: com.msa.fightandconquer.core.model.PlayerId): Int {
        val aura = state.config.rules.archerAuraDefense
        var gain = 0
        if (Rules.defenseOf(state, hex) < aura) gain++
        HexMath.forEachNeighbor(hex) { n ->
            val t = state.tiles[n]
            if (t != null && t.owner == me && Rules.defenseOf(state, n) < aura) gain++
        }
        return gain
    }

    private fun managedByOwnCamp(state: GameState, hex: Hex, me: com.msa.fightandconquer.core.model.PlayerId): Boolean {
        var found = false
        HexMath.forEachNeighbor(hex) { n ->
            val t = state.tiles[n]
            if (t != null && t.owner == me && t.building == com.msa.fightandconquer.core.model.Building.LUMBER_CAMP) found = true
        }
        return found
    }

    private fun adjacentOwnTrees(state: GameState, hex: Hex, me: com.msa.fightandconquer.core.model.PlayerId): Int {
        var count = 0
        HexMath.forEachNeighbor(hex) { n ->
            val t = state.tiles[n]
            if (t != null && t.owner == me && t.flora is Flora.Tree) count++
        }
        return count
    }
}
