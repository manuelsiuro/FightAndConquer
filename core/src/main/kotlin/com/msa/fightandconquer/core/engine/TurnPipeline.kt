package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.GamePhase

/**
 * Turn-boundary processing. On EndTurn the seat advances to the next living player Q,
 * then Q's turn starts in this exact order (each step emits events):
 *
 * 1. Q's gravestones one full round old become trees.
 * 2. Trees on/adjacent to Q's territory may spread (state RNG).
 * 3. Income and upkeep are applied atomically.
 * 4. Bankruptcy: treasury < 0 -> 0 and ALL of Q's units die.
 * 5. Starvation: units on hexes cut off from Q's capital die.
 * 6. Q's units refresh (spent = false).
 * 7. Elimination / victory check.
 */
internal object TurnPipeline {

    fun endTurn(b: StateBuilder) {
        val fromSeat = b.currentPlayer.value
        var seat = fromSeat
        do {
            seat = (seat + 1) % b.players.size
        } while (b.players[seat].eliminated && seat != fromSeat)
        if (seat <= fromSeat) b.turnNumber++ // wrapped: a full round completed
        b.currentPlayer = b.players[seat].id
        startTurn(b)
    }

    private fun startTurn(b: StateBuilder) {
        val playerId = b.currentPlayer
        if (b.player(playerId).eliminated) return

        expireDiplomacy(b)
        growGravestones(b)
        spreadTrees(b)

        // Income & upkeep — one atomic treasury update.
        val income = incomeIn(b)
        val upkeep = upkeepIn(b)
        b.updatePlayer(playerId) { it.copy(treasury = it.treasury + income - upkeep) }
        b.events.add(GameEvent.TurnStarted(playerId, income, upkeep))

        // Bankruptcy: everything dies.
        if (b.player(playerId).treasury < 0) {
            b.updatePlayer(playerId) { it.copy(treasury = 0) }
            b.events.add(GameEvent.Bankruptcy(playerId))
            b.units.values.filter { it.owner == playerId }.map { it.id }.forEach {
                b.killUnit(it, DeathCause.BANKRUPTCY)
            }
        }

        // Starvation on sliced-off hexes.
        b.recomputeStarving()
        b.units.values.filter { it.owner == playerId && b.tiles.getValue(it.hex).starving }
            .map { it.id }
            .forEach { b.killUnit(it, DeathCause.STARVED) }

        // Refresh.
        for (unit in b.units.values.toList()) {
            if (unit.owner == playerId && unit.spent) b.units[unit.id] = unit.copy(spent = false)
        }

        b.checkElimination()
    }

    /**
     * Step 0: lapse ended pacts and stale proposals (events in canonical sorted
     * order — the lists themselves are already kept sorted). A proposal survives
     * until its target had at least [RuleConstants.pactProposalTtlRounds] full
     * rounds to answer.
     */
    private fun expireDiplomacy(b: StateBuilder) {
        val d = b.diplomacy
        if (d.pacts.isEmpty() && d.proposals.isEmpty()) return
        val endedPacts = d.pacts.filter { it.expiresAtRound <= b.turnNumber }
        val stale = d.proposals.filter {
            b.turnNumber > it.proposedAtRound + b.rules.pactProposalTtlRounds
        }
        if (endedPacts.isEmpty() && stale.isEmpty()) return
        b.setDiplomacy(pacts = d.pacts - endedPacts.toSet(), proposals = d.proposals - stale.toSet())
        endedPacts.forEach { b.events.add(GameEvent.PactExpired(it.a, it.b)) }
        stale.forEach { b.events.add(GameEvent.PactProposalExpired(it.from, it.to)) }
    }

    /** Q's gravestones created at least one full round ago become trees. */
    private fun growGravestones(b: StateBuilder) {
        val playerId = b.currentPlayer
        for ((hex, tile) in b.tiles.entries.toList()) {
            val grave = tile.flora as? Flora.Gravestone ?: continue
            if (tile.owner == playerId && b.turnNumber > grave.createdRound) {
                b.tiles[hex] = tile.copy(flora = Flora.Tree)
                b.events.add(GameEvent.TreeGrown(hex))
            }
        }
    }

    /**
     * Each tree on or adjacent to the current player's territory rolls once to spread
     * to a uniformly-random adjacent empty land hex. Deterministic iteration order
     * (sorted by packed coordinate) keeps replays stable. Trees adjacent to a Lumber
     * Camp (any owner) are managed forest and never spread.
     */
    private fun spreadTrees(b: StateBuilder) {
        val playerId = b.currentPlayer
        val treeHexes = b.tiles.entries
            .filter { (hex, tile) ->
                tile.flora is Flora.Tree &&
                    (tile.owner == playerId ||
                        HexMath.neighbors(hex).any { b.tiles[it]?.owner == playerId }) &&
                    HexMath.neighbors(hex).none {
                        b.tiles[it]?.building == com.msa.fightandconquer.core.model.Building.LUMBER_CAMP
                    }
            }
            .map { it.key }
            .sortedBy { it.packed }

        for (tree in treeHexes) {
            if (b.rollPercent() >= b.rules.treeSpreadPercent) continue
            val candidates = HexMath.neighbors(tree).filter {
                val t = b.tiles[it]
                t != null && t.unit == null && t.building == null && t.flora == null
            }.sortedBy { it.packed }
            if (candidates.isEmpty()) continue
            val target = candidates[b.rollIndex(candidates.size)]
            b.updateTile(target) { it.copy(flora = Flora.Tree) }
            b.events.add(GameEvent.TreeSpread(tree, target))
        }
    }

    private fun incomeIn(b: StateBuilder): Int =
        Rules.incomeFrom(b.tiles, b.rules, b.currentPlayer)

    private fun upkeepIn(b: StateBuilder): Int =
        b.units.values.sumOf { if (it.owner == b.currentPlayer) Rules.unitUpkeepOf(it, b.rules) else 0 }
}
