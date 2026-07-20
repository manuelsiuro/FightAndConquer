package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.Rules
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

        var myHexes = 0
        var myTrees = 0
        var enemyHexes = 0
        var enemyStarving = 0
        for (tile in state.tiles.values) {
            when {
                tile.owner == me -> {
                    if (!tile.starving) myHexes++
                    if (tile.flora is Flora.Tree) myTrees++
                }
                tile.owner != null -> {
                    enemyHexes++
                    if (tile.starving) enemyStarving++
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
        var score = 0.0
        if (difficulty == Difficulty.EASY) {
            score += 12.0 * myHexes
            score += incomeScore
            score += 0.25 * min(treasury, 100)
        } else {
            score += 14.0 * myHexes
            score += incomeScore
            score += 0.15 * min(treasury, 200)
        }
        score -= 6.0 * myTrees
        score -= 2.0 * enemyHexes

        // Bankruptcy guard: never plan into a projected negative treasury.
        if (treasury + net < 0) {
            score -= if (difficulty == Difficulty.EASY) 100.0 else 1e6
        }

        if (difficulty == Difficulty.HARD) {
            // Slicing pays: enemy tiles cut off from their capital are dying assets.
            score += 8.0 * enemyStarving
            // Retake awareness: undefended fresh borders are a liability.
            score -= 1.5 * exposedBorderHexes(state, me)
        }
        return score
    }

    /** Own hexes adjacent to an enemy unit that outguns their defense. */
    private fun exposedBorderHexes(state: GameState, me: PlayerId): Int {
        var exposed = 0
        for ((hex, tile) in state.tiles) {
            if (tile.owner != me) continue
            var threat = 0
            com.msa.fightandconquer.core.hex.HexMath.forEachNeighbor(hex) { n ->
                val enemy = state.unitAt(n)
                if (enemy != null && enemy.owner != me) threat = maxOf(threat, enemy.tier)
            }
            if (threat > Rules.defenseOf(state, hex)) exposed++
        }
        return exposed
    }
}
