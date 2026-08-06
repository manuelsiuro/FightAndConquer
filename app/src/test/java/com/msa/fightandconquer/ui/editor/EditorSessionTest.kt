package com.msa.fightandconquer.ui.editor

import com.msa.fightandconquer.core.campaign.Objective
import com.msa.fightandconquer.core.campaign.SeatDef
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.map.MapViolation
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.UnitType
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
    fun `owner brush claims land for the active seat but never sea`() {
        val s = session()
        val neutral = Hex.of(0, 1)
        s.setActiveSeat(1)
        s.setBrush(EditorSession.Brush.Owner)
        s.paint(neutral)
        assertEquals(1, s.tile(neutral)?.owner)

        s.setBrush(EditorSession.Brush.Sea)
        s.paint(Hex.of(0, -1))
        s.setActiveSeat(0)
        s.setBrush(EditorSession.Brush.Owner)
        val before = s.ui.value.def
        s.paint(Hex.of(0, -1))
        assertEquals(before, s.ui.value.def) // sea refused the owner brush
    }

    @Test
    fun `capital brush moves atomically`() {
        val s = session()
        val oldCapital = s.ui.value.def.level.map.capitals[0]
        val target = Hex.of(-1, 0) // inside seat 0's region: the territory stays whole
        s.setActiveSeat(0)
        s.setBrush(EditorSession.Brush.Capital)
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
        s.setActiveSeat(0)
        s.setBrush(EditorSession.Brush.Capital)
        s.paint(Hex.of(0, 2)) // neutral hex with no path to seat 0's old land
        assertTrue(s.ui.value.violations.any { it is MapViolation.SeatCutOffTiles })
    }

    @Test
    fun `capital on a pending new seat grows seats and stays consistent`() {
        val s = session()
        s.setActiveSeat(2) // == seats.size: the pending seat
        s.setBrush(EditorSession.Brush.Capital)
        s.paint(Hex.of(0, 2))
        val level = s.ui.value.def.level
        assertEquals(3, level.seats.size)
        assertEquals(3, level.map.capitals.size)
        assertEquals(Hex.of(0, 2), level.map.capitals[2])
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
    fun `structures need owned land and never overwrite a capital`() {
        val s = session()
        s.setActiveSeat(0)
        s.setBrush(EditorSession.Brush.Structure(Building.FARM))
        val neutral = Hex.of(0, 1)
        s.paint(neutral) // neutral land: refused (campaign rule)
        assertNull(s.tile(neutral)?.building)
        val owned = Hex.of(-1, 0)
        s.paint(owned)
        assertEquals(Building.FARM, s.tile(owned)?.building)
        val capital = s.ui.value.def.level.map.capitals[0]
        s.paint(capital)
        assertEquals(Building.CAPITAL, s.tile(capital)?.building) // untouched
    }

    @Test
    fun `deposits respect their terrain`() {
        val s = session()
        s.setBrush(EditorSession.Brush.Sea)
        s.paint(Hex.of(0, -2))
        s.setBrush(EditorSession.Brush.Resource(Deposit.FISH_SHOAL))
        s.paint(Hex.of(0, -2))
        assertEquals(Deposit.FISH_SHOAL, s.tile(Hex.of(0, -2))?.deposit)
        s.paint(Hex.of(0, 1)) // shoal on land: refused
        assertNull(s.tile(Hex.of(0, 1))?.deposit)
        s.setBrush(EditorSession.Brush.Resource(Deposit.GOLD_VEIN))
        s.paint(Hex.of(0, 1))
        assertEquals(Deposit.GOLD_VEIN, s.tile(Hex.of(0, 1))?.deposit)
    }

    @Test
    fun `unit brush mirrors the placement rules and replaces on repaint`() {
        val s = session()
        s.setActiveSeat(0)
        s.setBrush(EditorSession.Brush.UnitBrush(UnitType.SOLDIER, 1))
        val owned = Hex.of(-1, 0)
        s.paint(owned)
        assertEquals(1, s.ui.value.def.level.startingUnits.size)
        s.setBrush(EditorSession.Brush.UnitBrush(UnitType.SOLDIER, 3))
        s.paint(owned) // replace, not stack
        val units = s.ui.value.def.level.startingUnits
        assertEquals(1, units.size)
        assertEquals(3, units.single().tier)
        s.paint(Hex.of(0, 1)) // neutral land: refused
        assertEquals(1, s.ui.value.def.level.startingUnits.size)
        assertTrue(s.ui.value.violations.isEmpty())
    }

    @Test
    fun `terrain edits sweep stranded units away`() {
        val s = session()
        s.setActiveSeat(0)
        s.setBrush(EditorSession.Brush.UnitBrush(UnitType.SOLDIER, 1))
        val owned = Hex.of(-1, 0)
        s.paint(owned)
        s.setBrush(EditorSession.Brush.Sea)
        s.paint(owned)
        assertTrue(s.ui.value.def.level.startingUnits.isEmpty())
    }

    @Test
    fun `seat kind changes keep exactly one human`() {
        val s = session()
        s.setSeatKind(1, SeatDef.Player)
        val seats = s.ui.value.def.level.seats
        assertEquals(1, seats.count { it is SeatDef.Player })
        assertTrue(seats[1] is SeatDef.Player)
        assertEquals(SeatDef.Ai(Difficulty.NORMAL), seats[0])
        assertTrue(s.ui.value.violations.isEmpty())
    }

    @Test
    fun `objectives are added painted and removed`() {
        val s = session()
        val index = s.addObjective(Objective.CaptureHexes(emptyList()))
        assertTrue(s.ui.value.brush is EditorSession.Brush.ObjectiveHexes)
        s.paint(Hex.of(0, 1))
        s.paint(Hex.of(1, 0))
        s.paint(Hex.of(0, 1)) // toggle off again
        assertEquals(setOf(Hex.of(1, 0)), s.objectiveHexes(index))
        s.removeObjective(index)
        assertEquals(1, s.ui.value.def.level.objectives.size) // ConquerAll remains
        assertEquals(EditorSession.Brush.Land, s.ui.value.brush)
    }

    @Test
    fun `treasury edits create the purse list on demand`() {
        val s = session()
        s.setTreasury(1, 250)
        val purses = s.ui.value.def.level.startingTreasury
        assertEquals(listOf(s.ui.value.def.level.rules.startingTreasury, 250), purses)
        assertTrue(s.ui.value.violations.isEmpty())
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
