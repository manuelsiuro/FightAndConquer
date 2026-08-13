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

    /** [Rules.effectiveRules] for builder-side callers (TurnPipeline has no GameState). */
    fun effectiveRules(id: PlayerId): com.msa.fightandconquer.core.model.RuleConstants =
        com.msa.fightandconquer.core.model.CivModifiers.effective(rules, player(id).civ)

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

    /** Removes a unit; combat kills and disbands leave no gravestone, starvation/bankruptcy do. */
    fun killUnit(unitId: UnitId, cause: DeathCause) {
        val unit = units.remove(unitId) ?: return
        updateTile(unit.hex) { tile ->
            // No gravestones at sea — the dead sink. Disbanded units just march home.
            val grave = cause != DeathCause.KILLED && cause != DeathCause.DISBANDED &&
                tile.terrain == com.msa.fightandconquer.core.model.Terrain.LAND
            tile.copy(unit = null, flora = if (grave) Flora.Gravestone(turnNumber) else tile.flora)
        }
        events.add(GameEvent.UnitDied(unitId, unit.hex, cause))
    }

    /**
     * Removes the non-capital building at [hex] and emits [GameEvent.BuildingDestroyed].
     * A BRIDGE collapses back into open neutral water (open sea is never owned).
     * Shared by bombardment and demolition; the caller recomputes starving —
     * removing a PORT or BRIDGE can cut a region off on the spot.
     */
    fun razeBuilding(hex: Hex) {
        val building = tiles.getValue(hex).building ?: return
        val bridge = building == Building.BRIDGE
        updateTile(hex) {
            it.copy(
                building = null,
                owner = if (bridge) null else it.owner,
                starving = if (bridge) false else it.starving,
                graceTurns = if (bridge) 0 else it.graceTurns,
                bridgeOrientation = null,
            )
        }
        events.add(GameEvent.BuildingDestroyed(hex, building))
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
            Building.PORT, Building.FISHERY,
            -> events.add(GameEvent.BuildingDestroyed(hex, tile.building))
            // A bridge outlives its conquerors — capturing the span keeps it.
            Building.BRIDGE -> {}
            null -> {}
        }
        val keepBridge = tile.building == Building.BRIDGE
        updateTile(hex) {
            it.copy(
                owner = attacker,
                building = if (keepBridge) Building.BRIDGE else null,
                starving = false,
                // Landing stores never change hands — the caller re-stamps them
                // for a fresh beachhead (disembark / grace-region expansion).
                graceTurns = 0,
            )
        }
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
        // Capitals stand on land only — an owned bridge hex can't host one.
        val remaining = tiles.entries
            .filter {
                it.value.owner == victim && it.key != hex &&
                    it.value.terrain == com.msa.fightandconquer.core.model.Terrain.LAND
            }
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

    /**
     * Re-derives the starving flag for every owned tile. Fed territory is the
     * capital's region PLUS any own region fed by an own OVERSEAS PORT — one on
     * a different landmass than the capital. Overseas colonies live off their
     * harbor (raze it and they starve); a port on the capital's own landmass
     * deliberately feeds nothing extra, or slicing would stop working on land.
     */
    fun recomputeStarving() {
        val fedByPlayer = players.associate { p ->
            p.id to run {
                if (p.eliminated) return@run emptySet<Hex>()
                val fed = HashSet<Hex>()
                val capital = p.capital
                if (capital != null && tiles[capital]?.owner == p.id) {
                    fed += HexMath.floodFill(capital) { tiles[it]?.owner == p.id }
                }
                val homeland = capital?.let { c ->
                    HexMath.floodFill(c) {
                        tiles[it]?.terrain == com.msa.fightandconquer.core.model.Terrain.LAND
                    }
                } ?: emptySet()
                for ((hex, tile) in tiles) {
                    if (tile.owner == p.id && tile.building == Building.PORT &&
                        hex !in fed && hex !in homeland
                    ) {
                        fed += HexMath.floodFill(hex) { tiles[it]?.owner == p.id }
                    }
                }
                fed
            }
        }
        for ((hex, tile) in tiles) {
            val owner = tile.owner ?: continue
            val shouldStarve = hex !in fedByPlayer.getValue(owner)
            // A normally fed hex needs no landing stores — reconnection (or an
            // expedition port) ends the beachhead and its grace clock for good.
            val grace = if (shouldStarve) tile.graceTurns else 0
            if (tile.starving != shouldStarve || tile.graceTurns != grace) {
                tiles[hex] = tile.copy(starving = shouldStarve, graceTurns = grace)
            }
        }
    }

    /** Eliminates players with no LAND hexes; declares victory when one remains. */
    fun checkElimination() {
        for (p in players.toList()) {
            if (!p.eliminated && tiles.values.none {
                    it.owner == p.id && it.terrain == com.msa.fightandconquer.core.model.Terrain.LAND
                }
            ) {
                // Any surviving units of an eliminated player die (their tiles are gone,
                // so this is normally a no-op safety net).
                units.values.filter { it.owner == p.id }.forEach { killUnit(it.id, DeathCause.STARVED) }
                // Owned sea hexes (bridges) outlive their builder as neutral structures.
                for ((hex, tile) in tiles.entries.toList()) {
                    if (tile.owner == p.id) {
                        tiles[hex] = tile.copy(owner = null, starving = false, graceTurns = 0)
                    }
                }
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
