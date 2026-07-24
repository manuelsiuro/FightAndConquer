package com.msa.fightandconquer.core.map

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.Terrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The coastal sea band added by the naval expansion (phase 1: fringe on all shapes). */
class SeaMapTest {

    private fun generate(shape: MapShape, seed: Long = 7L) =
        MapGenerator.generate(MapParams(seed = seed, size = MapSize.SMALL, playerCount = 2, shape = shape))

    @Test
    fun `every shape gets a coastal sea band`() {
        for (shape in MapShape.entries) {
            val map = generate(shape)
            val sea = map.tiles.filter { it.terrain == Terrain.SEA }
            assertTrue("$shape should have sea", sea.isNotEmpty())
            // Sea is bare: no owner, buildings or flora — fish shoals only.
            sea.forEach {
                assertEquals(null, it.owner)
                assertEquals(null, it.building)
                assertEquals(null, it.flora)
                if (it.deposit != null) {
                    assertEquals(com.msa.fightandconquer.core.model.Deposit.FISH_SHOAL, it.deposit)
                }
            }
        }
    }

    @Test
    fun `sea is one navigable body hugging the land`() {
        for (shape in MapShape.entries) {
            val map = generate(shape)
            val sea = map.tiles.filter { it.terrain == Terrain.SEA }.map { it.hex }.toSet()
            val land = map.tiles.filter { it.terrain == Terrain.LAND }.map { it.hex }.toSet()
            assertEquals("$shape sea components", 1, HexMath.connectedComponents(sea).size)
            assertEquals("$shape land+sea connected", 1, HexMath.connectedComponents(land + sea).size)
            // Wide enough to be worth sailing (roughly the outer perimeter x2).
            assertTrue("$shape sea too small: ${sea.size}", sea.size >= 30)
        }
    }

    @Test
    fun `the coastal band is complete and inland basins become sea`() {
        for (shape in MapShape.entries) {
            val map = generate(shape)
            val land = map.tiles.filter { it.terrain == Terrain.LAND }.map { it.hex }.toSet()
            val sea = map.tiles.filter { it.terrain == Terrain.SEA }.map { it.hex }.toSet()
            val tiles = land + sea
            val fringe = MapGenerator.seaFringe(MapSize.SMALL)

            // Flood the void from a bounding rim: what it reaches is open tabletop,
            // what it can't is enclosed by the map.
            var minQ = Int.MAX_VALUE; var maxQ = Int.MIN_VALUE
            var minR = Int.MAX_VALUE; var maxR = Int.MIN_VALUE
            for (hex in tiles) {
                minQ = minOf(minQ, hex.q); maxQ = maxOf(maxQ, hex.q)
                minR = minOf(minR, hex.r); maxR = maxOf(maxR, hex.r)
            }
            minQ--; maxQ++; minR--; maxR++
            val outside = HashSet<Hex>()
            val queue = ArrayDeque<Hex>()
            fun seed(h: Hex) {
                if (h.q in minQ..maxQ && h.r in minR..maxR && h !in tiles && outside.add(h)) queue.add(h)
            }
            for (q in minQ..maxQ) { seed(Hex.of(q, minR)); seed(Hex.of(q, maxR)) }
            for (r in minR..maxR) { seed(Hex.of(minQ, r)); seed(Hex.of(maxQ, r)) }
            while (queue.isNotEmpty()) HexMath.forEachNeighbor(queue.removeFirst()) { seed(it) }

            for (q in minQ..maxQ) {
                for (r in minR..maxR) {
                    val hex = Hex.of(q, r)
                    if (hex in tiles) continue
                    if (hex in outside) {
                        // Band completeness: open void never comes within the fringe of land.
                        assertTrue(
                            "$shape open void $hex within $fringe of land",
                            HexMath.range(hex, fringe).none { it in land },
                        )
                    } else {
                        // Enclosed void may only be a land-sealed hole, never an
                        // unfilled basin bordering the ocean.
                        assertTrue(
                            "$shape enclosed void $hex borders the sea",
                            HexMath.neighbors(hex).none { it in sea },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `capitals, deposits and trees are on land only`() {
        for (shape in MapShape.entries) {
            val map = generate(shape)
            val landByHex = map.tiles.associateBy { it.hex }
            map.capitals.forEach {
                assertEquals("$shape capital on land", Terrain.LAND, landByHex.getValue(it).terrain)
            }
            map.tiles.forEach {
                val landFeature = it.flora != null || it.owner != null ||
                    (it.deposit != null && it.deposit != com.msa.fightandconquer.core.model.Deposit.FISH_SHOAL)
                if (landFeature) {
                    assertEquals("$shape features on land only", Terrain.LAND, it.terrain)
                }
            }
        }
    }

    @Test
    fun `sea is pre-discovered under fog of war`() {
        val map = generate(MapShape.CONTINENT)
        val state = map.newGame(gameSeed = 1L, kinds = listOf(PlayerKind.Human, PlayerKind.Human))
        if (!state.config.rules.fogOfWar) return // fog off by default -> nothing to check
        val sea = map.tiles.filter { it.terrain == Terrain.SEA }.map { it.hex }
        state.players.forEach { p ->
            assertTrue("all sea pre-discovered", p.discovered.containsAll(sea))
        }
    }
}
