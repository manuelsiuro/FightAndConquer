package com.msa.fightandconquer.render

import com.google.android.filament.Camera
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.cross
import dev.romainguy.kotlin.math.dot
import dev.romainguy.kotlin.math.normalize
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Orbit camera over the board plane: a look-at [targetX]/[targetZ] on the ground,
 * spherical offset by yaw/pitch/distance. All motion is clamped; [update] pushes the
 * pose into Filament each frame. Also the source of truth for picking rays.
 */
class CameraRig(
    var targetX: Float = 0f,
    var targetZ: Float = 0f,
    var distance: Float = 14f,
    /** Radians. 0 looks toward -Z ("north" up the screen). */
    var yaw: Float = 0f,
    /** Radians above the horizon. */
    var pitch: Float = Math.toRadians(55.0).toFloat(),
) {
    var minTargetX = -50f; var maxTargetX = 50f
    var minTargetZ = -50f; var maxTargetZ = 50f
    var minDistance = 5f; var maxDistance = 35f
    private val minPitch = Math.toRadians(35.0).toFloat()
    private val maxPitch = Math.toRadians(70.0).toFloat()

    /** Additive camera-shake offset (decaying spring, driven by animations). */
    var shake = Float3(0f, 0f, 0f)

    fun boundsFromBoard(minX: Float, maxX: Float, minZ: Float, maxZ: Float, margin: Float = 2f) {
        minTargetX = minX - margin; maxTargetX = maxX + margin
        minTargetZ = minZ - margin; maxTargetZ = maxZ + margin
    }

    fun eye(): Float3 {
        val horizontal = distance * cos(pitch)
        return Float3(
            targetX + horizontal * sin(yaw),
            distance * sin(pitch),
            targetZ + horizontal * cos(yaw),
        ) + shake
    }

    fun target(): Float3 = Float3(targetX, 0f, targetZ) + shake

    /** Screen-space pan (pixels) -> ground-plane translation of the target. */
    fun pan(dxPx: Float, dyPx: Float, viewportHeightPx: Int) {
        // World units per pixel at the target's depth for a vertical-FOV camera.
        val worldPerPx = 2f * distance * tan(Math.toRadians(RenderEngine.FOV_DEGREES / 2).toFloat()) /
            viewportHeightPx
        // Screen right/up projected onto the ground plane, honoring yaw.
        val rightX = cos(yaw); val rightZ = -sin(yaw)
        val upX = -sin(yaw); val upZ = -cos(yaw)
        targetX -= (dxPx * rightX - dyPx * upX) * worldPerPx
        targetZ -= (dxPx * rightZ - dyPx * upZ) * worldPerPx
        clampTarget()
    }

    fun zoomBy(factor: Float) {
        distance = (distance / factor).coerceIn(minDistance, maxDistance)
    }

    fun rotateBy(radians: Float) {
        yaw += radians
    }

    fun pitchBy(radians: Float) {
        pitch = (pitch + radians).coerceIn(minPitch, maxPitch)
    }

    private fun clampTarget() {
        targetX = targetX.coerceIn(minTargetX, maxTargetX)
        targetZ = targetZ.coerceIn(minTargetZ, maxTargetZ)
    }

    fun update(camera: Camera) {
        val e = eye()
        val t = target()
        camera.lookAt(
            e.x.toDouble(), e.y.toDouble(), e.z.toDouble(),
            t.x.toDouble(), t.y.toDouble(), t.z.toDouble(),
            0.0, 1.0, 0.0,
        )
    }

    /**
     * World point -> screen pixels, exact inverse of [rayThrough]'s basis (same FOV,
     * same eye/target incl. shake — labels ride camera rumble with the board).
     * Null when the point is at/behind the eye plane.
     */
    fun project(world: Float3, viewportW: Int, viewportH: Int): dev.romainguy.kotlin.math.Float2? {
        val e = eye()
        val t = target()
        val forward = normalize(t - e)
        val right = normalize(cross(forward, Float3(0f, 1f, 0f)))
        val up = cross(right, forward)
        val d = world - e
        val zCam = dot(d, forward)
        if (zCam <= 0.01f) return null
        val tanHalf = tan(Math.toRadians(RenderEngine.FOV_DEGREES / 2).toFloat())
        val aspect = viewportW.toFloat() / viewportH
        val ndcX = dot(d, right) / (zCam * tanHalf * aspect)
        val ndcY = dot(d, up) / (zCam * tanHalf)
        return dev.romainguy.kotlin.math.Float2(
            (ndcX + 1f) * 0.5f * viewportW,
            (1f - ndcY) * 0.5f * viewportH,
        )
    }

    /** Camera basis for ray reconstruction (must match [update]'s look-at). */
    fun rayThrough(xPx: Float, yPx: Float, viewportW: Int, viewportH: Int): Pair<Float3, Float3> {
        val e = eye()
        val t = target()
        val forward = normalize(t - e)
        val right = normalize(cross(forward, Float3(0f, 1f, 0f)))
        val up = cross(right, forward)
        val tanHalf = tan(Math.toRadians(RenderEngine.FOV_DEGREES / 2).toFloat())
        val aspect = viewportW.toFloat() / viewportH
        val ndcX = (2f * xPx / viewportW) - 1f
        val ndcY = 1f - (2f * yPx / viewportH)
        val dir = normalize(forward + right * (ndcX * tanHalf * aspect) + up * (ndcY * tanHalf))
        return e to dir
    }
}
