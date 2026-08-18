package com.msa.fightandconquer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The civ delta table: KINGDOM and the gate-off path are strict identities (legacy
 * saves are all-Kingdom and must replay bit-identically), each civ lands exactly its
 * documented deltas and nothing else, and every derived value stays sane.
 */
class CivModifiersTest {

    private val base = RuleConstants()

    @Test
    fun `Kingdom is the identity - the very same instance`() {
        assertSame(base, CivModifiers.effective(base, Civilization.KINGDOM))
    }

    @Test
    fun `with the gate off every civ is the identity`() {
        val gated = RuleConstants(civBonusesEnabled = false)
        for (civ in Civilization.entries) {
            assertSame("$civ must be identity when gated off", gated, CivModifiers.effective(gated, civ))
        }
    }

    @Test
    fun `Vikings modify exactly their documented fields`() {
        assertEquals(
            base.copy(
                warshipStrength = base.warshipStrength + 1,
                transportCost = base.transportCost - 5,
                portCost = base.portCost - 5,
                farmCostStep = base.farmCostStep + 1,
                archerCost = base.archerCost + 4,
            ),
            CivModifiers.effective(base, Civilization.VIKINGS),
        )
    }

    @Test
    fun `Sultanate modifies exactly their documented fields`() {
        assertEquals(
            base.copy(
                marketCost = base.marketCost - 4,
                mineIncome = base.mineIncome + 1,
                farmCostBase = base.farmCostBase - 2,
                warshipCost = base.warshipCost + 5,
                lumberCampTreeIncome = base.lumberCampTreeIncome - 1,
            ),
            CivModifiers.effective(base, Civilization.SULTANATE),
        )
    }

    @Test
    fun `Shogunate modifies exactly their documented fields`() {
        assertEquals(
            base.copy(
                towerCost = base.towerCost - 3,
                watchtowerCost = base.watchtowerCost - 3,
                archerUpkeep = base.archerUpkeep - 1,
                catapultMoveRange = base.catapultMoveRange + 1,
                portCost = base.portCost + 5,
                transportCost = base.transportCost + 5,
            ),
            CivModifiers.effective(base, Civilization.SHOGUNATE),
        )
    }

    @Test
    fun `every effective cost, income, upkeep and stat stays positive at the defaults`() {
        for (civ in Civilization.entries) {
            val r = CivModifiers.effective(base, civ)
            val positives = listOf(
                r.farmCostBase, r.towerCost, r.strongTowerCost, r.mineCost, r.marketCost,
                r.lumberCampCost, r.watchtowerCost, r.portCost, r.fisheryCost, r.bridgeCost,
                r.archerCost, r.catapultCost, r.transportCost, r.warshipCost, r.fishingBoatCost,
                r.farmIncome, r.mineIncome, r.marketNeighborIncome, r.lumberCampTreeIncome,
                r.portIncome, r.fisheryShoalIncome, r.fishingBoatIncome,
            )
            assertTrue("$civ produced a non-positive cost/income: $positives", positives.all { it > 0 })
            val nonNegatives = listOf(
                r.farmCostStep, r.archerUpkeep, r.catapultUpkeep, r.transportUpkeep,
                r.warshipUpkeep, r.warshipStrength, r.catapultMoveRange,
                r.fishingBoatUpkeep, r.fishingBoatMoveRange,
            )
            assertTrue("$civ produced a negative value: $nonNegatives", nonNegatives.all { it >= 0 })
        }
    }

    @Test
    fun `a base config a delta would drive to zero is rejected`() {
        // Vikings' transportCost −5 would hit 0 — the sanity require must fire.
        val cheapBoats = RuleConstants(transportCost = 5)
        assertThrows(IllegalArgumentException::class.java) {
            CivModifiers.effective(cheapBoats, Civilization.VIKINGS)
        }
    }

    @Test
    fun `resolution is memoized per rules instance yet stays correct across instances`() {
        val a = RuleConstants()
        val b = RuleConstants(warshipStrength = 5)
        assertEquals(3, CivModifiers.effective(a, Civilization.VIKINGS).warshipStrength)
        assertEquals(6, CivModifiers.effective(b, Civilization.VIKINGS).warshipStrength)
        // Back to the first instance: the one-slot cache must recompute, not leak b's values.
        assertEquals(3, CivModifiers.effective(a, Civilization.VIKINGS).warshipStrength)
    }
}
