package com.msa.fightandconquer.core.map

import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Terrain
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fish shoals: sea-only, fairly spread (the validator's tripwire enforces both). */
class FishShoalGenerationTest {

    @Test
    fun `shoals are generated at sea and pass fairness across seeds`() {
        var withShoals = 0
        for (seed in 1L..30L) {
            val params = MapParams(
                seed = seed,
                size = MapSize.SMALL,
                playerCount = 2 + (seed % 3).toInt(),
                shape = MapShape.entries[(seed % 3).toInt()],
            )
            val map = MapGenerator.generate(params)
            // Validator already ran inside generate(); assert the shoal contract here.
            val shoals = map.tiles.filter { it.deposit == Deposit.FISH_SHOAL }
            shoals.forEach { assertTrue("shoal on sea", it.terrain == Terrain.SEA) }
            if (shoals.isNotEmpty()) withShoals++
        }
        assertTrue("most maps should carry shoals ($withShoals/30)", withShoals >= 20)
    }
}
