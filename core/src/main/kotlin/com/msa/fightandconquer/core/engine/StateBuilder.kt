package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.GameUnit
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitId

/**
 * Mutable scratch space for applying one action. The reducer builds one of these,
 * mutates it through the shared operations below, and freezes it back into an
 * immutable GameState. Never escapes the engine package.
 */
internal class StateBuilder(private val base: GameState) {
    val rules = base.config.rules
    val tiles: HashMap<Hex, com.msa.fightandconquer.core.model.Tile> = HashMap(base.tiles)
    val units: HashMap<UnitId, GameUnit> = HashMap(base.units)
    val players: MutableList<com.msa.fightandconquer.core.model.PlayerState> = base.players.toMutableList()
    var currentPlayer: PlayerId = base.currentPlayer
    var turnNumber: Int = base.turnNumber
    var rngState: Long = base.rngState
    var phase: GamePhase = base.phase
    var nextUnitId: Int = base.nextUnitId
    var diplomacy: com.msa.fightandconquer.core.model.DiplomacyState = base.diplomacy
    val events = ArrayList<GameEvent>()

    fun player(id: PlayerId) = players[id.value]

    fun updatePlayer(id: PlayerId, transform: (com.msa.fightandconquer.core.model.PlayerState) -> com.msa.fightandconquer.core.model.PlayerState) {
        players[id.value] = transform(players[id.value])
    }

    fun updateTile(hex: Hex, transform: (com.msa.fightandconquer.core.model.Tile) -> com.msa.fightandconquer.core.model.Tile) {
        tiles[hex] = transform(tiles.getValue(hex))
    }

    fun rollPercent(): Int {
        rngState = Rng.advance(rngState)
        return Rng.nextInt(rngState, 100)
    }

    fun rollIndex(bound: Int): Int {
        rngState = Rng.advance(rngState)
        return Rng.nextInt(rngState, bound)
    }

    // ----- shared operations -----

    fun spawnUnit(
        owner: PlayerId,
        tier: Int,
        hex: Hex,
        spent: Boolean,
        type: com.msa.fightandconquer.core.model.UnitType = com.msa.fightandconquer.core.model.UnitType.SOLDIER,
    ): GameUnit {
        val unit = GameUnit(UnitId(nextUnitId++), owner, tier, hex, spent, type)
        units[unit.id] = unit
        updateTile(hex) { it.copy(unit = unit.id) }
        return unit
    }

    /** Removes a unit; combat kills leave no gravestone (the hex is being occupied), starvation/bankruptcy do. */
    fun killUnit(unitId: UnitId, cause: DeathCause) {
        val unit = units.remove(unitId) ?: return
        updateTile(unit.hex) { tile ->
            val flora = if (cause == DeathCause.KILLED) tile.flora else Flora.Gravestone(turnNumber)
            tile.copy(unit = null, flora = flora)
        }
        events.add(GameEvent.UnitDied(unitId, unit.hex, cause))
    }

    /** Clears flora under an arriving/placed unit. Returns true if a tree was cleared (spends the unit). */
    fun clearFloraAt(hex: Hex, beneficiary: PlayerId): Boolean {
        val tile = tiles.getValue(hex)
        return when (tile.flora) {
            is Flora.Tree -> {
                updateTile(hex) { it.copy(flora = null) }
                updatePlayer(beneficiary) { it.copy(treasury = it.treasury + rules.treeClearBonus) }
                events.add(GameEvent.TreeCleared(hex, rules.treeClearBonus))
                true
            }
            is Flora.Gravestone -> {
                updateTile(hex) { it.copy(flora = null) }
                events.add(GameEvent.GravestoneTrampled(hex))
                false
            }
            null -> false
        }
    }

