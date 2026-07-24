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
    fun `a fishery needs an adjacent shoal`() {
        val engine = GameEngine(coast())
        val bare = engine.submit(GameAction.BuyBuilding(BuildingType.FISHERY, hex(1)))
        assertEquals(
            RejectionReason.BUILDING_NEEDS_DEPOSIT,
            (bare as LegalityResult.Rejected).reason,
        )
        val withShoal = GameEngine(coast().withShoal(hex(1, -1)))
        assertTrue(withShoal.submit(GameAction.BuyBuilding(BuildingType.FISHERY, hex(1))) is LegalityResult.Ok)
    }

    @Test
    fun `fishery income scales with adjacent shoals up to the cap`() {
        val base = coast().withShoal(hex(1, -1))
        val without = Rules.incomeOf(base, PlayerId(0))
        val one = base.withBuilding(Building.FISHERY, hex(1))
        assertEquals(without + rules.fisheryShoalIncome, Rules.incomeOf(one, PlayerId(0)))

        val two = coast().withShoal(hex(1, -1)).withShoal(hex(2, -1))
            .withBuilding(Building.FISHERY, hex(1))
        assertEquals(
            without + 2 * rules.fisheryShoalIncome,
            Rules.incomeOf(two, PlayerId(0)),
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
