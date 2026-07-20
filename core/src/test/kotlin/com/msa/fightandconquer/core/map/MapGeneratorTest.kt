package com.msa.fightandconquer.core.map

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.PlayerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapGeneratorTest {

    @Test
    fun `200 seeds produce valid maps across shapes and player counts`() {
        var checked = 0
        for (seed in 1L..200L) {
            val shape = MapShape.entries[(seed % 3).toInt()]
            val playerCount = 2 + (seed % 5).toInt() // 2..6
            val size = MapSize.entries[(seed % 2).toInt()] // SMALL/MEDIUM keeps the test fast
            val params = MapParams(seed = seed, size = size, playerCount = playerCount, shape = shape)
            val map = MapGenerator.generate(params)
            val violations = MapValidator.validate(map, params)
            assertTrue("seed $seed ($params): $violations", violations.isEmpty())
            checked++
        }
        assertEquals(200, checked)
    }

    @Test
    fun `identical params produce the identical map`() {
        val params = MapParams(seed = 99, size = MapSize.MEDIUM, playerCount = 4, shape = MapShape.ISLANDS)
        assertEquals(MapGenerator.generate(params), MapGenerator.generate(params))
    }

    @Test
    fun `land size is close to the target`() {
        val params = MapParams(seed = 5, size = MapSize.MEDIUM, playerCount = 2)
        val map = MapGenerator.generate(params)
        val land = map.tiles.size
        assertTrue(
            "expected ~${MapSize.MEDIUM.targetHexes} hexes, got $land",
            land >= MapSize.MEDIUM.targetHexes && land <= MapSize.MEDIUM.targetHexes * 3 / 2,
        )
    }

    @Test
    fun `each player starts with an equal 7-hex region`() {
        val map = MapGenerator.generate(MapParams(seed = 11, playerCount = 3))
        for (player in 0..2) {
            assertEquals(7, map.tiles.count { it.owner == player })
        }
    }

    @Test
    fun `hexLine is contiguous`() {
        val line = MapGenerator.hexLine(Hex.of(-5, 2), Hex.of(7, -4))
        for (i in 1 until line.size) {
            assertEquals("gap at $i in $line", 1, HexMath.distance(line[i - 1], line[i]))
        }
    }

    @Test
    fun `generated map starts a playable game`() {
        val map = MapGenerator.generate(MapParams(seed = 21, playerCount = 2))
        val state = map.newGame(
            gameSeed = 1234,
            kinds = listOf(PlayerKind.Human, PlayerKind.Human),
        )
        assertEquals(2, state.players.size)
        assertEquals(12, state.players[0].treasury)
        // Both capitals exist on owned tiles.
        state.players.forEach { p ->
            val capital = p.capital!!
            assertEquals(p.id, state.tiles.getValue(capital).owner)
        }
    }
}
