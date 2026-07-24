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
            is GameAction.Disembark -> applyDisembark(state, b, action)
            is GameAction.Bombard -> applyBombard(state, b, action)
            is GameAction.ProposePact -> applyProposePact(b, action)
            is GameAction.RespondPact -> applyRespondPact(b, action)
            is GameAction.SendTribute -> applySendTribute(b, action)
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

        if (action.to in reach.embarkTargets) {
            // Board the transport: the land unit leaves the units map entirely and
            // rides as cargo (state, not identity, is authoritative — disembarking
            // spawns a fresh id). The boat keeps its own action.
            val transport = state.unitAt(action.to)!!
            b.updateTile(unit.hex) { it.copy(unit = null) }
            b.units.remove(unit.id)
            b.units[transport.id] = transport.copy(
                cargo = com.msa.fightandconquer.core.model.CargoUnit(unit.tier, unit.type),
            )
            b.events.add(GameEvent.UnitEmbarked(unit.id, transport.id, unit.hex, action.to))
            return
        }

        val isCapture = action.to in reach.captureTargets
        val navalStrike = isCapture &&
            state.tiles.getValue(action.to).terrain == com.msa.fightandconquer.core.model.Terrain.SEA

        // Leave the origin hex.
        b.updateTile(unit.hex) { it.copy(unit = null) }

        if (navalStrike) {
            // Naval combat: sink the defender and take its water. Open sea has no
            // ownership, so nothing is captured — the loser just goes under.
            state.tiles.getValue(action.to).unit?.let { b.killUnit(it, DeathCause.SUNK) }
        } else if (isCapture) {
            b.captureHex(unit.owner, action.to)
        }
        // Arrive (tile ownership already transferred if capturing).
        b.updateTile(action.to) { it.copy(unit = unit.id) }
        b.units[unit.id] = b.units.getValue(unit.id).copy(hex = action.to, spent = true)
        b.events.add(GameEvent.UnitMoved(unit.id, unit.hex, action.to))
        b.clearFloraAt(action.to, unit.owner)
        if (isCapture && !navalStrike) {
            // captureHex already recomputed; arriving may reconnect regions for the attacker.
            b.recomputeStarving()
        }
    }

    private fun applyDisembark(state: GameState, b: StateBuilder, action: GameAction.Disembark) {
        val boat = state.units.getValue(action.boat)
        val cargo = boat.cargo!!
        val destTile = state.tiles.getValue(action.to)
        val isCapture = destTile.owner != boat.owner
        if (isCapture) b.captureHex(boat.owner, action.to)
        b.units[boat.id] = boat.copy(cargo = null, spent = true)
        val landed = b.spawnUnit(boat.owner, cargo.tier, action.to, spent = true, type = cargo.type)
        b.events.add(GameEvent.UnitDisembarked(boat.id, landed, action.to))
        b.clearFloraAt(action.to, boat.owner)
        if (isCapture) b.recomputeStarving()
    }

    private fun applyBombard(state: GameState, b: StateBuilder, action: GameAction.Bombard) {
        val ship = state.units.getValue(action.unit)
        val target = state.tiles.getValue(action.target)
        // A raid, not a conquest: ownership never changes and capitals are immune.
        b.events.add(GameEvent.Bombarded(ship.id, action.target))
        target.unit?.let { b.killUnit(it, DeathCause.KILLED) }
        val building = target.building
        if (building != null && building != com.msa.fightandconquer.core.model.Building.CAPITAL) {
            b.updateTile(action.target) { it.copy(building = null) }
            b.events.add(GameEvent.BuildingDestroyed(action.target, building))
        }
        b.units[ship.id] = ship.copy(spent = true)
        // Destroying a PORT can starve an overseas colony on the spot.
        b.recomputeStarving()
    }

    private fun applyBuyUnit(state: GameState, b: StateBuilder, action: GameAction.BuyUnit) {
        val buyer = state.currentPlayer
        val cost = Rules.unitCostOf(b.rules, action.tier, action.type)
        b.updatePlayer(buyer) { it.copy(treasury = it.treasury - cost) }

        val tile = state.tiles.getValue(action.at)
        when {
            Rules.isNaval(action.type) -> {
                // Launch a fresh boat on open sea next to an own port.
                val unit = b.spawnUnit(buyer, 1, action.at, spent = false, type = action.type)
                b.events.add(GameEvent.UnitSpawned(unit))
            }
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
        // Expedition rule: a new PORT feeds its region the moment it opens.
        if (action.type == com.msa.fightandconquer.core.model.BuildingType.PORT) {
            b.recomputeStarving()
        }
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

    private fun applyProposePact(b: StateBuilder, action: GameAction.ProposePact) {
        val me = b.currentPlayer
        val proposal = com.msa.fightandconquer.core.model.PactProposal(
            from = me,
            to = action.to,
            durationRounds = action.durationRounds,
            proposedAtRound = b.turnNumber,
        )
        val (lo, hi) = if (me.value < action.to.value) me to action.to else action.to to me
        b.setDiplomacy(
            proposals = b.diplomacy.proposals + proposal,
            lastProposalRounds = b.diplomacy.lastProposalRounds
                .filterNot { it.a == lo && it.b == hi } +
                com.msa.fightandconquer.core.model.PairRound(lo, hi, b.turnNumber),
        )
        b.events.add(GameEvent.PactProposed(me, action.to, action.durationRounds))
    }

    private fun applyRespondPact(b: StateBuilder, action: GameAction.RespondPact) {
        val me = b.currentPlayer
        val proposal = b.diplomacy.proposalBetween(action.from, me)!!
        if (action.accept) {
            val (lo, hi) = if (action.from.value < me.value) action.from to me else me to action.from
            val pact = com.msa.fightandconquer.core.model.Pact(
                a = lo,
                b = hi,
                expiresAtRound = b.turnNumber + proposal.durationRounds,
            )
            b.setDiplomacy(
                pacts = b.diplomacy.pacts + pact,
                proposals = b.diplomacy.proposals - proposal,
            )
            b.events.add(GameEvent.PactAccepted(lo, hi, pact.expiresAtRound))
        } else {
            b.setDiplomacy(proposals = b.diplomacy.proposals - proposal)
            b.events.add(GameEvent.PactDeclined(action.from, me))
        }
    }

    private fun applySendTribute(b: StateBuilder, action: GameAction.SendTribute) {
        val me = b.currentPlayer
        b.updatePlayer(me) { it.copy(treasury = it.treasury - action.amount) }
        b.updatePlayer(action.to) { it.copy(treasury = it.treasury + action.amount) }
        b.setDiplomacy(
            lastTributeRounds = b.diplomacy.lastTributeRounds
                .filterNot { it.a == me && it.b == action.to } +
                com.msa.fightandconquer.core.model.PairRound(me, action.to, b.turnNumber),
        )
        b.events.add(GameEvent.TributeSent(me, action.to, action.amount))
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
