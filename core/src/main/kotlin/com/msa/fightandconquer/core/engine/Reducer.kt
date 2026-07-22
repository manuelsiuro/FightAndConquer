package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.model.GameState

/**
 * The pure rules engine: reduce(state, action) -> new state + events.
 * Never throws on illegal input — rejected actions return the unchanged state
 * with a single [GameEvent.ActionRejected].
 */
object Reducer {

    fun reduce(state: GameState, action: GameAction): ReduceResult {
        val legality = Legality.check(state, action)
        if (legality is LegalityResult.Rejected) {
            return ReduceResult(
                state,
                listOf(GameEvent.ActionRejected(action, legality.reason, legality.amount)),
            )
        }
        val b = StateBuilder(state)
        when (action) {
            is GameAction.MoveUnit -> applyMove(state, b, action)
            is GameAction.BuyUnit -> applyBuyUnit(state, b, action)
            is GameAction.BuyBuilding -> applyBuyBuilding(state, b, action)
            is GameAction.MergeUnits -> applyMerge(state, b, action)
            GameAction.EndTurn -> TurnPipeline.endTurn(b)
            GameAction.Surrender -> applySurrender(b)
        }
        // Fog of war: after EndTurn/Surrender currentPlayer is already the incoming
        // seat, so this one hook also covers turn-start discovery.
        if (b.rules.fogOfWar) b.refreshDiscovered(b.currentPlayer)
        return b.build()
    }

    private fun applyMove(state: GameState, b: StateBuilder, action: GameAction.MoveUnit) {
        val unit = state.units.getValue(action.unit)
        val reach = Rules.reachable(state, action.unit)
        val isCapture = action.to in reach.captureTargets

        // Leave the origin hex.
        b.updateTile(unit.hex) { it.copy(unit = null) }

        if (isCapture) {
            b.captureHex(unit.owner, action.to)
        }
        // Arrive (tile ownership already transferred if capturing).
        b.updateTile(action.to) { it.copy(unit = unit.id) }
        b.units[unit.id] = unit.copy(hex = action.to, spent = true)
        b.events.add(GameEvent.UnitMoved(unit.id, unit.hex, action.to))
        b.clearFloraAt(action.to, unit.owner)
        if (isCapture) {
            // captureHex already recomputed; arriving may reconnect regions for the attacker.
            b.recomputeStarving()
        }
    }

    private fun applyBuyUnit(state: GameState, b: StateBuilder, action: GameAction.BuyUnit) {
        val buyer = state.currentPlayer
        val cost = Rules.unitCostOf(b.rules, action.tier, action.type)
        b.updatePlayer(buyer) { it.copy(treasury = it.treasury - cost) }

        val tile = state.tiles.getValue(action.at)
        when {
            tile.owner == buyer && tile.unit != null -> {
                // Buy-merge into the same-tier occupant (Legality guarantees SOLDIERs).
                val occupant = state.units.getValue(tile.unit)
                val merged = occupant.copy(tier = occupant.tier + 1)
                b.units[occupant.id] = merged
                val ghost = com.msa.fightandconquer.core.model.GameUnit(
                    com.msa.fightandconquer.core.model.UnitId(b.nextUnitId++), buyer, action.tier, action.at, spent = true,
                )
                b.events.add(GameEvent.UnitSpawned(ghost))
                b.events.add(GameEvent.UnitsMerged(into = merged, consumed = ghost.id))
            }
            tile.owner == buyer -> {
                val unit = b.spawnUnit(buyer, action.tier, action.at, spent = false, type = action.type)
                b.events.add(GameEvent.UnitSpawned(unit))
                val clearedTree = b.clearFloraAt(action.at, buyer)
                if (clearedTree) b.units[unit.id] = b.units.getValue(unit.id).copy(spent = true)
            }
            else -> {
                // Buy directly onto a capturable adjacent hex: arrives spent.
                b.captureHex(buyer, action.at)
                val unit = b.spawnUnit(buyer, action.tier, action.at, spent = true, type = action.type)
                b.events.add(GameEvent.UnitSpawned(unit))
                b.clearFloraAt(action.at, buyer)
                b.recomputeStarving()
            }
        }
    }

    private fun applyBuyBuilding(state: GameState, b: StateBuilder, action: GameAction.BuyBuilding) {
        val buyer = state.currentPlayer
        val cost = Rules.buildingCost(state, buyer, action.type)
        b.updatePlayer(buyer) { it.copy(treasury = it.treasury - cost) }
        b.updateTile(action.at) { it.copy(building = action.type.building) }
        b.events.add(GameEvent.BuildingBuilt(action.at, action.type.building))
    }

    private fun applyMerge(state: GameState, b: StateBuilder, action: GameAction.MergeUnits) {
        val a = state.units.getValue(action.a)
        val target = state.units.getValue(action.b)
        b.updateTile(a.hex) { it.copy(unit = null) }
        b.units.remove(a.id)
        val merged = target.copy(tier = target.tier + 1)
        b.units[target.id] = merged
        b.events.add(GameEvent.UnitMoved(a.id, a.hex, target.hex))
        b.events.add(GameEvent.UnitsMerged(into = merged, consumed = a.id))
    }

    private fun applySurrender(b: StateBuilder) {
        val quitter = b.currentPlayer
        // Territory reverts to neutral; units vanish into gravestones.
        b.units.values.filter { it.owner == quitter }.map { it.id }.forEach {
            b.killUnit(it, DeathCause.STARVED)
        }
        for ((hex, tile) in b.tiles.entries.toList()) {
            if (tile.owner == quitter) {
                b.tiles[hex] = tile.copy(owner = null, building = null, starving = false)
            }
        }
        b.updatePlayer(quitter) { it.copy(eliminated = true, capital = null) }
        b.events.add(GameEvent.PlayerEliminated(quitter))
        b.checkElimination()
        if (b.phase is com.msa.fightandconquer.core.model.GamePhase.Playing) {
            TurnPipeline.endTurn(b)
        }
    }
}
