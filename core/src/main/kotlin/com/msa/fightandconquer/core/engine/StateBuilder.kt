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

    fun spawnUnit(owner: PlayerId, tier: Int, hex: Hex, spent: Boolean): GameUnit {
        val unit = GameUnit(UnitId(nextUnitId++), owner, tier, hex, spent)
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
        tile.unit?.let { killUnit(it, DeathCause.KILLED) }
        when (tile.building) {
            Building.CAPITAL -> captureCapital(attacker, victim!!, hex)
            Building.FARM, Building.TOWER, Building.STRONG_TOWER ->
                events.add(GameEvent.BuildingDestroyed(hex, tile.building))
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
        ),
        events,
    )
}

data class ReduceResult(val state: GameState, val events: List<GameEvent>)
