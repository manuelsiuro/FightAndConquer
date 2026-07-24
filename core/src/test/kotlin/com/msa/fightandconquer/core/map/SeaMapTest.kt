package com.msa.fightandconquer.core.map

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
            // Sea is bare: no owner, buildings, flora or deposits.
            sea.forEach {
                assertEquals(null, it.owner)
                assertEquals(null, it.building)
                assertEquals(null, it.flora)
                assertEquals(null, it.deposit)
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
            if (shape == MapShape.CONTINENT) {
                // The continental band never drifts away from the coast (island
                // shapes also carry open-water corridors between the islands).
                sea.forEach { hex ->
                    assertTrue(
                        "$shape sea hex $hex further than ${MapGenerator.SEA_FRINGE} from land",
                        HexMath.range(hex, MapGenerator.SEA_FRINGE).any { it in land },
                    )
                }
            }
            // Wide enough to be worth sailing (roughly the outer perimeter x2).
            assertTrue("$shape sea too small: ${sea.size}", sea.size >= 30)
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
                if (it.deposit != null || it.flora != null || it.owner != null) {
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
