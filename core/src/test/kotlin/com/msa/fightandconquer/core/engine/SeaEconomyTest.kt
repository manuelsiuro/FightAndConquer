package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fishery income, shoal rules and the port's trade income. */
class SeaEconomyTest {

    private val rules = RuleConstants()

    private fun GameState.withShoal(at: com.msa.fightandconquer.core.hex.Hex): GameState =
        copy(tiles = tiles + (at to Tile(terrain = Terrain.SEA, deposit = Deposit.FISH_SHOAL)))

    /** P0 q 0..2 with sea above hex(1): (1,-1) and (2,-1) shoal-able. */
    private fun coast(): GameState =
        strip(9, 0..2, 6..8).withSea(listOf(hex(1, -1), hex(2, -1), hex(3)))

    @Test
    fun `a fishery needs a shoal within range two`() {
        val engine = GameEngine(coast())
        val bare = engine.submit(GameAction.BuyBuilding(BuildingType.FISHERY, hex(1)))
        val rejected = bare as LegalityResult.Rejected
        assertEquals(RejectionReason.FISHERY_NEEDS_SHOAL, rejected.reason)
        assertEquals(rules.fisheryRange, rejected.amount)

        // A shoal at exactly range 2 carries the placement.
        val atTwo = GameEngine(coast().withShoal(hex(1, -2)))
        assertTrue(atTwo.submit(GameAction.BuyBuilding(BuildingType.FISHERY, hex(1))) is LegalityResult.Ok)

        // A shoal at range 3 alone does not.
        val atThree = GameEngine(coast().withShoal(hex(1, -3)))
        assertEquals(
            RejectionReason.FISHERY_NEEDS_SHOAL,
            (atThree.submit(GameAction.BuyBuilding(BuildingType.FISHERY, hex(1))) as LegalityResult.Rejected).reason,
        )
    }

    @Test
    fun `fishery income scales with shoals in range up to the cap`() {
        val base = coast().withShoal(hex(1, -1))
        val without = Rules.incomeOf(base, PlayerId(0))
        val one = base.withBuilding(Building.FISHERY, hex(1))
        assertEquals(without + rules.fisheryShoalIncome, Rules.incomeOf(one, PlayerId(0)))

        // An adjacent shoal and a range-2 shoal both count; a range-3 one never does.
        val two = coast().withShoal(hex(1, -1)).withShoal(hex(1, -2))
            .withBuilding(Building.FISHERY, hex(1))
        assertEquals(without + 2 * rules.fisheryShoalIncome, Rules.incomeOf(two, PlayerId(0)))
        val plusFar = two.withShoal(hex(1, -3))
        assertEquals(without + 2 * rules.fisheryShoalIncome, Rules.incomeOf(plusFar, PlayerId(0)))

        // Four shoals in range still pay only the cap.
        val four = coast()
            .withShoal(hex(1, -1)).withShoal(hex(2, -1))
            .withShoal(hex(1, -2)).withShoal(hex(2, -2))
            .withBuilding(Building.FISHERY, hex(1))
        assertEquals(
            without + rules.fisheryShoalCap * rules.fisheryShoalIncome,
            Rules.incomeOf(four, PlayerId(0)),
        )
    }

    @Test
    fun `ports pay their trade income`() {
        val base = strip(9, 0..2, 6..8).withSea(hex(3))
        val without = Rules.incomeOf(base, PlayerId(0))
        val withPort = base.withBuilding(Building.PORT, hex(2))
        assertEquals(without + rules.portIncome, Rules.incomeOf(withPort, PlayerId(0)))
    }

    @Test
    fun `a shoal hex itself yields nothing without a fishery`() {
        val base = coast()
        assertEquals(
            Rules.incomeOf(base, PlayerId(0)),
            Rules.incomeOf(base.withShoal(hex(1, -1)), PlayerId(0)),
        )
    }
}
