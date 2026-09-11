package com.msa.fightandconquer.ui.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The menu's layout decisions, which live in a pure object precisely so they can be
 * asserted here (there is no Robolectric in `:app`; the Compose layout itself is checked
 * on the emulator).
 */
class MenuLayoutTest {

    @Test
    fun `the grid is always the same six entries in order`() {
        assertEquals(
            listOf(
                MenuEntry.NEW_GAME,
                MenuEntry.CAMPAIGN,
                MenuEntry.MAP_EDITOR,
                MenuEntry.GUIDE,
                MenuEntry.SETTINGS,
                MenuEntry.ABOUT,
            ),
            MenuLayout.tiles(),
        )
    }

    @Test
    fun `the grid has no duplicate and never holds Continue`() {
        val tiles = MenuLayout.tiles()
        assertEquals(6, tiles.size)
        assertEquals(tiles.size, tiles.toSet().size)
        assertFalse(tiles.contains(MenuEntry.CONTINUE))
    }

    @Test
    fun `the Continue bar only exists with an autosave`() {
        assertEquals(MenuEntry.CONTINUE, MenuLayout.continueBar(hasAutosave = true))
        assertNull(MenuLayout.continueBar(hasAutosave = false))
    }

    @Test
    fun `the one pastel surface is Continue, else New game`() {
        assertEquals(MenuEntry.CONTINUE, MenuLayout.primary(hasAutosave = true))
        assertEquals(MenuEntry.NEW_GAME, MenuLayout.primary(hasAutosave = false))
    }

    @Test
    fun `the six tiles chunk into two rows of three`() {
        assertEquals(
            listOf(
                listOf(MenuEntry.NEW_GAME, MenuEntry.CAMPAIGN, MenuEntry.MAP_EDITOR),
                listOf(MenuEntry.GUIDE, MenuEntry.SETTINGS, MenuEntry.ABOUT),
            ),
            MenuLayout.rows(MenuLayout.tiles()),
        )
    }

    @Test
    fun `a short last row keeps its remainder and is never empty`() {
        val rows = MenuLayout.rows(MenuLayout.tiles().take(4))
        assertEquals(2, rows.size)
        assertEquals(3, rows[0].size)
        assertEquals(1, rows[1].size)
        assertTrue(rows.none { it.isEmpty() })
    }

    @Test
    fun `no entries means no rows`() {
        assertEquals(emptyList<List<MenuEntry>>(), MenuLayout.rows(emptyList()))
    }

    @Test
    fun `every entry carries a label and a distinct glyph`() {
        val entries = MenuEntry.entries
        entries.forEach { entry ->
            assertNotEquals("labelRes of $entry", 0, entry.labelRes)
            assertNotEquals("iconRes of $entry", 0, entry.iconRes)
        }
        assertEquals(entries.size, entries.map { it.iconRes }.toSet().size)
    }

    @Test
    fun `a full row is 280 dp wide`() {
        assertEquals(280, MenuLayout.rowWidthDp())
    }

    @Test
    fun `the bottom block is 196 dp, 252 with the Continue bar`() {
        assertEquals(196, MenuLayout.blockHeightDp(hasAutosave = false))
        assertEquals(252, MenuLayout.blockHeightDp(hasAutosave = true))
    }

    @Test
    fun `anchoring needs 420 dp of usable height`() {
        assertFalse(MenuLayout.anchored(419))
        assertTrue(MenuLayout.anchored(420))
        assertTrue(MenuLayout.anchored(800))
    }

    // ----- liftFraction: how high the orbiting world sits above the screen center -----

    @Test
    fun `liftFraction centers the world in the band between the title and the block`() {
        // (block - title) / (2 * height): 196 vs 104 dp over 800 dp of usable height.
        assertEquals(0.0575f, MenuLayout.liftFraction(800, hasAutosave = false), 1e-3f)
        // The Continue bar makes the block 252 dp, so the band's middle sits higher.
        assertEquals(0.0925f, MenuLayout.liftFraction(800, hasAutosave = true), 1e-3f)
    }

    @Test
    fun `the Continue bar lifts the world further than the bare grid`() {
        assertTrue(
            MenuLayout.liftFraction(800, hasAutosave = true) >
                MenuLayout.liftFraction(800, hasAutosave = false),
        )
    }

    @Test
    fun `a scrolling menu never lifts the world`() {
        assertEquals(0f, MenuLayout.liftFraction(400, hasAutosave = true), 0f)
        assertEquals(0f, MenuLayout.liftFraction(419, hasAutosave = false), 0f)
    }

    @Test
    fun `liftFraction clamps at the maximum`() {
        assertEquals(0.15f, MenuLayout.MAX_LIFT_FRACTION, 1e-6f)
        // 148 / 960 = 0.154 -> clamped.
        assertEquals(MenuLayout.MAX_LIFT_FRACTION, MenuLayout.liftFraction(480, hasAutosave = true), 1e-6f)
        // 148 / 1000 = 0.148 -> still under the cap, so it is passed through.
        assertEquals(0.148f, MenuLayout.liftFraction(500, hasAutosave = true), 1e-3f)
    }

    @Test
    fun `liftFraction stays inside zero and the maximum for every height`() {
        for (height in 0..2400 step 7) {
            for (hasAutosave in listOf(false, true)) {
                val lift = MenuLayout.liftFraction(height, hasAutosave)
                assertTrue(
                    "lift $lift out of range at $height dp (autosave=$hasAutosave)",
                    lift >= 0f && lift <= MenuLayout.MAX_LIFT_FRACTION,
                )
            }
        }
    }

    @Test
    fun `a taller screen needs a smaller fraction for the same band`() {
        assertTrue(
            MenuLayout.liftFraction(1000, hasAutosave = true) <
                MenuLayout.liftFraction(700, hasAutosave = true),
        )
    }
}
