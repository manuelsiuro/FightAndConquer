package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId

/**
 * Deterministic diplomacy attitude, kept OUT of the greedy argmax on purpose:
 * a pact's value is a multi-turn expectation the one-ply evaluator cannot see,
 * and scoring accept/decline by state delta is exactly how propose/decline
 * oscillation starts. Pure threshold rules over GameState instead — RNG-free,
 * every iteration ordered by PlayerId, self-quenching purely via state (the
 * reducer records proposals/cooldowns), so the 500-action cap can't be hit.
 *
 * Threshold design: propose at >= 1.1x their power, accept down to 0.9x, betray
 * only beyond 2.0x — the wide hysteresis bands are what prevent flip-flopping.
 */
object DiplomacyPolicy {

    /** Answer the pending proposal with the lowest proposer id, if any. */
    fun mandatoryResponse(state: GameState, difficulty: Difficulty): GameAction? {
        val me = state.currentPlayer
        val proposal = state.diplomacy.proposals
            .filter { it.to == me }
            .minByOrNull { it.from.value }
            ?: return null
        val accept = when (difficulty) {
            // Easy always signs — predictable and exploitable, which is the point.
            Difficulty.EASY -> true
            Difficulty.NORMAL -> shouldAccept(state, me, proposal.from)
            Difficulty.HARD ->
                shouldAccept(state, me, proposal.from) &&
                    !isPrey(state, me, proposal.from) &&
                    aliveCount(state) > 2 // duel endgame: peace is pure delay
        }
        return GameAction.RespondPact(proposal.from, accept)
    }

    /** At most one ProposePact / SendTribute per call; Easy never initiates. */
    fun initiative(state: GameState, difficulty: Difficulty): GameAction? {
        if (difficulty == Difficulty.EASY) return null
        val me = state.currentPlayer
        val rules = state.config.rules
        val d = state.diplomacy
        val myPower = powerOf(state, me, me)
        val neighbors = adjacentEnemies(state, me)
        if (neighbors.size >= 2) {
            val target = neighbors
                .filter { enemy ->
                    powerOf(state, me, enemy) * 10 >= myPower * 11 &&
                        d.pactBetween(me, enemy) == null &&
                        d.proposalBetween(me, enemy) == null &&
                        d.proposalBetween(enemy, me) == null &&
                        (d.lastProposalRound(me, enemy) ?: Int.MIN_VALUE) +
                        rules.pactProposalCooldownRounds <= state.turnNumber
                }
                .maxWithOrNull(compareBy({ powerOf(state, me, it) }, { -it.value }))
            if (target != null) {
                val duration = (rules.pactMinDurationRounds + rules.pactMaxDurationRounds) / 2
                return GameAction.ProposePact(target, duration)
            }
        }
        // Tribute: appease a much stronger neighbor when the pact route is closed.
        val treasury = state.player(me).treasury
        if (treasury >= 45) {
            val bully = neighbors
                .filter { enemy ->
                    powerOf(state, me, enemy) * 2 >= myPower * 3 &&
                        d.pactBetween(me, enemy) == null &&
                        (d.lastProposalRound(me, enemy) ?: Int.MIN_VALUE) +
                        rules.pactProposalCooldownRounds > state.turnNumber &&
                        (d.lastTributeRound(me, enemy) ?: Int.MIN_VALUE) + TRIBUTE_COOLDOWN_ROUNDS <=
                        state.turnNumber
                }
                .maxWithOrNull(compareBy({ powerOf(state, me, it) }, { -it.value }))
            if (bully != null) {
                return GameAction.SendTribute(bully, minOf(15, treasury / 4))
            }
        }
        return null
    }

    /**
     * Hard only: partners worth betraying — crush a pacted rival once dominance is
     * overwhelming, so a pacted duel endgame can't deadlock forever. The 2.0x band
     * sits far above the 0.9x accept threshold: no accept/betray oscillation.
     */
    fun betrayalTargets(state: GameState, me: PlayerId): Set<PlayerId> {
        val d = state.diplomacy
        if (d.pacts.isEmpty()) return emptySet()
        val myPower = powerOf(state, me, me)
        val enemyLand = state.tiles.values.count { it.owner != null && it.owner != me }
        val out = LinkedHashSet<PlayerId>()
        for (pact in d.pacts) {
            val partner = when (me) {
                pact.a -> pact.b
                pact.b -> pact.a
                else -> continue
            }
            if (pact.expiresAtRound - state.turnNumber < 3) continue // just wait it out
            val dominant = myPower >= 2 * powerOf(state, me, partner)
            val lastObstacle = aliveCount(state) == 2 ||
                state.tiles.values.count { it.owner == partner } * 2 >= enemyLand
            if (dominant && lastObstacle) out.add(partner)
        }
        return out
    }

    // ----- shared assessments -----

    private fun shouldAccept(state: GameState, me: PlayerId, proposer: PlayerId): Boolean =
        powerOf(state, me, proposer) * 10 >= powerOf(state, me, me) * 9 ||
            adjacentEnemies(state, me).size >= 2

    private fun isPrey(state: GameState, me: PlayerId, other: PlayerId): Boolean =
        powerOf(state, me, me) * 2 >= powerOf(state, me, other) * 3 &&
            other in adjacentEnemies(state, me)

    private fun aliveCount(state: GameState): Int = state.players.count { !it.eliminated }

    /**
     * Fog-honoring military-economic weight of [target] as seen by [viewer].
     * Integer-only. An opponent with zero visible assets scores 0 — unknown
     * players are treated as non-threats (decline them, never court them).
     */
    internal fun powerOf(state: GameState, viewer: PlayerId, target: PlayerId): Int {
        val visible: Set<Hex>? =
            if (state.config.rules.fogOfWar && viewer != target) Rules.visibleHexes(state, viewer) else null
        var power = 0
        for ((hex, tile) in state.tiles) {
            if (tile.owner != target) continue
            if (visible != null && hex !in visible) continue
            if (!tile.starving) power += 2
            when (tile.building) {
                Building.TOWER -> power += 4
                Building.STRONG_TOWER -> power += 8
                else -> {}
            }
            state.unitAt(hex)?.let { power += 5 * Rules.strengthOf(it, state.config.rules) }
        }
        return power
    }

    /** Living opponents owning land adjacent to [me]'s territory, sorted by id. */
    internal fun adjacentEnemies(state: GameState, me: PlayerId): List<PlayerId> {
        val found = HashSet<PlayerId>()
        for ((hex, tile) in state.tiles) {
            if (tile.owner != me) continue
            HexMath.forEachNeighbor(hex) { n ->
                val owner = state.tiles[n]?.owner
                if (owner != null && owner != me && !state.player(owner).eliminated) found.add(owner)
            }
        }
        return found.sortedBy { it.value }
    }

    private const val TRIBUTE_COOLDOWN_ROUNDS = 4
}
