package com.msa.fightandconquer.core.editor

import com.msa.fightandconquer.core.campaign.CampaignTracker
import com.msa.fightandconquer.core.campaign.FailCondition
import com.msa.fightandconquer.core.campaign.LevelDef
import com.msa.fightandconquer.core.campaign.LevelFactory
import com.msa.fightandconquer.core.campaign.Objective
import com.msa.fightandconquer.core.campaign.Objectives
import com.msa.fightandconquer.core.campaign.SeatDef
import com.msa.fightandconquer.core.campaign.Verdict
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.map.MapViolation
import com.msa.fightandconquer.core.map.MapValidator
import com.msa.fightandconquer.core.model.Terrain

/**
 * Structural validation for a whole custom scenario: the authored-map invariants plus a
 * mirror of every `require` in [LevelFactory.instantiate] and the static objective
 * checks `CampaignFormatTest` runs against shipped campaigns.
 *
 * The contract this object exists for: **a scenario with no violations can never throw
 * inside [LevelFactory]** — the editor and every import channel gate on it before
 * instantiating anything.
 */
object CustomMapValidator {

    fun validate(def: CustomMapDef): List<MapViolation> {
        val level = def.level
        val violations = ArrayList(MapValidator.validateAuthoredCodes(level.map))

        // Seats — LevelFactory's arity require, plus exactly one human chair.
        if (level.seats.size != level.map.capitals.size) {
            violations.add(MapViolation.SeatCountMismatch(level.seats.size, level.map.capitals.size))
        }
        val playerSeats = level.seats.count { it is SeatDef.Player }
        if (playerSeats == 0) violations.add(MapViolation.NoPlayerSeat)
        if (playerSeats > 1) violations.add(MapViolation.MultiplePlayerSeats)
        level.startingTreasury?.let { purses ->
            if (purses.size != level.seats.size) {
                violations.add(MapViolation.TreasurySizeMismatch(purses.size, level.seats.size))
            }
        }
        level.civs?.let { civs ->
            if (civs.size != level.seats.size) {
                violations.add(MapViolation.CivsSizeMismatch(civs.size, level.seats.size))
            }
        }

        // Starting units — placeGarrison's requires, one code per defect.
        val tileByHex = level.map.tiles.associateBy { it.hex }
        val occupied = HashSet<Hex>()
        for (placement in level.startingUnits) {
            if (placement.seat !in level.seats.indices) {
                violations.add(MapViolation.UnitSeatMissing(placement.seat, placement.hex))
                continue
            }
            val tile = tileByHex[placement.hex]
            if (tile == null) {
                violations.add(MapViolation.UnitOffMap(placement.hex))
                continue
            }
            if (!occupied.add(placement.hex)) {
                violations.add(MapViolation.UnitStacked(placement.hex))
                continue
            }
            val naval = Rules.isNaval(placement.type)
            val grounded =
                if (naval) tile.terrain == Terrain.SEA else tile.owner == placement.seat
            if (!grounded) {
                violations.add(MapViolation.UnitNotOnOwnedGround(placement.seat, placement.hex))
            }
        }

        violations += objectiveViolations(level, tileByHex.keys)

        // The dynamic check needs a real opening state, so it only runs once everything
        // above is clean — which is exactly when instantiation is guaranteed not to throw.
        if (violations.isEmpty()) violations += turnZeroViolations(level)
        return violations
    }

    private fun objectiveViolations(
        level: LevelDef,
        onMap: Set<Hex>,
    ): List<MapViolation> {
        val violations = ArrayList<MapViolation>()
        if (level.objectives.isEmpty()) violations.add(MapViolation.NoObjectives)

        val targeted = level.objectives.flatMap { objective ->
            when (objective) {
                is Objective.CaptureHexes -> objective.hexes
                is Objective.HoldHexes -> objective.hexes
                else -> emptyList()
            }
        } + level.failures.flatMap { failure ->
            when (failure) {
                is FailCondition.LoseHexes -> failure.hexes
                else -> emptyList()
            }
        }
        val seats = level.objectives.mapNotNull { (it as? Objective.EliminatePlayer)?.seat } +
            level.failures.mapNotNull { (it as? FailCondition.AllyEliminated)?.seat }
        seats.forEach { seat ->
            if (seat.value !in level.seats.indices) {
                violations.add(MapViolation.ObjectiveSeatMissing(seat.value))
            }
        }
        if (targeted.isEmpty()) return violations

        // Reachability mirrors CampaignFormatTest: the hex shares the player capital's
        // landmass, or boats are on and it has a shore. Sea hexes can never be owned,
        // so a capture/hold/protect target on water is unreachable by definition.
        val land = level.map.tiles.filter { it.terrain == Terrain.LAND }.map { it.hex }.toSet()
        val sea = level.map.tiles.filter { it.terrain == Terrain.SEA }.map { it.hex }.toSet()
        val capital = level.map.capitals.getOrNull(level.playerSeat.value)
        val homeLandmass = capital?.let { HexMath.floodFill(it) { hex -> hex in land } }.orEmpty()
        targeted.forEach { hex ->
            when {
                hex !in onMap -> violations.add(MapViolation.ObjectiveHexOffMap(hex))
                hex in sea -> violations.add(MapViolation.ObjectiveHexUnreachable(hex))
                hex in homeLandmass -> Unit
                level.rules.navalEnabled &&
                    HexMath.neighbors(hex).any { it in sea } -> Unit
                else -> violations.add(MapViolation.ObjectiveHexUnreachable(hex))
            }
        }
        return violations
    }

    private fun turnZeroViolations(level: LevelDef): List<MapViolation> {
        val violations = ArrayList<MapViolation>()
        val state = LevelFactory.instantiate(level)
        val status = Objectives.evaluate(state, CampaignTracker(), level)
        status.rows.forEachIndexed { index, row ->
            if (row.done) violations.add(MapViolation.ObjectiveAlreadyDecided(index))
        }
        if (status.verdict is Verdict.Lost) violations.add(MapViolation.FailureAtStart)
        return violations
    }
}
