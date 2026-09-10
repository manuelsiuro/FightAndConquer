package com.msa.fightandconquer.ui.menu

import com.msa.fightandconquer.core.map.MapShape
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.PlayerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The menu backdrop must be reproducible from its seed (same seed → same world) and
 * varied across seeds (different shapes, different seat counts, distinct civs so the
 * capital art differs on screen).
 */
class MenuWorldTest {

    private val seeds = (1_000L until 1_030L).toList()

    @Test
    fun `same seed yields an identical world`() {
        for (seed in listOf(0L, 1L, -7L, 987_654_321L)) {
            assertEquals("world for seed $seed", MenuWorld.generate(seed), MenuWorld.generate(seed))
            assertEquals("params for seed $seed", MenuWorld.params(seed), MenuWorld.params(seed))
        }
    }

    @Test
    fun `every seed builds a showcase world of AI seats with distinct civs`() {
        for (seed in seeds) {
            val params = MenuWorld.params(seed)
            assertEquals("size for seed $seed", MapSize.MEDIUM, params.size)
            assertTrue("playerCount ${params.playerCount} for seed $seed", params.playerCount in 2..4)

            val state = MenuWorld.generate(seed)
            assertTrue("players ${state.players.size} for seed $seed", state.players.size in 2..4)
            assertEquals("seats match params for seed $seed", params.playerCount, state.players.size)
            assertTrue(
                "all seats AI for seed $seed",
                state.players.all { it.kind is PlayerKind.Ai },
            )
            val civs = state.players.map { it.civ }
            assertEquals("distinct civs for seed $seed", civs.size, civs.toSet().size)
            assertTrue("tiles for seed $seed", state.tiles.isNotEmpty())
            assertEquals("fog off for seed $seed", false, state.config.rules.fogOfWar)
        }
    }

    @Test
    fun `seeds vary shape and seat count`() {
        val shapes = HashSet<MapShape>()
        val counts = HashSet<Int>()
        for (seed in seeds) {
            val params = MenuWorld.params(seed)
            shapes.add(params.shape)
            counts.add(params.playerCount)
        }
        assertTrue("shapes seen: $shapes", shapes.size >= 2)
        assertTrue("player counts seen: $counts", counts.size >= 2)
    }

    @Test
    fun `civs are distinct and stable for a seed`() {
        for (seed in seeds) {
            val count = MenuWorld.params(seed).playerCount
            val civs = MenuWorld.civs(seed, count)
            assertEquals("civ count for seed $seed", count, civs.size)
            assertEquals("distinct civs for seed $seed", count, civs.toSet().size)
            assertEquals("stable civs for seed $seed", civs, MenuWorld.civs(seed, count))
        }
    }
}
