package com.msa.fightandconquer.core.editor

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.campaign.FailCondition
import com.msa.fightandconquer.core.campaign.LevelDef
import com.msa.fightandconquer.core.campaign.LevelFactory
import com.msa.fightandconquer.core.campaign.Objective
import com.msa.fightandconquer.core.campaign.SeatDef
import com.msa.fightandconquer.core.campaign.TestLevels
import com.msa.fightandconquer.core.campaign.UnitPlacement
import com.msa.fightandconquer.core.map.MapViolation
import com.msa.fightandconquer.core.map.TileDef
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The custom-map validator's contract: it mirrors every `require` in [LevelFactory]
 * and the campaign format's static objective checks, so a scenario it passes can
 * never throw at instantiation — the editor and the import channels both gate on it.
 */
class CustomMapValidatorTest {

    private fun def(level: LevelDef) = CustomMapDef(id = "m1", name = "Test", level = level)

    private val clean = def(TestLevels.strip())

    @Test
    fun `clean scenario has no violations and instantiates`() {
        assertEquals(emptyList<MapViolation>(), CustomMapValidator.validate(clean))
        LevelFactory.instantiate(clean.level) // must not throw
    }

    @Test
    fun `seat arity mismatch is flagged`() {
        val broken = def(clean.level.copy(seats = listOf(SeatDef.Player)))
        assertTrue(CustomMapValidator.validate(broken).contains(MapViolation.SeatCountMismatch(1, 2)))
    }

    @Test
    fun `exactly one player seat is required`() {
        val none = def(
            clean.level.copy(
                seats = listOf(SeatDef.Ai(Difficulty.EASY), SeatDef.Ai(Difficulty.EASY)),
            ),
        )
        assertTrue(CustomMapValidator.validate(none).contains(MapViolation.NoPlayerSeat))
        val two = def(clean.level.copy(seats = listOf(SeatDef.Player, SeatDef.Player)))
        assertTrue(CustomMapValidator.validate(two).contains(MapViolation.MultiplePlayerSeats))
    }

    @Test
    fun `treasury arity mismatch is flagged`() {
        val broken = def(clean.level.copy(startingTreasury = listOf(100)))
        assertTrue(CustomMapValidator.validate(broken).contains(MapViolation.TreasurySizeMismatch(1, 2)))
    }

    @Test
    fun `unit defects are flagged individually`() {
        val units = def(
            clean.level.copy(
                startingUnits = listOf(
                    UnitPlacement(seat = 0, hex = hex(40)), // off-map
                    UnitPlacement(seat = 0, hex = hex(1)),
                    UnitPlacement(seat = 1, hex = hex(1)), // stacked
                    UnitPlacement(seat = 0, hex = hex(8)), // seat 1's ground
                    UnitPlacement(seat = 5, hex = hex(2)), // no such seat
                ),
            ),
        )
        val violations = CustomMapValidator.validate(units)
        assertTrue(violations.contains(MapViolation.UnitOffMap(hex(40))))
        assertTrue(violations.contains(MapViolation.UnitStacked(hex(1))))
        assertTrue(violations.contains(MapViolation.UnitNotOnOwnedGround(0, hex(8))))
        assertTrue(violations.contains(MapViolation.UnitSeatMissing(5, hex(2))))
    }

    @Test
    fun `objective hex checks mirror the campaign format tests`() {
        val offMap = def(clean.level.copy(objectives = listOf(Objective.CaptureHexes(listOf(hex(40))))))
        assertTrue(CustomMapValidator.validate(offMap).contains(MapViolation.ObjectiveHexOffMap(hex(40))))

        // A sea target can never be owned, so capture/hold/protect on water is dead.
        val withSea = clean.level.copy(
            map = clean.level.map.copy(
                tiles = clean.level.map.tiles + TileDef(hex = hex(0, 1), terrain = Terrain.SEA),
            ),
            objectives = listOf(Objective.CaptureHexes(listOf(hex(0, 1)))),
        )
        assertTrue(
            CustomMapValidator.validate(def(withSea))
                .contains(MapViolation.ObjectiveHexUnreachable(hex(0, 1))),
        )

        val badSeat = def(clean.level.copy(objectives = listOf(Objective.EliminatePlayer(PlayerId(7)))))
        assertTrue(CustomMapValidator.validate(badSeat).contains(MapViolation.ObjectiveSeatMissing(7)))

        val silent = def(clean.level.copy(objectives = emptyList()))
        assertTrue(CustomMapValidator.validate(silent).contains(MapViolation.NoObjectives))
    }

    @Test
    fun `reachable objective across water needs boats`() {
        // An island east of the strip, joined only by sea: reachable only when naval is on.
        val strip = clean.level.map.tiles
        val sea = (0 until 2).map { TileDef(hex = hex(9 + it), terrain = Terrain.SEA) }
        val island = TileDef(hex = hex(11))
        val map = clean.level.map.copy(tiles = strip + sea + island)
        // Boats are on by default (RuleConstants.navalEnabled = true) — switch them off
        // to prove the check, then back on to prove the shore escape hatch.
        val target = clean.level.copy(
            map = map,
            rules = clean.level.rules.copy(navalEnabled = false),
            objectives = listOf(Objective.CaptureHexes(listOf(hex(11)))),
        )
        assertTrue(
            CustomMapValidator.validate(def(target))
                .contains(MapViolation.ObjectiveHexUnreachable(hex(11))),
        )
        val naval = target.copy(rules = target.rules.copy(navalEnabled = true))
        assertTrue(
            CustomMapValidator.validate(def(naval))
                .none { it is MapViolation.ObjectiveHexUnreachable },
        )
    }

    @Test
    fun `turn zero decided objectives and failures are flagged`() {
        val trivial = def(clean.level.copy(objectives = listOf(Objective.SurviveRounds(0))))
        assertTrue(CustomMapValidator.validate(trivial).contains(MapViolation.ObjectiveAlreadyDecided(0)))

        val doomed = def(clean.level.copy(failures = listOf(FailCondition.TurnLimit(0))))
        assertTrue(CustomMapValidator.validate(doomed).contains(MapViolation.FailureAtStart))
    }

    @Test
    fun `naval starting unit must be at sea and land unit on owned land`() {
        val sea = TileDef(hex = hex(0, 1), terrain = Terrain.SEA)
        val map = clean.level.map.copy(tiles = clean.level.map.tiles + sea)
        val good = clean.level.copy(
            map = map,
            rules = clean.level.rules.copy(navalEnabled = true),
            startingUnits = listOf(
                UnitPlacement(seat = 0, hex = hex(0, 1), type = UnitType.WARSHIP),
                UnitPlacement(seat = 0, hex = hex(1)),
            ),
        )
        assertEquals(emptyList<MapViolation>(), CustomMapValidator.validate(def(good)))
        LevelFactory.instantiate(good)

        val beached = good.copy(
            startingUnits = listOf(UnitPlacement(seat = 0, hex = hex(1), type = UnitType.WARSHIP)),
        )
        assertTrue(
            CustomMapValidator.validate(def(beached))
                .contains(MapViolation.UnitNotOnOwnedGround(0, hex(1))),
        )
    }
}