    /**
     * Transfers [hex] to [attacker]: kills the defender, destroys buildings
     * (capital → loot + relocation), then recomputes starvation and elimination.
     * The arriving unit (if any) is placed by the caller AFTER this returns.
     */
    fun captureHex(attacker: PlayerId, hex: Hex) {
        val tile = tiles.getValue(hex)
        val victim = tile.owner
        // Aggression against a pact partner breaks the pact first (penalty transfer)
        // — this single site covers both move-capture and buy-capture.
        if (victim != null && victim != attacker) {
            diplomacy.pactBetween(attacker, victim)?.let { breakPact(attacker, victim) }
        }
        tile.unit?.let { killUnit(it, DeathCause.KILLED) }
        when (tile.building) {
            Building.CAPITAL -> captureCapital(attacker, victim!!, hex)
            Building.FARM, Building.TOWER, Building.STRONG_TOWER,
            Building.MINE, Building.MARKET, Building.LUMBER_CAMP, Building.WATCHTOWER,
            -> events.add(GameEvent.BuildingDestroyed(hex, tile.building))
            null -> {}
        }
        updateTile(hex) { it.copy(owner = attacker, building = null, starving = false) }
        events.add(GameEvent.HexCaptured(hex, attacker, victim))
        recomputeStarving()
        checkElimination()
    }

    private fun captureCapital(attacker: PlayerId, victim: PlayerId, hex: Hex) {
        val victimState = player(victim)
        val loot = victimState.treasury * rules.capitalLootPercent / 100
        updatePlayer(victim) { it.copy(treasury = it.treasury - loot) }
        updatePlayer(attacker) { it.copy(treasury = it.treasury + loot) }

        // Relocate to the victim's largest remaining region (this hex is lost).
        val remaining = tiles.entries
            .filter { it.value.owner == victim && it.key != hex }
            .map { it.key }
            .toSet()
        if (remaining.isEmpty()) {
            updatePlayer(victim) { it.copy(capital = null) }
            events.add(GameEvent.CapitalMoved(victim, hex, hex, loot))
            return
        }
        val regions = HexMath.connectedComponents(remaining)
        val largest = regions.maxWith(
            compareBy({ it.size }, { -(it.minOf { h -> h.packed }) }),
        )
        val preferred = largest.filter {
            val t = tiles.getValue(it)
            t.unit == null && t.building == null && t.flora == null
        }.ifEmpty {
            largest.filter { tiles.getValue(it).building == null }
        }.ifEmpty { largest.toList() }
        val sorted = preferred.sortedBy { it.packed }
        val newCapital = sorted[rollIndex(sorted.size)]
        updateTile(newCapital) { it.copy(building = Building.CAPITAL, flora = null) }
        updatePlayer(victim) { it.copy(capital = newCapital) }
        events.add(GameEvent.CapitalMoved(victim, hex, newCapital, loot))
    }

    // ----- diplomacy -----

    /** Canonical write: keeps every diplomacy list sorted for byte-stable JSON. */
    fun setDiplomacy(
        pacts: List<com.msa.fightandconquer.core.model.Pact> = diplomacy.pacts,
        proposals: List<com.msa.fightandconquer.core.model.PactProposal> = diplomacy.proposals,
        lastProposalRounds: List<com.msa.fightandconquer.core.model.PairRound> = diplomacy.lastProposalRounds,
        lastTributeRounds: List<com.msa.fightandconquer.core.model.PairRound> = diplomacy.lastTributeRounds,
        pactBreaks: List<Int> = diplomacy.pactBreaks,
    ) {
        diplomacy = com.msa.fightandconquer.core.model.DiplomacyState(
            pacts = pacts.sortedWith(compareBy({ it.a.value }, { it.b.value })),
            proposals = proposals.sortedWith(compareBy({ it.from.value }, { it.to.value })),
            lastProposalRounds = lastProposalRounds.sortedWith(compareBy({ it.a.value }, { it.b.value })),
            lastTributeRounds = lastTributeRounds.sortedWith(compareBy({ it.a.value }, { it.b.value })),
            pactBreaks = pactBreaks,
        )
    }

