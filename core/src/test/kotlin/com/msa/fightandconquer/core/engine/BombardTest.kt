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
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P0 q 0..2, sea q 3..5, P1 q 6..8 (capital 8). Warship parks at (5,0) off P1's coast. */
class BombardTest {

    private fun withWarship(base: GameState = strip(9, 0..2, 6..8)): GameState =
        base.withSea(listOf(hex(3), hex(4), hex(5)))
            .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.WARSHIP)

    @Test
    fun `bombard kills the garrison but never captures the ground`() {
        val s = withWarship().withUnit(owner = 1, tier = 1, at = hex(6))
        val ship = s.unitIdAt(hex(5))
        val engine = GameEngine(s)
        assertTrue(engine.submit(GameAction.Bombard(ship, hex(6))) is LegalityResult.Ok)
        val after = engine.state.value
        assertEquals(null, after.tiles.getValue(hex(6)).unit)
        assertEquals("raid, not conquest", PlayerId(1), after.tiles.getValue(hex(6)).owner)
        assertEquals("blasted, not buried", null, after.tiles.getValue(hex(6)).flora)
        assertTrue(after.units.getValue(ship).spent)
        assertInvariants(after)
    }

    @Test
    fun `bombard razes a building`() {
        val s = withWarship().withBuilding(Building.FARM, hex(6))
        val engine = GameEngine(s)
        assertTrue(engine.submit(GameAction.Bombard(s.unitIdAt(hex(5)), hex(6))) is LegalityResult.Ok)
        assertEquals(null, engine.state.value.tiles.getValue(hex(6)).building)
        assertInvariants(engine.state.value)
    }

    @Test
    fun `a tower shrugs off the bombardment`() {
        val s = withWarship().withBuilding(Building.TOWER, hex(6))
        val result = GameEngine(s).submit(GameAction.Bombard(s.unitIdAt(hex(5)), hex(6)))
        assertEquals(RejectionReason.DEFENSE_TOO_HIGH, (result as LegalityResult.Rejected).reason)
    }

    @Test
    fun `capitals are immune to bombardment`() {
        // P1's whole island is 6..8 with the capital moved to the coast at 6.
        val base = com.msa.fightandconquer.core.TestStates.custom(
            owners = mapOf(
                hex(0) to 0, hex(1) to 0, hex(2) to 0,
                hex(6) to 1, hex(7) to 1, hex(8) to 1,
            ),
            capital0 = hex(0),
            capital1 = hex(6),
        )
        val s = withWarship(base)
        val result = GameEngine(s).submit(GameAction.Bombard(s.unitIdAt(hex(5)), hex(6)))
        assertEquals(
            RejectionReason.INVALID_BOMBARD_TARGET,
            (result as LegalityResult.Rejected).reason,
        )
    }

    @Test
    fun `empty ground and own tiles are not bombard targets`() {
        val s = withWarship()
        val empty = GameEngine(s).submit(GameAction.Bombard(s.unitIdAt(hex(5)), hex(6)))
        assertEquals(RejectionReason.INVALID_BOMBARD_TARGET, (empty as LegalityResult.Rejected).reason)

        val distant = GameEngine(s).submit(GameAction.Bombard(s.unitIdAt(hex(5)), hex(8)))
        assertEquals(
            RejectionReason.DESTINATION_UNREACHABLE,
            (distant as LegalityResult.Rejected).reason,
        )
    }
}
