package com.msa.fightandconquer.ui.game

import com.msa.fightandconquer.core.engine.PurchaseOption
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Tech
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The purchase menu's decisions, which live in a pure object precisely so they can be
 * asserted here (there is no Robolectric in `:app`; the Compose pair itself is checked
 * on a device).
 */
class PurchaseMenuTest {

    private fun soldier(tier: Int, cost: Int) = PurchaseOption.Unit(tier = tier, cost = cost)

    private val archer = PurchaseOption.Unit(
        tier = 1,
        cost = 15,
        type = UnitType.ARCHER,
        strength = 2,
        defense = 1,
    )
    private val catapult = PurchaseOption.Unit(
        tier = 1,
        cost = 25,
        type = UnitType.CATAPULT,
        strength = 3,
        defense = 1,
    )
    private val transport = PurchaseOption.Unit(tier = 1, cost = 15, type = UnitType.TRANSPORT)
    private val warship = PurchaseOption.Unit(tier = 2, cost = 30, type = UnitType.WARSHIP)
    private val fishingBoat = PurchaseOption.Unit(tier = 1, cost = 10, type = UnitType.FISHING_BOAT)
    private val musterLockedArcher = PurchaseOption.Unit(
        tier = 1,
        cost = 15,
        type = UnitType.ARCHER,
        strength = 2,
        defense = 1,
        lockedByBuilding = Building.ARCHERY_RANGE,
    )
    private val farm = PurchaseOption.Structure(BuildingType.FARM, 12)
    private val techLockedBank =
        PurchaseOption.Structure(BuildingType.BANK, 30, lockedByTech = Tech.BANKING)

    /** Units, structures, units again — the shape the engine actually offers. */
    private val mixed: List<PurchaseOption> = listOf(
        soldier(1, 10),
        soldier(2, 20),
        farm,
        PurchaseOption.Structure(BuildingType.TOWER, 15),
        musterLockedArcher,
        techLockedBank,
        soldier(3, 30),
    )
    private val unitsOnly: List<PurchaseOption> =
        listOf(soldier(1, 10), musterLockedArcher, fishingBoat)
    private val structuresOnly: List<PurchaseOption> = listOf(farm, techLockedBank)
    private val empty: List<PurchaseOption> = emptyList()

    @Test
    fun `every unit recruits, locked or not`() {
        val units = listOf(
            soldier(1, 10),
            soldier(2, 20),
            soldier(3, 30),
            soldier(4, 40),
            archer,
            catapult,
            transport,
            warship,
            fishingBoat,
            musterLockedArcher,
        )
        for (unit in units) {
            assertEquals(unit.toString(), PurchaseCategory.RECRUIT, PurchaseMenu.categoryOf(unit))
        }
    }

    @Test
    fun `every structure builds, tech-locked or not`() {
        assertEquals(PurchaseCategory.BUILD, PurchaseMenu.categoryOf(farm))
        assertEquals(PurchaseCategory.BUILD, PurchaseMenu.categoryOf(techLockedBank))
        for (type in BuildingType.entries) {
            assertEquals(
                type.name,
                PurchaseCategory.BUILD,
                PurchaseMenu.categoryOf(PurchaseOption.Structure(type, 10)),
            )
        }
    }

    @Test
    fun `the two halves partition the list and keep the engine order`() {
        val recruit = PurchaseMenu.options(mixed, PurchaseCategory.RECRUIT)
        val build = PurchaseMenu.options(mixed, PurchaseCategory.BUILD)

        assertEquals(mixed.size, recruit.size + build.size)
        assertEquals(listOf(soldier(1, 10), soldier(2, 20), musterLockedArcher, soldier(3, 30)), recruit)
        assertEquals(
            listOf(farm, PurchaseOption.Structure(BuildingType.TOWER, 15), techLockedBank),
            build,
        )
        // Each half is in the same relative order as in the engine list.
        assertEquals(mixed.filter { it is PurchaseOption.Unit }, recruit)
        assertEquals(mixed.filter { it is PurchaseOption.Structure }, build)
        // Nothing is lost and nothing is duplicated.
        assertEquals(mixed.toSet(), (recruit + build).toSet())
    }

    @Test
    fun `options of an empty list is empty in both halves`() {
        assertTrue(PurchaseMenu.options(empty, PurchaseCategory.RECRUIT).isEmpty())
        assertTrue(PurchaseMenu.options(empty, PurchaseCategory.BUILD).isEmpty())
    }

