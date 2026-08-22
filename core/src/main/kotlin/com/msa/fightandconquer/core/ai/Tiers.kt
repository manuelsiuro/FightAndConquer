package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitType

/**
 * Tier solving for the AI. Research (Smithing/Armory) retired the historical
 * "soldier strength == tier" identity, so every "which tier do I need?"
 * computation solves through [Rules.buyStrength]/[Rules.buyDefense] instead of
 * tier arithmetic. Without research each helper reduces to exactly the old
 * formula (strength(t) == defense(t) == t), which is what makes the conversion
 * commit provably behavior-identical while the flag is off.
 */
internal object Tiers {

    /** Smallest buyable soldier tier that CAPTURES against [defense] (strictly greater), or null. */
    fun cheapestBreaker(state: GameState, me: PlayerId, defense: Int): Int? {
        for (t in 1..state.config.rules.maxTier) {
            if (Rules.buyStrength(state, me, t, UnitType.SOLDIER) > defense) return t
        }
        return null
    }

    /** Smallest soldier tier whose garrison contribution HOLDS against [threat] (>=), or null. */
    fun cheapestGarrison(state: GameState, me: PlayerId, threat: Int): Int? {
        for (t in 1..state.config.rules.maxTier) {
            if (Rules.buyDefense(state, me, t, UnitType.SOLDIER) >= threat) return t
        }
        return null
    }

    /**
     * The strongest enemy land unit able to strike [me]'s capital this action
     * (0 = safe): within move range and beating the capital hex's defense.
     * The one threat definition shared by MoveGenerator's capital guard and
     * ResearchPolicy's yield-to-defense veto.
     */
    fun capitalThreat(state: GameState, me: PlayerId): Int {
        val capital = state.player(me).capital ?: return 0
        if (state.tiles[capital]?.owner != me) return 0
        val capDefense = Rules.defenseOf(state, capital)
        return state.units.values
            .filter { u ->
                u.owner != me && !Rules.isNaval(u.type) &&
                    HexMath.distance(u.hex, capital) <= Rules.moveRangeOf(state, u) &&
                    Rules.strengthOf(state, u) > capDefense
            }
            .maxOfOrNull { Rules.strengthOf(state, it) } ?: 0
    }

    /**
     * Smallest soldier tier that both cracks the weakest beatable coast
     * (strength > [minCoast]) and stands up to the enemy's best visible
     * soldier (garrison defense >= [enemyBest]), or null when no tier does.
     */
    fun marineTier(state: GameState, me: PlayerId, minCoast: Int, enemyBest: Int): Int? {
        for (t in 1..state.config.rules.maxTier) {
            if (Rules.buyStrength(state, me, t, UnitType.SOLDIER) > minCoast &&
                Rules.buyDefense(state, me, t, UnitType.SOLDIER) >= enemyBest
            ) {
                return t
            }
        }
        return null
    }
}
