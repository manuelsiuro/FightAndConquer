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
        var enemyVeins = 0
        var enemyForts = 0
        var buildingScore = 0.0
        for ((hex, tile) in state.tiles) {
            when {
                tile.owner == me -> {
                    if (!tile.starving) {
                        myHexes++
                        when (tile.deposit) {
                            Deposit.GOLD_VEIN ->
                                if (tile.building == Building.MINE) myVeinsWithMine++ else myVeins++
                            Deposit.FERTILE -> myFertile++
                            null -> {}
                        }
                        when (tile.building) {
                            Building.MARKET ->
                                buildingScore += 4.0 + 1.0 * min(adjacentOwned(state, hex, me), 6)
                            Building.LUMBER_CAMP ->
                                buildingScore += 3.0 + 1.5 * min(adjacentOwnTrees(state, hex, me), 4)
                            Building.WATCHTOWER -> myWatchtowers++
                            else -> {}
                        }
                    }
                    // Trees next to an own lumber camp are managed income, not rot.
                    if (tile.flora is Flora.Tree && !nextToOwnCamp(state, hex, me)) myTrees++
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
        }
        score -= 6.0 * myTrees
        score -= 2.0 * enemyHexes

        // Bankruptcy guard: never plan into a projected negative treasury.
        if (treasury + net < 0) {
            score -= if (difficulty == Difficulty.EASY) 100.0 else 1e6
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

        if (difficulty == Difficulty.HARD) {
            // Slicing pays: enemy tiles cut off from their capital are dying assets.
            score += 8.0 * enemyStarving
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

    private fun adjacentOwnTrees(state: GameState, hex: com.msa.fightandconquer.core.hex.Hex, me: PlayerId): Int {
        var count = 0
        com.msa.fightandconquer.core.hex.HexMath.forEachNeighbor(hex) { n ->
            val t = state.tiles[n]
            if (t != null && t.owner == me && t.flora is Flora.Tree) count++
        }
        return count
    }

    private fun nextToOwnCamp(state: GameState, hex: com.msa.fightandconquer.core.hex.Hex, me: PlayerId): Boolean {
        var found = false
        com.msa.fightandconquer.core.hex.HexMath.forEachNeighbor(hex) { n ->
            val t = state.tiles[n]
            if (t != null && t.owner == me && t.building == Building.LUMBER_CAMP) found = true
        }
        return found
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
                        threat = maxOf(threat, Rules.strengthOf(enemy, state.config.rules))
                    }
                }
            }
            if (threat > Rules.defenseOf(state, hex)) exposed++
        }
        return exposed
    }
}
