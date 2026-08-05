package com.msa.fightandconquer.core.editor

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.campaign.UnitPlacement
import com.msa.fightandconquer.core.map.TileDef
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The preview builder must accept anything the editor can transiently hold — an empty
 * canvas, a map with no capitals, water only — because it runs after every stroke.
 */
class EditorPreviewTest {

    @Test
    fun `empty canvas produces a state`() {
        val state = EditorPreview.state(emptyList(), seatCount = 0)
        assertTrue(state.tiles.isEmpty())
        assertEquals(1, state.players.size) // at least one seat so PlayerId(0) resolves
    }

    @Test
    fun `capital-less and sea-only maps do not throw`() {
        EditorPreview.state(listOf(TileDef(hex = hex(0), owner = 0)), seatCount = 2)
        EditorPreview.state(
            (0 until 4).map { TileDef(hex = hex(it), terrain = Terrain.SEA) },
            seatCount = 3,
        )
    }

    @Test
    fun `duplicate tiles last-wins instead of throwing`() {
        val state = EditorPreview.state(
            listOf(
                TileDef(hex = hex(0), terrain = Terrain.LAND),
                TileDef(hex = hex(0), terrain = Terrain.SEA),
            ),
            seatCount = 1,
        )
        assertEquals(Terrain.SEA, state.tiles[hex(0)]?.terrain)
    }

    @Test
    fun `illegal units are skipped not thrown`() {
        val tiles = listOf(
            TileDef(hex = hex(0), owner = 0),
            TileDef(hex = hex(1)),
            TileDef(hex = hex(2), terrain = Terrain.SEA),
        )
        val state = EditorPreview.state(
            tiles,
            seatCount = 1,
            units = listOf(
                UnitPlacement(seat = 0, hex = hex(0)), // fine: land unit on land
                UnitPlacement(seat = 0, hex = hex(0)), // stacked -> skipped
                UnitPlacement(seat = 0, hex = hex(2)), // land unit at sea -> skipped
                UnitPlacement(seat = 0, hex = hex(9)), // off-map -> skipped
                UnitPlacement(seat = 3, hex = hex(1)), // no such seat -> skipped
                UnitPlacement(seat = 0, hex = hex(2), type = UnitType.WARSHIP), // hex occupied? no - skipped land unit freed it
            ),
        )
        // The soldier on hex 0 and the warship on hex 2.
        assertEquals(2, state.units.size)
        assertTrue(state.tiles[hex(0)]?.unit != null)
        assertTrue(state.tiles[hex(2)]?.unit != null)
    }

    @Test
    fun `preview of a playable map matches its tile content`() {
        val state = EditorPreview.state(
            listOf(
                TileDef(hex = hex(0), owner = 0),
                TileDef(hex = hex(1), owner = 1),
            ),
            seatCount = 2,
        )
        assertEquals(2, state.players.size)
        assertEquals(0, state.tiles[hex(0)]?.owner?.value)
        assertEquals(1, state.tiles[hex(1)]?.owner?.value)
    }
}
