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
        // Civ-modifiable prices/stats (special units, buildings) MUST come from here;
        // the raw `rules` reads below are the universal soldier ladder only.
        val eff = Rules.effectiveRules(state, me)
        val treasury = state.player(me).treasury
        val out = ArrayList<GameAction>()

        // Pact partners are never capture targets (regardless of what the engine
        // would allow — attacking auto-breaks with a penalty). Hard lifts the filter
        // for partners the betrayal policy has marked.
        val partners: Set<com.msa.fightandconquer.core.model.PlayerId> =
            if (rules.diplomacyEnabled && state.diplomacy.pacts.isNotEmpty()) {
                val all = state.diplomacy.partnersOf(me)
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
                    // Open sea is not conquerable land — it never joins the frontier.
                    if (t != null && t.terrain == com.msa.fightandconquer.core.model.Terrain.LAND &&
                        t.owner != me && t.owner !in partners
                    ) {
                        frontier[n] = Rules.defenseOf(state, n)
                    }
                }
            }
        }
        val frontierDefenses = frontier.values.toSet()

        // --- Capital defense (range-bound movement means nobody teleports home:
        // the garrison must be raised BEFORE the axe falls, so when an enemy is
        // within striking range of the throne, offer moves and buys onto its
        // neighboring hexes — the evaluator's capital-guard term picks them up).
        val capital = state.player(me).capital
        val capitalGuardHexes: Set<Hex>
        val capitalThreat: Int
        if (capital != null && state.tiles[capital]?.owner == me) {
            val capDefense = Rules.defenseOf(state, capital)
            capitalThreat = state.units.values
                .filter { u ->
                    u.owner != me && !Rules.isNaval(u.type) &&
                        HexMath.distance(u.hex, capital) <= Rules.moveRangeOf(state, u) &&
                        Rules.strengthOf(state, u) > capDefense
                }
                .maxOfOrNull { Rules.strengthOf(state, it) } ?: 0
            capitalGuardHexes = if (capitalThreat == 0) {
                emptySet()
            } else {
                HexMath.neighbors(capital).filter { n ->
                    val t = state.tiles[n]
                    t != null && t.owner == me && !t.starving &&
                        t.unit == null && t.building == null
                }.toSet()
            }
        } else {
            capitalGuardHexes = emptySet()
            capitalThreat = 0
        }
        if (capitalThreat > 0) {
            for (hex in capitalGuardHexes.sortedBy { it.packed }) {
                val tier = minOf(capitalThreat, rules.maxTier)
                if (treasury >= rules.unitCost[tier - 1]) out.add(GameAction.BuyUnit(tier, hex))
                if (tier > 1 && treasury >= rules.unitCost[0]) out.add(GameAction.BuyUnit(1, hex))
            }
        }

        // --- Unit actions ---
        val myUnits = state.units.values
            .filter { it.owner == me && !it.spent }
            .sortedBy { it.id.value }
        for (unit in myUnits) {
            val reach = Rules.reachable(state, unit.id)
            if (capitalThreat > 0) {
                // Rush the guard hexes with whoever can reach them.
                reach.moveTargets.intersect(capitalGuardHexes).sortedBy { it.packed }.forEach {
                    out.add(GameAction.MoveUnit(unit.id, it))
                }
            }
            reach.captureTargets.sortedBy { it.packed }.forEach {
                // The victim of a warship strike is the boat's owner — its sea
                // hex is unowned, so the tile owner alone would miss partners.
                val victim = state.tiles.getValue(it).owner ?: state.unitAt(it)?.owner
                if (victim !in partners) {
                    out.add(GameAction.MoveUnit(unit.id, it))
                }
            }
            // Warship raids on adjacent coastal targets (never on pact partners —
            // including their boats on unowned open sea).
            if (unit.type == com.msa.fightandconquer.core.model.UnitType.WARSHIP) {
                HexMath.neighbors(unit.hex).sortedBy { it.packed }.forEach { n ->
                    val raidVictim = state.tiles[n]?.owner ?: state.unitAt(n)?.owner
                    if (raidVictim !in partners) {
                        val bombard = GameAction.Bombard(unit.id, n)
                        if (com.msa.fightandconquer.core.engine.Legality.check(state, bombard)
                            is com.msa.fightandconquer.core.engine.LegalityResult.Ok
                        ) {
                            out.add(bombard)
                        }
                    }
                }
            }
            // Clear trees rotting our income (managed camp trees are income, keep them).
            reach.moveTargets.sortedBy { it.packed }.forEach {
                if (state.tiles.getValue(it).flora is Flora.Tree && !Adjacency.nextToOwnCamp(state, it, me)) {
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
                    tile.unit == null && tile.building == null && !Adjacency.nextToOwnCamp(state, hex, me)
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
            if (treasury >= eff.towerCost) {
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
                            (tile.deposit == com.msa.fightandconquer.core.model.Deposit.FERTILE ||
                                HexMath.neighbors(hex).any { n ->
                                    val t = state.tiles[n]
                                    t?.owner == me && (t.building == com.msa.fightandconquer.core.model.Building.CAPITAL ||
                                        t.building == com.msa.fightandconquer.core.model.Building.FARM)
                                })
                    }
                    // Fertile spots first: same farm, +fertileFarmBonus income.
                    .sortedWith(
                        compareByDescending<Map.Entry<Hex, com.msa.fightandconquer.core.model.Tile>> {
                            it.value.deposit == com.msa.fightandconquer.core.model.Deposit.FERTILE
                        }.thenBy { it.key.packed },
                    )
                    .take(2)
                farmSpots.forEach { out.add(GameAction.BuyBuilding(BuildingType.FARM, it.key)) }
            }

            // Mines: a vein without a mine is dead weight at every difficulty.
            if (treasury >= eff.mineCost) {
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
                if (myMarkets < MAX_AI_MARKETS && treasury >= eff.marketCost + 10) {
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
                if (treasury >= eff.lumberCampCost + 10) {
                    state.tiles.entries
                        .filter { (hex, tile) ->
                            tile.owner == me && !tile.starving && tile.building == null &&
                                tile.unit == null && tile.flora == null && tile.deposit == null &&
                                Adjacency.adjacentOwnTrees(state, hex, me) >= 2
                        }
                        .sortedWith(
                            compareByDescending<Map.Entry<Hex, com.msa.fightandconquer.core.model.Tile>> {
                                Adjacency.adjacentOwnTrees(state, it.key, me)
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
                if (treasury >= eff.catapultCost) {
                    frontier.entries
                        .filter { (hex, defense) ->
                            val siegeDefense = Rules.defenseOf(state, hex, com.msa.fightandconquer.core.model.UnitType.CATAPULT)
                            defense > siegeDefense && siegeDefense < eff.catapultStrength
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
                if (treasury >= eff.archerCost) {
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

            if (rules.navalEnabled && difficulty != Difficulty.EASY) {
                // Ports: the gateway asset of sea maps (income + boat yard + supply).
                if (treasury >= eff.portCost + 10) {
                    state.tiles.entries
                        .filter { (hex, tile) ->
                            tile.owner == me && !tile.starving && tile.building == null &&
                                tile.unit == null && tile.flora == null && tile.deposit == null &&
                                HexMath.neighbors(hex).any {
                                    state.tiles[it]?.terrain == com.msa.fightandconquer.core.model.Terrain.SEA
                                }
                        }
                        .sortedWith(
                            compareByDescending<Map.Entry<Hex, com.msa.fightandconquer.core.model.Tile>> { (hex, _) ->
                                HexMath.neighbors(hex).count {
                                    state.tiles[it]?.terrain == com.msa.fightandconquer.core.model.Terrain.SEA
                                }
                            }.thenBy { it.key.packed },
                        )
                        .take(2)
                        .forEach { out.add(GameAction.BuyBuilding(BuildingType.PORT, it.key)) }
                }
                // Fisheries where shoals glitter within working range.
                if (treasury >= eff.fisheryCost + 10) {
                    state.tiles.entries
                        .filter { (hex, tile) ->
                            tile.owner == me && !tile.starving && tile.building == null &&
                                tile.unit == null && tile.flora == null && tile.deposit == null &&
                                Rules.shoalsWithin(state.tiles, hex, rules.fisheryRange) > 0
                        }
                        .sortedWith(
                            compareByDescending<Map.Entry<Hex, com.msa.fightandconquer.core.model.Tile>> { (hex, _) ->
                                // Rank by CAPPED count: a 4-shoal spot cannot out-earn a 3-shoal one.
                                minOf(
                                    Rules.shoalsWithin(state.tiles, hex, rules.fisheryRange),
                                    rules.fisheryShoalCap,
                                )
                            }.thenBy { it.key.packed },
                        )
                        .take(2)
                        .forEach { out.add(GameAction.BuyBuilding(BuildingType.FISHERY, it.key)) }
                }
                // Warships answer visible enemy boats (the -4/boat evaluator term
                // makes the hunt worthwhile once one is afloat).
                if (treasury >= eff.warshipCost) {
                    val visible = if (rules.fogOfWar) Rules.visibleHexes(state, me) else null
                    val enemyBoats = state.units.values.any {
                        it.owner != me && Rules.isNaval(it.type) && (visible == null || it.hex in visible)
                    }
                    if (enemyBoats) {
                        val spot = state.tiles.entries
                            .asSequence()
                            .filter { (_, tile) ->
                                tile.owner == me && !tile.starving &&
                                    tile.building == com.msa.fightandconquer.core.model.Building.PORT
                            }
                            .flatMap { (hex, _) -> HexMath.neighbors(hex) }
                            .filter {
                                val t = state.tiles[it]
                                t?.terrain == com.msa.fightandconquer.core.model.Terrain.SEA &&
                                    t.unit == null && t.building == null
                            }
                            .minByOrNull { it.packed }
                        spot?.let {
                            out.add(GameAction.BuyUnit(1, it, com.msa.fightandconquer.core.model.UnitType.WARSHIP))
                        }
                    }
                }
            }

            // Watchtowers: Hard only, fog games only, and only with a healthy economy.
            if (difficulty == Difficulty.HARD && rules.fogOfWar &&
                treasury >= eff.watchtowerCost + 10 && income - Rules.upkeepOf(state, me) >= 4
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
        // Never pave the last muster yard: in a naval game a fully built-up
        // island leaves no hex to raise a unit on, and a rich AI with no army
        // can never invade anyone again — the game freezes with a full purse.
        if (rules.navalEnabled) {
            val emptyOwnLand = state.tiles.values.count { t ->
                t.owner == me && !t.starving &&
                    t.terrain == com.msa.fightandconquer.core.model.Terrain.LAND &&
                    t.building == null && t.unit == null
            }
            if (emptyOwnLand <= 1) out.removeAll { it is GameAction.BuyBuilding }
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

}
