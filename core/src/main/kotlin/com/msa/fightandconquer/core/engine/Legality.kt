package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.UnitType

sealed interface LegalityResult {
    data object Ok : LegalityResult

    /** [amount] carries the relevant number when the reason has one (cost, defense). */
    data class Rejected(val reason: RejectionReason, val amount: Int? = null) : LegalityResult
}

object Legality {

    fun check(state: GameState, action: GameAction): LegalityResult {
        if (state.phase !is GamePhase.Playing) return reject(RejectionReason.GAME_FINISHED)
        return when (action) {
            is GameAction.MoveUnit -> checkMove(state, action)
            is GameAction.BuyUnit -> checkBuyUnit(state, action)
            is GameAction.BuyBuilding -> checkBuyBuilding(state, action)
            is GameAction.MergeUnits -> checkMerge(state, action)
            is GameAction.RotateBuilding -> checkRotateBuilding(state, action)
            is GameAction.DemolishBuilding -> checkDemolishBuilding(state, action)
            is GameAction.DisbandUnit -> checkDisbandUnit(state, action)
            is GameAction.Disembark -> checkDisembark(state, action)
            is GameAction.Bombard -> checkBombard(state, action)
            is GameAction.ProposePact -> checkProposePact(state, action)
            is GameAction.RespondPact -> checkRespondPact(state, action)
            is GameAction.SendTribute -> checkSendTribute(state, action)
            is GameAction.RunScript -> checkRunScript(state, action)
            GameAction.EndTurn -> LegalityResult.Ok
            GameAction.Surrender -> LegalityResult.Ok
        }
    }

    /**
     * A campaign story beat. Only campaign levels enable it, and it may only place
     * units where an ordinary purchase could have placed them — on an owned, empty,
     * flora-free land hex of the spawn's own player — so no scripted event can
     * violate a board invariant or hand a player ground they never took.
     */
    private fun checkRunScript(state: GameState, action: GameAction.RunScript): LegalityResult {
        if (!state.config.rules.scriptedEventsEnabled) {
            return reject(RejectionReason.SCRIPTED_EVENTS_DISABLED)
        }
        val seats = state.players.indices
        val claimed = HashSet<com.msa.fightandconquer.core.hex.Hex>()
        for (spawn in action.spawns) {
            if (spawn.owner.value !in seats || state.player(spawn.owner).eliminated) {
                return reject(RejectionReason.INVALID_SCRIPT_TARGET)
            }
            val maxTier = if (spawn.type == UnitType.SOLDIER) state.config.rules.maxTier else 1
            if (spawn.tier !in 1..maxTier) return reject(RejectionReason.INVALID_SCRIPT_TARGET)
            val tile = state.tiles[spawn.hex] ?: return reject(RejectionReason.INVALID_SCRIPT_TARGET)
            val naval = Rules.isNaval(spawn.type)
            val onSea = tile.terrain == Terrain.SEA && tile.building != Building.BRIDGE
            // Boats muster at sea, everyone else on their owner's dry, empty ground.
            if (naval != onSea) return reject(RejectionReason.INVALID_SCRIPT_TARGET)
            if (!naval && (tile.owner != spawn.owner || tile.flora != null)) {
                return reject(RejectionReason.INVALID_SCRIPT_TARGET)
            }
            if (tile.unit != null || !claimed.add(spawn.hex)) {
                return reject(RejectionReason.INVALID_SCRIPT_TARGET)
            }
        }
        for (grant in action.grants) {
            if (grant.player.value !in seats || grant.coins < 0) {
                return reject(RejectionReason.INVALID_SCRIPT_TARGET)
            }
        }
        return LegalityResult.Ok
    }

    private fun checkDiplomacyTarget(state: GameState, target: com.msa.fightandconquer.core.model.PlayerId): LegalityResult? {
        if (!state.config.rules.diplomacyEnabled) return reject(RejectionReason.DIPLOMACY_DISABLED)
        if (target == state.currentPlayer || target.value !in state.players.indices ||
            state.player(target).eliminated
        ) {
            return reject(RejectionReason.INVALID_PLAYER)
        }
        return null
    }

