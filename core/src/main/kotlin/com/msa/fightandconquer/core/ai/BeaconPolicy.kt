package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameState

/**
 * Beacon lighting as a threshold policy — the single owner of "spend gold on
 * night safety" (never an argmax candidate: a beacon changes nothing the turn
 * it is lit unless a monster already stands next door, so its payoff is turns
 * away and invisible to a one-ply comparison — the ResearchPolicy structural
 * argument, verbatim). Stateless, deterministic, RNG-free; consulted after the
 * muster halls (war prerequisites outrank comfort) and before the naval ladder.
 *
 * Demand-driven: fires only with night on the doorstep (or overhead), on the
 * standing defense building whose lit radius would shelter the most assets —
 * units about to be stalked and the buildings/capital that stop earning under
 * a squatter. EASY stays a rookie and blunders through the dark (the Evaluator
 * night-threat precedent).
 */
internal object BeaconPolicy {

    /** Fire this many rounds before nightfall — the toast_night_approaching window. */
    private const val APPROACH_ROUNDS = 2

    /** A beacon must shelter at least this many assets to be worth the gold. */
    private const val MIN_SHELTERED = 2

    fun action(state: GameState, difficulty: Difficulty): GameAction? {
        val rules = state.config.rules
        if (!rules.dayNightEnabled || difficulty == Difficulty.EASY) return null
        val nightSoon = Rules.isNight(state) ||
            (Rules.roundsUntilNight(state.turnNumber, rules) ?: Int.MAX_VALUE) <= APPROACH_ROUNDS
        if (!nightSoon) return null

        val me = state.currentPlayer
        val eff = Rules.effectiveRules(state, me)
        // Keep the border-tower reflex funded: the greedy loop's towers answer
        // players, the beacon only answers monsters.
        if (state.player(me).treasury < eff.beaconCost + eff.towerCost) return null

        val lit = Rules.litHexes(state)
        var best: com.msa.fightandconquer.core.hex.Hex? = null
        var bestSheltered = MIN_SHELTERED - 1
        val candidates = state.tiles.entries
            .filter { (_, tile) ->
                tile.owner == me && !tile.beacon &&
                    tile.building?.let { Rules.beaconRadiusOf(it) } != null
            }
            .sortedBy { it.key.packed }
        for ((hex, tile) in candidates) {
            val radius = Rules.beaconRadiusOf(tile.building!!)!!
            var sheltered = 0
            for (h in HexMath.range(hex, radius)) {
                if (h in lit) continue // already someone else's light
                val t = state.tiles[h] ?: continue
                if (t.unit != null && state.units[t.unit]?.owner == me) sheltered++
                if (t.owner == me) {
                    when (t.building) {
                        Building.CAPITAL -> sheltered += 3
                        Building.FARM, Building.MINE, Building.MARKET, Building.LUMBER_CAMP,
                        Building.PORT, Building.FISHERY, Building.BANK,
                        -> sheltered++
                        else -> {}
                    }
                }
            }
            // Strictly-greater keeps the packed-smallest winner on ties.
            if (sheltered > bestSheltered) {
                best = hex
                bestSheltered = sheltered
            }
        }
        return best?.let { GameAction.UpgradeBuilding(it) }
    }
}