    @Test
    fun `a button is live only when its half has a card`() {
        assertTrue(PurchaseMenu.available(mixed, PurchaseCategory.RECRUIT))
        assertTrue(PurchaseMenu.available(mixed, PurchaseCategory.BUILD))

        assertTrue(PurchaseMenu.available(unitsOnly, PurchaseCategory.RECRUIT))
        assertFalse(PurchaseMenu.available(unitsOnly, PurchaseCategory.BUILD))

        assertFalse(PurchaseMenu.available(structuresOnly, PurchaseCategory.RECRUIT))
        assertTrue(PurchaseMenu.available(structuresOnly, PurchaseCategory.BUILD))

        assertFalse(PurchaseMenu.available(empty, PurchaseCategory.RECRUIT))
        assertFalse(PurchaseMenu.available(empty, PurchaseCategory.BUILD))
    }

    @Test
    fun `a locked card still makes its half live`() {
        assertTrue(PurchaseMenu.available(listOf(musterLockedArcher), PurchaseCategory.RECRUIT))
        assertTrue(PurchaseMenu.available(listOf(techLockedBank), PurchaseCategory.BUILD))
    }

    @Test
    fun `the pair shows whenever the hex sells anything`() {
        assertFalse(PurchaseMenu.shown(empty))
        assertTrue(PurchaseMenu.shown(unitsOnly))
        assertTrue(PurchaseMenu.shown(structuresOnly))
        assertTrue(PurchaseMenu.shown(mixed))
    }

    @Test
    fun `open honours a request only when that half sells something`() {
        assertNull(PurchaseMenu.open(mixed, null))
        assertEquals(PurchaseCategory.RECRUIT, PurchaseMenu.open(mixed, PurchaseCategory.RECRUIT))
        assertEquals(PurchaseCategory.BUILD, PurchaseMenu.open(mixed, PurchaseCategory.BUILD))
        assertNull(PurchaseMenu.open(unitsOnly, PurchaseCategory.BUILD))
        assertEquals(
            PurchaseCategory.RECRUIT,
            PurchaseMenu.open(unitsOnly, PurchaseCategory.RECRUIT),
        )
        assertNull(PurchaseMenu.open(structuresOnly, PurchaseCategory.RECRUIT))
        assertNull(PurchaseMenu.open(empty, PurchaseCategory.RECRUIT))
        assertNull(PurchaseMenu.open(empty, PurchaseCategory.BUILD))
        assertNull(PurchaseMenu.open(empty, null))
    }

    @Test
    fun `a tap opens, switches or folds`() {
        assertEquals(PurchaseCategory.RECRUIT, PurchaseMenu.toggle(null, PurchaseCategory.RECRUIT))
        assertEquals(PurchaseCategory.BUILD, PurchaseMenu.toggle(null, PurchaseCategory.BUILD))
        assertNull(PurchaseMenu.toggle(PurchaseCategory.RECRUIT, PurchaseCategory.RECRUIT))
        assertNull(PurchaseMenu.toggle(PurchaseCategory.BUILD, PurchaseCategory.BUILD))
        assertEquals(
            PurchaseCategory.BUILD,
            PurchaseMenu.toggle(PurchaseCategory.RECRUIT, PurchaseCategory.BUILD),
        )
        assertEquals(
            PurchaseCategory.RECRUIT,
            PurchaseMenu.toggle(PurchaseCategory.BUILD, PurchaseCategory.RECRUIT),
        )
    }

    @Test
    fun `each category carries its own non-zero label, glyph and description`() {
        for (category in PurchaseCategory.entries) {
            assertNotEquals(category.name, 0, category.labelRes)
            assertNotEquals(category.name, 0, category.iconRes)
            assertNotEquals(category.name, 0, category.descriptionRes)
        }
        assertEquals(2, PurchaseCategory.entries.size)
        assertNotEquals(PurchaseCategory.RECRUIT.labelRes, PurchaseCategory.BUILD.labelRes)
        assertNotEquals(PurchaseCategory.RECRUIT.iconRes, PurchaseCategory.BUILD.iconRes)
        assertNotEquals(
            PurchaseCategory.RECRUIT.descriptionRes,
            PurchaseCategory.BUILD.descriptionRes,
        )
    }
}