    private fun checkProposePact(state: GameState, action: GameAction.ProposePact): LegalityResult {
        checkDiplomacyTarget(state, action.to)?.let { return it }
        val rules = state.config.rules
        if (action.durationRounds !in rules.pactMinDurationRounds..rules.pactMaxDurationRounds) {
            return reject(RejectionReason.INVALID_PACT_DURATION)
        }
        val d = state.diplomacy
        val me = state.currentPlayer
        if (d.pactBetween(me, action.to) != null) return reject(RejectionReason.PACT_ALREADY_ACTIVE)
        if (d.proposalBetween(me, action.to) != null || d.proposalBetween(action.to, me) != null) {
            return reject(RejectionReason.PROPOSAL_PENDING)
        }
        d.lastProposalRound(me, action.to)?.let { last ->
            val readyAt = last + rules.pactProposalCooldownRounds
            if (state.turnNumber < readyAt) {
                return reject(RejectionReason.PROPOSAL_COOLDOWN, readyAt - state.turnNumber)
            }
        }
        return LegalityResult.Ok
    }

    private fun checkRespondPact(state: GameState, action: GameAction.RespondPact): LegalityResult {
        if (!state.config.rules.diplomacyEnabled) return reject(RejectionReason.DIPLOMACY_DISABLED)
        val proposal = state.diplomacy.proposalBetween(action.from, state.currentPlayer)
            ?: return reject(RejectionReason.NO_SUCH_PROPOSAL)
        if (action.from.value !in state.players.indices || state.player(proposal.from).eliminated) {
            return reject(RejectionReason.NO_SUCH_PROPOSAL)
        }
        return LegalityResult.Ok
    }

    private fun checkSendTribute(state: GameState, action: GameAction.SendTribute): LegalityResult {
        checkDiplomacyTarget(state, action.to)?.let { return it }
        if (action.amount < 1) return reject(RejectionReason.INVALID_TRIBUTE_AMOUNT)
        val treasury = state.player(state.currentPlayer).treasury
        if (treasury < action.amount) return reject(RejectionReason.CANNOT_AFFORD, action.amount)
        return LegalityResult.Ok
    }

    private fun checkMove(state: GameState, action: GameAction.MoveUnit): LegalityResult {
        val unit = state.units[action.unit] ?: return reject(RejectionReason.NO_SUCH_UNIT)
        if (unit.owner != state.currentPlayer) return reject(RejectionReason.NOT_YOUR_UNIT)
        if (unit.spent) return reject(RejectionReason.UNIT_ALREADY_ACTED)
        val reach = Rules.reachable(state, action.unit)
        return when (action.to) {
            in reach.moveTargets, in reach.captureTargets, in reach.embarkTargets -> LegalityResult.Ok
            in reach.mergeTargets -> reject(RejectionReason.DESTINATION_HAS_UNIT)
            in reach.fullTransports -> reject(RejectionReason.TRANSPORT_FULL)
            else -> reject(RejectionReason.DESTINATION_UNREACHABLE)
        }
    }

    private fun checkDisembark(state: GameState, action: GameAction.Disembark): LegalityResult {
        val boat = state.units[action.boat] ?: return reject(RejectionReason.NO_SUCH_UNIT)
        if (boat.owner != state.currentPlayer) return reject(RejectionReason.NOT_YOUR_UNIT)
        if (boat.type != UnitType.TRANSPORT) return reject(RejectionReason.NOT_A_TRANSPORT)
        val cargo = boat.cargo ?: return reject(RejectionReason.TRANSPORT_EMPTY)
        if (boat.spent) return reject(RejectionReason.UNIT_ALREADY_ACTED)
        if (HexMath.distance(boat.hex, action.to) != 1) {
            return reject(RejectionReason.DESTINATION_UNREACHABLE)
        }
        val tile = state.tiles[action.to] ?: return reject(RejectionReason.NO_SUCH_HEX)
        if (tile.terrain != Terrain.LAND) return reject(RejectionReason.SEA_IMPASSABLE)
        if (tile.owner == state.currentPlayer) {
            if (tile.unit != null) return reject(RejectionReason.HEX_HAS_UNIT)
            if (tile.building != null) return reject(RejectionReason.HEX_HAS_BUILDING)
            return LegalityResult.Ok
        }
        // Amphibious assault: the cargo captures the beach with its own strength.
        val defense = Rules.defenseOf(state, action.to, cargo.type)
        if (Rules.buyStrength(state, boat.owner, cargo.tier, cargo.type) <= defense) {
            return reject(RejectionReason.DEFENSE_TOO_HIGH, defense)
        }
        return LegalityResult.Ok
    }

