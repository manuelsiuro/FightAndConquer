package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withFlora
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.PlayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveAndCaptureTest {

    private val base = strip(9, 0..2, 6..8)

    @Test
    fun `move within region relocates and spends the unit`() {
        val s = base.withUnit(owner = 0, tier = 1, at = hex(1))
        val id = s.unitIdAt(hex(1))
        val (next, events) = Reducer.reduce(s, GameAction.MoveUnit(id, hex(2)))
        assertEquals(hex(2), next.units.getValue(id).hex)
        assertTrue(next.units.getValue(id).spent)
        assertNull(next.tiles.getValue(hex(1)).unit)
        assertEquals(id, next.tiles.getValue(hex(2)).unit)
        assertTrue(events.any { it is GameEvent.UnitMoved })
        assertInvariants(next)
    }

    @Test
    fun `capturing a neutral hex transfers ownership`() {
        val s = base.withUnit(owner = 0, tier = 1, at = hex(2))
        val id = s.unitIdAt(hex(2))
        val (next, events) = Reducer.reduce(s, GameAction.MoveUnit(id, hex(3)))
        assertEquals(PlayerId(0), next.tiles.getValue(hex(3)).owner)
        assertTrue(next.units.getValue(id).spent)
        assertTrue(events.any { it is GameEvent.HexCaptured && it.hex == hex(3) })
        assertInvariants(next)
    }

    @Test
    fun `capture kills the weaker defender without leaving a gravestone`() {
        // P0 baron attacks P1 peasant standing on 6 (defense: peasant 1, capital too far)
        val s = strip(9, 0..5, 6..8)
            .withUnit(owner = 0, tier = 3, at = hex(5))
            .withUnit(owner = 1, tier = 1, at = hex(6))
        val attacker = s.unitIdAt(hex(5))
        val defender = s.unitIdAt(hex(6))
        val (next, events) = Reducer.reduce(s, GameAction.MoveUnit(attacker, hex(6)))
        assertNull(next.units[defender])
        assertEquals(attacker, next.tiles.getValue(hex(6)).unit)
        assertNull("combat kill leaves no gravestone", next.tiles.getValue(hex(6)).flora)
        assertTrue(events.any { it is GameEvent.UnitDied && it.cause == DeathCause.KILLED })
        assertInvariants(next)
    }

    @Test
    fun `rejected move leaves state untouched`() {
        val s = base.withUnit(owner = 0, tier = 1, at = hex(1))
        val id = s.unitIdAt(hex(1))
        val (next, events) = Reducer.reduce(s, GameAction.MoveUnit(id, hex(7)))
        assertEquals(s, next)
        assertTrue(events.single() is GameEvent.ActionRejected)
    }

    @Test
    fun `cannot move opponents unit`() {
        val s = base.withUnit(owner = 1, tier = 1, at = hex(7))
        val id = s.unitIdAt(hex(7))
        val (next, events) = Reducer.reduce(s, GameAction.MoveUnit(id, hex(6)))
        assertEquals(s, next)
        assertTrue(events.single() is GameEvent.ActionRejected)
    }

    @Test
    fun `moving onto a tree clears it for the bonus`() {
        val s = base.withFlora(Flora.Tree, at = hex(2)).withUnit(owner = 0, tier = 1, at = hex(1))
        val id = s.unitIdAt(hex(1))
        val treasuryBefore = s.player(PlayerId(0)).treasury
        val (next, events) = Reducer.reduce(s, GameAction.MoveUnit(id, hex(2)))
        assertNull(next.tiles.getValue(hex(2)).flora)
        assertEquals(treasuryBefore + 3, next.player(PlayerId(0)).treasury)
        assertTrue(events.any { it is GameEvent.TreeCleared && it.bonus == 3 })
        assertInvariants(next)
    }

    @Test
    fun `capturing a tower destroys it`() {
        // Baron (3) beats tower defense (2) on an unprotected hex
        val s = strip(9, 0..5, 6..8)
            .withBuilding(Building.TOWER, at = hex(6))
            .withUnit(owner = 0, tier = 3, at = hex(5))
        val id = s.unitIdAt(hex(5))
        val (next, events) = Reducer.reduce(s, GameAction.MoveUnit(id, hex(6)))
        assertNull(next.tiles.getValue(hex(6)).building)
        assertTrue(events.any { it is GameEvent.BuildingDestroyed && it.building == Building.TOWER })
        assertInvariants(next)
    }

    @Test
    fun `spearman cannot capture tower hex`() {
        val s = strip(9, 0..5, 6..8)
            .withBuilding(Building.TOWER, at = hex(6))
            .withUnit(owner = 0, tier = 2, at = hex(5))
        val id = s.unitIdAt(hex(5))
        val (next, events) = Reducer.reduce(s, GameAction.MoveUnit(id, hex(6)))
        assertEquals(s, next)
        assertTrue(events.single() is GameEvent.ActionRejected)
    }

    @Test
    fun `capturing the capital loots half the treasury and relocates it`() {
        // P0 knight next to P1 capital at 8; hex 8 defense = capital(1)
        val s = strip(9, 0..7, 8..8, treasury = 100)
            .withUnit(owner = 0, tier = 4, at = hex(7))
        // P1's capital hex 8 is its ONLY hex -> capture eliminates P1 entirely
        val id = s.unitIdAt(hex(7))
        val (next, events) = Reducer.reduce(s, GameAction.MoveUnit(id, hex(8)))
        assertEquals(150, next.player(PlayerId(0)).treasury)
        assertEquals(50, next.player(PlayerId(1)).treasury)
        assertTrue(next.player(PlayerId(1)).eliminated)
        assertTrue(events.any { it is GameEvent.PlayerEliminated })
        assertTrue(events.any { it is GameEvent.GameOver && it.winner == PlayerId(0) })
        assertTrue(next.phase is com.msa.fightandconquer.core.model.GamePhase.Finished)
        assertInvariants(next)
    }

    @Test
    fun `capturing the capital relocates it when victim has territory left`() {
        val s = strip(9, 0..5, 6..8, treasury = 40)
            .withUnit(owner = 0, tier = 4, at = hex(5))
        // P1 capital at 8 — attack 6 first? No: capture capital directly needs adjacency.
        // Rebuild: capital at 6 instead for adjacency.
        val tiles = s.tiles
            .plus(hex(8) to s.tiles.getValue(hex(8)).copy(building = null))
            .plus(hex(6) to s.tiles.getValue(hex(6)).copy(building = Building.CAPITAL))
        val s2 = s.copy(
            tiles = tiles,
            players = s.players.map { if (it.id == PlayerId(1)) it.copy(capital = hex(6)) else it },
        )
        val id = s2.unitIdAt(hex(5))
        val (next, events) = Reducer.reduce(s2, GameAction.MoveUnit(id, hex(6)))
        val moved = events.filterIsInstance<GameEvent.CapitalMoved>().single()
        assertEquals(20, moved.loot)
        assertTrue(moved.to in setOf(hex(7), hex(8)))
        assertEquals(Building.CAPITAL, next.tiles.getValue(moved.to).building)
        assertEquals(moved.to, next.player(PlayerId(1)).capital)
        assertInvariants(next)
    }
}

