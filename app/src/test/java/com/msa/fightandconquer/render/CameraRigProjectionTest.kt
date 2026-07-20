package com.msa.fightandconquer.render

import dev.romainguy.kotlin.math.Float3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * project() must be the exact inverse of rayThrough(): a point taken along the pick ray
 * for any screen pixel must project back to that pixel. This is what anchors HUD labels
 * (defense chips, coin popups) to board hexes.
 */
class CameraRigProjectionTest {

    private val viewportW = 1080
    private val viewportH = 2340

    private fun rigVariants() = listOf(
        CameraRig(),
        CameraRig(targetX = 4.2f, targetZ = -3.1f, distance = 9f, yaw = 0.7f),
        CameraRig(targetX = -8f, targetZ = 12f, distance = 25f, yaw = -2.3f, pitch = Math.toRadians(40.0).toFloat()),
    )

    @Test
    fun `project inverts rayThrough across the screen`() {
        for (rig in rigVariants()) {
            val pixels = listOf(
                10f to 10f,
                viewportW / 2f to viewportH / 2f,
                viewportW - 5f to viewportH - 5f,
                100f to 2000f,
                900f to 300f,
            )
            for ((px, py) in pixels) {
                val (origin, dir) = rig.rayThrough(px, py, viewportW, viewportH)
                for (t in floatArrayOf(1f, 5f, 30f)) {
                    val world = origin + dir * t
                    val projected = rig.project(world, viewportW, viewportH)!!
                    assertEquals("x for ($px,$py) t=$t", px, projected.x, 0.05f)
                    assertEquals("y for ($px,$py) t=$t", py, projected.y, 0.05f)
                }
            }
        }
    }

    @Test
    fun `points behind the camera project to null`() {
        val rig = CameraRig()
        val eye = rig.eye()
        val target = rig.target()
        val backward = eye + (eye - target) // beyond the eye, away from the board
        assertNull(rig.project(backward, viewportW, viewportH))
    }
}
