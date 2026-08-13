package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import kotlin.math.min

/** Position scoring from [me]'s perspective. Higher is better. */
object Evaluator {

    fun score(state: GameState, me: PlayerId, difficulty: Difficulty): Double {
        (state.phase as? GamePhase.Finished)?.let {
            return if (it.winner == me) 1e9 else -1e9
        }

        // Fog of war: the AI honors fog — enemy information outside its own vision
        // simply doesn't exist for scoring (own assets are always fully visible).
        val visible: Set<com.msa.fightandconquer.core.hex.Hex>? =
            if (state.config.rules.fogOfWar) Rules.visibleHexes(state, me) else null

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
                                    min(adjacentOwned(state, hex, me), state.config.rules.marketNeighborCap)
                            Building.LUMBER_CAMP ->
                                buildingScore += 3.0 + 1.5 *
                                    min(Adjacency.adjacentOwnTrees(state, hex, me), state.config.rules.lumberCampTreeCap)
                            Building.WATCHTOWER -> myWatchtowers++
                            Building.PORT -> myPorts++
                            Building.FISHERY ->
                                buildingScore += 2.0 + 1.5 *
                                    min(adjacentShoals(state, hex), state.config.rules.fisheryShoalCap)
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
                            Building.TOWER, Building.STRONG_TOWER, Building.CAPITAL -> enemyForts++
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
            score += 14.0 * myHexes
            score += incomeScore
            score += 0.15 * min(treasury, 200)
            score += 10.0 * myVeins + 18.0 * myVeinsWithMine + 6.0 * myFertile
            score += buildingScore
            if (state.config.rules.fogOfWar) score += 6.0 * myWatchtowers
            score -= 4.0 * enemyVeins
            if (state.config.rules.navalEnabled) {
                // Ports are gateway assets (supply + boat yard), but two is plenty.
                score += 6.0 * min(myPorts, 2)
                // Enemy boats are threats worth sinking (+4 per kill via this term).
                val enemyBoats = state.units.values.count {
                    it.owner != me && Rules.isNaval(it.type) &&
                        (visible == null || it.hex in visible)
                }
                score -= 4.0 * enemyBoats
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
                if (threatened) score -= 30.0
            }
        }

        // Slicing pays: enemy tiles cut off from their capital are dying assets.
        // Not just Hard's trick — it is a core mechanic the Academy teaches in
        // mission 5, and under range-bound movement the cut is the main answer
        // to a cheap swarm, so Normal must see it too (Easy stays blind).
        if (difficulty != Difficulty.EASY) {
            score += 8.0 * enemyStarving
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
            score -= 6.0 * invaders
        }

        if (difficulty == Difficulty.HARD) {
            // Retake awareness: undefended fresh borders are a liability.
            score -= 1.5 * exposedBorderHexes(state, me, visible)
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

    private fun adjacentOwned(state: GameState, hex: com.msa.fightandconquer.core.hex.Hex, me: PlayerId): Int {
        var count = 0
        com.msa.fightandconquer.core.hex.HexMath.forEachNeighbor(hex) { n ->
            val t = state.tiles[n]
            if (t != null && t.owner == me && !t.starving && t.flora == null) count++
        }
        return count
    }

    private fun adjacentShoals(state: GameState, hex: com.msa.fightandconquer.core.hex.Hex): Int {
        var count = 0
        com.msa.fightandconquer.core.hex.HexMath.forEachNeighbor(hex) { n ->
            val t = state.tiles[n]
            if (t != null && t.terrain == com.msa.fightandconquer.core.model.Terrain.SEA &&
                t.deposit == Deposit.FISH_SHOAL
            ) {
                count++
            }
        }
        return count
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
                }
            }
            if (threat > Rules.defenseOf(state, hex)) exposed++
        }
        return exposed
    }
}