    private fun checkBombard(state: GameState, action: GameAction.Bombard): LegalityResult {
        val ship = state.units[action.unit] ?: return reject(RejectionReason.NO_SUCH_UNIT)
        if (ship.owner != state.currentPlayer) return reject(RejectionReason.NOT_YOUR_UNIT)
        if (ship.type != UnitType.WARSHIP) return reject(RejectionReason.NOT_A_WARSHIP)
        if (ship.spent) return reject(RejectionReason.UNIT_ALREADY_ACTED)
        if (HexMath.distance(ship.hex, action.target) != 1) {
            return reject(RejectionReason.DESTINATION_UNREACHABLE)
        }
        val tile = state.tiles[action.target] ?: return reject(RejectionReason.NO_SUCH_HEX)
        if (tile.owner == state.currentPlayer) return reject(RejectionReason.INVALID_BOMBARD_TARGET)
        // Open sea is never owned, so the tile check alone would let a warship
        // sink its own fleet — the unit's owner must be checked too.
        if (state.unitAt(action.target)?.owner == state.currentPlayer) {
            return reject(RejectionReason.INVALID_BOMBARD_TARGET)
        }
        // Something raid-able must be there: a unit, or a destroyable building.
        val hasTarget = tile.unit != null ||
            (tile.building != null && tile.building != Building.CAPITAL)
        if (!hasTarget) return reject(RejectionReason.INVALID_BOMBARD_TARGET)
        val defense = Rules.defenseOf(state, action.target)
        if (Rules.strengthOf(state, ship) <= defense) {
            return reject(RejectionReason.DEFENSE_TOO_HIGH, defense)
        }
        return LegalityResult.Ok
    }

    /** Boats: tier-1 purchases on open sea adjacent to an own working Port. */
    private fun checkBuyNaval(state: GameState, action: GameAction.BuyUnit): LegalityResult {
        val rules = state.config.rules
        if (!rules.navalEnabled) return reject(RejectionReason.NAVAL_DISABLED)
        if (action.tier != 1) return reject(RejectionReason.INVALID_TIER)
        val cost = Rules.unitCostOf(state, state.currentPlayer, 1, action.type)
        if (state.player(state.currentPlayer).treasury < cost) {
            return reject(RejectionReason.CANNOT_AFFORD, cost)
        }
        val tile = state.tiles[action.at] ?: return reject(RejectionReason.NO_SUCH_HEX)
        if (tile.terrain != Terrain.SEA) return reject(RejectionReason.REQUIRES_SEA)
        if (tile.unit != null) return reject(RejectionReason.HEX_HAS_UNIT)
        if (tile.building != null) return reject(RejectionReason.HEX_HAS_BUILDING)
        val nearPort = HexMath.neighbors(action.at).any {
            val t = state.tiles[it]
            t?.owner == state.currentPlayer && t.building == Building.PORT && !t.starving
        }
        if (!nearPort) return reject(RejectionReason.NO_ADJACENT_PORT)
        return LegalityResult.Ok
    }

    private fun checkBuyUnit(state: GameState, action: GameAction.BuyUnit): LegalityResult {
        val rules = state.config.rules
        if (Rules.isNaval(action.type)) return checkBuyNaval(state, action)
        if (action.type != UnitType.SOLDIER) {
            if (!rules.specialUnitsEnabled) return reject(RejectionReason.SPECIAL_UNITS_DISABLED)
            if (action.tier != 1) return reject(RejectionReason.INVALID_TIER)
        }
        if (action.tier !in 1..rules.maxTier) return reject(RejectionReason.INVALID_TIER)
        val cost = Rules.unitCostOf(state, state.currentPlayer, action.tier, action.type)
        val player = state.player(state.currentPlayer)
        if (player.treasury < cost) return reject(RejectionReason.CANNOT_AFFORD, cost)
        val tile = state.tiles[action.at] ?: return reject(RejectionReason.NO_SUCH_HEX)

        if (tile.owner == state.currentPlayer) {
            if (tile.starving) return reject(RejectionReason.HEX_CUT_OFF)
            if (tile.building != null) return reject(RejectionReason.HEX_HAS_BUILDING)
            val occupant = state.unitAt(action.at)
            return when {
                occupant == null -> LegalityResult.Ok
                occupant.type != UnitType.SOLDIER || action.type != UnitType.SOLDIER ->
                    reject(RejectionReason.CANNOT_MERGE_SPECIAL)
                occupant.tier == action.tier && action.tier < rules.maxTier -> LegalityResult.Ok // buy-merge
                else -> reject(RejectionReason.HEX_OCCUPIED_INCOMPATIBLE)
            }
        }
        // Not owned: must be a capture placement adjacent to funded (non-starving) territory.
        if (tile.terrain == Terrain.SEA) return reject(RejectionReason.SEA_IMPASSABLE)
        val adjacentToFunded = HexMath.neighbors(action.at).any {
            val t = state.tiles[it]
            t?.owner == state.currentPlayer && !t.starving
        }
        if (!adjacentToFunded) return reject(RejectionReason.NOT_ADJACENT_TO_TERRITORY)
        val defense = Rules.defenseOf(state, action.at, action.type)
        if (Rules.buyStrength(state, state.currentPlayer, action.tier, action.type) <= defense) {
            return reject(RejectionReason.DEFENSE_TOO_HIGH, defense)
        }
        return LegalityResult.Ok
    }

