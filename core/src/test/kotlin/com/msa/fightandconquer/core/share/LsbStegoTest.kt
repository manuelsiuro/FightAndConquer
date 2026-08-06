package com.msa.fightandconquer.core.share

import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LsbStegoTest {

    private fun pixels(count: Int, seed: Long = 1L): IntArray {
        val rng = Random(seed)
        return IntArray(count) { rng.nextInt() }
    }

    @Test
    fun `payload round trips`() {
        val image = pixels(512 * 512)
        val payload = ByteArray(4096).also { Random(2L).nextBytes(it) }
        LsbStego.embed(image, payload)
        assertArrayEquals(payload, LsbStego.extract(image))
    }

    @Test
    fun `payload at exact capacity fits and one byte more is refused`() {
        val image = pixels(1000)
        val capacity = LsbStego.capacityBytes(image.size)
        assertTrue(capacity > 0)
        LsbStego.embed(image, ByteArray(capacity) { it.toByte() })
        assertArrayEquals(
            ByteArray(capacity) { it.toByte() },
            LsbStego.extract(image),
        )
        try {
            LsbStego.embed(pixels(1000), ByteArray(capacity + 1))
            throw AssertionError("expected an over-capacity payload to be refused")
        } catch (expected: IllegalArgumentException) {
            // refused, as required
        }
    }

    @Test
    fun `random noise extracts to null`() {
        assertNull(LsbStego.extract(pixels(4096, seed = 3L)))
        assertNull(LsbStego.extract(IntArray(4))) // smaller than the header
    }

    @Test
    fun `alpha channel is untouched and rgb moves at most one step`() {
        val image = pixels(2048, seed = 4L)
        val before = image.copyOf()
        LsbStego.embed(image, ByteArray(512) { (it * 7).toByte() })
        for (i in image.indices) {
            assertEquals("alpha changed at $i", before[i] ushr 24, image[i] ushr 24)
            for (shift in intArrayOf(16, 8, 0)) {
                val was = (before[i] ushr shift) and 0xFF
                val now = (image[i] ushr shift) and 0xFF
                assertTrue("channel moved by more than the LSB at $i", Math.abs(was - now) <= 1)
            }
        }
    }

    @Test
    fun `a recompressed image loses the magic and fails cleanly`() {
        val image = pixels(2048, seed = 5L)
        LsbStego.embed(image, ByteArray(64) { it.toByte() })
        // Simulate lossy recompression: nudge every channel up, wiping the LSB plane.
        val mangled = IntArray(image.size) { i ->
            val p = image[i]
            val r = (((p ushr 16) and 0xFF) or 1) xor 1
            val g = (((p ushr 8) and 0xFF) or 1) xor 1
            val b = ((p and 0xFF) or 1) xor 1
            (p and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
        assertNull(LsbStego.extract(mangled))
    }
}