class BuyAndMergeTest {

    private val base = strip(9, 0..2, 6..8)

    @Test
    fun `buying a peasant on an owned hex costs 10 and leaves it fresh`() {
        val s = base.withTreasury(0, 10)
        val (next, events) = Reducer.reduce(s, GameAction.BuyUnit(1, hex(1)))
        assertEquals(0, next.player(PlayerId(0)).treasury)
        val unit = next.unitAt(hex(1))!!
        assertEquals(1, unit.tier)
        assertTrue(!unit.spent)
        assertTrue(events.any { it is GameEvent.UnitSpawned })
        assertInvariants(next)
    }

    @Test
    fun `cannot buy without funds`() {
        val s = base.withTreasury(0, 9)
        val (next, events) = Reducer.reduce(s, GameAction.BuyUnit(1, hex(1)))
        assertEquals(s, next)
        assertTrue(events.single() is GameEvent.ActionRejected)
    }

    @Test
    fun `buying directly onto a capturable hex captures and spends`() {
        val (next, _) = Reducer.reduce(base, GameAction.BuyUnit(1, hex(3)))
        assertEquals(PlayerId(0), next.tiles.getValue(hex(3)).owner)
        assertTrue(next.unitAt(hex(3))!!.spent)
        assertInvariants(next)
    }

