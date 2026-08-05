package com.msa.fightandconquer.ui.editor

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.map.MapViolation
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Terrain
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EditorSessionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun session(): EditorSession {
        val store = CustomMapStore(File(tmp.root, "maps"))
        val def = MapTemplates.starter("map-1", "Test", createdAt = 1_000)
        store.save(def)
        return EditorSession(store, def)
    }

    private fun EditorSession.tile(hex: Hex) =
        ui.value.def.level.map.tiles.firstOrNull { it.hex == hex }

    @Test
    fun `starter opens clean and not dirty`() {
        val s = session()
        assertTrue(s.ui.value.violations.isEmpty())
        assertFalse(s.ui.value.dirty)
        assertFalse(s.ui.value.canUndo)
    }

    @Test
    fun `land brush grows the map and undo shrinks it back`() {
        val s = session()
        val outside = Hex.of(0, 4)
        assertNull(s.tile(outside))
        s.paint(outside)
        assertEquals(Terrain.LAND, s.tile(outside)?.terrain)
        assertTrue(s.ui.value.dirty)
        s.undo()
        assertNull(s.tile(outside))
    }

    @Test
    fun `painting the same terrain twice is not an undo step`() {
        val s = session()
        s.paint(Hex.of(0, 4))
        s.paint(Hex.of(0, 4)) // no-op: already land
        s.undo()
        assertNull(s.tile(Hex.of(0, 4)))
        assertFalse(s.ui.value.canUndo)
    }

    @Test
    fun `sea brush strips everything the sea contract forbids`() {
        val s = session()
        val owned = Hex.of(-1, 0) // inside seat 0's start
        s.setBrush(EditorSession.Brush.Sea)
        s.paint(owned)
        val tile = s.tile(owned)!!
        assertEquals(Terrain.SEA, tile.terrain)
        assertNull(tile.owner)
        assertNull(tile.building)
    }

    @Test
    fun `owner brush claims land but never sea`() {
        val s = session()
        val neutral = Hex.of(0, 1)
        s.setBrush(EditorSession.Brush.Owner(1))
        s.paint(neutral)
        assertEquals(1, s.tile(neutral)?.owner)

        s.setBrush(EditorSession.Brush.Sea)
        s.paint(Hex.of(0, -1))
        s.setBrush(EditorSession.Brush.Owner(0))
        val before = s.ui.value.def
        s.paint(Hex.of(0, -1))
        assertEquals(before, s.ui.value.def) // sea refused the owner brush
    }

    @Test
    fun `capital brush moves atomically`() {
        val s = session()
        val oldCapital = s.ui.value.def.level.map.capitals[0]
        val target = Hex.of(-1, 0) // inside seat 0's region: the territory stays whole
        s.setBrush(EditorSession.Brush.Capital(0))
        s.paint(target)
        val def = s.ui.value.def
        assertEquals(target, def.level.map.capitals[0])
        assertEquals(Building.CAPITAL, s.tile(target)?.building)
        assertEquals(0, s.tile(target)?.owner)
        assertNull(s.tile(oldCapital)?.building) // old marker cleared
        assertTrue(s.ui.value.violations.isEmpty()) // still playable after the move
    }

    @Test
    fun `moving a capital away from its territory is flagged, not blocked`() {
        val s = session()
        s.setBrush(EditorSession.Brush.Capital(0))
        s.paint(Hex.of(0, 2)) // neutral hex with no path to seat 0's old land
        assertTrue(s.ui.value.violations.any { it is MapViolation.SeatCutOffTiles })
    }

    @Test
    fun `capital brush on a new seat grows seats and stays consistent`() {
        val s = session()
        s.setBrush(EditorSession.Brush.Capital(2))
        s.paint(Hex.of(0, 2))
        val level = s.ui.value.def.level
        assertEquals(3, level.seats.size)
        assertEquals(3, level.map.capitals.size)
        assertEquals(Hex.of(0, 2), level.map.capitals[2])
        // New seat owns only its capital hex; that is a valid single-hex region.
        assertTrue(s.ui.value.violations.isEmpty())
    }

    @Test
    fun `erasing a capital hex leaves a draft not a crash`() {
        val s = session()
        val capital = s.ui.value.def.level.map.capitals[0]
        s.setBrush(EditorSession.Brush.Erase)
        s.paint(capital)
        assertTrue(s.ui.value.violations.contains(MapViolation.CapitalOffMap(0)))
        s.previewState() // draft must still render
    }

    @Test
    fun `save clears dirty and survives a reload`() {
        val store = CustomMapStore(File(tmp.root, "maps"))
        val def = MapTemplates.starter("map-2", "Persist", createdAt = 1_000)
        store.save(def)
        val s = EditorSession(store, def)
        s.paint(Hex.of(0, 4))
        s.save(now = 2_000)
        assertFalse(s.ui.value.dirty)
        val reloaded = CustomMapStore(File(tmp.root, "maps")).load("map-2")!!
        assertEquals(2_000, reloaded.modifiedAt)
        assertTrue(reloaded.level.map.tiles.any { it.hex == Hex.of(0, 4) })
    }

    @Test
    fun `growth ring surrounds the whole map and only the map`() {
        val s = session()
        val present = s.ui.value.def.level.map.tiles.map { it.hex }.toSet()
        val ring = s.growthRing()
        assertTrue(ring.isNotEmpty())
        assertTrue(ring.none { it in present })
    }
}
