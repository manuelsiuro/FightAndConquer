package com.msa.fightandconquer.render.scene

import com.msa.fightandconquer.render.CameraRig
import dev.romainguy.kotlin.math.Float3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * The orbit camera must keep the whole board on screen at EVERY yaw: the fit distance
 * frames the circumscribed circle of the board footprint, never a single axis. And the
 * yaw must stay in [0, 2pi) forever, whichever way it turns.
 */
class OrbitMathTest {

    private val fov = 30.0

    /** The menu world's elevation (MenuScreen.MENU_PITCH_RADIANS), radians above the horizon. */
    private val pitch55 = Math.toRadians(55.0).toFloat()

    /** The pre-orbit, single-axis fit BoardScene.fitCameraOnce uses for the game path. */
    private fun singleAxisFit(spanX: Float, spanZ: Float, aspect: Float): Float {
        val tanHalf = kotlin.math.tan(Math.toRadians(fov / 2).toFloat())
        val fitZ = spanZ * 0.5f / tanHalf
        val fitX = spanX * 0.5f / (tanHalf * aspect)
        return maxOf(fitZ, fitX) * 1.1f
    }

    @Test
    fun `orbit fit is never closer than the single-axis fit`() {
        val cases = listOf(
            Triple(20f, 14f, 0.46f),
            Triple(14f, 20f, 0.46f),
            Triple(32f, 8f, 1f),
            Triple(10f, 10f, 2.1f),
            Triple(45f, 30f, 0.75f),
        )
        for ((spanX, spanZ, aspect) in cases) {
            val orbit = OrbitMath.orbitFitDistance(spanX, spanZ, aspect, fov)
            val single = singleAxisFit(spanX, spanZ, aspect)
            assertTrue(
                "orbit fit $orbit must be >= single-axis fit $single for ($spanX, $spanZ, $aspect)",
                orbit >= single - 1e-4f,
            )
        }
    }

    @Test
    fun `orbit fit is symmetric in spanX and spanZ`() {
        for (aspect in floatArrayOf(0.46f, 1f, 2.1f)) {
            assertEquals(
                OrbitMath.orbitFitDistance(24f, 11f, aspect, fov),
                OrbitMath.orbitFitDistance(11f, 24f, aspect, fov),
                1e-4f,
            )
        }
    }

    @Test
    fun `narrower aspect pushes the camera further back`() {
        val phone = OrbitMath.orbitFitDistance(30f, 12f, 0.45f, fov)
        val tall = OrbitMath.orbitFitDistance(30f, 12f, 0.8f, fov)
        val square = OrbitMath.orbitFitDistance(30f, 12f, 1f, fov)
        val landscape = OrbitMath.orbitFitDistance(30f, 12f, 2f, fov)
        assertTrue("portrait 0.45 ($phone) > 0.8 ($tall)", phone > tall)
        assertTrue("0.8 ($tall) > square ($square)", tall > square)
        // Past square the vertical FOV binds: the footprint circle cannot be framed any
        // closer, so a wider screen never pulls the camera in.
        assertEquals(square, landscape, 1e-4f)
        assertTrue("landscape $landscape <= square $square", landscape <= square)
    }

    @Test
    fun `margin scales the fit distance`() {
        val tight = OrbitMath.orbitFitDistance(20f, 14f, 1f, fov, margin = 1f)
        val loose = OrbitMath.orbitFitDistance(20f, 14f, 1f, fov, margin = 1.5f)
        assertEquals(tight * 1.5f, loose, 1e-3f)
    }

    @Test
    fun `advanceYaw wraps past two pi`() {
        val yaw = OrbitMath.advanceYaw(OrbitMath.TWO_PI - 0.01f, 0.02f, 1f)
        assertTrue("wrapped yaw $yaw must land in [0, 0.02]", yaw in 0f..0.02f)
        assertEquals(0.01f, yaw, 1e-4f)
    }

    @Test
    fun `advanceYaw wraps upward for a negative rate`() {
        val yaw = OrbitMath.advanceYaw(0.01f, -0.02f, 1f)
        assertTrue("wrapped yaw $yaw must land in [2pi - 0.02, 2pi)", yaw >= OrbitMath.TWO_PI - 0.02f)
        assertTrue("wrapped yaw $yaw must stay below 2pi", yaw < OrbitMath.TWO_PI)
        assertEquals(OrbitMath.TWO_PI - 0.01f, yaw, 1e-4f)
    }

    @Test
    fun `advanceYaw wraps a yaw that is many turns away`() {
        for (rate in floatArrayOf(-40f, -7.3f, -1f, 0.5f, 6.5f, 100f)) {
            val yaw = OrbitMath.advanceYaw(3f, rate, 1.7f)
            assertTrue("yaw $yaw out of range for rate $rate", yaw >= 0f && yaw < OrbitMath.TWO_PI)
        }
    }

