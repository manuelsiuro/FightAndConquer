package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withFlora
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitId
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the [RejectionReason] codes the UI shows but no behavioral test asserted
 * before the 2026-08 rules audit — the state outcomes were covered, the codes
 * were not, and the codes are what players read.
 */
class RejectionReasonCoverageTest {

    private val base = strip(9, 0..3, 6..8)

    private fun reasonOf(state: com.msa.fightandconquer.core.model.GameState, action: GameAction): RejectionReason {
        val (next, events) = Reducer.reduce(state, action)
        assertEquals("a rejected action must not change state", state, next)
        return events.filterIsInstance<GameEvent.ActionRejected>().single().reason
    }

    @Test
    fun `any action after the game finished is rejected`() {
        val finished = base.copy(phase = GamePhase.Finished(PlayerId(0)))
        assertEquals(RejectionReason.GAME_FINISHED, reasonOf(finished, GameAction.EndTurn))
    }

    @Test
    fun `buying onto a hex outside the map is rejected`() {
        assertEquals(
            RejectionReason.NO_SUCH_HEX,
            reasonOf(base, GameAction.BuyUnit(1, hex(20))),
        )
    }

    @Test
    fun `moving a unit that does not exist is rejected`() {
        assertEquals(
            RejectionReason.NO_SUCH_UNIT,
            reasonOf(base, GameAction.MoveUnit(UnitId(999), hex(1))),
        )
    }

    @Test
    fun `building on a forested hex asks for clearing first`() {
        val s = base.withFlora(Flora.Tree, hex(1))
        assertEquals(
            RejectionReason.HEX_NEEDS_CLEARING,
            reasonOf(s, GameAction.BuyBuilding(BuildingType.TOWER, hex(1))),
        )
    }

    @Test
    fun `a port needs a coastal hex`() {
        // The strip map has no sea at all.
        assertEquals(
            RejectionReason.REQUIRES_COAST,
            reasonOf(base, GameAction.BuyBuilding(BuildingType.PORT, hex(1))),
        )
    }

    @Test
    fun `merging with an enemy unit is rejected as not your units`() {
        val s = base
            .withUnit(owner = 0, tier = 1, at = hex(3))
            .withUnit(owner = 1, tier = 1, at = hex(6))
        assertEquals(
            RejectionReason.NOT_YOUR_UNITS,
            reasonOf(s, GameAction.MergeUnits(s.unitIdAt(hex(3)), s.unitIdAt(hex(6)))),
        )
    }

    @Test
    fun `merging a unit with itself is rejected`() {
        val s = base.withUnit(owner = 0, tier = 1, at = hex(1))
        val id = s.unitIdAt(hex(1))
        assertEquals(
            RejectionReason.CANNOT_MERGE_WITH_SELF,
            reasonOf(s, GameAction.MergeUnits(id, id)),
        )
    }

    @Test
    fun `merging different tiers is rejected as a tier mismatch`() {
        val s = base
            .withUnit(owner = 0, tier = 1, at = hex(1))
            .withUnit(owner = 0, tier = 2, at = hex(2))
        assertEquals(
            RejectionReason.TIER_MISMATCH,
            reasonOf(s, GameAction.MergeUnits(s.unitIdAt(hex(1)), s.unitIdAt(hex(2)))),
        )
    }

    @Test
    fun `merging across unreachable distance is rejected as not in the same region`() {
        // Same connected region, but 5 steps is beyond a peasant's reach.
        val far = strip(9, 0..5, 7..8)
            .withUnit(owner = 0, tier = 1, at = hex(0))
            .withUnit(owner = 0, tier = 1, at = hex(5))
        assertEquals(
            RejectionReason.NOT_IN_SAME_REGION,
            reasonOf(far, GameAction.MergeUnits(far.unitIdAt(hex(0)), far.unitIdAt(hex(5)))),
        )
    }

    @Test
    fun `walking onto a mergeable friend via move is rejected - merging is its own action`() {
        val s = base
            .withUnit(owner = 0, tier = 1, at = hex(1))
            .withUnit(owner = 0, tier = 1, at = hex(2))
        assertEquals(
            RejectionReason.DESTINATION_HAS_UNIT,
            reasonOf(s, GameAction.MoveUnit(s.unitIdAt(hex(1)), hex(2))),
        )
    }

    @Test
    fun `buying onto an occupant of a different tier is rejected as incompatible`() {
        val s = base.withUnit(owner = 0, tier = 2, at = hex(1))
        assertEquals(
            RejectionReason.HEX_OCCUPIED_INCOMPATIBLE,
            reasonOf(s, GameAction.BuyUnit(1, hex(1))),
        )
    }

    @Test
    fun `buying onto a starving own hex is rejected as cut off`() {
        val s = base.copy(
            tiles = base.tiles + (hex(3) to base.tiles.getValue(hex(3)).copy(starving = true)),
        )
        assertEquals(
            RejectionReason.HEX_CUT_OFF,
            reasonOf(s, GameAction.BuyUnit(1, hex(3))),
        )
    }

    @Test
    fun `bombarding with anything but a warship is rejected`() {
        val sea = strip(9, 0..2, 6..8)
            .withSea(hex(5))
            .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.FISHING_BOAT)
            .withUnit(owner = 1, tier = 1, at = hex(6))
        assertEquals(
            RejectionReason.NOT_A_WARSHIP,
            reasonOf(sea, GameAction.Bombard(sea.unitIdAt(hex(5)), hex(6))),
        )
    }

    @Test
    fun `proposing a pact while one is active is rejected`() {
        val engine = GameEngine(base)
        assertTrue(engine.submit(GameAction.ProposePact(PlayerId(1), durationRounds = 2)) is LegalityResult.Ok)
        assertTrue(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
        assertTrue(engine.submit(GameAction.RespondPact(PlayerId(0), accept = true)) is LegalityResult.Ok)
        assertTrue(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
        val again = engine.submit(GameAction.ProposePact(PlayerId(1), durationRounds = 2))
        assertEquals(
            RejectionReason.PACT_ALREADY_ACTIVE,
            (again as LegalityResult.Rejected).reason,
        )
    }
}
