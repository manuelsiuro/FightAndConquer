package com.msa.fightandconquer.core.map

import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.RuleConstants
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

    @Test
    fun `per-capital shoals are fishery-workable on most seeds`() {
        // The generator walks the band for a target every capital can serve with
        // workable water (within fisheryRange of land), falling back to offshore
        // placement only when no target fits — so on the seeds that place per-capital
        // shoals at all, nearly all of them should give every capital a workable one
        // inside the band.
        val rules = RuleConstants()
        var withBandShoals = 0
        var allWorkable = 0
        for (seed in 1L..30L) {
            val params = MapParams(
                seed = seed,
                size = MapSize.SMALL,
                playerCount = 2 + (seed % 3).toInt(),
                shape = MapShape.entries[(seed % 3).toInt()],
            )
            val map = MapGenerator.generate(params)
            val land = map.tiles.filter { it.terrain == Terrain.LAND }.map { it.hex }.toSet()
            val shoals = map.tiles.filter { it.deposit == Deposit.FISH_SHOAL }.map { it.hex }
            val band = rules.fishShoalBandMin..rules.fishShoalBandMax
            val everyCapitalServed = map.capitals.all { capital ->
                shoals.any { HexMath.distance(it, capital) in band }
            }
            if (!everyCapitalServed) continue
            withBandShoals++
            val everyCapitalWorkable = map.capitals.all { capital ->
                shoals.any { shoal ->
                    HexMath.distance(shoal, capital) in band &&
                        HexMath.range(shoal, rules.fisheryRange).any { it in land }
                }
            }
            if (everyCapitalWorkable) allWorkable++
        }
        assertTrue("no seed placed per-capital shoals", withBandShoals > 0)
        assertTrue(
            "workable shoals on $allWorkable of $withBandShoals shoal-carrying maps",
            allWorkable * 10 >= withBandShoals * 9,
        )
    }
}
