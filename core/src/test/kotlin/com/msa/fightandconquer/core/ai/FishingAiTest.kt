package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withDeposit
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fishing ladder: dories get bought at the port, sailed onto shoals, parked
 * forever, and disbanded when squatters leave nothing to work — while the rest
 * of the AI treats an enemy fisherman as prey, never as an invasion.
 *
 * Micro-map: P0 owns q 0..2 (port on 2), P1 owns q 6..8, open sea q 3..5.
 */
class FishingAiTest {

    private fun coast(treasury: Int = 60): GameState =
        strip(9, 0..2, 6..8)
            .withSea(listOf(hex(3), hex(4), hex(5)))
            .withBuilding(Building.PORT, hex(2))
            .withTreasury(0, treasury)

    @Test
    fun `the policy launches a dory at the port when an open shoal beckons`() {
        val s = coast().withDeposit(Deposit.FISH_SHOAL, hex(5))
        val action = FishingPolicy.action(s, Difficulty.NORMAL)
        assertTrue("expected a dory purchase, got $action", action is GameAction.BuyUnit)
        assertEquals(UnitType.FISHING_BOAT, (action as GameAction.BuyUnit).type)
        assertEquals(Terrain.SEA, s.tiles.getValue(action.at).terrain)
    }

    @Test
    fun `the policy sails an idle dory onto the shoal and then rests`() {
        val sailing = coast()
            .withDeposit(Deposit.FISH_SHOAL, hex(5))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.FISHING_BOAT)
        val action = FishingPolicy.action(sailing, Difficulty.NORMAL)
        assertEquals(hex(5), (action as GameAction.MoveUnit).to)

