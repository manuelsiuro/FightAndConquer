package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.UnitType

/**
 * Marches idle rear soldiers toward the front — Antiyoy's third phase
 * (tactical moves → spending → relocate the AFK), and the piece a one-ply
 * argmax structurally cannot provide: a step that captures nothing scores
 * nothing, so without this policy a unit raised behind the line stands there
 * forever while the border starves for strength.
 *
 * Consulted only after the argmax settled on EndTurn: anything that captures,
 * defends, or builds outranks a march. Termination is safe twice over — every
 * move marks the unit spent, and only strictly-closer steps are taken.
 */
internal object RepositionPolicy {

    /** How many laggards get a marching order per consultation (perf bound). */
    private const val MAX_MARCHERS = 8

    fun action(state: GameState, context: StrategicContext): GameAction? {
        if (context.frontHexes.isEmpty()) return null
        val me = state.currentPlayer
        val capital = state.player(me).capital
        val capitalThreat = Tiers.capitalThreat(state, me)

        // A unit standing on or beside a hex the enemy currently out-attacks is
        // the garrison, not a laggard.
        val holdGround = HashSet(context.defenseGaps)
        for (gap in context.defenseGaps) {
            HexMath.forEachNeighbor(gap) { n -> holdGround.add(n) }
        }

        val laggards = state.units.values
            .filter { u ->
                u.owner == me && !u.spent && u.type == UnitType.SOLDIER &&
                    (context.distanceToFront[u.hex] ?: Int.MAX_VALUE) > 1 &&
                    u.hex !in holdGround &&
                    // The throne guard stays home while an axe is in range.
                    !(capitalThreat > 0 && capital != null && HexMath.distance(u.hex, capital) <= 1)
            }
            .sortedWith(
                compareByDescending<com.msa.fightandconquer.core.model.GameUnit> {
                    context.distanceToFront[it.hex] ?: Int.MAX_VALUE
                }.thenBy { it.id.value },
            )
            .take(MAX_MARCHERS)

        for (unit in laggards) {
            val here = context.distanceToFront[unit.hex] ?: Int.MAX_VALUE
            val step = Rules.reachable(state, unit.id).moveTargets
                .filter { (context.distanceToFront[it] ?: Int.MAX_VALUE) < here }
                .minWithOrNull(
                    compareBy({ context.distanceToFront[it] ?: Int.MAX_VALUE }, { it.packed }),
                )
            if (step != null) return GameAction.MoveUnit(unit.id, step)
        }
        return null
    }
}
