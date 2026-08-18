package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Legality
import com.msa.fightandconquer.core.engine.LegalityResult
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.UnitType

/**
 * Deterministic fishing ladder, consulted after [NavalPolicy] (invasion is
 * termination-load-bearing and wins treasury contention). The greedy one-ply
 * evaluator can never justify an upkeep hull whose payoff arrives next turn —
 * the same structural blindness that made NavalPolicy necessary — so dories
 * are a threshold decision: buy while open shoals outnumber the fleet, sail
 * each hull onto the nearest open shoal, then leave it parked forever.
 *
 * Fog: shoal POSITIONS are chart knowledge (all sea is pre-discovered and
 * deposits never move); shoal OCCUPANCY is honest — an unseen enemy squatter
 * is treated as absent, and the boat re-targets when its own vision (move <=
 * vision) reveals the hex. Treating it as present would be the omniscience
 * leak AiFogTest exists to catch.
 */
internal object FishingPolicy {

    /** Fleet cap regardless of shoal count — upkeep discipline and turn-time safety. */
    private const val MAX_FISHING_BOATS = 3

    fun action(state: GameState, difficulty: Difficulty = Difficulty.NORMAL): GameAction? {
        val rules = state.config.rules
        if (!rules.navalEnabled || !rules.specialUnitsEnabled) return null
        // Easy skips the fishery in MoveGenerator too — it is the beatable seat.
        if (difficulty == Difficulty.EASY) return null
        val me = state.currentPlayer
        val eff = Rules.effectiveRules(state, me)

        // Charted shoals a boat could stand on (a bridge would block parking).
        val shoals = state.tiles.entries
            .filter { (_, t) ->
                t.terrain == Terrain.SEA && t.deposit == Deposit.FISH_SHOAL && t.building == null
            }
            .map { it.key }
            .sortedBy { it.packed }
        if (shoals.isEmpty()) return null

        val visible = if (rules.fogOfWar) Rules.visibleHexes(state, me) else null
        val open = shoals.filter { hex ->
            val occupant = state.tiles.getValue(hex).unit?.let { state.units[it] }
            when {
                occupant == null -> true
                occupant.owner == me -> false
                else -> visible != null && hex !in visible // unseen squatter = honest absence
            }
        }

        val fleet = state.units.values
            .filter { it.owner == me && it.type == UnitType.FISHING_BOAT }
            .sortedBy { it.id.value }

        // Park or sail every idle hull; a hull with no shoal left to work is
        // pure upkeep — the 50% disband refund beats bleeding forever.
        for (boat in fleet) {
            if (boat.spent) continue
            if (state.tiles.getValue(boat.hex).deposit == Deposit.FISH_SHOAL) continue // parked
            Sailing.sailToward(state, boat, open, ontoGoals = true)?.let { return it }
            if (open.isEmpty()) {
                val disband = GameAction.DisbandUnit(boat.id)
                if (Legality.check(state, disband) is LegalityResult.Ok) return disband
            }
        }

        // Launch a new hull while open shoals outnumber the fleet. Fishing is
        // optional economy: unlike war hulls it never dips into savings, and it
        // never launches into water that cannot reach an open shoal.
        if (fleet.size < minOf(MAX_FISHING_BOATS, open.size) &&
            state.player(me).treasury >= eff.fishingBoatCost + 10 &&
            Rules.incomeOf(state, me) - Rules.upkeepOf(state, me) >= eff.fishingBoatUpkeep
        ) {
            val spot = Sailing.launchSpot(state, difficulty)
            if (spot != null && Sailing.seaField(state, open, ontoGoals = true).containsKey(spot)) {
                val buy = GameAction.BuyUnit(1, spot, UnitType.FISHING_BOAT)
                if (Legality.check(state, buy) is LegalityResult.Ok) return buy
            }
        }
        return null
    }
}
