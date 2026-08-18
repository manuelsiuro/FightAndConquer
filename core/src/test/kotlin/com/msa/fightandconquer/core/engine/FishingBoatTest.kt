package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withDeposit
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fishing dory: bought like any boat, parks on a FISH_SHOAL sea hex, and is
 * the game's only income-producing unit — [Rules.boatIncomeFrom] pays it at its
 * owner's turn start. Two islands (P0 q 0..2, P1 q 6..8), open sea q 3..5.
 */
class FishingBoatTest {

    private val rules = RuleConstants()

    private fun islands(): GameState = strip(9, 0..2, 6..8).withSea(listOf(hex(3), hex(4), hex(5)))

    private fun GameState.endTurn(): ReduceResult = Reducer.reduce(this, GameAction.EndTurn)

    private fun turnStartOf(state: GameState): GameEvent.TurnStarted =
        state.endTurn().events.filterIsInstance<GameEvent.TurnStarted>().single()

    @Test
    fun `fishing boats are bought at sea beside an own working port`() {
        val noPort = GameEngine(islands()).submit(GameAction.BuyUnit(1, hex(3), UnitType.FISHING_BOAT))
        assertEquals(RejectionReason.NO_ADJACENT_PORT, (noPort as LegalityResult.Rejected).reason)

        val withPort = GameEngine(islands().withBuilding(Building.PORT, hex(2)))
        val onLand = withPort.submit(GameAction.BuyUnit(1, hex(1), UnitType.FISHING_BOAT))
        assertEquals(RejectionReason.REQUIRES_SEA, (onLand as LegalityResult.Rejected).reason)

        assertTrue(withPort.submit(GameAction.BuyUnit(1, hex(3), UnitType.FISHING_BOAT)) is LegalityResult.Ok)
        val after = withPort.state.value
        val boat = after.unitAt(hex(3))!!
        assertEquals(UnitType.FISHING_BOAT, boat.type)
        assertFalse("launches fresh", boat.spent)
        assertInvariants(after)
    }

    @Test
    fun `a fishing boat sails open sea and can park on a shoal hex`() {
        val s = islands()
            .withDeposit(Deposit.FISH_SHOAL, hex(5))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.FISHING_BOAT)
        val boat = s.unitIdAt(hex(3))
        assertEquals(setOf(hex(4), hex(5)), Rules.reachable(s, boat).moveTargets)

        val engine = GameEngine(s)
        assertTrue(engine.submit(GameAction.MoveUnit(boat, hex(5))) is LegalityResult.Ok)
        assertEquals(hex(5), engine.state.value.units.getValue(boat).hex)
        assertInvariants(engine.state.value)
    }

    @Test
    fun `a parked fishing boat earns its income at turn start`() {
        // P1: 3 hexes (income 3) + a dory parked on the shoal.
        val s = islands()
            .withDeposit(Deposit.FISH_SHOAL, hex(4))
            .withUnit(owner = 1, tier = 1, at = hex(4), type = UnitType.FISHING_BOAT)
        assertEquals(3 + rules.fishingBoatIncome, Rules.incomeOf(s, PlayerId(1)))
        val started = turnStartOf(s)
        assertEquals(3 + rules.fishingBoatIncome, started.income)
        assertEquals(rules.fishingBoatUpkeep, started.upkeep)
    }

    @Test
    fun `an unparked fishing boat earns nothing and still pays upkeep`() {
        val s = islands()
            .withDeposit(Deposit.FISH_SHOAL, hex(5))
            .withUnit(owner = 1, tier = 1, at = hex(3), type = UnitType.FISHING_BOAT)
        assertEquals(3, Rules.incomeOf(s, PlayerId(1)))
        val started = turnStartOf(s)
        assertEquals(3, started.income)
        assertEquals(rules.fishingBoatUpkeep, started.upkeep)
    }

    @Test
    fun `a parked boat earns regardless of having acted`() {
        val s = islands()
            .withDeposit(Deposit.FISH_SHOAL, hex(4))
            .withUnit(owner = 1, tier = 1, at = hex(4), spent = true, type = UnitType.FISHING_BOAT)
        assertEquals(3 + rules.fishingBoatIncome, turnStartOf(s).income)
    }

    @Test
    fun `only one boat fits on a shoal - an occupant blocks the water`() {
        // An enemy dory parked on the shoal: not enterable, not attackable.
        val enemyParked = islands()
            .withDeposit(Deposit.FISH_SHOAL, hex(4))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.FISHING_BOAT)
            .withUnit(owner = 1, tier = 1, at = hex(4), type = UnitType.FISHING_BOAT)
        val blocked = Rules.reachable(enemyParked, enemyParked.unitIdAt(hex(3)))
        assertFalse(hex(4) in blocked.moveTargets)
        assertTrue("fishing boats never attack", blocked.captureTargets.isEmpty())

        // An own dory blocks it just the same.
        val ownParked = islands()
            .withDeposit(Deposit.FISH_SHOAL, hex(4))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.FISHING_BOAT)
            .withUnit(owner = 0, tier = 1, at = hex(4), type = UnitType.FISHING_BOAT)
        assertFalse(hex(4) in Rules.reachable(ownParked, ownParked.unitIdAt(hex(3))).moveTargets)
    }

    @Test
    fun `a fishing boat never attacks`() {
        val s = islands()
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.FISHING_BOAT)
            .withUnit(owner = 1, tier = 1, at = hex(4), type = UnitType.TRANSPORT)
        val reach = Rules.reachable(s, s.unitIdAt(hex(3)))
        assertTrue(reach.captureTargets.isEmpty())
        assertTrue("blocked by the enemy hull, no way around", reach.moveTargets.isEmpty())
    }

    @Test
    fun `a warship sinks a parked fishing boat and earns nothing on the shoal itself`() {
        val s = islands()
            .withDeposit(Deposit.FISH_SHOAL, hex(4))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.WARSHIP)
            .withUnit(owner = 1, tier = 1, at = hex(4), type = UnitType.FISHING_BOAT)
        val attacker = s.unitIdAt(hex(3))
        assertTrue(hex(4) in Rules.reachable(s, attacker).captureTargets)

        val engine = GameEngine(s)
        assertTrue(engine.submit(GameAction.MoveUnit(attacker, hex(4))) is LegalityResult.Ok)
        val after = engine.state.value
        assertEquals(1, after.units.size)
        assertEquals(hex(4), after.units.getValue(attacker).hex)
        // Only dories harvest: the conquering warship squats the shoal for nothing.
        assertEquals(3, Rules.incomeOf(after, PlayerId(0)))
        assertInvariants(after)
    }

    @Test
    fun `a fishery in range and a parked boat harvest the same shoal simultaneously`() {
        val s = islands()
            .withDeposit(Deposit.FISH_SHOAL, hex(5))
            .withBuilding(Building.FISHERY, hex(6))
            .withUnit(owner = 1, tier = 1, at = hex(5), type = UnitType.FISHING_BOAT)
        assertEquals(
            3 + rules.fisheryShoalIncome + rules.fishingBoatIncome,
            Rules.incomeOf(s, PlayerId(1)),
        )
    }
}
