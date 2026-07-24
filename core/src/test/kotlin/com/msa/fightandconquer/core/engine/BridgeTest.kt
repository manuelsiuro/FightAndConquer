package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bridges: the one building on water — walkable, contested, bombardable. */
class BridgeTest {

    /** P0 q 0..2 (capital 0), sea q 3..5, P1 q 6..8 (capital 8). */
    private fun islands(): GameState =
        strip(9, 0..2, 6..8).withSea(listOf(hex(3), hex(4), hex(5)))

    @Test
    fun `a bridge chain grows hex by hex from the shore`() {
        val engine = GameEngine(islands())
        // Mid-sea span with no anchor: rejected.
        val floating = engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(4)))
        assertEquals(
            RejectionReason.NOT_ADJACENT_TO_TERRITORY,
            (floating as LegalityResult.Rejected).reason,
        )
        // Anchored spans, one after the other.
        assertTrue(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(3))) is LegalityResult.Ok)
        assertTrue(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(4))) is LegalityResult.Ok)
        val s = engine.state.value
        assertEquals(Building.BRIDGE, s.tiles.getValue(hex(3)).building)
        assertEquals(PlayerId(0), s.tiles.getValue(hex(3)).owner)
        assertEquals(Terrain.SEA, s.tiles.getValue(hex(3)).terrain)
        assertInvariants(s)
    }

    @Test
    fun `troops walk the bridge`() {
        val engine = GameEngine(islands())
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(3))) is LegalityResult.Ok)
        val engine2 = GameEngine(engine.state.value.withUnit(owner = 0, tier = 2, at = hex(1)))
        val soldier = engine2.state.value.unitIdAt(hex(1))
        val reach = Rules.reachable(engine2.state.value, soldier)
        assertTrue("bridge is stand-able", hex(3) in reach.moveTargets)
        assertTrue(engine2.submit(GameAction.MoveUnit(soldier, hex(3))) is LegalityResult.Ok)
        assertInvariants(engine2.state.value)
    }

    @Test
    fun `a bridgehead reaches the far shore and joins the supply web`() {
        // Bridge all the way across, then capture the far island's coast.
        val engine = GameEngine(islands().withUnit(owner = 0, tier = 3, at = hex(2)))
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(3))) is LegalityResult.Ok)
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(4))) is LegalityResult.Ok)
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(5))) is LegalityResult.Ok)
        val baron = engine.state.value.unitIdAt(hex(2))
        val reach = Rules.reachable(engine.state.value, baron)
        assertTrue("capture across the bridge", hex(6) in reach.captureTargets)
        assertTrue(engine.submit(GameAction.MoveUnit(baron, hex(6))) is LegalityResult.Ok)
        val s = engine.state.value
        assertEquals(PlayerId(0), s.tiles.getValue(hex(6)).owner)
        assertFalse("bridge-linked beachhead is fed", s.tiles.getValue(hex(6)).starving)
        assertInvariants(s)
    }

    @Test
    fun `capturing a bridge hex preserves the span`() {
        // P1's soldier storms P0's bridge at hex(3).
        val s = islands()
            .withUnit(owner = 1, tier = 3, at = hex(6))
        val engine = GameEngine(s)
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(3))) is LegalityResult.Ok)
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(4))) is LegalityResult.Ok)
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(5))) is LegalityResult.Ok)
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
        val baron = engine.state.value.unitIdAt(hex(6))
        assertTrue(
            "enemy bridge is capturable",
            hex(5) in Rules.reachable(engine.state.value, baron).captureTargets,
        )
        assertTrue(engine.submit(GameAction.MoveUnit(baron, hex(5))) is LegalityResult.Ok)
        val after = engine.state.value
        assertEquals("span survives", Building.BRIDGE, after.tiles.getValue(hex(5)).building)
        assertEquals(PlayerId(1), after.tiles.getValue(hex(5)).owner)
        assertInvariants(after)
    }

    @Test
    fun `bombardment collapses a bridge back into open water`() {
        val s = islands()
            .withSea(hex(4, -1))
            .withUnit(owner = 1, tier = 1, at = hex(4, -1), type = UnitType.WARSHIP)
        val engine = GameEngine(s)
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(3))) is LegalityResult.Ok)
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(4))) is LegalityResult.Ok)
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
        val ship = engine.state.value.unitIdAt(hex(4, -1))
        assertTrue(engine.submit(GameAction.Bombard(ship, hex(4))) is LegalityResult.Ok)
        val after = engine.state.value
        val tile = after.tiles.getValue(hex(4))
        assertEquals(null, tile.building)
        assertEquals("open neutral water again", null, tile.owner)
        assertInvariants(after)
    }

    @Test
    fun `bridges block boats`() {
        val s = islands().withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.TRANSPORT)
        val engine = GameEngine(s)
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(3))) is LegalityResult.Ok)
        val boat = engine.state.value.unitIdAt(hex(5))
        val reach = Rules.reachable(engine.state.value, boat)
        assertTrue(hex(4) in reach.moveTargets)
        assertFalse("no sailing under the span", hex(3) in reach.moveTargets)
    }
}
