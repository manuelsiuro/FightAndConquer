package com.msa.fightandconquer.render.mesh

import com.google.android.filament.Engine

/** How a part gets tinted. */
enum class ColorRole { FACTION, GOLD, TREE_FOLIAGE, TRUNK, STONE }

/** One visual part of a piece: a mesh plus its tint role. */
class Part(val mesh: GpuMesh, val role: ColorRole)

enum class PieceKind { UNIT_T1, UNIT_T2, UNIT_T3, UNIT_T4, CAPITAL, FARM, TOWER, STRONG_TOWER, TREE, GRAVESTONE }

/**
 * The low-poly "chess piece" vocabulary (doc section 5), built once per engine and
 * shared by every renderable. Sizes are tuned to hex circumradius 0.5.
 */
class PieceMeshes(engine: Engine) {

    private val parts: Map<PieceKind, List<Part>> = buildMap {
        fun up(mesh: MeshData) = mesh.upload(engine)

        // Tier 1 Peasant: simple cone token.
        put(PieceKind.UNIT_T1, listOf(Part(up(Primitives.cone(0.17f, 0.34f, 10)), ColorRole.FACTION)))
        // Tier 2 Spearman: elongated diamond crystal.
        put(PieceKind.UNIT_T2, listOf(Part(up(Primitives.bipyramid(0.17f, 0.18f, 0.5f, 6)), ColorRole.FACTION)))
        // Tier 3 Baron: hex pillar with a gold cap.
        put(
            PieceKind.UNIT_T3,
            listOf(
                Part(up(Primitives.prism(List(6) { 0.17f }, 0.34f)), ColorRole.FACTION),
                Part(up(Primitives.prism(List(6) { 0.19f }, 0.08f, baseY = 0.34f)), ColorRole.GOLD),
            ),
        )
        // Tier 4 Knight: bold star extrusion.
        put(
            PieceKind.UNIT_T4,
            listOf(Part(up(Primitives.prism(Primitives.starProfile(0.26f, 0.12f), 0.3f)), ColorRole.FACTION)),
        )
        // Capital: faction keep with a gold roof.
        put(
            PieceKind.CAPITAL,
            listOf(
                Part(up(Primitives.cylinder(0.18f, 0.3f, 10)), ColorRole.FACTION),
                Part(up(Primitives.cone(0.24f, 0.2f, 10, baseY = 0.3f)), ColorRole.GOLD),
            ),
        )
        // Farm: greenhouse wedge.
        put(PieceKind.FARM, listOf(Part(up(Primitives.wedge(0.24f, 0.2f, 0.16f)), ColorRole.FACTION)))
        // Tower: cylinder + cone cap.
        put(
            PieceKind.TOWER,
            listOf(
                Part(up(Primitives.cylinder(0.13f, 0.4f, 10)), ColorRole.STONE),
                Part(up(Primitives.cone(0.19f, 0.22f, 10, baseY = 0.4f)), ColorRole.FACTION),
            ),
        )
        // Strong tower: cube base with an intersecting cylinder + cone top.
        put(
            PieceKind.STRONG_TOWER,
            listOf(
                Part(up(Primitives.box(0.17f, 0.28f, 0.17f)), ColorRole.STONE),
                Part(up(Primitives.cylinder(0.11f, 0.27f, 10, baseY = 0.28f)), ColorRole.STONE),
                Part(up(Primitives.cone(0.16f, 0.18f, 10, baseY = 0.55f)), ColorRole.FACTION),
            ),
        )
        // Tree: trunk + stacked juniper cones.
        put(
            PieceKind.TREE,
            listOf(
                Part(up(Primitives.cylinder(0.05f, 0.16f, 7)), ColorRole.TRUNK),
                Part(up(Primitives.cone(0.2f, 0.26f, 8, baseY = 0.14f)), ColorRole.TREE_FOLIAGE),
                Part(up(Primitives.cone(0.14f, 0.2f, 8, baseY = 0.32f)), ColorRole.TREE_FOLIAGE),
            ),
        )
        // Gravestone: small rounded-feel slab.
        put(PieceKind.GRAVESTONE, listOf(Part(up(Primitives.box(0.11f, 0.24f, 0.05f)), ColorRole.STONE)))
    }

    fun partsFor(kind: PieceKind): List<Part> = parts.getValue(kind)

    fun unitKind(tier: Int): PieceKind = when (tier) {
        1 -> PieceKind.UNIT_T1
        2 -> PieceKind.UNIT_T2
        3 -> PieceKind.UNIT_T3
        else -> PieceKind.UNIT_T4
    }

    fun destroy(engine: Engine) {
        parts.values.flatten().forEach { it.mesh.destroy(engine) }
    }
}
