package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitType
import kotlin.math.min

/** Position scoring from [me]'s perspective. Higher is better. */
object Evaluator {

    fun score(
        state: GameState,
        me: PlayerId,
        difficulty: Difficulty,
        visibleOverride: Set<com.msa.fightandconquer.core.hex.Hex>? = null,
        profile: AiProfile = AiProfile.NEUTRAL,
        /**
         * Enemy units that threatened my territory at the TURN'S START (the
         * frozen [StrategicContext.threatUnits]) — the visibleOverride idiom
         * for soldiers. The counter-attack term prices only these: one killed
         * is a gain, but an enemy newly ADJACENT after a candidate advance
         * must never read as a fresh penalty — pricing contact itself turns
         * the term into a repulsion field that steers expansion AWAY from the
         * war (measured on crown_granary: the stand-in fled its EASY attacker
         * south into a thin ribbon and was cut apart).
         */
        threats: Collection<com.msa.fightandconquer.core.model.UnitId> = emptyList(),
    ): Double {
        (state.phase as? GamePhase.Finished)?.let {
            return if (it.winner == me) 1e9 else -1e9
        }

        // Fog of war: the AI honors fog — enemy information outside its own vision
        // simply doesn't exist for scoring (own assets are always fully visible).
        // [visibleOverride] freezes the set across a one-ply comparison: a
        // candidate must never be penalized for the fog it LIFTS — an advance
        // reveals enemy ground that already existed, and pricing the reveal as
        // a loss froze whole invasions at the beachhead (the measured fog-1
        // stall: every capture scored negative because it uncovered the
        // defender's interior).
        val visible: Set<com.msa.fightandconquer.core.hex.Hex>? = visibleOverride
            ?: if (state.config.rules.fogOfWar) Rules.visibleHexes(state, me) else null

        // Own buildings are valued at MY effective (civ) caps — same table their
        // income is actually paid from, so the valuation can't drift from it.
        val eff = Rules.effectiveRules(state, me)

        val partners: Set<PlayerId> =
            if (state.config.rules.diplomacyEnabled) state.diplomacy.partnersOf(me) else emptySet()
        val ownLand = HashSet<com.msa.fightandconquer.core.hex.Hex>()

        var myHexes = 0
        var myTrees = 0
        var enemyHexes = 0
        var enemyStarving = 0
        var myVeins = 0
        var myVeinsWithMine = 0
        var myFertile = 0
        var myWatchtowers = 0
        var myPorts = 0
        var enemyVeins = 0
        var enemyForts = 0
        var buildingScore = 0.0
        for ((hex, tile) in state.tiles) {
            // Only land counts as territory — an owned bridge hex is a road, not a
            // 14-point asset (else the AI would pave the sea with bridges).
            if (tile.terrain != com.msa.fightandconquer.core.model.Terrain.LAND) continue
            when {
                tile.owner == me -> {
                    ownLand.add(hex)
                    if (!tile.starving) {
                        myHexes++
                        when (tile.deposit) {
                            Deposit.GOLD_VEIN ->
                                if (tile.building == Building.MINE) myVeinsWithMine++ else myVeins++
                            Deposit.FERTILE -> myFertile++
                            Deposit.FISH_SHOAL, null -> {} // shoals live at sea, valued via FISHERY
                        }
                        // Caps come from the rule constants so the valuation can't
                        // drift from the income these buildings actually earn.
                        when (tile.building) {
                            Building.MARKET ->
                                buildingScore += 4.0 + 1.0 *
                                    min(Rules.marketNeighbors(state.tiles, hex, me), eff.marketNeighborCap)
                            Building.LUMBER_CAMP ->
                                buildingScore += 3.0 + 1.5 *
                                    min(Adjacency.adjacentOwnTrees(state, hex, me), eff.lumberCampTreeCap)
                            Building.WATCHTOWER -> myWatchtowers++
                            Building.PORT -> myPorts++
                            Building.FISHERY ->
                                buildingScore += 2.0 + 1.5 *
                                    min(
                                        Rules.shoalsWithin(state.tiles, hex, eff.fisheryRange),
                                        eff.fisheryShoalCap,
                                    )
                            // Research line. The UNIVERSITY term anchors the sunk
                            // asset (ResearchPolicy buys it — this prices keeping
                            // and defending it); BANK fills the same income-curve
                            // hole the mine term documents above. Deliberately NO
                            // per-tech or active-research terms: research state
                            // only mutates at turn start, so it is constant within
                            // any one-ply comparison — a term could never steer a
                            // decision, only distort cross-position comparisons.
                            Building.UNIVERSITY -> if (!tile.starving) buildingScore += 8.0
                            Building.BANK -> buildingScore += 6.0
                            // Muster line: anchors the sunk prerequisite
                            // (MilitaryPolicy buys it — these price keeping and
                            // defending it, never the purchase). Zero-valued in
                            // dormant games (the buildings never appear), so no
                            // snapshot can reshuffle.
                            Building.BARRACKS -> buildingScore += 6.0
                            Building.SIEGE_WORKSHOP -> buildingScore += 4.0
                            Building.ARCHERY_RANGE -> buildingScore += 3.0
                            else -> {}
                        }
                    }
                    // Trees next to an own lumber camp are managed income, not rot.
                    if (tile.flora is Flora.Tree && !Adjacency.nextToOwnCamp(state, hex, me)) myTrees++
                }
                tile.owner != null -> {
                    if (visible == null || hex in visible) {
                        enemyHexes++
                        if (tile.starving) enemyStarving++
                        if (tile.deposit == Deposit.GOLD_VEIN) enemyVeins++
                        when (tile.building) {
                            Building.TOWER, Building.STRONG_TOWER, Building.CAPITAL,
                            Building.FORTRESS,
                            -> enemyForts++
                            else -> {}
                        }
                    }
                }
            }
        }

        val income = Rules.incomeOf(state, me)
        val upkeep = Rules.upkeepOf(state, me)
        val net = income - upkeep
        val treasury = state.player(me).treasury

        // Land dominates: every hex pays income forever and is the win condition.
        // Hoarded coins are nearly worthless — spending them on expansion must win.
        // EASY deliberately keeps rookie weights: it hoards coins and undervalues land.
        // Income scoring has diminishing returns: a deficit or thin margin is dangerous
        // (full weight), but above +10/turn extra income barely matters — otherwise the
        // AI refuses the upkeep needed to break defended hexes and stalemates forever.
        val incomeScore = 6.0 * min(net, 10).toDouble() + 0.5 * maxOf(0, net - 10)

        // EASY still hoards relative to the others (weaker land pull, stronger coin pull)
        // but a plain peasant buy-capture MUST stay net-positive from turn one:
        // +12 hex − 6 income − ~2.5 treasury > 0. The old 10/0.5·min(150) weights made
        // every expansion negative until ~150 coins — an AI that visibly did nothing.
        // Deposits and income buildings carry explicit ASSET terms: past net +10 the
        // diminishing income curve values +6 income at ~3 points, less than the coins
        // spent — without these the AI would stop building its economy mid-game.
        var score = 0.0
        if (difficulty == Difficulty.EASY) {
            // Easy stays a rookie: no deposit/building asset terms. It still builds a
            // mine early because +6 income is huge while net is below the curve's knee.
            score += 12.0 * myHexes
            score += incomeScore
            score += 0.25 * min(treasury, 100)
        } else {
            score += 14.0 * profile.hexWeight * myHexes
            score += incomeScore
            score += 0.15 * min(treasury, 200)
            score += profile.assetWeight *
                (10.0 * myVeins + 18.0 * myVeinsWithMine + 6.0 * myFertile + buildingScore)
            if (state.config.rules.fogOfWar) score += 6.0 * myWatchtowers
            score -= 4.0 * enemyVeins
            if (state.config.rules.navalEnabled) {
                // Ports are gateway assets (supply + boat yard), but two is plenty.
                score += 6.0 * profile.navalWeight * min(myPorts, 2)
                // A bridge REACHING foreign land is a second front the argmax can
                // fund on its own (its purchase captures nothing, so without an
                // asset term the simulated score only ever drops). Kept small and
                // capped: territory it is not (see the terrain filter above —
                // pricing bridge hexes as land had the AI paving the sea).
                var warBridges = 0
                for ((hex, tile) in state.tiles) {
                    if (tile.owner != me || tile.building != Building.BRIDGE) continue
                    val opensFront = com.msa.fightandconquer.core.hex.HexMath.neighbors(hex).any { n ->
                        val t = state.tiles[n]
                        t != null && t.terrain == com.msa.fightandconquer.core.model.Terrain.LAND &&
                            t.owner != me && t.owner !in partners
                    }
                    if (opensFront) warBridges++
                }
                score += 4.0 * min(warBridges, 2)
                // Enemy WAR boats are threats worth sinking (+4 per kill via this
                // term); a fisherman is not an invasion — just a snack worth
                // taking when a warship is already alongside, never worth buying
                // a 25-coin hunter for.
                var enemyWarBoats = 0
                var enemyFishingBoats = 0
                for (u in state.units.values) {
                    if (u.owner == me || !Rules.isNaval(u.type)) continue
                    if (visible != null && u.hex !in visible) continue
                    if (u.type == UnitType.FISHING_BOAT) enemyFishingBoats++ else enemyWarBoats++
                }
                score -= profile.navalWeight * (4.0 * enemyWarBoats + 1.0 * enemyFishingBoats)
            }
        }
        score -= 6.0 * myTrees
        score -= 2.0 * enemyHexes

        // Bankruptcy guard: never plan into a projected negative treasury — and
        // (rookies excepted) stay solvent through a HALVED income. The 1-ply
        // projection uses CURRENT income, but under range-bound movement a
        // slice on the opponent's turn routinely erases half of it before the
        // next upkeep tick, and an army financed to the last coin dies whole
        // to the first cut.
        // Soft on purpose (a capture that reconnects starving territory swings
        // income by far more than this): the guard prunes routine army padding
        // at the margin without freezing a zero-net economy solid.
        if (treasury + net < 0) {
            score -= if (difficulty == Difficulty.EASY) 100.0 else 1e6
        } else if (difficulty != Difficulty.EASY && treasury + income / 2 - upkeep < 0) {
            score -= 60.0
        }

        // Pact value (Normal/Hard): peace with someone stronger is worth keeping.
        // A simulated partner-capture drops this term AND pays the break penalty
        // through the treasury term, so the greedy loop can't back-door the policy.
        if (difficulty != Difficulty.EASY && state.config.rules.diplomacyEnabled &&
            state.diplomacy.pacts.isNotEmpty()
        ) {
            val strongWeight = if (difficulty == Difficulty.HARD) 14.0 else 10.0
            val myPower = DiplomacyPolicy.powerOf(state, me, me)
            for (pact in state.diplomacy.pacts) {
                val partner = when (me) {
                    pact.a -> pact.b
                    pact.b -> pact.a
                    else -> null
                } ?: continue
                val partnerPower = DiplomacyPolicy.powerOf(state, me, partner)
                score += if (partnerPower * 5 >= myPower * 6) strongWeight else 4.0
            }
        }

        // Capital guard (Normal/Hard): with range-bound movement no unit
        // teleports home to save the throne, so the throne must be held ahead
        // of time. Losing the capital slices the realm, halves the purse, and
        // usually bankrupts what remains — fear any enemy that could reach it.
        if (difficulty != Difficulty.EASY) {
            val capital = state.player(me).capital
            if (capital != null && state.tiles[capital]?.owner == me) {
                val capDefense = Rules.defenseOf(state, capital)
                val threatened = state.units.values.any { u ->
                    u.owner != me && !Rules.isNaval(u.type) &&
                        (visible == null || u.hex in visible) &&
                        com.msa.fightandconquer.core.hex.HexMath.distance(u.hex, capital) <=
                        Rules.moveRangeOf(state, u) &&
                        Rules.strengthOf(state, u) > capDefense
                }
                if (threatened) score -= 30.0 * profile.defenseWeight
            }
        }

        // Night threat (Normal/Hard, day-night games): a land unit some monster
        // can reach and out-attack is a unit about to die at the round wrap —
        // stepping under a tower, merging up, or slaying the beast all clear
        // the penalty, so survival falls out of the ordinary argmax. Hex
        // distance approximates the monsters' BFS at one ply (cheap and never
        // under-warns). EASY stays a rookie and blunders through the dark.
        if (difficulty != Difficulty.EASY && state.config.rules.dayNightEnabled) {
            val monsterRange = state.config.rules.monsterMoveRange
            val monsters = ArrayList<Pair<com.msa.fightandconquer.core.hex.Hex, Int>>()
            for ((hex, tile) in state.tiles) {
                val monster = tile.monster ?: continue
                if (visible == null || hex in visible) {
                    monsters.add(hex to Rules.monsterAttackOf(monster))
                }
            }
            if (monsters.isNotEmpty()) {
                // Lit ground is monster-proof (no entry, no strikes): a unit
                // standing in a beacon's light is never a threatened unit —
                // without this the argmax over-garrisons protected interiors.
                val lit = Rules.litHexes(state)
                var threatened = 0
                for (u in state.units.values) {
                    if (u.owner != me || Rules.isNaval(u.type)) continue
                    if (u.hex in lit) continue
                    val defense = Rules.defenseOf(state, u.hex)
                    if (monsters.any { (hex, attack) ->
                            attack > defense &&
                                com.msa.fightandconquer.core.hex.HexMath.distance(hex, u.hex) <= monsterRange
                        }
                    ) {
                        threatened++
                    }
                }
                score -= 12.0 * threatened
            }
        }

        // Slicing pays: enemy tiles cut off from their capital are dying assets.
        // Not just Hard's trick — it is a core mechanic the Academy teaches in
        // mission 5, and under range-bound movement the cut is the main answer
        // to a cheap swarm, so Normal must see it too (Easy stays blind). Priced
        // near a starving tile's true swing (its lost income plus my denial),
        // so a genuine cut outbids a plain capture of the same cost.
        if (difficulty != Difficulty.EASY) {
            score += 12.0 * profile.cutWeight * enemyStarving
        }

        // Counter-attack pressure (Normal/Hard, every map): an enemy soldier
        // that threatened my territory at the turn's start is a raid in
        // progress — a candidate that kills it scores the removal on top of
        // any hex it takes, which is what makes recapturing a fresh enemy
        // foothold beat expanding politely somewhere quiet. Priced from the
        // FROZEN threat list only (see the parameter doc): contact made by my
        // own advance is never a penalty. The naval invader term below still
        // prices deep beachheads on top. Enemy units already starving are
        // bonus corpses: they die at their own turn start, the payoff of a
        // landed cut.
        if (difficulty != Difficulty.EASY) {
            var threatStrength = 0
            var maxThreat = 0
            var starvingEnemies = 0
            for (id in threats) {
                val u = state.units[id] ?: continue // already dead: the payoff
                if (u.owner == me) continue
                threatStrength += Rules.strengthOf(state, u)
            }
            for (u in state.units.values) {
                if (u.owner == me || u.owner in partners || Rules.isNaval(u.type)) continue
                if (visible != null && u.hex !in visible) continue
                if (state.tiles[u.hex]?.starving == true) starvingEnemies++
                val near = u.hex in ownLand ||
                    com.msa.fightandconquer.core.hex.HexMath.neighbors(u.hex).any { it in ownLand }
                if (near) {
                    val s = Rules.strengthOf(state, u)
                    if (s > maxThreat) maxThreat = s
                }
            }
            score -= 0.8 * profile.counterAttackWeight * threatStrength
            score += 3.0 * profile.cutWeight * starvingEnemies

            // Army value: a soldier sized to a wall or raider that actually
            // exists is an asset, not just upkeep — without this the one-ply
            // argmax treats every unit as a liability the moment it stops
            // capturing, buys nothing but the cheapest breaker, and never
            // holds a standing force. Surplus peasants beyond the open (
            // undefended) frontier plus slack stay a liability, so a swarm
            // still reads as waste and a disband can win the comparison.
            var openFrontier = 0
            val walls = HashSet<Int>()
            val seen = HashSet<com.msa.fightandconquer.core.hex.Hex>()
            for (hex in ownLand) {
                if (state.tiles.getValue(hex).starving) continue
                com.msa.fightandconquer.core.hex.HexMath.forEachNeighbor(hex) { n ->
                    if (seen.add(n)) {
                        val t = state.tiles[n]
                        if (t != null && t.terrain == com.msa.fightandconquer.core.model.Terrain.LAND &&
                            t.owner != me && t.owner !in partners
                        ) {
                            val d = Rules.defenseOf(state, n)
                            if (d == 0) openFrontier++ else walls.add(d)
                        }
                    }
                }
            }
            var peasants = 0
            var armyScore = 0.0
            for (u in state.units.values) {
                if (u.owner != me || u.type != UnitType.SOLDIER) continue
                if (u.tier == 1) {
                    peasants++
                } else if (walls.any { it >= u.tier - 1 } || maxThreat >= u.tier - 1) {
                    armyScore += 0.6 * u.tier
                }
            }
            val surplus = peasants - openFrontier - 2
            if (surplus > 0) armyScore -= 1.0 * surplus
            score += armyScore
        }

        // Invasion defense (Normal/Hard, naval games only): an enemy soldier
        // standing on the capital's own landmass is a beachhead growing under
        // grace — every one is worth killing. This is the term that prices
        // warship bombards on landings and land counterattacks (a kill alone
        // wins no hex, so without it the greedy loop scores crushing an
        // invasion at zero). Landlocked games get nothing: with no sea there
        // are no invasions, and on a shared continent the term would just
        // count the ordinary front line twice.
        if (difficulty != Difficulty.EASY && state.config.rules.navalEnabled) {
            val homeland = state.player(me).capital?.let { cap ->
                com.msa.fightandconquer.core.hex.HexMath.floodFill(cap) {
                    state.tiles[it]?.terrain == com.msa.fightandconquer.core.model.Terrain.LAND
                }
            } ?: emptySet()
            val invaders = state.units.values.count {
                it.owner != me && !Rules.isNaval(it.type) && it.hex in homeland &&
                    (visible == null || it.hex in visible)
            }
            score -= 6.0 * profile.defenseWeight * invaders
        }

        if (difficulty == Difficulty.HARD) {
            // Retake awareness: undefended fresh borders are a liability — but a
            // BOUNDED one. The old flat -1.5/hex compounded down a long front until
            // every expanding move scored negative and HARD passed turns while an
            // EASY swarm ate it (the diagnosed mutual-turtle stall, docs/roadmap.md).
            // The cap keeps the signal ("don't leave a few fresh borders hanging")
            // and scales with the balance of fielded force: an army that outguns
            // the visible enemy can afford exposed ground (retakes are cheap), an
            // outgunned one stays twice as careful — but never frozen.
            val exposed = exposedBorderHexes(state, me, visible)
            if (exposed > 0) {
                var myForce = 0
                var enemyForce = 0
                for (u in state.units.values) {
                    if (Rules.isNaval(u.type)) continue
                    if (u.owner == me) {
                        myForce += Rules.strengthOf(state, u)
                    } else if (visible == null || u.hex in visible) {
                        enemyForce += Rules.strengthOf(state, u)
                    }
                }
                val cap = if (myForce >= enemyForce) 3 else 6
                score -= 1.5 * profile.defenseWeight * min(exposed, cap)
            }
            // Anti-hoard: a catapult with no visible fortification left to crack is
            // pure upkeep — let attrition pressure retire it.
            if (enemyForts == 0) {
                val idleCatapults = state.units.values.count {
                    it.owner == me && it.type == com.msa.fightandconquer.core.model.UnitType.CATAPULT
                }
                score -= 1.5 * idleCatapults
            }
        }
        return score
    }

    /**
     * Own hexes adjacent to an enemy unit that outguns their defense. Under fog the
     * inputs (own hexes + neighbors) are always within the visionRadiusOwned >= 2
     * guarantee, but we gate on [visible] anyway to stay honest if radii change.
     */
    private fun exposedBorderHexes(
        state: GameState,
        me: PlayerId,
        visible: Set<com.msa.fightandconquer.core.hex.Hex>?,
    ): Int {
        var exposed = 0
        for ((hex, tile) in state.tiles) {
            if (tile.owner != me) continue
            var threat = 0
            com.msa.fightandconquer.core.hex.HexMath.forEachNeighbor(hex) { n ->
                if (visible == null || n in visible) {
                    val enemy = state.unitAt(n)
                    if (enemy != null && enemy.owner != me) {
                        threat = maxOf(threat, Rules.strengthOf(state, enemy))
                    }
                    // A monster at the fence counts like any raider (HARD garrisons at night).
                    state.tiles[n]?.monster?.let { threat = maxOf(threat, Rules.monsterAttackOf(it)) }
                }
            }
            if (threat > Rules.defenseOf(state, hex)) exposed++
        }
        return exposed
    }
}