    /** Removes the pact, transfers the penalty to the victim, counts the betrayal. */
    fun breakPact(breaker: PlayerId, victim: PlayerId) {
        val pact = diplomacy.pactBetween(breaker, victim) ?: return
        val penalty = player(breaker).treasury * rules.pactBreakPenaltyPercent / 100
        updatePlayer(breaker) { it.copy(treasury = it.treasury - penalty) }
        updatePlayer(victim) { it.copy(treasury = it.treasury + penalty) }
        val breaks = MutableList(maxOf(diplomacy.pactBreaks.size, players.size)) {
            diplomacy.pactBreaks.getOrElse(it) { 0 }
        }
        breaks[breaker.value]++
        setDiplomacy(pacts = diplomacy.pacts - pact, pactBreaks = breaks)
        events.add(GameEvent.PactBroken(breaker, victim, penalty))
    }

    /** Drops pacts/proposals involving eliminated players (no events — housekeeping). */
    fun pruneDiplomacy() {
        fun alive(p: PlayerId) = !players[p.value].eliminated
        val pacts = diplomacy.pacts.filter { alive(it.a) && alive(it.b) }
        val proposals = diplomacy.proposals.filter { alive(it.from) && alive(it.to) }
        if (pacts.size != diplomacy.pacts.size || proposals.size != diplomacy.proposals.size) {
            setDiplomacy(pacts = pacts, proposals = proposals)
        }
    }

    /**
     * Fog of war: merges [player]'s current vision into their monotonic discovered set.
     * Refreshing only the acting player is sufficient — vision sources are exclusively
     * own assets, so an opponent's action can only shrink (never grow) another
     * player's visible set. Pure (no RNG), so replays and saves reproduce it exactly.
     */
    fun refreshDiscovered(player: PlayerId) {
        val visible = Rules.visibleHexesFrom(tiles, units.values, rules, player)
        updatePlayer(player) { p ->
            if (p.discovered.containsAll(visible)) p
            else p.copy(discovered = Rules.sortedDiscovered(p.discovered + visible))
        }
    }

    /** Re-derives the starving flag for every owned tile (disconnected from its owner's capital). */
    fun recomputeStarving() {
        val connectedByPlayer = players.associate { p ->
            p.id to run {
                val capital = p.capital
                if (p.eliminated || capital == null || tiles[capital]?.owner != p.id) {
                    emptySet()
                } else {
                    HexMath.floodFill(capital) { tiles[it]?.owner == p.id }
                }
            }
        }
        for ((hex, tile) in tiles) {
            val owner = tile.owner ?: continue
            val shouldStarve = hex !in connectedByPlayer.getValue(owner)
            if (tile.starving != shouldStarve) {
                tiles[hex] = tile.copy(starving = shouldStarve)
            }
        }
    }

    /** Eliminates players with no hexes; declares victory when one remains. */
    fun checkElimination() {
        for (p in players.toList()) {
            if (!p.eliminated && tiles.values.none { it.owner == p.id }) {
                // Any surviving units of an eliminated player die (their tiles are gone,
                // so this is normally a no-op safety net).
                units.values.filter { it.owner == p.id }.forEach { killUnit(it.id, DeathCause.STARVED) }
                updatePlayer(p.id) { it.copy(eliminated = true, capital = null) }
                events.add(GameEvent.PlayerEliminated(p.id))
            }
        }
        pruneDiplomacy()
        val alive = players.filter { !it.eliminated }
        if (alive.size == 1 && phase is GamePhase.Playing) {
            phase = GamePhase.Finished(alive.single().id)
            events.add(GameEvent.GameOver(alive.single().id))
        }
    }

    fun build(): ReduceResult = ReduceResult(
        GameState(
            config = base.config,
            tiles = tiles,
            units = units,
            players = players.toList(),
            currentPlayer = currentPlayer,
            turnNumber = turnNumber,
            rngState = rngState,
            phase = phase,
            nextUnitId = nextUnitId,
            diplomacy = diplomacy,
        ),
        events,
    )
}

data class ReduceResult(val state: GameState, val events: List<GameEvent>)
