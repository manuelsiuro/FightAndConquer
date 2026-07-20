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

    /** Flat hexagonal plate (top face only) for selection/legal-move highlights. */
    fun hexDisc(radius: Float): MeshData {
        val b = MeshBuilder()
        val rim = (0..5).map { corner(radius, it, 0f) }
        val center = Float3(0f, 0f, 0f)
        for (k in 0..5) b.addTriangle(center, rim[k], rim[(k + 1) % 6], UP)
        return b.build()
    }

    // ----- generic low-poly builders for the chess-piece vocabulary -----

    private fun ring(radius: Float, segments: Int, y: Float, cx: Float = 0f, cz: Float = 0f): List<Float3> =
        (0 until segments).map { k ->
            val angle = (2.0 * Math.PI * k / segments).toFloat()
            Float3(cx + radius * sin(angle), y, cz - radius * cos(angle))
        }

    private fun MeshBuilder.sideWall(bottom: List<Float3>, top: List<Float3>, cx: Float = 0f, cz: Float = 0f) {
        val n = bottom.size
        for (k in 0 until n) {
            val k1 = (k + 1) % n
            val mid = normalize(
                Float3(
                    bottom[k].x + bottom[k1].x + top[k].x + top[k1].x - 4f * cx,
                    0f,
                    bottom[k].z + bottom[k1].z + top[k].z + top[k1].z - 4f * cz,
                ),
            )
            addQuad(bottom[k], bottom[k1], top[k1], top[k], mid)
        }
    }

    private fun MeshBuilder.capFan(rim: List<Float3>, centerY: Float, upward: Boolean, cx: Float = 0f, cz: Float = 0f) {
        val center = Float3(cx, centerY, cz)
        val dir = if (upward) UP else Float3(0f, -1f, 0f)
        for (k in rim.indices) {
            addTriangle(center, rim[k], rim[(k + 1) % rim.size], dir)
        }
    }

    private fun MeshBuilder.apexFan(rim: List<Float3>, apex: Float3, cx: Float = 0f, cz: Float = 0f) {
        for (k in rim.indices) {
            val k1 = (k + 1) % rim.size
            val outward = normalize(
                Float3(rim[k].x + rim[k1].x - 2f * cx, 0f, rim[k].z + rim[k1].z - 2f * cz),
            )
            val tilt = if (apex.y >= rim[k].y) UP else Float3(0f, -1f, 0f)
            addTriangle(rim[k], rim[k1], apex, normalize(outward + tilt * 0.6f))
        }
    }

    fun cylinder(
        radius: Float,
        height: Float,
        segments: Int,
        baseY: Float = 0f,
        cx: Float = 0f,
        cz: Float = 0f,
    ): MeshData {
        val b = MeshBuilder()
        b.cylinderInto(radius, height, segments, baseY, cx, cz)
        return b.build()
    }

    fun MeshBuilder.cylinderInto(
        radius: Float,
        height: Float,
        segments: Int,
        baseY: Float = 0f,
        cx: Float = 0f,
        cz: Float = 0f,
    ) {
        val bottom = ring(radius, segments, baseY, cx, cz)
        val top = ring(radius, segments, baseY + height, cx, cz)
        sideWall(bottom, top, cx, cz)
        capFan(top, baseY + height, upward = true, cx, cz)
    }

    fun cone(
        radius: Float,
        height: Float,
        segments: Int,
        baseY: Float = 0f,
        cx: Float = 0f,
        cz: Float = 0f,
    ): MeshData {
        val b = MeshBuilder()
        val rim = ring(radius, segments, baseY, cx, cz)
        b.apexFan(rim, Float3(cx, baseY + height, cz), cx, cz)
        b.capFan(rim, baseY, upward = false, cx, cz)
        return b.build()
    }

    /** Cylinder with different top/bottom radii (robes, tapered turrets). */
    fun frustum(rBottom: Float, rTop: Float, height: Float, segments: Int, baseY: Float = 0f): MeshData {
        val b = MeshBuilder()
        val bottom = ring(rBottom, segments, baseY)
        val top = ring(rTop, segments, baseY + height)
        b.sideWall(bottom, top)
        b.capFan(top, baseY + height, upward = true)
        return b.build()
    }

    /** Flat-shaded UV sphere. Tri count = 2*slices*(stacks-1); 3x8 = 32 tris. */
    fun sphere(radius: Float, stacks: Int, slices: Int, centerY: Float): MeshData {
        val b = MeshBuilder()
        val center = Float3(0f, centerY, 0f)
        val rings = (0..stacks).map { i ->
            val phi = Math.PI * i / stacks
            val y = centerY + radius * cos(phi).toFloat()
            val r = radius * sin(phi).toFloat()
            ring(maxOf(r, 0f), slices, y)
        }
        for (i in 0 until stacks) {
            for (k in 0 until slices) {
                val k1 = (k + 1) % slices
                val v00 = rings[i][k]; val v01 = rings[i][k1]
                val v10 = rings[i + 1][k]; val v11 = rings[i + 1][k1]
                when (i) {
                    0 -> {
                        val mid = (v00 + v10 + v11) * (1f / 3f)
                        b.addTriangle(v00, v10, v11, normalize(mid - center))
                    }
                    stacks - 1 -> {
                        val mid = (v00 + v01 + v10) * (1f / 3f)
                        b.addTriangle(v00, v01, v10, normalize(mid - center))
                    }
                    else -> {
                        val mid = (v00 + v01 + v10 + v11) * 0.25f
                        b.addQuad(v00, v01, v11, v10, normalize(mid - center))
                    }
                }
            }
        }
        return b.build()
    }

    /** Two cones base-to-base: the Spearman's "diamond-cut crystal". */
    fun bipyramid(radius: Float, waistY: Float, apexY: Float, segments: Int, baseY: Float = 0f): MeshData {
        val b = MeshBuilder()
        val waist = ring(radius, segments, baseY + waistY)
        b.apexFan(waist, Float3(0f, baseY + apexY, 0f))
        b.apexFan(waist, Float3(0f, baseY, 0f))
        return b.build()
    }

    /** Axis-aligned box at (cx, cz): 4 side quads + top, no bottom. 10 tris. */
    fun MeshBuilder.boxInto(
        cx: Float,
        cz: Float,
        halfW: Float,
        height: Float,
        halfD: Float,
        baseY: Float = 0f,
    ) {
        val y0 = baseY
        val y1 = baseY + height
        val b00 = Float3(cx - halfW, y0, cz - halfD); val b10 = Float3(cx + halfW, y0, cz - halfD)
        val b11 = Float3(cx + halfW, y0, cz + halfD); val b01 = Float3(cx - halfW, y0, cz + halfD)
        val t00 = Float3(cx - halfW, y1, cz - halfD); val t10 = Float3(cx + halfW, y1, cz - halfD)
        val t11 = Float3(cx + halfW, y1, cz + halfD); val t01 = Float3(cx - halfW, y1, cz + halfD)
        addQuad(b00, b10, t10, t00, Float3(0f, 0f, -1f))
        addQuad(b11, b01, t01, t11, Float3(0f, 0f, 1f))
        addQuad(b01, b00, t00, t01, Float3(-1f, 0f, 0f))
        addQuad(b10, b11, t11, t10, Float3(1f, 0f, 0f))
        addQuad(t00, t10, t11, t01, UP)
    }

    fun boxAt(cx: Float, cz: Float, halfW: Float, height: Float, halfD: Float, baseY: Float = 0f): MeshData {
        val b = MeshBuilder()
        b.boxInto(cx, cz, halfW, height, halfD, baseY)
        return b.build()
    }

    /** Triangular-prism wedge at (cx, cz) — gabled roofs. */
    fun MeshBuilder.wedgeInto(
        cx: Float,
        cz: Float,
        halfW: Float,
        height: Float,
        halfD: Float,
        baseY: Float = 0f,
    ) {
        val apex0 = Float3(cx, baseY + height, cz - halfD)
        val apex1 = Float3(cx, baseY + height, cz + halfD)
        val bl0 = Float3(cx - halfW, baseY, cz - halfD)
        val br0 = Float3(cx + halfW, baseY, cz - halfD)
        val bl1 = Float3(cx - halfW, baseY, cz + halfD)
        val br1 = Float3(cx + halfW, baseY, cz + halfD)
        addQuad(bl0, apex0, apex1, bl1, Float3(-0.7f, 0.7f, 0f))
        addQuad(br0, br1, apex1, apex0, Float3(0.7f, 0.7f, 0f))
        addTriangle(bl0, br0, apex0, Float3(0f, 0f, -1f))
        addTriangle(bl1, apex1, br1, Float3(0f, 0f, 1f))
    }

    fun wedgeAt(cx: Float, cz: Float, halfW: Float, height: Float, halfD: Float, baseY: Float = 0f): MeshData {
        val b = MeshBuilder()
        b.wedgeInto(cx, cz, halfW, height, halfD, baseY)
        return b.build()
    }

    /** Torus-free crenellation: [count] outward-facing blocks on a circle, one mesh. */
    fun MeshBuilder.merlonRingInto(
        count: Int,
        ringRadius: Float,
        halfTangential: Float,
        height: Float,
        halfRadial: Float,
        baseY: Float,
        cx: Float = 0f,
        cz: Float = 0f,
    ) {
        for (k in 0 until count) {
            val angle = (2.0 * Math.PI * k / count).toFloat()
            val o = Float3(sin(angle), 0f, -cos(angle))
            val t = Float3(cos(angle), 0f, sin(angle))
            val c = Float3(cx + ringRadius * o.x, baseY, cz + ringRadius * o.z)
            val up = Float3(0f, height, 0f)
            val po = o * halfRadial
            val pt = t * halfTangential
            val b1 = c + pt + po; val b2 = c - pt + po // outer edge
            val b3 = c - pt - po; val b4 = c + pt - po // inner edge
            addQuad(b1, b2, b2 + up, b1 + up, o)
            addQuad(b3, b4, b4 + up, b3 + up, -o)
            addQuad(b4, b1, b1 + up, b4 + up, t)
            addQuad(b2, b3, b3 + up, b2 + up, -t)
            addQuad(b1 + up, b2 + up, b3 + up, b4 + up, UP)
        }
    }

    fun merlonRing(
        count: Int,
        ringRadius: Float,
        halfTangential: Float,
        height: Float,
        halfRadial: Float,
        baseY: Float,
        cx: Float = 0f,
        cz: Float = 0f,
    ): MeshData {
        val b = MeshBuilder()
        b.merlonRingInto(count, ringRadius, halfTangential, height, halfRadial, baseY, cx, cz)
        return b.build()
    }

    /** Flat hex ring (defense auras), rotation-aligned with hexPrism/hexDisc. 12 tris. */
    fun hexAnnulus(innerR: Float, outerR: Float): MeshData {
        val b = MeshBuilder()
        val inner = (0..5).map { corner(innerR, it, 0f) }
        val outer = (0..5).map { corner(outerR, it, 0f) }
        for (k in 0..5) {
            val k1 = (k + 1) % 6
            b.addQuad(inner[k], inner[k1], outer[k1], outer[k], UP)
        }
        return b.build()
    }

    /** Double-sided vertical flag triangle — no X-rotation needed, built vertical in mesh space. */
    fun pennant(attachX: Float, topY: Float, drop: Float, length: Float): MeshData {
        val b = MeshBuilder()
        val p0 = Float3(attachX, topY, 0f)
        val p1 = Float3(attachX, topY - drop, 0f)
        val p2 = Float3(attachX + length, topY - drop / 2f, 0f)
        b.addTriangle(p0, p1, p2, Float3(0f, 0f, 1f))
        b.addTriangle(p0, p1, p2, Float3(0f, 0f, -1f))
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
