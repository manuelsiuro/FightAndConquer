package com.msa.fightandconquer.core.hex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HexTest {

    @Test
    fun `pack and unpack roundtrip including negatives`() {
        val cases = listOf(0 to 0, 5 to -3, -7 to 12, 32767 to -32768, -32768 to 32767, -1 to -1)
        for ((q, r) in cases) {
            val hex = Hex.of(q, r)
            assertEquals("q for ($q,$r)", q, hex.q)
            assertEquals("r for ($q,$r)", r, hex.r)
        }
    }

    @Test
    fun `s coordinate satisfies cube invariant`() {
        val hex = Hex.of(3, -5)
        assertEquals(0, hex.q + hex.r + hex.s)
    }

    @Test
    fun `equal coordinates are equal values`() {
        assertEquals(Hex.of(2, -9), Hex.of(2, -9))
        assertFalse(Hex.of(2, -9) == Hex.of(-9, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of range coordinate rejected`() {
        Hex.of(40000, 0)
    }
}

class HexMathTest {

    @Test
    fun `six distinct neighbors all at distance 1`() {
        val center = Hex.of(4, -2)
        val neighbors = HexMath.neighbors(center)
        assertEquals(6, neighbors.size)
        assertEquals(6, neighbors.toSet().size)
        neighbors.forEach { assertEquals(1, HexMath.distance(center, it)) }
    }

    @Test
    fun `forEachNeighbor matches neighbors list`() {
        val center = Hex.of(-3, 7)
        val collected = mutableListOf<Hex>()
        HexMath.forEachNeighbor(center) { collected.add(it) }
        assertEquals(HexMath.neighbors(center), collected)
    }

    @Test
    fun `distance is symmetric and correct`() {
        val a = Hex.of(0, 0)
        val b = Hex.of(3, -1)
        assertEquals(3, HexMath.distance(a, b))
        assertEquals(HexMath.distance(a, b), HexMath.distance(b, a))
        assertEquals(0, HexMath.distance(a, a))
        // straight line along +q
        assertEquals(5, HexMath.distance(Hex.of(0, 0), Hex.of(5, 0)))
        // diagonal-ish
        assertEquals(4, HexMath.distance(Hex.of(0, 0), Hex.of(2, 2)))
    }

    @Test
    fun `range sizes follow centered hexagonal numbers`() {
        val center = Hex.of(0, 0)
        assertEquals(1, HexMath.range(center, 0).size)
        assertEquals(7, HexMath.range(center, 1).size)
        assertEquals(19, HexMath.range(center, 2).size)
        assertEquals(37, HexMath.range(center, 3).size)
        // all within radius
        HexMath.range(center, 3).forEach { assertTrue(HexMath.distance(center, it) <= 3) }
    }

    @Test
    fun `ring has exactly 6r hexes at distance r`() {
        val center = Hex.of(2, 2)
        for (radius in 1..3) {
            val ring = HexMath.ring(center, radius)
            assertEquals(6 * radius, ring.size)
            assertEquals(6 * radius, ring.toSet().size)
            ring.forEach { assertEquals(radius, HexMath.distance(center, it)) }
        }
    }

    @Test
    fun `flood fill covers connected region and stops at borders`() {
        // Two blobs connected by nothing: {origin ring1} and a far single hex
        val blob = HexMath.range(Hex.of(0, 0), 1).toSet()
        val island = setOf(Hex.of(10, 10))
        val world = blob + island

        val filled = HexMath.floodFill(Hex.of(0, 0)) { it in world }
        assertEquals(blob, filled)
    }

    @Test
    fun `flood fill from excluded start is empty`() {
        assertTrue(HexMath.floodFill(Hex.of(0, 0)) { false }.isEmpty())
    }

    @Test
    fun `connected components partitions correctly`() {
        val blobA = HexMath.range(Hex.of(0, 0), 1).toSet()
        val blobB = HexMath.range(Hex.of(20, 0), 1).toSet()
        val single = setOf(Hex.of(-15, 3))
        val components = HexMath.connectedComponents(blobA + blobB + single)
        assertEquals(3, components.size)
        assertEquals(setOf(blobA, blobB, single), components.toSet())
    }

    @Test
    fun `narrow bridge keeps regions connected`() {
        // blob - bridge - blob should be ONE component
        val left = HexMath.range(Hex.of(0, 0), 1).toSet()
        val right = HexMath.range(Hex.of(4, 0), 1).toSet()
        val bridge = setOf(Hex.of(2, 0)) // 1..3 gap: (2,0) adjacent to (1,0) and (3,0)
        val components = HexMath.connectedComponents(left + right + bridge)
        assertEquals(1, components.size)
    }
}
