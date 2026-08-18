package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.GameUnit
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.UnitType

/**
 * Shared naval steering primitives, extracted from [NavalPolicy] so
 * [FishingPolicy] can sail the same water. Semantics are bit-identical to the
 * originals: same 128-step field cap, same strictly-closer rule, same packed
 * tie-breaks, same HARD hunter-shadow launch refusal.
 */
internal object Sailing {

    /**
     * The sailed-distance field toward [goals]: a multi-source BFS over water
     * (straight-line distance strands boats in local minima behind their own
     * island whenever the route bends around land). Other boats are ignored for
     * the FIELD (they drift); actual moves come from reachability.
     *
     * [ontoGoals] seeds the field AT sailable goals (distance 0 on the goal hex
     * itself) so a boat can be steered ONTO a sea goal — a shoal to park on —
     * instead of alongside land the goals lap.
     */
    fun seaField(
        state: GameState,
        goals: Collection<Hex>,
        ontoGoals: Boolean = false,
    ): Map<Hex, Int> {
        fun sailable(hex: Hex): Boolean {
            val t = state.tiles[hex] ?: return false
            return t.terrain == Terrain.SEA && t.building == null
        }

        val dist = HashMap<Hex, Int>()
        var frontier = ArrayList<Hex>()
        for (goal in goals) {
            if (ontoGoals) {
                if (sailable(goal) && dist.putIfAbsent(goal, 0) == null) frontier.add(goal)
            } else {
                HexMath.forEachNeighbor(goal) { n ->
                    if (sailable(n) && dist.putIfAbsent(n, 0) == null) frontier.add(n)
                }
            }
        }
        var d = 0
        while (frontier.isNotEmpty() && d < 128) {
            d++
            val next = ArrayList<Hex>()
            for (hex in frontier) {
                HexMath.forEachNeighbor(hex) { n ->
                    if (sailable(n) && dist.putIfAbsent(n, d) == null) next.add(n)
                }
            }
            frontier = next
        }
        return dist
    }

    /**
     * Move to the reachable sea hex strictly nearer to [goals] by SAILED
     * distance. Only moves that actually close distance are taken (no
     * tide-pacing loops).
     */
    fun sailToward(
        state: GameState,
        boat: GameUnit,
        goals: Collection<Hex>,
        ontoGoals: Boolean = false,
    ): GameAction? {
        if (goals.isEmpty()) return null
        val targets = Rules.reachable(state, boat.id).moveTargets
        if (targets.isEmpty()) return null

        val dist = seaField(state, goals, ontoGoals)
        val here = dist[boat.hex] ?: Int.MAX_VALUE
        val best = targets.minWith(
            compareBy({ dist[it] ?: Int.MAX_VALUE }, { it.packed }),
        )
        val bestDist = dist[best] ?: Int.MAX_VALUE
        return if (bestDist < here) GameAction.MoveUnit(boat.id, best) else null
    }

    /**
     * Lowest-packed legal launch hex: open sea beside an own working port.
     * HARD only refuses water a visible enemy warship can strike before the
     * hull ever acts (a launch beside a hunter is a donation; the sink-relaunch
     * money pump is how symmetric HARD interdiction deadlocked mirror duels —
     * with no safe water HARD hoards toward the war chest instead). Everyone
     * else launches on the old rule: interdiction is Hard's edge, and a
     * campaign AI on a one-port island must risk the hull or lose on the clock.
     */
    fun launchSpot(state: GameState, difficulty: Difficulty): Hex? {
        val me = state.currentPlayer
        val rules = state.config.rules
        val visibleNow = if (rules.fogOfWar) Rules.visibleHexes(state, me) else null
        val hunters = if (difficulty != Difficulty.HARD) {
            emptyList()
        } else {
            state.units.values.filter {
                it.owner != me && it.type == UnitType.WARSHIP &&
                    (visibleNow == null || it.hex in visibleNow)
            }
        }
        // The hunters' one-turn strike shadow by SAILED distance (islands block;
        // cube distance would blanket whole archipelago coasts and stop all
        // launches). +1 covers the adjacent-capture final step.
        val shadow = HashSet<Hex>()
        for (w in hunters) {
            val range = Rules.moveRangeOf(state, w) + 1
            val dist = HashMap<Hex, Int>()
            dist[w.hex] = 0
            var frontier = listOf(w.hex)
            var d = 0
            while (frontier.isNotEmpty() && d < range) {
                d++
                val next = ArrayList<Hex>()
                for (hex in frontier) {
                    HexMath.forEachNeighbor(hex) { n ->
                        val t = state.tiles[n]
                        if (t != null && t.terrain == Terrain.SEA && t.building == null &&
                            dist.putIfAbsent(n, d) == null
                        ) {
                            next.add(n)
                        }
                    }
                }
                frontier = next
            }
            shadow.addAll(dist.keys)
        }
        val spots = HashSet<Hex>()
        for ((hex, tile) in state.tiles) {
            if (tile.owner != me || tile.building != Building.PORT || tile.starving) continue
            HexMath.forEachNeighbor(hex) { n ->
                val t = state.tiles[n]
                if (t != null && t.terrain == Terrain.SEA && t.unit == null && t.building == null) {
                    spots.add(n)
                }
            }
        }
        return if (difficulty == Difficulty.HARD) {
            spots.filter { it !in shadow }.minByOrNull { it.packed }
        } else {
            spots.minByOrNull { it.packed }
        }
    }
}
