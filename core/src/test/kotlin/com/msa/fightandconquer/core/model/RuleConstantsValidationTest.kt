package com.msa.fightandconquer.core.model

import com.msa.fightandconquer.core.campaign.CampaignAssets
import com.msa.fightandconquer.core.persist.CompatJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

/**
 * [RuleConstants]' init block: the documented "MUST stay" contracts turned into
 * loud load-time failures. Every shipped configuration must pass; every
 * documented invariant must actually throw when violated (a hand-edited save
 * fails at decode instead of crashing the engine mid-game).
 */
class RuleConstantsValidationTest {

    @Test
    fun `default rules pass validation`() {
        RuleConstants() // init runs here; throwing would fail the test
    }

    @Test
    fun `every shipped campaign level's rules pass validation`() {
        val levels = CampaignAssets.campaigns().flatMap { it.levels }
        assertTrue("campaigns must ship levels", levels.isNotEmpty())
        levels.forEach { it.rules } // decoded through the init block already
    }

    @Test
    fun `every civilization's effective rules pass validation`() {
        for (civ in Civilization.entries) {
            CivModifiers.effective(RuleConstants(), civ)
        }
    }

    @Test
    fun `a tutorial-style zeroed economy decodes and validates`() {
        // The academy ships exactly this shape: free upkeep, no hex income, tier cap 1.
        val rules = CompatJson.decodeFromString<RuleConstants>(
            """{"maxTier":1,"hexIncome":0,"unitUpkeep":[0,0,0,0]}""",
        )
        assertEquals(1, rules.maxTier)
        assertEquals(0, rules.hexIncome)
    }

    @Test
    fun `maxTier above the price lists throws instead of crashing mid-game`() {
        // Without validation this surfaced later as IndexOutOfBounds in
        // Rules.costIn/upkeepIn the first time a top-tier unit was priced.
        assertThrows(IllegalArgumentException::class.java) { RuleConstants(maxTier = 5) }
        assertThrows(IllegalArgumentException::class.java) { RuleConstants(maxTier = 0) }
    }

    @Test
    fun `non-positive unit costs throw`() {
        assertThrows(IllegalArgumentException::class.java) {
            RuleConstants(unitCost = listOf(10, 0, 30, 40))
        }
    }

    @Test
    fun `empty soldier move ranges throw`() {
        assertThrows(IllegalArgumentException::class.java) { RuleConstants(soldierMoveRanges = emptyList()) }
    }

    @Test
    fun `fog contract - vision radius below two throws`() {
        assertThrows(IllegalArgumentException::class.java) { RuleConstants(visionRadiusOwned = 1) }
    }

    @Test
    fun `fog contract - naval move range above unit vision throws`() {
        assertThrows(IllegalArgumentException::class.java) { RuleConstants(warshipMoveRange = 4) }
        assertThrows(IllegalArgumentException::class.java) { RuleConstants(transportMoveRange = 4) }
        assertThrows(IllegalArgumentException::class.java) { RuleConstants(fishingBoatMoveRange = 4) }
    }

    @Test
    fun `fog contract - fishery range above owned vision throws`() {
        assertThrows(IllegalArgumentException::class.java) { RuleConstants(fisheryRange = 3) }
    }

    @Test
    fun `full demolish refund throws - build-then-demolish must lose money`() {
        assertThrows(IllegalArgumentException::class.java) { RuleConstants(demolishRefundPercent = 100) }
    }

    @Test
    fun `inverted generation and pact bands throw`() {
        assertThrows(IllegalArgumentException::class.java) {
            RuleConstants(goldVeinBandMin = 7, goldVeinBandMax = 6)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RuleConstants(fishShoalBandMin = 7, fishShoalBandMax = 6)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RuleConstants(pactMinDurationRounds = 11, pactMaxDurationRounds = 10)
        }
    }

    @Test
    fun `out-of-range percents throw`() {
        assertThrows(IllegalArgumentException::class.java) { RuleConstants(capitalLootPercent = 101) }
        assertThrows(IllegalArgumentException::class.java) { RuleConstants(treeSpreadPercent = -1) }
        assertThrows(IllegalArgumentException::class.java) { RuleConstants(pactBreakPenaltyPercent = 101) }
    }
}
