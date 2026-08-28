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

    fun candidates(
        state: GameState,
        difficulty: Difficulty,
        profile: AiProfile = AiProfile.NEUTRAL,
        context: StrategicContext? = null,
    ): List<GameAction> {
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
                if (difficulty == Difficulty.HARD) {
                    all - DiplomacyPolicy.betrayalTargets(state, me, profile)
                } else {
                    all
                }
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

        // Soldier strength per tier at MY effective rules (Smithing-aware), for
        // the merge gate below. Index 0 unused.
        val soldierStrength = IntArray(rules.maxTier + 1)
        for (t in 1..rules.maxTier) {
            soldierStrength[t] = Rules.buyStrength(state, me, t, com.msa.fightandconquer.core.model.UnitType.SOLDIER)
        }
        // Muster ceiling once per call, not per unit (a tile scan per lookup).
        val maxRecruitable = Tiers.maxRecruitable(state, me)

        // --- Capital defense (range-bound movement means nobody teleports home:
        // the garrison must be raised BEFORE the axe falls, so when an enemy is
        // within striking range of the throne, offer moves and buys onto its
        // neighboring hexes — the evaluator's capital-guard term picks them up).
        val capital = state.player(me).capital
        val capitalThreat = Tiers.capitalThreat(state, me)
        val capitalGuardHexes: Set<Hex> = if (capitalThreat == 0 || capital == null) {
            emptySet()
        } else {
            HexMath.neighbors(capital).filter { n ->
                val t = state.tiles[n]
                t != null && t.owner == me && !t.starving &&
                    t.unit == null && t.building == null
            }.toSet()
        }
        if (capitalThreat > 0) {
            for (hex in capitalGuardHexes.sortedBy { it.packed }) {
                // Solve the garrison through buyDefense (Armory research raises it);
                // identity without research: smallest t with t >= threat. The
                // fallback is the best tier the muster halls allow, not maxTier.
                val tier = Tiers.cheapestGarrison(state, me, capitalThreat) ?: maxRecruitable
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
            // Scoop a waiting monster cache on own ground (a bombarded or dawn-dropped
            // chest nobody walked yet) — the sim credits the gold on arrival, so the
            // treasury term prices the trip; walks within territory are otherwise
            // never candidates. Caches on neutral ground ride captureTargets above.
            reach.moveTargets.sortedBy { it.packed }.forEach {
                if (state.tiles.getValue(it).cache != null) {
                    out.add(GameAction.MoveUnit(unit.id, it))
                }
            }
            // Merge only when the merged tier would break a currently-unbreakable
            // frontier hex: my strength at this tier fails against D, the next
            // tier's succeeds. Identity without research: D == unit.tier. The
            // reducer refuses a merge past the muster gate (reach.mergeTargets is
            // already filtered) — the guard just avoids proposing doomed pairs.
            val mergedBreaks = unit.tier < rules.maxTier &&
                unit.tier + 1 <= maxRecruitable &&
                frontierDefenses.any { d ->
                    soldierStrength[unit.tier] <= d && d < soldierStrength[unit.tier + 1]
                }
            if (mergedBreaks) {
                reach.mergeTargets.sortedBy { it.packed }.forEach { targetHex ->
                    out.add(GameAction.MergeUnits(unit.id, state.tiles.getValue(targetHex).unit!!))
                }
            }
        }

        // --- Buy-capture: cheapest tier that takes each frontier hex (solved
        // through buyStrength — Smithing lowers it; identity: defense + 1) ---
        for ((hex, defense) in frontier.entries.sortedBy { it.key.packed }) {
            val tier = Tiers.cheapestBreaker(state, me, defense) ?: continue
            if (treasury >= rules.unitCost[tier - 1]) {
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
            // Towers ranked by PREDICTED COVERAGE (the Antiyoy expert rule): what
            // counts is how many contested own hexes the aura would actually
            // harden, not how exposed the tower hex itself is — one hex behind
            // the line often covers more border than standing on it.
            if (treasury >= eff.towerCost) {
                val towerSpots = state.tiles.entries
                    .filter { (_, tile) ->
                        tile.owner == me && !tile.starving && tile.building == null &&
                            tile.unit == null && tile.flora == null
                    }
                    .map { it.key to towerGain(state, it.key, me, eff.towerDefense) }
                    .filter { it.second >= profile.towerGainThreshold }
                    .sortedWith(compareByDescending<Pair<Hex, Int>> { it.second }.thenBy { it.first.packed })
                    .take(3)
                towerSpots.forEach { out.add(GameAction.BuyBuilding(BuildingType.TOWER, it.first)) }
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
                if (myMarkets < profile.maxMarkets && treasury >= eff.marketCost + 10) {
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

                // Strong towers where a plain tower wouldn't hold (new behavior,
                // research games only — the flag keeps pre-research worlds
                // bit-identical; the AI historically never bought castles).
                if (rules.researchEnabled &&
                    Rules.buildingAvailable(state, me, BuildingType.STRONG_TOWER) &&
                    treasury >= eff.strongTowerCost
                ) {
                    state.tiles.entries
                        .filter { (hex, tile) ->
                            tile.owner == me && !tile.starving && tile.building == null &&
                                tile.unit == null && tile.flora == null &&
                                Rules.defenseOf(state, hex) < eff.strongTowerDefense &&
                                HexMath.neighbors(hex).any { n ->
                                    state.unitAt(n)?.let { u ->
                                        u.owner != me && Rules.strengthOf(state, u) > eff.towerDefense
                                    } == true
                                }
                        }
                        .sortedBy { it.key.packed }
                        .take(2)
                        .forEach { out.add(GameAction.BuyBuilding(BuildingType.STRONG_TOWER, it.key)) }
                }

                // Banks: markets' placement discipline (interior, capped) without
                // the neighbor bookkeeping. Unavailable until BANKING completes.
                val myBanks = state.tiles.values.count {
                    it.owner == me && it.building == com.msa.fightandconquer.core.model.Building.BANK
                }
                if (Rules.buildingAvailable(state, me, BuildingType.BANK) &&
                    myBanks < profile.maxBanks && treasury >= eff.bankCost + 10
                ) {
                    state.tiles.entries
                        .filter { (hex, tile) ->
                            tile.owner == me && !tile.starving && tile.building == null &&
                                tile.unit == null && tile.flora == null && tile.deposit == null &&
                                HexMath.neighbors(hex).all { state.tiles[it]?.owner == me }
                        }
                        .sortedBy { it.key.packed }
                        .take(2)
                        .forEach { out.add(GameAction.BuyBuilding(BuildingType.BANK, it.key)) }
                }

                // Fortresses: HARD only, capped, and only where the border is under
                // pressure a castle could not hold — anti-turtle by construction.
                if (difficulty == Difficulty.HARD &&
                    Rules.buildingAvailable(state, me, BuildingType.FORTRESS) &&
                    treasury >= eff.fortressCost + 10
                ) {
                    val myFortresses = state.tiles.values.count {
                        it.owner == me && it.building == com.msa.fightandconquer.core.model.Building.FORTRESS
                    }
                    if (myFortresses < profile.maxFortresses) {
                        state.tiles.entries
                            .filter { (hex, tile) ->
                                tile.owner == me && !tile.starving && tile.building == null &&
                                    tile.unit == null && tile.flora == null &&
                                    Rules.defenseOf(state, hex) < eff.fortressDefense &&
                                    HexMath.neighbors(hex).any { n ->
                                        state.unitAt(n)?.let { u ->
                                            u.owner != me &&
                                                Rules.strengthOf(state, u) > eff.strongTowerDefense
                                        } == true
                                    }
                            }
                            .sortedWith(
                                compareByDescending<Map.Entry<Hex, com.msa.fightandconquer.core.model.Tile>> { (hex, _) ->
                                    HexMath.neighbors(hex).count { n ->
                                        val t = state.tiles[n]
                                        t?.owner != null && t.owner != me
                                    }
                                }.thenBy { it.key.packed },
                            )
                            .take(1)
                            .forEach { out.add(GameAction.BuyBuilding(BuildingType.FORTRESS, it.key)) }
                    }
                }
            }

            // --- Special units (Normal/Hard) ---
            if (rules.specialUnitsEnabled && difficulty != Difficulty.EASY) {
                // Catapults where BUILDING defense is the blocker: the cheapest-tier
                // logic can't crack defense >= maxTier, a catapult ignores it.
                // Muster-gated behind the Siege Workshop; generating doomed
                // candidates would only waste reducer runs (the Port idiom).
                if (treasury >= eff.catapultCost &&
                    Rules.unitAvailable(state, me, 1, com.msa.fightandconquer.core.model.UnitType.CATAPULT)
                ) {
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
                // aura would actually raise. Muster-gated behind the Archery Range.
                if (treasury >= eff.archerCost &&
                    Rules.unitAvailable(state, me, 1, com.msa.fightandconquer.core.model.UnitType.ARCHER)
                ) {
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
                        .filter { it.second >= profile.towerGainThreshold }
                        .sortedWith(compareByDescending<Pair<Hex, Int>> { it.second }.thenBy { it.first.packed })
                        .take(3)
                        .forEach {
                            out.add(GameAction.BuyUnit(1, it.first, com.msa.fightandconquer.core.model.UnitType.ARCHER))
                        }
                }
            }

            if (rules.navalEnabled && difficulty != Difficulty.EASY) {
                // Ports: the gateway asset of sea maps (income + boat yard + supply).
                // Research-gated behind NAVIGATION; generating doomed candidates
                // would only waste reducer runs (the AI's per-turn perf budget).
                if (Rules.buildingAvailable(state, me, BuildingType.PORT) && treasury >= eff.portCost + 10) {
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
                                Rules.shoalsWithin(state.tiles, hex, eff.fisheryRange) > 0
                        }
                        .sortedWith(
                            compareByDescending<Map.Entry<Hex, com.msa.fightandconquer.core.model.Tile>> { (hex, _) ->
                                // Rank by CAPPED count: a 4-shoal spot cannot out-earn a 3-shoal one.
                                minOf(
                                    Rules.shoalsWithin(state.tiles, hex, eff.fisheryRange),
                                    eff.fisheryShoalCap,
                                )
                            }.thenBy { it.key.packed },
                        )
                        .take(2)
                        .forEach { out.add(GameAction.BuyBuilding(BuildingType.FISHERY, it.key)) }
                }
                // Bridges as ordinary strategy, not just the rich-man's war-chest
                // escape: one span from our shore to foreign land opens a
                // permanent second front (or shortcut) the simulated capture
                // terms can price like any other purchase.
                if (treasury >= eff.bridgeCost + 10) {
                    state.tiles.entries
                        .filter { (hex, tile) ->
                            tile.terrain == com.msa.fightandconquer.core.model.Terrain.SEA &&
                                tile.building == null && tile.unit == null &&
                                HexMath.neighbors(hex).any {
                                    val t = state.tiles[it]
                                    t?.owner == me && !t.starving &&
                                        t.terrain == com.msa.fightandconquer.core.model.Terrain.LAND
                                } &&
                                HexMath.neighbors(hex).any {
                                    val t = state.tiles[it]
                                    t != null && t.terrain == com.msa.fightandconquer.core.model.Terrain.LAND &&
                                        t.owner != me && t.owner !in partners
                                }
                        }
                        .sortedBy { it.key.packed }
                        .take(2)
                        .forEach { out.add(GameAction.BuyBuilding(BuildingType.BRIDGE, it.key)) }
                }
                // Warships answer visible enemy WAR boats (the -4/boat evaluator
                // term makes the hunt worthwhile once one is afloat). Fishermen
                // never trigger a purchase — a dory is prey, not a threat. An
                // admiral doesn't wait to be provoked: any beatable coastal
                // target justifies a hull.
                if (treasury >= eff.warshipCost) {
                    val visible = if (rules.fogOfWar) Rules.visibleHexes(state, me) else null
                    val enemyBoats = state.units.values.any {
                        it.owner != me &&
                            (
                                it.type == com.msa.fightandconquer.core.model.UnitType.TRANSPORT ||
                                    it.type == com.msa.fightandconquer.core.model.UnitType.WARSHIP
                                ) &&
                            (visible == null || it.hex in visible)
                    }
                    val coastalPrey = profile.proactiveWarships && state.tiles.entries.any { (hex, tile) ->
                        tile.terrain == com.msa.fightandconquer.core.model.Terrain.LAND &&
                            tile.owner != null && tile.owner != me && tile.owner !in partners &&
                            (visible == null || hex in visible) &&
                            Rules.defenseOf(state, hex) < eff.warshipStrength &&
                            HexMath.neighbors(hex).any {
                                state.tiles[it]?.terrain == com.msa.fightandconquer.core.model.Terrain.SEA
                            }
                    }
                    if (enemyBoats || coastalPrey) {
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
                    HexMath.range(hex, eff.watchtowerVisionRadius).count { it !in discovered }
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
        // --- Upkeep relief: on a strained economy, offer to pension off units
        // parked deep behind the line. The evaluator arbitrates — the freed
        // upkeep and the surplus-peasant penalty must actually beat the army
        // value lost, so a useful reserve is never sold off. ---
        if (context != null && difficulty != Difficulty.EASY &&
            income - Rules.upkeepOf(state, me) <= 2
        ) {
            state.units.values
                .filter { u ->
                    u.owner == me && u.type == com.msa.fightandconquer.core.model.UnitType.SOLDIER &&
                        (context.distanceToFront[u.hex] ?: Int.MAX_VALUE) > 2
                }
                .sortedBy { it.id.value }
                .take(3)
                .forEach { out.add(GameAction.DisbandUnit(it.id)) }
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

    /**
     * How many CONTESTED own hexes (bordering non-owned land) a tower at [hex]
     * would actually raise above their current defense — the tower hex itself
     * included. Interior spots score 0 and never qualify.
     */
    private fun towerGain(
        state: GameState,
        hex: Hex,
        me: com.msa.fightandconquer.core.model.PlayerId,
        towerDefense: Int,
    ): Int {
        fun contested(h: Hex): Boolean = HexMath.neighbors(h).any { n ->
            val t = state.tiles[n]
            t != null && t.terrain == com.msa.fightandconquer.core.model.Terrain.LAND && t.owner != me
        }

        var gain = 0
        if (Rules.defenseOf(state, hex) < towerDefense && contested(hex)) gain++
        HexMath.forEachNeighbor(hex) { n ->
            val t = state.tiles[n]
            if (t != null && t.owner == me && Rules.defenseOf(state, n) < towerDefense && contested(n)) gain++
        }
        return gain
    }

    /** How many hexes (self + adjacent own) an archer's aura would raise above their current defense. */
    private fun auraGain(state: GameState, hex: Hex, me: com.msa.fightandconquer.core.model.PlayerId): Int {
        val aura = Rules.effectiveRules(state, me).archerAuraDefense
        var gain = 0
        if (Rules.defenseOf(state, hex) < aura) gain++
        HexMath.forEachNeighbor(hex) { n ->
            val t = state.tiles[n]
            if (t != null && t.owner == me && Rules.defenseOf(state, n) < aura) gain++
        }
        return gain
    }

}
