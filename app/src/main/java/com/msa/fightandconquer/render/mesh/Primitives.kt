package com.msa.fightandconquer.render.mesh

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.normalize
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural low-poly meshes. Everything is flat-shaded and unit-ish sized;
 * entities scale/position via their transforms.
 *
 * Board metrics (shared with picking): pointy-top hexes, circumradius [HEX_RADIUS],
 * prism top at [HEX_TOP_Y]; captured tiles rise by [CAPTURE_RAISE].
 */
object Primitives {
    const val HEX_RADIUS = 0.5f
    const val HEX_HEIGHT = 0.25f
    const val HEX_BEVEL = 0.05f
    const val HEX_SKIRT = 0.15f
    const val HEX_TOP_Y = HEX_HEIGHT
    const val CAPTURE_RAISE = 0.1f

    private val UP = Float3(0f, 1f, 0f)

    /** Pointy-top hex corner k (k=0 points toward -Z). */
    private fun corner(radius: Float, k: Int, y: Float): Float3 {
        val angle = Math.toRadians(60.0 * k).toFloat()
        return Float3(radius * sin(angle), y, -radius * cos(angle))
    }

    /**
     * The board tile: beveled hexagonal prism. Top face at y=[HEX_HEIGHT], a 45-degree
     * bevel ring catching highlights, and side walls extending down to -[HEX_SKIRT] so
     * raised tiles never reveal a gap.
     */
    fun hexPrism(): MeshData {
        val b = MeshBuilder()
        val topY = HEX_HEIGHT
        val bevelY = HEX_HEIGHT - HEX_BEVEL
        val innerR = HEX_RADIUS - HEX_BEVEL
        val outerR = HEX_RADIUS

        // Top face fan.
        val topCorners = (0..5).map { corner(innerR, it, topY) }
        val center = Float3(0f, topY, 0f)
        for (k in 0..5) {
            b.addTriangle(center, topCorners[k], topCorners[(k + 1) % 6], UP)
        }
        // Bevel ring + side walls.
        for (k in 0..5) {
            val k1 = (k + 1) % 6
            val outward = normalize(
                Float3(
                    (topCorners[k].x + topCorners[k1].x) / 2f,
                    0f,
                    (topCorners[k].z + topCorners[k1].z) / 2f,
                ),
            )
            val bevelDir = normalize(outward + UP)
            val o0 = corner(outerR, k, bevelY)
            val o1 = corner(outerR, k1, bevelY)
            b.addQuad(topCorners[k], topCorners[k1], o1, o0, bevelDir)

            val s0 = corner(outerR, k, -HEX_SKIRT)
            val s1 = corner(outerR, k1, -HEX_SKIRT)
            b.addQuad(o0, o1, s1, s0, outward)
        }
        return b.build()
    }

    /** Large ground quad centered at origin (bring-up shadow catcher). */
    fun groundPlane(halfSize: Float): MeshData {
        val b = MeshBuilder()
        b.addQuad(
            Float3(-halfSize, 0f, -halfSize),
            Float3(halfSize, 0f, -halfSize),
            Float3(halfSize, 0f, halfSize),
            Float3(-halfSize, 0f, halfSize),
            UP,
        )
        return b.build()
    }

    /** Flat hexagonal plate (top face only) for selection/legal-move highlights. */
    fun hexDisc(radius: Float): MeshData {
        val b = MeshBuilder()
        val rim = (0..5).map { corner(radius, it, 0f) }
        val center = Float3(0f, 0f, 0f)
        for (k in 0..5) b.addTriangle(center, rim[k], rim[(k + 1) % 6], UP)
        return b.build()
    }

    // ----- generic low-poly builders for the chess-piece vocabulary -----

    private fun ring(radius: Float, segments: Int, y: Float): List<Float3> =
        (0 until segments).map { k ->
            val angle = (2.0 * Math.PI * k / segments).toFloat()
            Float3(radius * sin(angle), y, -radius * cos(angle))
        }

    private fun MeshBuilder.sideWall(bottom: List<Float3>, top: List<Float3>) {
        val n = bottom.size
        for (k in 0 until n) {
            val k1 = (k + 1) % n
            val mid = normalize(
                Float3(
                    bottom[k].x + bottom[k1].x + top[k].x + top[k1].x,
                    0f,
                    bottom[k].z + bottom[k1].z + top[k].z + top[k1].z,
                ),
            )
            addQuad(bottom[k], bottom[k1], top[k1], top[k], mid)
        }
    }

