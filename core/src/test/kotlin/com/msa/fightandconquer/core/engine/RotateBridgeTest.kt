package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Player-chosen bridge deck axis (cosmetic, but engine state so saves agree). */
class RotateBridgeTest {

    /** P0 q 0..2 (capital 0), sea q 3..5, P1 q 6..8 (capital 8). */
    private fun islands(): GameState =
        strip(9, 0..2, 6..8).withSea(listOf(hex(3), hex(4), hex(5)))

    private fun withBridge(): GameEngine {
        val engine = GameEngine(islands())
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(3))) is LegalityResult.Ok)
        return engine
    }

    @Test
    fun `rotating an own bridge stores the axis and emits the event`() {
        val engine = withBridge()
        assertEquals(null, engine.state.value.tiles.getValue(hex(3)).bridgeOrientation)
        assertTrue(engine.submit(GameAction.RotateBuilding(hex(3), 2)) is LegalityResult.Ok)
        val s = engine.state.value
        assertEquals(2, s.tiles.getValue(hex(3)).bridgeOrientation)
        assertTrue(engine.lastEvents.any { it is GameEvent.BuildingRotated && it.orientation == 2 })
        assertInvariants(s)
    }

    @Test
    fun `only bridges rotate`() {
        val engine = withBridge()
        val land = Reducer.reduce(engine.state.value, GameAction.RotateBuilding(hex(1), 1))
        assertEquals(
            RejectionReason.NOT_A_BRIDGE,
            (land.events.single() as GameEvent.ActionRejected).reason,
        )
        assertEquals(engine.state.value, land.state)
    }

    @Test
    fun `only the owner rotates, and only to a valid axis`() {
        val engine = withBridge()
        val s = engine.state.value
        val tooBig = Reducer.reduce(s, GameAction.RotateBuilding(hex(3), 3))
        assertEquals(
            RejectionReason.INVALID_ORIENTATION,
            (tooBig.events.single() as GameEvent.ActionRejected).reason,
        )
        val negative = Reducer.reduce(s, GameAction.RotateBuilding(hex(3), -1))
        assertEquals(
            RejectionReason.INVALID_ORIENTATION,
            (negative.events.single() as GameEvent.ActionRejected).reason,
        )
        // P1's turn: P0's bridge is off limits.
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
        val enemy = Reducer.reduce(engine.state.value, GameAction.RotateBuilding(hex(3), 1))
        assertEquals(
            RejectionReason.NOT_YOUR_HEX,
            (enemy.events.single() as GameEvent.ActionRejected).reason,
        )
    }

    @Test
    fun `undo restores the previous orientation`() {
        val engine = withBridge()
        check(engine.submit(GameAction.RotateBuilding(hex(3), 1)) is LegalityResult.Ok)
        check(engine.submit(GameAction.RotateBuilding(hex(3), 2)) is LegalityResult.Ok)
        assertTrue(engine.undo())
        assertEquals(1, engine.state.value.tiles.getValue(hex(3)).bridgeOrientation)
        assertTrue(engine.undo())
        assertEquals(null, engine.state.value.tiles.getValue(hex(3)).bridgeOrientation)
    }

    @Test
    fun `bombardment clears the stored orientation with the span`() {
        val engine = GameEngine(
            islands()
                .withSea(hex(4, -1))
                .withUnit(owner = 1, tier = 1, at = hex(4, -1), type = UnitType.WARSHIP),
        )
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(3))) is LegalityResult.Ok)
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(4))) is LegalityResult.Ok)
        check(engine.submit(GameAction.RotateBuilding(hex(4), 1)) is LegalityResult.Ok)
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
        val ship = engine.state.value.unitIdAt(hex(4, -1))
        assertTrue(engine.submit(GameAction.Bombard(ship, hex(4))) is LegalityResult.Ok)
        assertEquals(null, engine.state.value.tiles.getValue(hex(4)).bridgeOrientation)
        assertInvariants(engine.state.value)
    }
}
