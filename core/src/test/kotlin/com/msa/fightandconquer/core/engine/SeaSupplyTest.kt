package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Overseas supply rules A (port feeds its region), B (adjacent own boat feeds a
 * starving unit) and C (a port may be founded on a starving colony), ending in
 * the full invasion loop they exist for.
 */
class SeaSupplyTest {

    /**
     * P0's home island q 0..2 (capital 0) plus an overseas colony q 6..7 —
     * disconnected from the capital by sea q 3..5. P1 sits at q 8 (its capital).
     */
    private fun colonyState(): GameState =
        com.msa.fightandconquer.core.TestStates.custom(
            owners = mapOf(
                hex(0) to 0, hex(1) to 0, hex(2) to 0,
                hex(6) to 0, hex(7) to 0,
                hex(8) to 1,
            ),
            capital0 = hex(0),
            capital1 = hex(8),
        ).withSea(listOf(hex(3), hex(4), hex(5)))

    /** Runs one full round (P0 end -> P1 end) so P0's turn-start pipeline fires. */
    private fun fullRound(engine: GameEngine) {
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
    }

    @Test
    fun `rule A - a port feeds its whole disconnected region`() {
        val engine = GameEngine(colonyState().withBuilding(Building.PORT, hex(6)))
        fullRound(engine)
        val s = engine.state.value
        assertFalse("port hex fed", s.tiles.getValue(hex(6)).starving)
        assertFalse("whole colony fed", s.tiles.getValue(hex(7)).starving)
        assertInvariants(s)
    }

    @Test
    fun `without a port the colony starves and its garrison dies`() {
        val engine = GameEngine(colonyState().withUnit(owner = 0, tier = 1, at = hex(7)))
        fullRound(engine)
        val s = engine.state.value
        assertTrue(s.tiles.getValue(hex(6)).starving)
        assertEquals("garrison starved", null, s.tiles.getValue(hex(7)).unit)
        assertInvariants(s)
    }

    @Test
    fun `rule B - a boat alongside keeps the starving garrison alive`() {
        val engine = GameEngine(
            colonyState()
                .withUnit(owner = 0, tier = 1, at = hex(6))
                .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.TRANSPORT),
        )
        fullRound(engine)
        val s = engine.state.value
        assertTrue("still starving (no income)", s.tiles.getValue(hex(6)).starving)
        assertTrue("but the fleet feeds the garrison", s.tiles.getValue(hex(6)).unit != null)
        assertInvariants(s)
    }

    @Test
    fun `rule C - an expedition port can be founded on the starving colony`() {
        val engine = GameEngine(colonyState())
        fullRound(engine) // colony flagged starving at P0's turn start
        assertTrue(engine.state.value.tiles.getValue(hex(7)).starving)

        assertTrue(
            engine.submit(GameAction.BuyBuilding(BuildingType.PORT, hex(6))) is LegalityResult.Ok,
        )
        val s = engine.state.value
        assertFalse("colony unfreezes the same action", s.tiles.getValue(hex(6)).starving)
        assertFalse(s.tiles.getValue(hex(7)).starving)
        assertInvariants(s)
    }

    @Test
    fun `bombarding the port starves the colony again`() {
        val s0 = colonyState().withBuilding(Building.PORT, hex(6))
            .withUnit(owner = 1, tier = 1, at = hex(5), type = UnitType.WARSHIP)
        val engine = GameEngine(s0)
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok) // P1's turn
        assertTrue(
            engine.submit(GameAction.Bombard(engine.state.value.unitIdAt(hex(5)), hex(6))) is LegalityResult.Ok,
        )
        val s = engine.state.value
        assertEquals(null, s.tiles.getValue(hex(6)).building)
        assertTrue("supply line cut on the spot", s.tiles.getValue(hex(6)).starving)
        assertInvariants(s)
    }

    @Test
    fun `the full invasion loop - port, ferry, beachhead, expedition port`() {
        // P0 alone on its island; P1 across the water holding q 6..8.
        val start = com.msa.fightandconquer.core.TestStates.custom(
            owners = mapOf(
                hex(0) to 0, hex(1) to 0, hex(2) to 0,
                hex(6) to 1, hex(7) to 1, hex(8) to 1,
            ),
            capital0 = hex(0),
            capital1 = hex(8),
            treasury = 100,
        ).withSea(listOf(hex(3), hex(4), hex(5)))
            .withUnit(owner = 0, tier = 3, at = hex(1))
        val engine = GameEngine(start)

        // Turn 1: harbor, boat, board.
        assertTrue(engine.submit(GameAction.BuyBuilding(BuildingType.PORT, hex(2))) is LegalityResult.Ok)
        assertTrue(engine.submit(GameAction.BuyUnit(1, hex(3), UnitType.TRANSPORT)) is LegalityResult.Ok)
        val boat = engine.state.value.unitIdAt(hex(3))
        val baron = engine.state.value.unitIdAt(hex(1))
        assertTrue(engine.submit(GameAction.MoveUnit(baron, hex(3))) is LegalityResult.Ok)
        // The boat still has its action: sail to the far shore.
        assertTrue(engine.submit(GameAction.MoveUnit(boat, hex(5))) is LegalityResult.Ok)
        fullRound(engine)

        // Turn 2: storm the beach. The boat alongside keeps the baron fed.
        assertTrue(engine.submit(GameAction.Disembark(boat, hex(6))) is LegalityResult.Ok)
        assertEquals(PlayerId(0), engine.state.value.tiles.getValue(hex(6)).owner)
        fullRound(engine)
        assertTrue("marine supply held the beachhead", engine.state.value.unitAt(hex(6)) != null)

        // Turn 3: push inland, then found the expedition port behind the line.
        val marine = engine.state.value.tiles.getValue(hex(6)).unit!!
        assertTrue(engine.submit(GameAction.MoveUnit(marine, hex(7))) is LegalityResult.Ok)
        assertTrue(engine.submit(GameAction.BuyBuilding(BuildingType.PORT, hex(6))) is LegalityResult.Ok)
        val s = engine.state.value
        assertFalse("colony is self-sufficient", s.tiles.getValue(hex(7)).starving)
        assertInvariants(s)
    }
}