    private fun checkBuyBuilding(state: GameState, action: GameAction.BuyBuilding): LegalityResult {
        val player = state.player(state.currentPlayer)
        // Campaign levels teach one structure at a time by switching the rest off.
        if (action.type in state.config.rules.disabledBuildings) {
            return reject(RejectionReason.BUILDING_NOT_AVAILABLE)
        }
        val cost = Rules.buildingCost(state, state.currentPlayer, action.type)
        if (player.treasury < cost) return reject(RejectionReason.CANNOT_AFFORD, cost)
        val tile = state.tiles[action.at] ?: return reject(RejectionReason.NO_SUCH_HEX)
        // The BRIDGE is the one building placed ON open sea: unoccupied water
        // adjacent to an own non-starving land or bridge hex (chains grow hex
        // by hex from your shore).
        if (action.type == BuildingType.BRIDGE) {
            if (!state.config.rules.navalEnabled) return reject(RejectionReason.NAVAL_DISABLED)
            if (tile.terrain != Terrain.SEA) return reject(RejectionReason.REQUIRES_SEA)
            if (tile.building != null) return reject(RejectionReason.HEX_HAS_BUILDING)
            if (tile.unit != null) return reject(RejectionReason.HEX_HAS_UNIT)
            val anchored = HexMath.neighbors(action.at).any {
                val t = state.tiles[it]
                t?.owner == state.currentPlayer && !t.starving &&
                    (t.terrain == Terrain.LAND || t.building == Building.BRIDGE)
            }
            if (!anchored) return reject(RejectionReason.NOT_ADJACENT_TO_TERRITORY)
            return LegalityResult.Ok
        }
        if (tile.terrain == Terrain.SEA) return reject(RejectionReason.SEA_IMPASSABLE)
        if (tile.owner != state.currentPlayer) return reject(RejectionReason.NOT_YOUR_HEX)
        // Expedition rule: a PORT may be founded on a cut-off (starving) OVERSEAS
        // colony — it is exactly what reconnects the colony to supply (the reducer
        // recomputes starvation right after the build). On the capital's own
        // landmass a port feeds nothing, so the usual cut-off rule stands there.
        if (tile.starving) {
            val overseas = action.type == BuildingType.PORT &&
                state.player(state.currentPlayer).capital?.let { capital ->
                    action.at !in HexMath.floodFill(capital) {
                        state.tiles[it]?.terrain == Terrain.LAND
                    }
                } ?: true
            if (!overseas) return reject(RejectionReason.HEX_CUT_OFF)
        }
        if (tile.building != null) return reject(RejectionReason.HEX_HAS_BUILDING)
        if (tile.unit != null) return reject(RejectionReason.HEX_HAS_UNIT)
        if (tile.flora != null) return reject(RejectionReason.HEX_NEEDS_CLEARING)
        if (action.type == BuildingType.PORT) {
            if (!state.config.rules.navalEnabled) return reject(RejectionReason.NAVAL_DISABLED)
            val coastal = HexMath.neighbors(action.at).any {
                state.tiles[it]?.terrain == Terrain.SEA
            }
            if (!coastal) return reject(RejectionReason.REQUIRES_COAST)
        }
        if (action.type == BuildingType.FISHERY) {
            if (!state.config.rules.navalEnabled) return reject(RejectionReason.NAVAL_DISABLED)
            val range = state.config.rules.fisheryRange
            if (Rules.shoalsWithin(state.tiles, action.at, range) == 0) {
                return reject(RejectionReason.FISHERY_NEEDS_SHOAL, range)
            }
        }
        if (action.type == BuildingType.MINE && tile.deposit != Deposit.GOLD_VEIN) {
            return reject(RejectionReason.BUILDING_NEEDS_DEPOSIT)
        }
        if (action.type == BuildingType.WATCHTOWER && !state.config.rules.fogOfWar) {
            return reject(RejectionReason.REQUIRES_FOG_OF_WAR)
        }
        if (action.type == BuildingType.LUMBER_CAMP && tile.deposit == Deposit.FERTILE) {
            return reject(RejectionReason.FERTILE_RESERVED_FOR_FARM)
        }
        if (action.type == BuildingType.FARM && tile.deposit != Deposit.FERTILE) {
            val adjacentToChain = HexMath.neighbors(action.at).any {
                val t = state.tiles[it]
                t?.owner == state.currentPlayer &&
                    (t.building == Building.CAPITAL || t.building == Building.FARM)
            }
            if (!adjacentToChain) return reject(RejectionReason.FARM_NEEDS_ADJACENCY)
        }
        return LegalityResult.Ok
    }