    private fun MeshBuilder.capFan(rim: List<Float3>, centerY: Float, upward: Boolean) {
        val center = Float3(0f, centerY, 0f)
        val dir = if (upward) UP else Float3(0f, -1f, 0f)
        for (k in rim.indices) {
            addTriangle(center, rim[k], rim[(k + 1) % rim.size], dir)
        }
    }

    private fun MeshBuilder.apexFan(rim: List<Float3>, apex: Float3) {
        for (k in rim.indices) {
            val k1 = (k + 1) % rim.size
            val outward = normalize(
                Float3(rim[k].x + rim[k1].x, 0f, rim[k].z + rim[k1].z),
            )
            val tilt = if (apex.y >= rim[k].y) UP else Float3(0f, -1f, 0f)
            addTriangle(rim[k], rim[k1], apex, normalize(outward + tilt * 0.6f))
        }
    }

    fun cylinder(radius: Float, height: Float, segments: Int, baseY: Float = 0f): MeshData {
        val b = MeshBuilder()
        val bottom = ring(radius, segments, baseY)
        val top = ring(radius, segments, baseY + height)
        b.sideWall(bottom, top)
        b.capFan(top, baseY + height, upward = true)
        return b.build()
    }

    fun cone(radius: Float, height: Float, segments: Int, baseY: Float = 0f): MeshData {
        val b = MeshBuilder()
        val rim = ring(radius, segments, baseY)
        b.apexFan(rim, Float3(0f, baseY + height, 0f))
        b.capFan(rim, baseY, upward = false)
        return b.build()
    }

    /** Two cones base-to-base: the Spearman's "diamond-cut crystal". */
    fun bipyramid(radius: Float, waistY: Float, apexY: Float, segments: Int): MeshData {
        val b = MeshBuilder()
        val waist = ring(radius, segments, waistY)
        b.apexFan(waist, Float3(0f, apexY, 0f))
        b.apexFan(waist, Float3(0f, 0f, 0f))
        return b.build()
    }

    /** N-sided prism (hex pillar, star extrusion...) from a radial profile. */
    fun prism(radii: List<Float>, height: Float, baseY: Float = 0f): MeshData {
        val b = MeshBuilder()
        val n = radii.size
        val bottom = (0 until n).map { k ->
            val angle = (2.0 * Math.PI * k / n).toFloat()
            Float3(radii[k] * sin(angle), baseY, -radii[k] * cos(angle))
        }
        val top = bottom.map { Float3(it.x, baseY + height, it.z) }
        b.sideWall(bottom, top)
        b.capFan(top, baseY + height, upward = true)
        b.capFan(bottom, baseY, upward = false)
        return b.build()
    }

    /** 5-point star profile radii (10 vertices alternating outer/inner). */
    fun starProfile(outer: Float, inner: Float): List<Float> =
        (0 until 10).map { if (it % 2 == 0) outer else inner }

    fun box(halfW: Float, height: Float, halfD: Float, baseY: Float = 0f): MeshData {
        val b = MeshBuilder()
        val y0 = baseY
        val y1 = baseY + height
        val c = listOf(
            Float3(-halfW, y0, -halfD), Float3(halfW, y0, -halfD),
            Float3(halfW, y0, halfD), Float3(-halfW, y0, halfD),
        )
        val t = c.map { Float3(it.x, y1, it.z) }
        b.sideWall(c, t)
        b.capFan(t, y1, upward = true)
        return b.build()
    }

    /** Triangular-prism wedge: the Farm's minimalist greenhouse. */
    fun wedge(halfW: Float, height: Float, halfD: Float, baseY: Float = 0f): MeshData {
        val b = MeshBuilder()
        val y0 = baseY
        val apex0 = Float3(0f, y0 + height, -halfD)
        val apex1 = Float3(0f, y0 + height, halfD)
        val bl0 = Float3(-halfW, y0, -halfD)
        val br0 = Float3(halfW, y0, -halfD)
        val bl1 = Float3(-halfW, y0, halfD)
        val br1 = Float3(halfW, y0, halfD)
        // Sloped roofs.
        b.addQuad(bl0, apex0, apex1, bl1, Float3(-0.7f, 0.7f, 0f))
        b.addQuad(br0, br1, apex1, apex0, Float3(0.7f, 0.7f, 0f))
        // Gable ends.
        b.addTriangle(bl0, br0, apex0, Float3(0f, 0f, -1f))
        b.addTriangle(bl1, apex1, br1, Float3(0f, 0f, 1f))
        return b.build()
    }
}
