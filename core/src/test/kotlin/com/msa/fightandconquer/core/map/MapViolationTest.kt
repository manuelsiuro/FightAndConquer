package com.msa.fightandconquer.core.map

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Terrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The typed violation codes and the legacy prose API must stay one thing: the string
 * list is defined as `codes.map { describe() }`, and this suite pins that the wording
 * of every long-standing message survived the refactor.
 */
class MapViolationTest {

    private fun strip(
        tiles: List<TileDef>,
        capitals: List<com.msa.fightandconquer.core.hex.Hex>,
    ) = MapDefinition(name = "t", tiles = tiles, capitals = capitals)

    private val valid = strip(
        tiles = (0 until 6).map { q ->
            TileDef(
                hex = hex(q),
                owner = if (q <= 1) 0 else if (q >= 4) 1 else null,
                building = if (q == 0 || q == 5) Building.CAPITAL else null,
            )
        },
        capitals = listOf(hex(0), hex(5)),
    )

    @Test
    fun `valid strip has no violations in either api`() {
        assertEquals(emptyList<MapViolation>(), MapValidator.validateAuthoredCodes(valid))
        assertEquals(emptyList<String>(), MapValidator.validateAuthored(valid))
    }

    @Test
    fun `string api is exactly describe over the codes`() {
        val broken = listOf(
            strip(emptyList(), emptyList()),
            strip(valid.tiles + TileDef(hex = hex(0)), valid.capitals),
            valid.copy(capitals = listOf(hex(0), hex(4))),
            valid.copy(tiles = valid.tiles.map { if (it.hex == hex(4)) it.copy(owner = null) else it }),
        )
        broken.forEach { map ->
            assertEquals(
                MapValidator.validateAuthoredCodes(map).map { it.describe() },
                MapValidator.validateAuthored(map),
            )
        }
    }

    @Test
    fun `legacy wording is preserved`() {
        assertEquals(listOf("map has no land"), MapValidator.validateAuthored(strip(emptyList(), emptyList())))
        val noTerritory = valid.copy(
            tiles = valid.tiles.map { if (it.hex == hex(1)) it.copy(owner = null) else it },
        )
        assertTrue(MapValidator.validateAuthored(noTerritory).isEmpty())
        val orphan = valid.copy(
            tiles = valid.tiles.map { if (it.hex == hex(2)) it.copy(owner = 3) else it },
        )
        assertEquals(
            listOf("tiles owned by seat 3, which has no capital"),
            MapValidator.validateAuthored(orphan),
        )
    }

    @Test
    fun `sea contract violations carry their hexes`() {
        val badSea = valid.copy(
            tiles = valid.tiles + TileDef(hex = hex(0, 1), terrain = Terrain.SEA, owner = 0) +
                TileDef(hex = hex(1, 1), terrain = Terrain.SEA, deposit = Deposit.GOLD_VEIN),
        )
        val codes = MapValidator.validateAuthoredCodes(badSea)
        assertTrue(codes.contains(MapViolation.SeaTileOwned(hex(0, 1))))
        assertTrue(codes.contains(MapViolation.SeaTileLandDeposit(hex(1, 1))))
    }

    @Test
    fun `cut off territory is counted`() {
        // Seat 0 owns hex 0 (capital) and hex 3, with unowned land between them.
        val cutOff = valid.copy(
            tiles = valid.tiles.map {
                when (it.hex) {
                    hex(1) -> it.copy(owner = null)
                    hex(3) -> it.copy(owner = 0)
                    else -> it
                }
            },
        )
        assertTrue(
            MapValidator.validateAuthoredCodes(cutOff)
                .contains(MapViolation.SeatCutOffTiles(0, 1)),
        )
    }
}