    /** Cosmetic and free: any own bridge may be re-aimed at any of the 3 deck axes. */
    private fun checkRotateBuilding(state: GameState, action: GameAction.RotateBuilding): LegalityResult {
        val tile = state.tiles[action.at] ?: return reject(RejectionReason.NO_SUCH_HEX)
        if (tile.building != Building.BRIDGE) return reject(RejectionReason.NOT_A_BRIDGE)
        if (tile.owner != state.currentPlayer) return reject(RejectionReason.NOT_YOUR_HEX)
        if (action.orientation !in 0..2) return reject(RejectionReason.INVALID_ORIENTATION)
        return LegalityResult.Ok
    }

    /**
     * Any own non-capital building may be razed for a partial refund — even on a
     * starving tile (razing needs no supply). A BRIDGE reverts its hex to open sea,
     * so a span carrying a unit refuses rather than stranding it.
     */
    private fun checkDemolishBuilding(state: GameState, action: GameAction.DemolishBuilding): LegalityResult {
        val tile = state.tiles[action.at] ?: return reject(RejectionReason.NO_SUCH_HEX)
        if (tile.owner != state.currentPlayer) return reject(RejectionReason.NOT_YOUR_HEX)
        val building = tile.building ?: return reject(RejectionReason.NO_BUILDING_THERE)
        if (building == Building.CAPITAL) return reject(RejectionReason.CANNOT_DEMOLISH_CAPITAL)
        if (building == Building.BRIDGE && tile.unit != null) {
            return reject(RejectionReason.HEX_HAS_UNIT)
        }
        return LegalityResult.Ok
    }

    /** Any own unit — fresh or spent — may be dismissed for a partial refund. */
    private fun checkDisbandUnit(state: GameState, action: GameAction.DisbandUnit): LegalityResult {
        val unit = state.units[action.unit] ?: return reject(RejectionReason.NO_SUCH_UNIT)
        if (unit.owner != state.currentPlayer) return reject(RejectionReason.NOT_YOUR_UNIT)
        return LegalityResult.Ok
    }

    private fun checkMerge(state: GameState, action: GameAction.MergeUnits): LegalityResult {
        val a = state.units[action.a] ?: return reject(RejectionReason.NO_SUCH_UNIT)
        val b = state.units[action.b] ?: return reject(RejectionReason.NO_SUCH_UNIT)
        if (a.owner != state.currentPlayer || b.owner != state.currentPlayer) {
            return reject(RejectionReason.NOT_YOUR_UNITS)
        }
        if (a.id == b.id) return reject(RejectionReason.CANNOT_MERGE_WITH_SELF)
        if (a.spent) return reject(RejectionReason.UNIT_ALREADY_ACTED)
        if (a.type != UnitType.SOLDIER || b.type != UnitType.SOLDIER) {
            return reject(RejectionReason.CANNOT_MERGE_SPECIAL)
        }
        if (a.tier != b.tier) return reject(RejectionReason.TIER_MISMATCH)
        if (a.tier >= state.config.rules.maxTier) return reject(RejectionReason.ALREADY_MAX_TIER)
        val reach = Rules.reachable(state, action.a)
        if (b.hex !in reach.mergeTargets) return reject(RejectionReason.NOT_IN_SAME_REGION)
        return LegalityResult.Ok
    }

    private fun reject(reason: RejectionReason, amount: Int? = null) =
        LegalityResult.Rejected(reason, amount)
}
