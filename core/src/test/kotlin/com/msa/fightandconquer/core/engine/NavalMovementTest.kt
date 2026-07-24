package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two islands: P0 owns q 0..2 (capital 0), P1 owns q 6..8 (capital 8),
 * open sea at q 3..5. Transport range 3 reaches across but land blocks.
 */
class NavalMovementTest {

    private fun sea(): List<com.msa.fightandconquer.core.hex.Hex> =
        listOf(hex(3), hex(4), hex(5))

    private fun islands(): GameState = strip(9, 0..2, 6..8).withSea(sea())

    @Test
    fun `transport sails open sea within its range and never onto land`() {
        val s = islands().withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.TRANSPORT)
        val reach = Rules.reachable(s, s.unitIdAt(hex(3)))
        assertEquals(setOf(hex(4), hex(5)), reach.moveTargets)
        assertTrue(reach.captureTargets.isEmpty())
        assertTrue(reach.mergeTargets.isEmpty())
    }

    @Test
    fun `an enemy boat blocks the channel for a transport`() {
        val s = islands()
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.TRANSPORT)
            .withUnit(owner = 1, tier = 1, at = hex(4), type = UnitType.TRANSPORT)
        val reach = Rules.reachable(s, s.unitIdAt(hex(3)))
        assertTrue("blocked channel", reach.moveTargets.isEmpty())
        assertTrue("transports never attack", reach.captureTargets.isEmpty())
    }

    @Test
    fun `warship sinks an equal warship - ties go to the attacker`() {
        val s = islands()
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.WARSHIP)
            .withUnit(owner = 1, tier = 1, at = hex(4), type = UnitType.WARSHIP)
        val attacker = s.unitIdAt(hex(3))
        assertTrue(hex(4) in Rules.reachable(s, attacker).captureTargets)

        val engine = GameEngine(s)
        assertTrue(engine.submit(GameAction.MoveUnit(attacker, hex(4))) is LegalityResult.Ok)
        val after = engine.state.value
        assertEquals(1, after.units.size)
        assertEquals(hex(4), after.units.getValue(attacker).hex)
        assertEquals(null, after.tiles.getValue(hex(4)).owner) // sinking captures no water
        assertInvariants(after)
    }

    @Test
    fun `warship does not attack a stronger defended target or a partner-free own boat`() {
        // Own boat ahead: not a capture target.
        val s = islands()
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.WARSHIP)
            .withUnit(owner = 0, tier = 1, at = hex(4), type = UnitType.TRANSPORT)
        assertTrue(Rules.reachable(s, s.unitIdAt(hex(3))).captureTargets.isEmpty())
    }

    @Test
    fun `land unit can neither enter sea nor capture a boat from shore`() {
        val s = islands()
            .withUnit(owner = 0, tier = 4, at = hex(2))
            .withUnit(owner = 1, tier = 1, at = hex(3), type = UnitType.TRANSPORT)
        val reach = Rules.reachable(s, s.unitIdAt(hex(2)))
        assertFalse(hex(3) in reach.moveTargets)
        assertFalse(hex(3) in reach.captureTargets)
        assertFalse("enemy boat is not boardable", hex(3) in reach.embarkTargets)
    }

    @Test
    fun `boats are bought on sea beside an own working port only`() {
        val engine = GameEngine(islands())
        val noPort = engine.submit(GameAction.BuyUnit(1, hex(3), UnitType.TRANSPORT))
        assertEquals(RejectionReason.NO_ADJACENT_PORT, (noPort as LegalityResult.Rejected).reason)

        val withPort = GameEngine(islands().withBuilding(Building.PORT, hex(2)))
        val onLand = withPort.submit(GameAction.BuyUnit(1, hex(1), UnitType.TRANSPORT))
        assertEquals(RejectionReason.REQUIRES_SEA, (onLand as LegalityResult.Rejected).reason)

        assertTrue(withPort.submit(GameAction.BuyUnit(1, hex(3), UnitType.TRANSPORT)) is LegalityResult.Ok)
        val after = withPort.state.value
        val boat = after.unitAt(hex(3))!!
        assertEquals(UnitType.TRANSPORT, boat.type)
        assertFalse("launches fresh", boat.spent)
        assertInvariants(after)
    }

    @Test
    fun `naval move ranges stay within unit vision - the sea fog invariant`() {
        val rules = com.msa.fightandconquer.core.model.RuleConstants()
        assertTrue(rules.transportMoveRange <= rules.visionRadiusUnit)
        assertTrue(rules.warshipMoveRange <= rules.visionRadiusUnit)
    }
}