        val parked = coast()
            .withDeposit(Deposit.FISH_SHOAL, hex(5))
            .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.FISHING_BOAT)
        assertNull("a parked fleet needs nothing", FishingPolicy.action(parked, Difficulty.NORMAL))
    }

    @Test
    fun `the fleet never exceeds the open shoal count`() {
        // One shoal, already worked by our own dory: no second hull, ever.
        val s = coast(treasury = 200)
            .withDeposit(Deposit.FISH_SHOAL, hex(5))
            .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.FISHING_BOAT)
        assertNull(FishingPolicy.action(s, Difficulty.NORMAL))
    }

    @Test
    fun `a parked dory does not stall the next purchase`() {
        // Claim accounting is en-route boats vs open shoals: the parked hull
        // already subtracts its shoal via occupancy, so the second, still-open
        // shoal must trigger a second buy even when purchases are staggered.
        val s = coast(treasury = 200)
            .withDeposit(Deposit.FISH_SHOAL, hex(4))
            .withDeposit(Deposit.FISH_SHOAL, hex(5))
            .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.FISHING_BOAT)
        val action = FishingPolicy.action(s, Difficulty.NORMAL)
        assertTrue("expected a second dory purchase, got $action", action is GameAction.BuyUnit)
        assertEquals(UnitType.FISHING_BOAT, (action as GameAction.BuyUnit).type)
    }

    @Test
    fun `an own transiting hull does not scuttle an en-route dory`() {
        // A ferry staging on the only shoal is a one-turn transit, not a lost
        // shoal — disbanding the dory over it would be a coin pump.
        val s = coast()
            .withDeposit(Deposit.FISH_SHOAL, hex(5))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.FISHING_BOAT)
            .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.TRANSPORT)
        val action = FishingPolicy.action(s, Difficulty.NORMAL)
        assertTrue("expected patience, got $action", action !is GameAction.DisbandUnit)
    }

    @Test
    fun `easy never fishes`() {
        val s = coast().withDeposit(Deposit.FISH_SHOAL, hex(5))
        assertNull(FishingPolicy.action(s, Difficulty.EASY))
    }

    @Test
    fun `a surplus dory disbands once every shoal is own-worked`() {
        val s = coast()
            .withDeposit(Deposit.FISH_SHOAL, hex(5))
            .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.FISHING_BOAT)
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.FISHING_BOAT)
        val action = FishingPolicy.action(s, Difficulty.NORMAL)
        assertTrue("expected a disband, got $action", action is GameAction.DisbandUnit)
    }

    @Test
    fun `a squatted shoal is waited on - never the disband trigger`() {
        // Writing off a visibly squatted shoal is a fog money pump: the enemy
        // hull fades from view when this dory dies, the shoal reads open again,
        // and the policy buys a replacement forever. The idle hull waits.
        val s = coast()
            .withDeposit(Deposit.FISH_SHOAL, hex(5))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.FISHING_BOAT)
            .withUnit(owner = 1, tier = 1, at = hex(5), type = UnitType.FISHING_BOAT)
        val action = FishingPolicy.action(s, Difficulty.NORMAL)
        assertTrue("expected patience, got $action", action !is GameAction.DisbandUnit)
    }

    @Test
    fun `a dory re-targets when an enemy squats the nearer shoal`() {
        val s = coast()
            .withDeposit(Deposit.FISH_SHOAL, hex(4))
            .withDeposit(Deposit.FISH_SHOAL, hex(5))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.FISHING_BOAT)
            .withUnit(owner = 1, tier = 1, at = hex(4), type = UnitType.FISHING_BOAT)
        // hex(4) is visibly taken; the open goal is hex(5) — but the direct lane
        // through 4 is blocked, so with this strip there is no closing move at
        // all: the policy must NOT disband (a shoal is still open) and must not
        // thrash into the blocked hull.
        val action = FishingPolicy.action(s, Difficulty.NORMAL)
        assertTrue(
            "expected null or a move toward the open shoal, got $action",
            action == null || (action is GameAction.MoveUnit && action.to != hex(4)),
        )
    }

    @Test
    fun `enemy fishing boats alone do not trigger warship purchases`() {
        val s = coast(treasury = 100)
            .withDeposit(Deposit.FISH_SHOAL, hex(4))
            .withUnit(owner = 1, tier = 1, at = hex(4), type = UnitType.FISHING_BOAT)
        val warshipBuys = MoveGenerator.candidates(s, Difficulty.NORMAL)
            .filterIsInstance<GameAction.BuyUnit>()
            .filter { it.type == UnitType.WARSHIP }
        assertTrue("a dory is prey, not a threat: $warshipBuys", warshipBuys.isEmpty())

        val hardNaval = NavalPolicy.action(s, Difficulty.HARD)
        assertTrue(
            "HARD must not buy a hunter for a fisherman: $hardNaval",
            (hardNaval as? GameAction.BuyUnit)?.type != UnitType.WARSHIP,
        )
    }

    @Test
    fun `a warship sinks an adjacent parked dory through the greedy loop`() {
        // Treasury 10 keeps every policy quiet (no port/transport money) so the
        // argmax alone must find the kill worth taking.
        val s = strip(9, 0..2, 6..8)
            .withSea(listOf(hex(3), hex(4), hex(5)))
            .withTreasury(0, 10)
            .withDeposit(Deposit.FISH_SHOAL, hex(4))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.WARSHIP)
            .withUnit(owner = 1, tier = 1, at = hex(4), type = UnitType.FISHING_BOAT)
        val action = AiPlayer(Difficulty.NORMAL).chooseAction(s)
        assertEquals(GameAction.MoveUnit(s.tiles.getValue(hex(3)).unit!!, hex(4)), action)
    }

    @Test
    fun `fog hides an out-of-vision enemy dory from evaluation`() {
        val base = strip(15, 0..2, 12..14, rules = RuleConstants(fogOfWar = true))
            .withSea(listOf(hex(3), hex(4), hex(5), hex(6), hex(7)))
            .withDeposit(Deposit.FISH_SHOAL, hex(7))
        val withDory = base.withUnit(owner = 1, tier = 1, at = hex(7), type = UnitType.FISHING_BOAT)
        // hex(7) is far outside P0's vision: the score must not move.
        assertEquals(
            Evaluator.score(base, base.currentPlayer, Difficulty.NORMAL),
            Evaluator.score(withDory, base.currentPlayer, Difficulty.NORMAL),
            1e-9,
        )
    }

    @Test
    fun `normal AI buys a dory and parks it across a full game loop`() {
        // Quiet-front island: P0 fully owns a disc, P1 far away on its own island
        // (the NavalPolicy invasion runs in parallel; fishing must still happen).
        var state = coast(treasury = 80).withDeposit(Deposit.FISH_SHOAL, hex(5))
        val ai = AiPlayer(Difficulty.NORMAL)
        var parkedSeen = false
        var steps = 0
        while (state.phase is GamePhase.Playing && steps < 600 && !parkedSeen) {
            val action = ai.chooseAction(state)
            state = Reducer.reduce(state, action).state
            assertInvariants(state)
            parkedSeen = state.units.values.any { u ->
                u.type == UnitType.FISHING_BOAT &&
                    state.tiles.getValue(u.hex).deposit == Deposit.FISH_SHOAL
            }
            steps++
        }
        assertTrue("no dory ever parked on the shoal in $steps steps", parkedSeen)
    }
}
