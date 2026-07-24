package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withCargo
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.CargoUnit
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same two-island layout as NavalMovementTest: P0 q 0..2, sea q 3..5, P1 q 6..8. */
class EmbarkDisembarkTest {

    private fun islands(): GameState =
        strip(9, 0..2, 6..8).withSea(listOf(hex(3), hex(4), hex(5)))

    @Test
    fun `embarking stows the unit as cargo and frees its hex`() {
        val s = islands()
            .withUnit(owner = 0, tier = 2, at = hex(2))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.TRANSPORT)
        val soldier = s.unitIdAt(hex(2))
        val boat = s.unitIdAt(hex(3))
        assertTrue(hex(3) in Rules.reachable(s, soldier).embarkTargets)

        val engine = GameEngine(s)
        assertTrue(engine.submit(GameAction.MoveUnit(soldier, hex(3))) is LegalityResult.Ok)
        val after = engine.state.value
        assertFalse("passenger left the units map", soldier in after.units)
        assertEquals(CargoUnit(2, UnitType.SOLDIER), after.units.getValue(boat).cargo)
        assertEquals(null, after.tiles.getValue(hex(2)).unit)
        assertFalse("the boat keeps its own action", after.units.getValue(boat).spent)
        assertInvariants(after)
    }

    @Test
    fun `a full transport takes no second passenger`() {
        val s = islands()
            .withUnit(owner = 0, tier = 1, at = hex(2))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.TRANSPORT)
            .withCargo(at = hex(3), tier = 1)
        assertTrue(Rules.reachable(s, s.unitIdAt(hex(2))).embarkTargets.isEmpty())
    }

    @Test
    fun `a loaded transport pays its cargo's upkeep`() {
        val rules = com.msa.fightandconquer.core.model.RuleConstants()
        val empty = islands().withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.TRANSPORT)
        val loaded = empty.withCargo(at = hex(3), tier = 2)
        assertEquals(rules.transportUpkeep, Rules.upkeepOf(empty, PlayerId(0)))
        assertEquals(
            rules.transportUpkeep + rules.unitUpkeep[1],
            Rules.upkeepOf(loaded, PlayerId(0)),
        )
    }

    @Test
    fun `disembark onto an own hex spends boat and landed unit`() {
        val s = islands()
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.TRANSPORT)
            .withCargo(at = hex(3), tier = 2)
        val boat = s.unitIdAt(hex(3))
        val engine = GameEngine(s)
        assertTrue(engine.submit(GameAction.Disembark(boat, hex(2))) is LegalityResult.Ok)
        val after = engine.state.value
        val landed = after.unitAt(hex(2))!!
        assertEquals(2, landed.tier)
        assertTrue(landed.spent)
        assertEquals(null, after.units.getValue(boat).cargo)
        assertTrue(after.units.getValue(boat).spent)
        assertInvariants(after)
    }

    @Test
    fun `amphibious landing captures an enemy beach`() {
        val s = islands()
            .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.TRANSPORT)
            .withCargo(at = hex(5), tier = 3)
        val boat = s.unitIdAt(hex(5))
        val engine = GameEngine(s)
        assertTrue(engine.submit(GameAction.Disembark(boat, hex(6))) is LegalityResult.Ok)
        val after = engine.state.value
        assertEquals(PlayerId(0), after.tiles.getValue(hex(6)).owner)
        assertEquals(3, after.unitAt(hex(6))!!.tier)
        assertTrue(after.unitAt(hex(6))!!.spent)
        assertInvariants(after)
    }

    @Test
    fun `a defended beach blocks the landing`() {
        val s = islands()
            .withBuilding(Building.TOWER, hex(7))
            .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.TRANSPORT)
            .withCargo(at = hex(5), tier = 1)
        val result = GameEngine(s).submit(GameAction.Disembark(s.unitIdAt(hex(5)), hex(6)))
        assertEquals(RejectionReason.DEFENSE_TOO_HIGH, (result as LegalityResult.Rejected).reason)
    }

    @Test
    fun `cargo goes down with the ship`() {
        val s = islands()
            .withUnit(owner = 1, tier = 1, at = hex(4), type = UnitType.TRANSPORT)
            .withCargo(at = hex(4), tier = 4)
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.WARSHIP)
        val warship = s.unitIdAt(hex(3))
        val engine = GameEngine(s)
        assertTrue(engine.submit(GameAction.MoveUnit(warship, hex(4))) is LegalityResult.Ok)
        val after = engine.state.value
        assertEquals("only the warship remains", setOf(warship), after.units.keys)
        assertEquals(null, after.tiles.getValue(hex(4)).flora) // no gravestones at sea
        assertInvariants(after)
    }

    @Test
    fun `empty and spent transports refuse to disembark`() {
        val empty = islands().withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.TRANSPORT)
        val r1 = GameEngine(empty).submit(GameAction.Disembark(empty.unitIdAt(hex(3)), hex(2)))
        assertEquals(RejectionReason.TRANSPORT_EMPTY, (r1 as LegalityResult.Rejected).reason)

        val spent = islands()
            .withUnit(owner = 0, tier = 1, at = hex(3), spent = true, type = UnitType.TRANSPORT)
            .withCargo(at = hex(3), tier = 1)
        val r2 = GameEngine(spent).submit(GameAction.Disembark(spent.unitIdAt(hex(3)), hex(2)))
        assertEquals(RejectionReason.UNIT_ALREADY_ACTED, (r2 as LegalityResult.Rejected).reason)
    }
}