    @Test
    fun `advanceYaw adds rate times dt when no wrap is needed`() {
        assertEquals(1.2f, OrbitMath.advanceYaw(1f, 0.4f, 0.5f), 1e-5f)
        assertEquals(0.8f, OrbitMath.advanceYaw(1f, -0.4f, 0.5f), 1e-5f)
    }

    @Test
    fun `a zero rate leaves the yaw untouched`() {
        assertEquals(2.5f, OrbitMath.advanceYaw(2.5f, 0f, 0.033f), 0f)
        assertEquals(0f, OrbitMath.advanceYaw(0f, 0f, 1f), 0f)
    }

    // ----- circumscribedRadius: the board's true footprint, not its bounding box -----

    @Test
    fun `circumscribed radius is the farthest center plus the hex radius`() {
        val centers = listOf(0f to 0f, 3f to 4f, -1f to 2f, 2f to -2f)
        assertEquals(5f + 0.5f, OrbitMath.circumscribedRadius(centers, 0f, 0f, 0.5f), 1e-4f)
    }

    @Test
    fun `a single tile on the center is exactly one hex wide`() {
        assertEquals(
            0.62f,
            OrbitMath.circumscribedRadius(listOf(7f to -3f), 7f, -3f, 0.62f),
            1e-4f,
        )
    }

    @Test
    fun `circumscribed radius covers every center it is given`() {
        val centers = listOf(-6f to 1.5f, 4f to 9f, 0f to 0f, 11f to -2f, -3f to -8f)
        val cx = 1f
        val cz = 0.5f
        val hexRadius = 0.5f
        val radius = OrbitMath.circumscribedRadius(centers, cx, cz, hexRadius)
        for ((x, z) in centers) {
            val distance = hypot(x - cx, z - cz)
            assertTrue("radius $radius must cover ($x, $z) at $distance", radius >= distance)
        }
        val farthest = centers.maxOf { (x, z) -> hypot(x - cx, z - cz) }
        assertEquals(farthest + hexRadius, radius, 1e-4f)
    }

    @Test
    fun `the circumscribed footprint of a round board is smaller than its bounding diagonal`() {
        // A hex-shaped board of radius 8: the bbox diagonal that the first fit used is
        // ~1.4x the circle it really sweeps, which framed the world far too small.
        val centers = ArrayList<Pair<Float, Float>>()
        for (q in -8..8) for (r in -8..8) {
            if (kotlin.math.abs(q + r) > 8) continue
            centers += (q + r * 0.5f) to (r * 0.866f)
        }
        val radius = OrbitMath.circumscribedRadius(centers, 0f, 0f, 0.5f)
        val spanX = centers.maxOf { it.first } - centers.minOf { it.first } + 2f
        val spanZ = centers.maxOf { it.second } - centers.minOf { it.second } + 2f
        assertTrue("2R ${radius * 2f} must be smaller than the bbox diagonal", radius * 2f < hypot(spanX, spanZ))
    }

    @Test
    fun `the circle fit and the span fit agree on a circle-shaped diameter`() {
        assertEquals(
            OrbitMath.orbitFitDistance(12f, 16f, 0.46f, fov),
            OrbitMath.orbitFitDistanceForCircle(20f, 0.46f, fov),
            1e-4f,
        )
    }

    @Test
    fun `a margin below one pulls the camera in so the world overflows the width`() {
        val framed = OrbitMath.orbitFitDistanceForCircle(24f, 0.46f, fov, margin = 1f)
        val overflowing = OrbitMath.orbitFitDistanceForCircle(24f, 0.46f, fov, margin = 0.6f)
        assertEquals(framed * 0.6f, overflowing, 1e-3f)
        assertTrue("margin 0.6 ($overflowing) must be closer than margin 1 ($framed)", overflowing < framed)
    }

    // ----- targetLift / liftedTarget: the menu world sits in the free band -----

    @Test
    fun `a zero fraction lifts nothing`() {
        assertEquals(0f, OrbitMath.targetLift(18f, fov, pitch55, 0f), 0f)
        assertEquals(0f, OrbitMath.targetLift(4f, 90.0, 0.3f, 0f), 0f)
    }

    @Test
    fun `targetLift is linear in the fraction`() {
        val single = OrbitMath.targetLift(18f, fov, pitch55, 0.05f)
        assertEquals(single * 2f, OrbitMath.targetLift(18f, fov, pitch55, 0.10f), 1e-4f)
        assertEquals(single * 3f, OrbitMath.targetLift(18f, fov, pitch55, 0.15f), 1e-4f)
    }

    @Test
    fun `targetLift is linear in the distance`() {
        val near = OrbitMath.targetLift(10f, fov, pitch55, 0.09f)
        assertEquals(near * 2f, OrbitMath.targetLift(20f, fov, pitch55, 0.09f), 1e-4f)
        assertEquals(near * 0.5f, OrbitMath.targetLift(5f, fov, pitch55, 0.09f), 1e-4f)
    }

