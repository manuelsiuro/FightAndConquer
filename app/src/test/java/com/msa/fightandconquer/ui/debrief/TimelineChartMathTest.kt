package com.msa.fightandconquer.ui.debrief

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chart's pure axis math: [niceCeil] tops and [chartGridValues] interior lines. */
class TimelineChartMathTest {

    @Test
    fun `niceCeil lands on the smallest friendly top`() {
        assertEquals(1, niceCeil(0))
        assertEquals(1, niceCeil(1))
        assertEquals(5, niceCeil(3))
        assertEquals(10, niceCeil(7))
        assertEquals(20, niceCeil(11))
        assertEquals(100, niceCeil(51))
        assertEquals(200, niceCeil(101))
        assertEquals(1000, niceCeil(999))
    }

    @Test
    fun `every nice top splits into labeled integer gridlines`() {
        assertEquals(emptyList<Int>(), chartGridValues(1))
        assertEquals(listOf(1), chartGridValues(2))
        assertEquals(listOf(1, 2, 3, 4), chartGridValues(5))
        assertEquals(listOf(2, 4, 6, 8), chartGridValues(10))
        assertEquals(listOf(5, 10, 15), chartGridValues(20))
        assertEquals(listOf(10, 20, 30, 40), chartGridValues(50))
        assertEquals(listOf(20, 40, 60, 80), chartGridValues(100))
        assertEquals(listOf(50, 100, 150), chartGridValues(200))
        assertEquals(listOf(100, 200, 300, 400), chartGridValues(500))
        assertEquals(listOf(200, 400, 600, 800), chartGridValues(1000))
    }

    @Test
    fun `gridlines stay strictly inside the frame and sorted`() {
        for (top in generateSequence(1) { niceCeil(it + 1) }.takeWhile { it <= 100_000 }) {
            val values = chartGridValues(top)
            assertEquals(values.sorted(), values)
            assertTrue(values.all { it in 1 until top })
        }
    }
}