    @Test
    fun `buy-merge upgrades the standing unit`() {
        val s = base.withUnit(owner = 0, tier = 1, at = hex(1))
        val standing = s.unitIdAt(hex(1))
        val (next, events) = Reducer.reduce(s, GameAction.BuyUnit(1, hex(1)))
        assertEquals(2, next.units.getValue(standing).tier)
        assertTrue(events.any { it is GameEvent.UnitsMerged })
        assertInvariants(next)
    }

    @Test
    fun `merging two peasants makes a spearman`() {
        val s = base
            .withUnit(owner = 0, tier = 1, at = hex(1))
            .withUnit(owner = 0, tier = 1, at = hex(2))
        val a = s.unitIdAt(hex(1))
        val b = s.unitIdAt(hex(2))
        val (next, events) = Reducer.reduce(s, GameAction.MergeUnits(a, b))
        assertNull(next.units[a])
        assertEquals(2, next.units.getValue(b).tier)
        assertNull(next.tiles.getValue(hex(1)).unit)
        assertTrue(events.any { it is GameEvent.UnitsMerged })
        assertInvariants(next)
    }

    @Test
    fun `cannot merge knights beyond max tier`() {
        val s = base
            .withUnit(owner = 0, tier = 4, at = hex(1))
            .withUnit(owner = 0, tier = 4, at = hex(2))
        val (next, events) = Reducer.reduce(s, GameAction.MergeUnits(s.unitIdAt(hex(1)), s.unitIdAt(hex(2))))
        assertEquals(s, next)
        assertTrue(events.single() is GameEvent.ActionRejected)
    }

    @Test
    fun `tower goes on any empty owned hex`() {
        val s = base.withTreasury(0, 15)
        val (next, _) = Reducer.reduce(s, GameAction.BuyBuilding(BuildingType.TOWER, hex(2)))
        assertEquals(Building.TOWER, next.tiles.getValue(hex(2)).building)
        assertEquals(0, next.player(PlayerId(0)).treasury)
        assertInvariants(next)
    }

    @Test
    fun `farm requires adjacency to capital or farm and costs escalate`() {
        val s = base.withTreasury(0, 100)
        // hex 2 is NOT adjacent to capital hex 0 -> rejected
        val (afterBad, badEvents) = Reducer.reduce(s, GameAction.BuyBuilding(BuildingType.FARM, hex(2)))
        assertEquals(s, afterBad)
        assertTrue(badEvents.single() is GameEvent.ActionRejected)
        // hex 1 IS adjacent to the capital -> ok, costs 12
        val (afterFirst, _) = Reducer.reduce(s, GameAction.BuyBuilding(BuildingType.FARM, hex(1)))
        assertEquals(Building.FARM, afterFirst.tiles.getValue(hex(1)).building)
        assertEquals(88, afterFirst.player(PlayerId(0)).treasury)
        // second farm: adjacent to first farm at hex 1 -> hex 2 ok now, costs 14
        val (afterSecond, _) = Reducer.reduce(afterFirst, GameAction.BuyBuilding(BuildingType.FARM, hex(2)))
        assertEquals(74, afterSecond.player(PlayerId(0)).treasury)
        assertInvariants(afterSecond)
    }

    @Test
    fun `cannot build on occupied or forested hexes`() {
        val s = base.withTreasury(0, 100).withFlora(Flora.Tree, at = hex(2))
        val (next, events) = Reducer.reduce(s, GameAction.BuyBuilding(BuildingType.TOWER, hex(2)))
        assertEquals(s, next)
        assertTrue(events.single() is GameEvent.ActionRejected)
    }
}
