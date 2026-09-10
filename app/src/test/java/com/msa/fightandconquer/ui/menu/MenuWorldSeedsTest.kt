package com.msa.fightandconquer.ui.menu

import com.msa.fightandconquer.core.engine.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The menu promises a *different* world on every entry. That promise rests entirely on
 * this seed policy: a clock read that neighbouring milliseconds do not correlate, and a
 * hard guarantee that the seed never repeats the previous one (a fast Back tap can land
 * on the very same millisecond).
 */
class MenuWorldSeedsTest {

    @Test
    fun `no previous seed returns the mixed clock value`() {
        val now = 1_726_000_000_000L
        assertEquals(Rng.output(Rng.advance(now)), MenuWorldSeeds.next(now, previous = null))
    }

    @Test
    fun `the same millisecond twice never repeats the seed`() {
        val now = 1_726_000_000_000L
        val first = MenuWorldSeeds.next(now, previous = null)
        val second = MenuWorldSeeds.next(now, previous = first)
        assertNotEquals(first, second)
        // And once more, still on the same clock reading.
        val third = MenuWorldSeeds.next(now, previous = second)
        assertNotEquals(second, third)
    }

    @Test
    fun `consecutive milliseconds give unrelated seeds`() {
        val base = 1_726_000_000_000L
        val seeds = (0 until 64).map { MenuWorldSeeds.next(base + it, previous = null) }
        assertEquals("all distinct", seeds.size, seeds.toSet().size)
        // Unrelated, not merely distinct: neighbours must not be a small increment apart.
        for (i in 1 until seeds.size) {
            val gap = seeds[i] - seeds[i - 1]
            assertTrue("gap $gap between seed $i and ${i - 1} looks like a counter", gap > 1_000L || gap < -1_000L)
        }
    }

    @Test
    fun `next is pure`() {
        val now = 42L
        assertEquals(MenuWorldSeeds.next(now, previous = null), MenuWorldSeeds.next(now, previous = null))
        val previous = MenuWorldSeeds.next(now, previous = null)
        assertEquals(
            MenuWorldSeeds.next(now, previous = previous),
            MenuWorldSeeds.next(now, previous = previous),
        )
    }

    @Test
    fun `a chain of menu entries on a frozen clock never repeats consecutively`() {
        var previous: Long? = null
        val now = 7L
        repeat(20) {
            val seed = MenuWorldSeeds.next(now, previous)
            assertNotEquals("entry $it repeated the previous world", previous, seed)
            previous = seed
        }
    }
}
