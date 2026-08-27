package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Flora

/**
 * Turn-boundary processing. On EndTurn the seat advances to the next living player Q,
 * then Q's turn starts in this exact order (each step emits events):
 *
 * 1. Q's gravestones one full round old become trees.
 * 2. Trees on/adjacent to Q's territory may spread (state RNG).
 * 3. Income and upkeep are applied atomically.
 * 4. Research progresses: +1 point per standing University; a completing tech's
 *    effects are live for the turn Q is about to take.
 * 5. Bankruptcy: treasury < 0 -> 0 and ALL of Q's units die.
 * 6. Starvation: units on hexes cut off from Q's capital die.
 * 7. Q's units refresh (spent = false).
 * 8. Elimination / victory check.
 */
internal object TurnPipeline {

    fun endTurn(b: StateBuilder) {
        val fromSeat = b.currentPlayer.value
        var seat = fromSeat
        do {
            seat = (seat + 1) % b.players.size
        } while (b.players[seat].eliminated && seat != fromSeat)
        if (seat <= fromSeat) {
            b.turnNumber++ // wrapped: a full round completed
            // The one once-per-ROUND hook in the engine: the day-night cycle
            // ticks here, never in the per-seat turn-start steps below.
            if (b.rules.dayNightEnabled) NightPipeline.roundTick(b)
        }
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

        tickResearch(b)

        // Bankruptcy: everything dies.
        if (b.player(playerId).treasury < 0) {
            b.updatePlayer(playerId) { it.copy(treasury = 0) }
            b.events.add(GameEvent.Bankruptcy(playerId))
            b.units.values.filter { it.owner == playerId }.map { it.id }.forEach {
                b.killUnit(it, DeathCause.BANKRUPTCY)
            }
        }

        // Starvation on sliced-off hexes. Marine supply: a unit whose hex touches
        // an own boat is fed by the fleet for as long as it stays alongside.
        // Beachhead grace: a landing region lives off its stores for a few turns.
        b.recomputeStarving()
        val graced = gracedHexes(b, playerId)
        b.units.values.filter {
            it.owner == playerId && b.tiles.getValue(it.hex).starving &&
                it.hex !in graced && !suppliedBySea(b, it.hex, playerId)
        }
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
                t != null && t.terrain == com.msa.fightandconquer.core.model.Terrain.LAND &&
                    t.unit == null && t.building == null && t.flora == null
            }.sortedBy { it.packed }
            if (candidates.isEmpty()) continue
            val target = candidates[b.rollIndex(candidates.size)]
            b.updateTile(target) { it.copy(flora = Flora.Tree) }
            b.events.add(GameEvent.TreeSpread(tree, target))
        }
    }

    /**
     * Beachhead grace (overseas supply rule D): a starving region holding at
     * least one tile with landing stores ([Tile.graceTurns] > 0) skips
     * starvation deaths this turn; every stocked tile in it burns one turn of
     * stores. Regions without stores starve exactly as before, so mainland
     * slicing (which never stamps grace) is untouched.
     */
    private fun gracedHexes(
        b: StateBuilder,
        player: com.msa.fightandconquer.core.model.PlayerId,
    ): Set<com.msa.fightandconquer.core.hex.Hex> {
        val starving = b.tiles.entries
            .filter { it.value.owner == player && it.value.starving }
            .map { it.key }
            .toSet()
        if (starving.isEmpty()) return emptySet()
        val graced = HashSet<com.msa.fightandconquer.core.hex.Hex>()
        for (region in HexMath.connectedComponents(starving)) {
            val stocked = region.filter { b.tiles.getValue(it).graceTurns > 0 }
            if (stocked.isEmpty()) continue
            graced += region
            for (hex in stocked) {
                b.updateTile(hex) { it.copy(graceTurns = it.graceTurns - 1) }
            }
        }
        return graced
    }

    /** Marine supply (rule B): any adjacent own boat feeds a starving unit. */
    private fun suppliedBySea(
        b: StateBuilder,
        hex: com.msa.fightandconquer.core.hex.Hex,
        player: com.msa.fightandconquer.core.model.PlayerId,
    ): Boolean {
        var supplied = false
        HexMath.forEachNeighbor(hex) { n ->
            val u = b.tiles[n]?.unit?.let { b.units[it] }
            if (u != null && u.owner == player && Rules.isNaval(u.type)) supplied = true
        }
        return supplied
    }

    /**
     * Research progress: +1 point per standing, non-starving own University; the
     * tech completes when progress reaches its duration (overshoot discarded —
     * no banking into the next tech). Runs right after income so a razed or
     * cut-off lab has already stopped counting, and before bankruptcy — the
     * research was prepaid, and bankruptcy kills units, not scholarship. With
     * zero labs the slot freezes (no refund) and resumes when one stands again.
     */
    private fun tickResearch(b: StateBuilder) {
        if (!b.rules.researchEnabled) return // hand-authored active state never advances
        val me = b.currentPlayer
        val active = b.player(me).research.active ?: return
        val labs = Rules.workingUniversities(b.tiles, me)
        if (labs == 0) return
        val duration = b.effectiveRules(me).techDurationByTier[active.tech.tier - 1]
        val progress = active.progress + labs
        if (progress >= duration) {
            b.updatePlayer(me) { it.copy(research = it.research.completing(active.tech)) }
            b.events.add(GameEvent.ResearchCompleted(me, active.tech))
        } else {
            b.updatePlayer(me) {
                it.copy(research = it.research.copy(active = active.copy(progress = progress)))
            }
        }
    }

    private fun incomeIn(b: StateBuilder): Int {
        val eff = b.effectiveRules(b.currentPlayer)
        return Rules.scaleIncome(
            Rules.incomeFrom(b.tiles, eff, b.currentPlayer) +
                Rules.boatIncomeFrom(b.tiles, b.units.values, eff, b.currentPlayer),
            eff,
        )
    }

    private fun upkeepIn(b: StateBuilder): Int =
        Rules.upkeepFrom(b.units.values, b.effectiveRules(b.currentPlayer), b.currentPlayer)
}