    @Test
    fun `a top-down camera lifts exactly the fraction of the visible height`() {
        val distance = 18f
        val fraction = 0.0925f
        val visibleHeight = 2f * distance * kotlin.math.tan(Math.toRadians(fov / 2).toFloat())
        assertEquals(
            fraction * visibleHeight,
            OrbitMath.targetLift(distance, fov, (Math.PI / 2).toFloat(), fraction),
            1e-4f,
        )
    }

    @Test
    fun `a lower pitch needs a longer ground move for the same screen shift`() {
        val topDown = OrbitMath.targetLift(18f, fov, (Math.PI / 2).toFloat(), 0.09f)
        val menu = OrbitMath.targetLift(18f, fov, pitch55, 0.09f)
        val flat = OrbitMath.targetLift(18f, fov, Math.toRadians(20.0).toFloat(), 0.09f)
        assertTrue("55 deg ($menu) must exceed top-down ($topDown)", menu > topDown)
        assertTrue("20 deg ($flat) must exceed 55 deg ($menu)", flat > menu)
    }

    @Test
    fun `a near-horizon pitch stays finite`() {
        val lift = OrbitMath.targetLift(18f, fov, 0f, 0.09f)
        assertTrue("lift $lift must stay finite", lift.isFinite())
        assertEquals(OrbitMath.targetLift(18f, fov, 0.0001f, 0.09f), lift, 1e-4f)
    }

    @Test
    fun `the lifted target moves toward the camera at yaw zero`() {
        // The camera sits at +Z when yaw = 0 (CameraRig.eye), so pulling the target
        // toward it (+Z) pushes the board center up the screen.
        val (tx, tz) = OrbitMath.liftedTarget(3f, -2f, 0f, 1.5f)
        assertEquals(3f, tx, 1e-4f)
        assertEquals(-0.5f, tz, 1e-4f)
    }

    @Test
    fun `the lifted target follows the yaw a quarter turn`() {
        val (tx, tz) = OrbitMath.liftedTarget(3f, -2f, (Math.PI / 2).toFloat(), 1.5f)
        assertEquals(4.5f, tx, 1e-4f)
        assertEquals(-2f, tz, 1e-4f)
    }

    @Test
    fun `a zero lift returns the board center at every yaw`() {
        for (yaw in floatArrayOf(0f, 0.7f, 1.57f, 3.1f, 4.9f, 6.2f)) {
            val (tx, tz) = OrbitMath.liftedTarget(-4.25f, 7.5f, yaw, 0f)
            assertEquals("x at yaw $yaw", -4.25f, tx, 1e-4f)
            assertEquals("z at yaw $yaw", 7.5f, tz, 1e-4f)
        }
    }

    /**
     * The behaviour the two functions exist for: with the lifted target the board center
     * projects that fraction of the viewport height ABOVE the screen center, at every
     * yaw, and stays horizontally centered. Slightly under the asked fraction because
     * the target also came closer to the camera (the frustum narrows) — never over.
     */
    @Test
    fun `the lift raises the projected board center by about the asked fraction`() {
        val viewportW = 1080
        val viewportH = 2340
        val fraction = 0.0925f
        for (yaw in floatArrayOf(0f, 0.7f, 1.9f, 3.6f, 5.4f)) {
            val rig = CameraRig(distance = 18f, yaw = yaw, pitch = pitch55)
            val lift = OrbitMath.targetLift(rig.distance, fov, rig.pitch, fraction)
            val (tx, tz) = OrbitMath.liftedTarget(0f, 0f, rig.yaw, lift)
            rig.targetX = tx
            rig.targetZ = tz
            val projected = rig.project(Float3(0f, 0f, 0f), viewportW, viewportH)!!
            val rise = viewportH / 2f - projected.y
            assertEquals("board center stays centered at yaw $yaw", viewportW / 2f, projected.x, 0.05f)
            assertTrue("board center must rise at yaw $yaw, rose $rise", rise > 0f)
            assertEquals("rise at yaw $yaw", fraction * viewportH, rise, 0.08f * fraction * viewportH)
            assertTrue("rise $rise must not overshoot", rise <= fraction * viewportH + 0.5f)
        }
    }

    @Test
    fun `a bigger fraction raises the board center further`() {
        val viewportW = 1080
        val viewportH = 2340
        var previous = viewportH / 2f
        for (fraction in floatArrayOf(0.03f, 0.0575f, 0.0925f, 0.15f)) {
            val rig = CameraRig(distance = 18f, yaw = 1.1f, pitch = pitch55)
            val lift = OrbitMath.targetLift(rig.distance, fov, rig.pitch, fraction)
            val (tx, tz) = OrbitMath.liftedTarget(0f, 0f, rig.yaw, lift)
            rig.targetX = tx
            rig.targetZ = tz
            val y = rig.project(Float3(0f, 0f, 0f), viewportW, viewportH)!!.y
            assertTrue("fraction $fraction must sit higher than the previous one", y < previous)
            previous = y
        }
    }
}
