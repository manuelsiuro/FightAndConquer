package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withCargo
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Overseas supply rule D: a sea-captured beachhead lives off its landing stores
 * ([RuleConstants.beachheadGraceTurns], default 3) instead of starving on the
 * spot — the window in which the invasion must expand or found its port.
 */
class BeachheadTest {

    /**
     * P0 home island q 0..2 (capital 0), sea q 3..5 with a loaded transport at
     * q 5, neutral island q 6..8, more sea at q 9, P1's island q 10..11 — so
     * the landing decides a beachhead, not the game.
     */
    private fun invasionState(): GameState =
        com.msa.fightandconquer.core.TestStates.custom(
            owners = mapOf(
                hex(0) to 0, hex(1) to 0, hex(2) to 0,
                hex(6) to null, hex(7) to null, hex(8) to null,
                hex(10) to 1, hex(11) to 1,
            ),
            capital0 = hex(0),
            capital1 = hex(10),
            treasury = 100,
        ).withSea(listOf(hex(3), hex(4), hex(5), hex(9)))
            .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.TRANSPORT)
            .withCargo(at = hex(5), tier = 2)

    /** Runs one full round (P0 end -> P1 end) so P0's turn-start pipeline fires. */
    private fun fullRound(engine: GameEngine) {
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
    }

    /**
     * Lands the cargo on the neutral island at q 6 (grace stamped at 3), then
     * runs one round (grace burns to 2) and sails the boat out of adjacency so
     * marine supply (rule B) cannot mask what grace does. The marine is fresh.
     */
    private fun landedAndAbandoned(): GameEngine {
        val engine = GameEngine(invasionState())
        val boat = engine.state.value.unitIdAt(hex(5))
        check(engine.submit(GameAction.Disembark(boat, hex(6))) is LegalityResult.Ok)
        assertEquals(3, engine.state.value.tiles.getValue(hex(6)).graceTurns)
        fullRound(engine) // boat refreshes; grace 3 -> 2
        check(engine.submit(GameAction.MoveUnit(boat, hex(3))) is LegalityResult.Ok)
        return engine
    }

    @Test
    fun `a landing survives on its stores and dies when they run out`() {
        val engine = landedAndAbandoned()
        assertTrue("still starving for income", engine.state.value.tiles.getValue(hex(6)).starving)
        assertEquals(2, engine.state.value.tiles.getValue(hex(6)).graceTurns)

        repeat(2) {
            fullRound(engine)
            assertNotNull("garrison alive with grace left", engine.state.value.unitAt(hex(6)))
        }
        assertEquals(0, engine.state.value.tiles.getValue(hex(6)).graceTurns)
        fullRound(engine)
        assertEquals("stores ran out", null, engine.state.value.tiles.getValue(hex(6)).unit)
        assertInvariants(engine.state.value)
    }

    @Test
    fun `expanding the beachhead inherits the remaining grace`() {
        val engine = landedAndAbandoned()
        val marine = engine.state.value.tiles.getValue(hex(6)).unit!!
        assertTrue(engine.submit(GameAction.MoveUnit(marine, hex(7))) is LegalityResult.Ok)
        val s = engine.state.value
        assertEquals("stores travel with the front", 2, s.tiles.getValue(hex(7)).graceTurns)
        // One region, one clock: every stocked tile burns at the next turn start.
        fullRound(engine)
        val after = engine.state.value
        assertNotNull(after.unitAt(hex(7)))
        assertEquals(1, after.tiles.getValue(hex(6)).graceTurns)
        assertEquals(1, after.tiles.getValue(hex(7)).graceTurns)
        assertInvariants(after)
    }

    @Test
    fun `a graced beachhead still pays no income and cannot fund purchases`() {
        val engine = GameEngine(invasionState())
        val boat = engine.state.value.unitIdAt(hex(5))
        check(engine.submit(GameAction.Disembark(boat, hex(6))) is LegalityResult.Ok)
        assertTrue(engine.state.value.tiles.getValue(hex(6)).starving)
        // A graced region is still cut off for funding: it does not even count
        // as territory for buy-placement adjacency.
        assertEquals(
            RejectionReason.NOT_ADJACENT_TO_TERRITORY,
            (engine.submit(GameAction.BuyUnit(1, hex(7))) as LegalityResult.Rejected).reason,
        )
    }

    @Test
    fun `an expedition port founded inside the window makes the colony permanent`() {
        val engine = landedAndAbandoned()
        val marine = engine.state.value.tiles.getValue(hex(6)).unit!!
        assertTrue(engine.submit(GameAction.MoveUnit(marine, hex(7))) is LegalityResult.Ok)
        assertTrue(
            engine.submit(GameAction.BuyBuilding(BuildingType.PORT, hex(6))) is LegalityResult.Ok,
        )
        val s = engine.state.value
        assertFalse("colony fed by its port", s.tiles.getValue(hex(6)).starving)
        assertEquals("grace clock ends with reconnection", 0, s.tiles.getValue(hex(6)).graceTurns)
        assertEquals(0, s.tiles.getValue(hex(7)).graceTurns)
        // Long after the window, the colony lives.
        repeat(4) { fullRound(engine) }
        assertNotNull(engine.state.value.unitAt(hex(7)))
        assertInvariants(engine.state.value)
    }

    @Test
    fun `mainland slicing still starves with no grace`() {
        // P1 cuts P0's strip from the row below -> the severed lobe starves
        // exactly as before (slicing captures never stamp grace).
        val s = com.msa.fightandconquer.core.TestStates.custom(
            owners = mapOf(
                hex(0) to 0, hex(1) to 0, hex(2) to 0, hex(3) to 0, hex(4) to 0,
                hex(3, 1) to 1, hex(4, 1) to 1, hex(5, 1) to 1,
            ),
            capital0 = hex(0),
            capital1 = hex(5, 1),
        ).withUnit(owner = 0, tier = 1, at = hex(4))
            .withUnit(owner = 1, tier = 2, at = hex(3, 1))
        val engine = GameEngine(s)
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok) // P1's turn
        val cutter = engine.state.value.unitIdAt(hex(3, 1))
        assertTrue(engine.submit(GameAction.MoveUnit(cutter, hex(3))) is LegalityResult.Ok)
        assertTrue("lobe sliced off", engine.state.value.tiles.getValue(hex(4)).starving)
        assertEquals(0, engine.state.value.tiles.getValue(hex(4)).graceTurns)
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok) // back to P0
        assertEquals(
            "sliced garrison dies on schedule",
            null,
            engine.state.value.tiles.getValue(hex(4)).unit,
        )
        assertInvariants(engine.state.value)
    }

    @Test
    fun `grace state survives a save round-trip`() {
        val engine = GameEngine(invasionState())
        val boat = engine.state.value.unitIdAt(hex(5))
        check(engine.submit(GameAction.Disembark(boat, hex(6))) is LegalityResult.Ok)
        val json = kotlinx.serialization.json.Json
        val s = engine.state.value
        val decoded = json.decodeFromString(
            GameState.serializer(),
            json.encodeToString(GameState.serializer(), s),
        )
        assertEquals(s, decoded)
        assertEquals(3, decoded.tiles.getValue(hex(6)).graceTurns)
    }
}
