package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The muster line (Barracks / Archery Range / Siege Workshop). This file covers
 * the buildings as inert structures; the recruiting gate behind
 * `militaryBuildingsRequired` gets its own sections as the feature lands.
 */
class MilitaryBuildingsTest {

    private val base = strip(9, 0..2, 6..8)
    private val rules = com.msa.fightandconquer.core.model.RuleConstants()

    private val musterCosts = mapOf(
        BuildingType.BARRACKS to rules.barracksCost,
        BuildingType.ARCHERY_RANGE to rules.archeryRangeCost,
        BuildingType.SIEGE_WORKSHOP to rules.siegeWorkshopCost,
    )

    @Test
    fun `each muster building goes on any empty owned hex at its rules cost`() {
        for ((type, cost) in musterCosts) {
            val s = base.withTreasury(0, cost)
            val (next, events) = Reducer.reduce(s, GameAction.BuyBuilding(type, hex(2)))
            assertEquals(type.building, next.tiles.getValue(hex(2)).building)
            assertEquals("$type spends its whole cost", 0, next.player(com.msa.fightandconquer.core.model.PlayerId(0)).treasury)
            assertTrue(events.any { it is GameEvent.BuildingBuilt && it.building == type.building })
            assertInvariants(next)
        }
    }

    @Test
    fun `muster buildings grant no defense - a tier-1 buy-capture takes the hex`() {
        // A tower here would reject the peasant (defense 2); the barracks must not.
        for (building in listOf(Building.BARRACKS, Building.ARCHERY_RANGE, Building.SIEGE_WORKSHOP)) {
            val s = base.withBuilding(building, at = hex(3))
                .copy(tiles = base.tiles.let { it + (hex(3) to it.getValue(hex(3)).copy(owner = com.msa.fightandconquer.core.model.PlayerId(1), building = building)) })
                .withTreasury(0, 100)
            val (next, _) = Reducer.reduce(s, GameAction.BuyUnit(1, hex(3)))
            assertEquals(com.msa.fightandconquer.core.model.PlayerId(0), next.tiles.getValue(hex(3)).owner)
            assertInvariants(next)
        }
    }

    @Test
    fun `capturing a muster building destroys it`() {
        val s = base.withBuilding(Building.BARRACKS, at = hex(3))
            .copy(tiles = base.tiles.let { it + (hex(3) to it.getValue(hex(3)).copy(owner = com.msa.fightandconquer.core.model.PlayerId(1), building = Building.BARRACKS)) })
            .withUnit(owner = 0, tier = 1, at = hex(2))
        val id = s.unitIdAt(hex(2))
        val (next, events) = Reducer.reduce(s, GameAction.MoveUnit(id, hex(3)))
        assertNull(next.tiles.getValue(hex(3)).building)
        assertTrue(events.any { it is GameEvent.BuildingDestroyed && it.building == Building.BARRACKS })
        assertInvariants(next)
    }
}
