package com.msa.fightandconquer.core.map

import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.RuleConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepositGenerationTest {

    @Test
    fun `60 seeds place deposits fairly outside starts`() {
        for (seed in 1L..60L) {
            val playerCount = 2 + (seed % 5).toInt()
            val size = MapSize.entries[(seed % 2).toInt()]
            val params = MapParams(seed = seed, size = size, playerCount = playerCount)
            val map = MapGenerator.generate(params)

            assertTrue("seed $seed: ${MapValidator.validate(map, params)}", MapValidator.validate(map, params).isEmpty())
            assertTrue("seed $seed: deposit in a start region", map.tiles.none { it.owner != null && it.deposit != null })
            assertTrue("seed $seed: deposit under flora", map.tiles.none { it.flora != null && it.deposit != null })
        }
    }

    @Test
    fun `uncramped maps give every capital a vein within the band`() {
        val rules = RuleConstants()
        for (seed in 1L..20L) {
            val params = MapParams(seed = seed, size = MapSize.MEDIUM, playerCount = 2 + (seed % 2).toInt())
            val map = MapGenerator.generate(params)
            val veins = map.tiles.filter { it.deposit == Deposit.GOLD_VEIN }.map { it.hex }
            assertTrue("seed $seed: no veins on a medium map", veins.isNotEmpty())
            map.capitals.forEach { capital ->
                val nearest = veins.minOf { HexMath.distance(capital, it) }
                assertTrue(
                    "seed $seed: nearest vein at $nearest, outside band",
                    nearest in rules.goldVeinBandMin..rules.goldVeinBandMax,
                )
            }
        }
    }

    @Test
    fun `zeroed deposit rules disable all deposits`() {
        val rules = RuleConstants(
            goldVeinsPerPlayer = 0,
            goldVeinsNeutralPer150Hexes = 0,
            fertilePerPlayer = 0,
            fertileNeutralPercent = 0,
        )
        val map = MapGenerator.generate(MapParams(seed = 7, size = MapSize.MEDIUM, playerCount = 3), rules)
        assertEquals(0, map.tiles.count { it.deposit != null })
    }

    @Test
    fun `deposit placement is deterministic`() {
        val params = MapParams(seed = 31, size = MapSize.MEDIUM, playerCount = 4)
        val a = MapGenerator.generate(params).tiles.filter { it.deposit != null }
        val b = MapGenerator.generate(params).tiles.filter { it.deposit != null }
        assertEquals(a, b)
        assertTrue(a.isNotEmpty())
    }
}
