package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withFlora
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.Terrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sea tiles are present but impassable/unusable for everything land-based (phase 1). */
class TerrainTest {

    // A 9-hex strip with a sea tile bolted onto the row above P0's territory.
    private val seaHex = hex(1, -1)
    private fun state() = strip(9, 0..2, 6..8).withSea(seaHex)

    @Test
    fun `sea is not a move or capture target for a land unit`() {
        val s = state().withUnit(owner = 0, tier = 3, at = hex(1))
        val reach = Rules.reachable(s, s.unitIdAt(hex(1)))
        assertFalse("sea must not be capturable", seaHex in reach.captureTargets)
        assertFalse("sea must not be enterable", seaHex in reach.moveTargets)
        // Sanity: the same unit can still capture adjacent neutral land.
        assertTrue(hex(3) in reach.captureTargets)
    }

    @Test
    fun `moving onto sea is rejected`() {
        val engine = GameEngine(state().withUnit(owner = 0, tier = 3, at = hex(1)))
        val result = engine.submit(GameAction.MoveUnit(engine.state.value.unitIdAt(hex(1)), seaHex))
        assertEquals(
            RejectionReason.DESTINATION_UNREACHABLE,
            (result as LegalityResult.Rejected).reason,
        )
    }

    @Test
    fun `buying a unit onto sea is rejected`() {
        val engine = GameEngine(state())
        val result = engine.submit(GameAction.BuyUnit(1, seaHex))
        assertEquals(RejectionReason.SEA_IMPASSABLE, (result as LegalityResult.Rejected).reason)
    }

    @Test
    fun `buying a building on sea is rejected`() {
        val engine = GameEngine(state())
        val result = engine.submit(GameAction.BuyBuilding(BuildingType.TOWER, seaHex))
        assertEquals(RejectionReason.SEA_IMPASSABLE, (result as LegalityResult.Rejected).reason)
    }

    @Test
    fun `sea produces no income`() {
        val withoutSea = strip(9, 0..2, 6..8)
        assertEquals(
            Rules.incomeOf(withoutSea, PlayerId(0)),
            Rules.incomeOf(state(), PlayerId(0)),
        )
    }

    @Test
    fun `trees never spread onto sea`() {
        // A tree on P0 land right next to the sea tile, with a 100% spread chance.
        val rules = com.msa.fightandconquer.core.model.RuleConstants(treeSpreadPercent = 100)
        var s = strip(9, 0..2, 6..8, rules = rules)
            .withSea(seaHex) // (1,-1) is adjacent to hex(1)
            .withFlora(Flora.Tree, hex(1))
        // Run several full rounds; no spread may ever land on the sea hex.
        val engine = GameEngine(s)
        repeat(6) { engine.submit(GameAction.EndTurn) }
        s = engine.state.value
        assertEquals(null, s.tiles.getValue(seaHex).flora)
        assertInvariants(s)
    }

    @Test
    fun `capital relocates to land only`() {
        // P1 holds its capital and one other land hex, with sea nearby. Capturing
        // the capital must relocate it to the remaining land hex.
        val s = com.msa.fightandconquer.core.TestStates.custom(
            owners = mapOf(hex(0) to 0, hex(1) to 1, hex(2) to 1),
            capital0 = hex(0),
            capital1 = hex(1),
        )
            .withSea(listOf(hex(3), hex(2, -1), hex(3, -1)))
            .withUnit(owner = 0, tier = 4, at = hex(0))
        val engine = GameEngine(s)
        val result = engine.submit(GameAction.MoveUnit(s.unitIdAt(hex(0)), hex(1)))
        assertTrue("capital capture should be legal: $result", result is LegalityResult.Ok)
        val after = engine.state.value
        assertEquals(hex(2), after.players[1].capital)
        assertEquals(Terrain.LAND, after.tiles.getValue(hex(2)).terrain)
        assertInvariants(after)
    }

    @Test
    fun `sea state round-trips through the save codec`() {
        val s = state().withUnit(owner = 0, tier = 2, at = hex(1))
        val engine = GameEngine(s)
        engine.submit(GameAction.MoveUnit(engine.state.value.unitIdAt(hex(1)), hex(2)))
        val decoded = com.msa.fightandconquer.core.persist.SaveCodec.decode(
            com.msa.fightandconquer.core.persist.SaveCodec.encode(engine.toSave()),
        )
        assertEquals(
            engine.state.value,
            com.msa.fightandconquer.core.persist.SaveCodec.restore(decoded),
        )
        assertEquals(Terrain.SEA, engine.state.value.tiles.getValue(seaHex).terrain)
    }
}
