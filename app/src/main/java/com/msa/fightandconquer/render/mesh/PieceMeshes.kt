package com.msa.fightandconquer.render.mesh

import android.content.Context
import com.google.android.filament.Engine

/** How a part gets tinted. */
enum class ColorRole { FACTION, GOLD, TREE_FOLIAGE, TRUNK, STONE, PIP }

/** One visual part of a piece: a mesh plus its tint role. */
class Part(val mesh: GpuMesh, val role: ColorRole)

enum class PieceKind {
    UNIT_T1, UNIT_T2, UNIT_T3, UNIT_T4,
    CAPITAL, FARM, TOWER, STRONG_TOWER,
    MINE, MARKET, LUMBER_CAMP, WATCHTOWER,
    TREE, GRAVESTONE,
    GOLD_VEIN, FERTILE,
}

/**
 * The piece model set, built once per engine and shared by every renderable.
 * Sizes are tuned to hex circumradius 0.5.
 *
 * Loader-first: Blender-authored minis baked into assets/pieces/<name>.pmesh take
 * priority (tools/glb2pmesh.py); the procedural token set remains as a per-kind
 * fallback so a missing asset degrades gracefully instead of crashing.
 */
class PieceMeshes(private val engine: Engine, context: Context? = null) {

    private val parts: Map<PieceKind, List<Part>> =
        PieceKind.entries.associateWith { kind ->
            context?.let { PieceMeshLoader.load(it, engine, kind.name.lowercase()) }
                ?: proceduralFor(kind)
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

    // ----- procedural fallback set (original token designs) -----

    private fun up(mesh: MeshData) = mesh.upload(engine)

    private fun build(block: MeshBuilder.() -> Unit) = up(MeshBuilder().apply(block).build())

    /** Tier pips: ink collar rings stacked low on the plinth — countable at any zoom/yaw. */
    private fun pips(count: Int, radius: Float, firstY: Float = 0.014f, step: Float = 0.034f) =
        build {
            with(Primitives) {
                for (k in 0 until count) cylinderInto(radius, 0.02f, 6, baseY = firstY + k * step)
            }
        }

    private fun proceduralFor(kind: PieceKind): List<Part> = when (kind) {
        // Units: pawn -> spear -> rook -> king; heights 0.30 / 0.41 / 0.48 / 0.55;
        // gold: none -> tip -> cornice -> crown + base ring; pips 1..4.
        PieceKind.UNIT_T1 -> listOf(
            Part(up(Primitives.cylinder(0.13f, 0.05f, 8)), ColorRole.FACTION),
            Part(pips(1, 0.148f, firstY = 0.015f), ColorRole.PIP),
            Part(up(Primitives.cone(0.105f, 0.19f, 8, baseY = 0.05f)), ColorRole.FACTION),
            Part(up(Primitives.sphere(0.055f, 3, 8, centerY = 0.245f)), ColorRole.FACTION),
        )
        PieceKind.UNIT_T2 -> listOf(
            Part(up(Primitives.cylinder(0.135f, 0.08f, 8)), ColorRole.FACTION),
            Part(pips(2, 0.152f), ColorRole.PIP),
            Part(up(Primitives.bipyramid(0.095f, waistY = 0.14f, apexY = 0.27f, segments = 6, baseY = 0.08f)), ColorRole.FACTION),
            Part(up(Primitives.cone(0.04f, 0.10f, 6, baseY = 0.315f)), ColorRole.GOLD),
        )
        PieceKind.UNIT_T3 -> listOf(
            Part(up(Primitives.cylinder(0.14f, 0.11f, 8)), ColorRole.FACTION),
            Part(pips(3, 0.157f), ColorRole.PIP),
            Part(up(Primitives.prism(List(6) { 0.115f }, 0.26f, baseY = 0.11f)), ColorRole.FACTION),
            Part(up(Primitives.prism(List(6) { 0.135f }, 0.03f, baseY = 0.37f)), ColorRole.GOLD),
            Part(up(Primitives.merlonRing(4, 0.10f, 0.045f, 0.08f, 0.032f, baseY = 0.40f)), ColorRole.FACTION),
        )
        PieceKind.UNIT_T4 -> listOf(
            Part(up(Primitives.cylinder(0.162f, 0.03f, 8)), ColorRole.GOLD),
            Part(up(Primitives.cylinder(0.145f, 0.18f, 8, baseY = 0.03f)), ColorRole.FACTION),
            Part(pips(4, 0.162f, firstY = 0.042f), ColorRole.PIP),
            Part(up(Primitives.frustum(0.115f, 0.08f, 0.24f, 8, baseY = 0.18f)), ColorRole.FACTION),
            Part(up(Primitives.sphere(0.06f, 3, 8, centerY = 0.44f)), ColorRole.FACTION),
            Part(up(Primitives.prism(Primitives.starProfile(0.085f, 0.05f), 0.05f, baseY = 0.49f)), ColorRole.GOLD),
        )

        // Capital: keep + corner merlons + turret + gold cap + banner.
        PieceKind.CAPITAL -> listOf(
            Part(up(Primitives.boxAt(0f, 0f, 0.17f, 0.28f, 0.14f)), ColorRole.FACTION),
            Part(up(Primitives.boxAt(0f, 0f, 0.185f, 0.035f, 0.155f, baseY = 0.28f)), ColorRole.GOLD),
            Part(
                build {
                    with(Primitives) {
                        for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) {
                            boxInto(sx * 0.135f, sz * 0.105f, 0.035f, 0.05f, 0.035f, baseY = 0.315f)
                        }
                    }
                },
                ColorRole.FACTION,
            ),
            Part(up(Primitives.cylinder(0.075f, 0.46f, 8)), ColorRole.STONE),
            Part(up(Primitives.cone(0.10f, 0.10f, 8, baseY = 0.46f)), ColorRole.GOLD),
            Part(up(Primitives.boxAt(0f, 0f, 0.008f, 0.18f, 0.008f, baseY = 0.52f)), ColorRole.PIP),
            Part(up(Primitives.pennant(attachX = 0.008f, topY = 0.70f, drop = 0.07f, length = 0.14f)), ColorRole.GOLD),
        )

        // Tower: tapered crenellated turret with a faction band.
        PieceKind.TOWER -> listOf(
            Part(up(Primitives.cylinder(0.16f, 0.05f, 8)), ColorRole.STONE),
            Part(up(Primitives.frustum(0.14f, 0.115f, 0.30f, 8, baseY = 0.05f)), ColorRole.STONE),
            Part(up(Primitives.cylinder(0.145f, 0.035f, 8, baseY = 0.305f)), ColorRole.FACTION),
            Part(up(Primitives.cylinder(0.15f, 0.045f, 8, baseY = 0.35f)), ColorRole.STONE),
            Part(up(Primitives.merlonRing(5, 0.115f, 0.038f, 0.07f, 0.028f, baseY = 0.395f)), ColorRole.STONE),
        )

        // Strong tower / castle: twin turrets + faction wall + ink gate.
        PieceKind.STRONG_TOWER -> listOf(
            Part(
                build {
                    with(Primitives) {
                        for (sx in intArrayOf(-1, 1)) {
                            cylinderInto(0.095f, 0.42f, 6, cx = sx * 0.155f)
                            cylinderInto(0.115f, 0.035f, 6, baseY = 0.42f, cx = sx * 0.155f)
                            merlonRingInto(3, 0.085f, 0.03f, 0.055f, 0.024f, baseY = 0.455f, cx = sx * 0.155f)
                        }
                    }
                },
                ColorRole.STONE,
            ),
            Part(
                build {
                    with(Primitives) {
                        boxInto(0f, 0f, 0.10f, 0.26f, 0.055f)
                        boxInto(-0.05f, 0f, 0.028f, 0.045f, 0.058f, baseY = 0.26f)
                        boxInto(0.05f, 0f, 0.028f, 0.045f, 0.058f, baseY = 0.26f)
                    }
                },
                ColorRole.FACTION,
            ),
            Part(up(Primitives.boxAt(0f, 0f, 0.045f, 0.14f, 0.06f)), ColorRole.PIP),
        )

        // Farm: house + roof + chimney + crop rows.
        PieceKind.FARM -> listOf(
            Part(up(Primitives.boxAt(-0.15f, -0.08f, 0.11f, 0.13f, 0.09f)), ColorRole.FACTION),
            Part(up(Primitives.wedgeAt(-0.15f, -0.08f, 0.13f, 0.10f, 0.11f, baseY = 0.13f)), ColorRole.TRUNK),
            Part(up(Primitives.boxAt(-0.20f, -0.13f, 0.02f, 0.12f, 0.02f, baseY = 0.10f)), ColorRole.STONE),
            Part(
                build {
                    with(Primitives) {
                        for (cz in floatArrayOf(-0.12f, 0.01f, 0.14f)) {
                            boxInto(0.14f, cz, 0.16f, 0.035f, 0.035f)
                        }
                    }
                },
                ColorRole.TREE_FOLIAGE,
            ),
        )

        // Economy buildings (expansion): silhouette-faithful tokens.
        // Mine: rock mound + timber portal + ore + claim flag.
        PieceKind.MINE -> listOf(
            Part(up(Primitives.frustum(0.17f, 0.10f, 0.16f, 8)), ColorRole.STONE),
            Part(up(Primitives.boxAt(0f, -0.14f, 0.05f, 0.09f, 0.02f)), ColorRole.TRUNK),
            Part(up(Primitives.sphere(0.045f, 3, 8, centerY = 0.19f)), ColorRole.GOLD),
            Part(up(Primitives.boxAt(0.14f, 0.10f, 0.02f, 0.24f, 0.02f)), ColorRole.FACTION),
        )
        // Market: counter + faction awning + coin stack.
        PieceKind.MARKET -> listOf(
            Part(
                build {
                    with(Primitives) {
                        boxInto(0f, 0f, 0.12f, 0.10f, 0.08f)
                        boxInto(-0.10f, 0f, 0.015f, 0.20f, 0.015f)
                        boxInto(0.10f, 0f, 0.015f, 0.20f, 0.015f)
                    }
                },
                ColorRole.TRUNK,
            ),
            Part(up(Primitives.wedgeAt(0f, 0f, 0.14f, 0.09f, 0.11f, baseY = 0.20f)), ColorRole.FACTION),
            Part(up(Primitives.cylinder(0.03f, 0.035f, 6, baseY = 0.10f, cx = 0.05f, cz = 0.03f)), ColorRole.GOLD),
        )
        // Lumber camp: log pile + stump + faction lean-to.
        PieceKind.LUMBER_CAMP -> listOf(
            Part(
                build {
                    with(Primitives) {
                        boxInto(0f, 0.06f, 0.11f, 0.06f, 0.035f)
                        boxInto(0f, -0.02f, 0.11f, 0.06f, 0.035f)
                        boxInto(0f, 0.02f, 0.10f, 0.05f, 0.03f, baseY = 0.06f)
                        cylinderInto(0.045f, 0.06f, 7, cx = 0.15f, cz = -0.12f)
                    }
                },
                ColorRole.TRUNK,
            ),
            Part(up(Primitives.boxAt(0.15f, -0.12f, 0.012f, 0.07f, 0.03f, baseY = 0.05f)), ColorRole.STONE),
            Part(up(Primitives.wedgeAt(-0.14f, -0.09f, 0.07f, 0.05f, 0.06f, baseY = 0.12f)), ColorRole.FACTION),
        )
        // Watchtower: tall skeletal post + platform + brazier + faction pennant.
        PieceKind.WATCHTOWER -> listOf(
            Part(
                build {
                    with(Primitives) {
                        cylinderInto(0.05f, 0.42f, 6)
                        boxInto(0f, 0f, 0.08f, 0.02f, 0.08f, baseY = 0.42f)
                    }
                },
                ColorRole.TRUNK,
            ),
            Part(up(Primitives.boxAt(0f, 0f, 0.08f, 0.03f, 0.08f, baseY = 0.44f)), ColorRole.FACTION),
            Part(up(Primitives.sphere(0.03f, 3, 8, centerY = 0.52f)), ColorRole.GOLD),
            Part(up(Primitives.boxAt(0f, 0f, 0.006f, 0.12f, 0.006f, baseY = 0.46f)), ColorRole.PIP),
            Part(up(Primitives.pennant(attachX = 0.006f, topY = 0.58f, drop = 0.05f, length = 0.10f)), ColorRole.FACTION),
        )

        // Tree + gravestone.
        PieceKind.TREE -> listOf(
            Part(up(Primitives.cylinder(0.05f, 0.16f, 7)), ColorRole.TRUNK),
            Part(up(Primitives.cone(0.2f, 0.26f, 8, baseY = 0.14f)), ColorRole.TREE_FOLIAGE),
            Part(up(Primitives.cone(0.14f, 0.2f, 8, baseY = 0.32f)), ColorRole.TREE_FOLIAGE),
        )
        PieceKind.GRAVESTONE -> listOf(
            Part(up(Primitives.box(0.11f, 0.24f, 0.05f)), ColorRole.STONE),
        )

        // Terrain deposits: low edge-scatter rings (hex center stays clear for units).
        PieceKind.GOLD_VEIN -> listOf(
            Part(
                build {
                    with(Primitives) {
                        cylinderInto(0.06f, 0.05f, 6, cx = 0.24f, cz = 0.10f)
                        cylinderInto(0.05f, 0.045f, 6, cx = 0.10f, cz = -0.26f)
                        cylinderInto(0.055f, 0.055f, 6, cx = -0.22f, cz = 0.14f)
                    }
                },
                ColorRole.STONE,
            ),
            Part(
                build {
                    with(Primitives) {
                        cylinderInto(0.022f, 0.03f, 5, baseY = 0.05f, cx = 0.24f, cz = 0.10f)
                        cylinderInto(0.02f, 0.028f, 5, baseY = 0.045f, cx = 0.10f, cz = -0.26f)
                        cylinderInto(0.02f, 0.03f, 5, baseY = 0.055f, cx = -0.22f, cz = 0.14f)
                    }
                },
                ColorRole.GOLD,
            ),
        )
        PieceKind.FERTILE -> listOf(
            Part(
                build {
                    with(Primitives) {
                        cylinderInto(0.03f, 0.06f, 5, cx = 0.24f, cz = 0.08f)
                        cylinderInto(0.028f, 0.055f, 5, cx = 0.12f, cz = -0.24f)
                        cylinderInto(0.03f, 0.06f, 5, cx = -0.20f, cz = 0.16f)
                        cylinderInto(0.026f, 0.05f, 5, cx = -0.04f, cz = 0.27f)
                    }
                },
                ColorRole.TREE_FOLIAGE,
            ),
            Part(up(Primitives.cylinder(0.045f, 0.035f, 6, cx = 0.05f, cz = -0.05f)), ColorRole.TRUNK),
        )
    }
}
