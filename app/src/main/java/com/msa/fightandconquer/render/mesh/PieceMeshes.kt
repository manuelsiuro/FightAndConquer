package com.msa.fightandconquer.render.mesh

import android.content.Context
import com.google.android.filament.Engine
import com.msa.fightandconquer.core.model.Civilization

/** How a part gets tinted. */
enum class ColorRole { FACTION, GOLD, TREE_FOLIAGE, TRUNK, STONE, PIP }

/** One visual part of a piece: a mesh plus its tint role. */
class Part(val mesh: GpuMesh, val role: ColorRole)

enum class PieceKind {
    UNIT_T1, UNIT_T2, UNIT_T3, UNIT_T4,
    ARCHER, CATAPULT,
    BOAT, WARSHIP,
    CAPITAL, FARM, TOWER, STRONG_TOWER,
    MINE, MARKET, LUMBER_CAMP, WATCHTOWER, PORT, FISHERY, BRIDGE,
    TREE, GRAVESTONE,
    GOLD_VEIN, FERTILE, FISH_SHOAL,
}

/**
 * The (civilization, kind) -> parts resolution behind [PieceMeshes], generic over the
 * part payload so JVM tests can exercise the exact fallback/ownership logic without
 * Filament. Civ sets load lazily on first request (or via [preload]).
 *
 * Fallback ladder:
 *  - a non-Kingdom civ tries `pieces/<civ>/<kind>.pmesh` for civ-forked kinds, else
 *    SHARES the Kingdom entry (same instance — loaded once, freed once);
 *  - neutral kinds ([PieceMeshes.NEUTRAL_KINDS]) never fork and always share Kingdom;
 *  - Kingdom tries the flat `pieces/<kind>.pmesh`, else the procedural token.
 */
internal class CivArtTable<P : Any>(
    private val loadCivAsset: (Civilization, PieceKind) -> P?,
    private val loadFlatAsset: (PieceKind) -> P?,
    private val procedural: (PieceKind) -> P,
) {
    /** Payloads this table created and owns; fallback shares are excluded, so a
     *  release pass frees every payload exactly once. */
    private val owned = ArrayList<P>()
    private val sets = HashMap<Civilization, Map<PieceKind, P>>()

    /** Kinds for which a civ shipped its OWN asset (everything else is a Kingdom share). */
    private val forked = HashMap<Civilization, Set<PieceKind>>()

    fun get(civ: Civilization, kind: PieceKind): P = setFor(civ).getValue(kind)

    fun preload(civs: Set<Civilization>) = civs.forEach { setFor(it) }

    /**
     * The civilization whose art actually renders for (civ, kind): [civ] itself when it
     * shipped that asset, [Civilization.KINGDOM] for neutral kinds and fallback shares.
     * This is the piece IDENTITY the scene diffs — two civs sharing Kingdom art compare
     * equal, so a capture between them never recreates an identical-looking piece.
     */
    fun artCiv(civ: Civilization, kind: PieceKind): Civilization {
        if (civ == Civilization.KINGDOM) return Civilization.KINGDOM
        setFor(civ) // ensure loaded
        return if (kind in forked.getValue(civ)) civ else Civilization.KINGDOM
    }

    /** Visits every owned payload exactly once (destroy pass), then forgets everything. */
    fun releaseOwned(action: (P) -> Unit) {
        owned.forEach(action)
        owned.clear()
        sets.clear()
        forked.clear()
    }

    private fun setFor(civ: Civilization): Map<PieceKind, P> {
        sets[civ]?.let { return it }
        val set: Map<PieceKind, P>
        if (civ == Civilization.KINGDOM) {
            set = PieceKind.entries.associateWith { kind ->
                (loadFlatAsset(kind) ?: procedural(kind)).also { owned.add(it) }
            }
        } else {
            val kingdom = setFor(Civilization.KINGDOM)
            val civForked = HashSet<PieceKind>()
            set = PieceKind.entries.associateWith { kind ->
                val own = if (kind in PieceMeshes.CIV_FORKED_KINDS) loadCivAsset(civ, kind) else null
                own?.also { owned.add(it); civForked.add(kind) } ?: kingdom.getValue(kind)
            }
            forked[civ] = civForked
        }
        sets[civ] = set
        return set
    }
}

/**
 * The piece model sets, built once per engine and shared by every renderable.
 * Sizes are tuned to hex circumradius 0.5.
 *
 * Keyed by (civilization, kind) so per-civ art ships incrementally: Kingdom IS the
 * flat `assets/pieces/<kind>.pmesh` set; other civs bake into `pieces/<civ>/` and
 * fall back to the Kingdom entry for any kind they don't have yet (see [CivArtTable]).
 *
 * Loader-first: Blender-authored minis baked by tools/glb2pmesh.py take priority;
 * the procedural token set remains as a per-kind Kingdom fallback so a missing asset
 * degrades gracefully instead of crashing.
 */
class PieceMeshes(private val engine: Engine, context: Context? = null) {

    private val table = CivArtTable<List<Part>>(
        loadCivAsset = { civ, kind ->
            context?.let {
                PieceMeshLoader.load(it, engine, "${civ.name.lowercase()}/${kind.name.lowercase()}")
            }
        },
        loadFlatAsset = { kind ->
            context?.let { PieceMeshLoader.load(it, engine, kind.name.lowercase()) }
        },
        procedural = ::proceduralFor,
    )

    /** Loads the art sets for every civ present in a game up front (no first-use hitch). */
    fun preload(civs: Set<Civilization>) = table.preload(civs)

    fun partsFor(civ: Civilization, kind: PieceKind): List<Part> = table.get(civ, kind)

    /** See [CivArtTable.artCiv]: the identity the scene should diff pieces by. */
    fun artCivFor(civ: Civilization, kind: PieceKind): Civilization = table.artCiv(civ, kind)

    fun unitKind(unit: com.msa.fightandconquer.core.model.GameUnit): PieceKind = when (unit.type) {
        com.msa.fightandconquer.core.model.UnitType.ARCHER -> PieceKind.ARCHER
        com.msa.fightandconquer.core.model.UnitType.CATAPULT -> PieceKind.CATAPULT
        com.msa.fightandconquer.core.model.UnitType.TRANSPORT -> PieceKind.BOAT
        com.msa.fightandconquer.core.model.UnitType.WARSHIP -> PieceKind.WARSHIP
        com.msa.fightandconquer.core.model.UnitType.SOLDIER -> when (unit.tier) {
            1 -> PieceKind.UNIT_T1
            2 -> PieceKind.UNIT_T2
            3 -> PieceKind.UNIT_T3
            else -> PieceKind.UNIT_T4
        }
    }

    /** Frees every GpuMesh loaded across all civ sets exactly once (shares excluded). */
    fun destroy(engine: Engine) {
        table.releaseOwned { parts -> parts.forEach { it.mesh.destroy(engine) } }
    }

    companion object {
        /** Ownerless board furniture: never forks per civ, always renders Kingdom art. */
        val NEUTRAL_KINDS: Set<PieceKind> = setOf(
            PieceKind.TREE, PieceKind.GRAVESTONE,
            PieceKind.GOLD_VEIN, PieceKind.FERTILE, PieceKind.FISH_SHOAL,
        )

        /** Player-owned kinds whose art may fork per civilization. */
        val CIV_FORKED_KINDS: Set<PieceKind> = PieceKind.entries.toSet() - NEUTRAL_KINDS
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

        // Special units (expansion): silhouette-faithful tokens.
        // Archer: hooded ranger with a side-held bow arc. H 0.44 (between T2/T3).
        PieceKind.ARCHER -> listOf(
            Part(up(Primitives.cylinder(0.135f, 0.06f, 8)), ColorRole.FACTION),
            Part(pips(1, 0.15f, firstY = 0.02f), ColorRole.PIP),
            Part(up(Primitives.frustum(0.095f, 0.06f, 0.20f, 8, baseY = 0.06f)), ColorRole.FACTION),
            Part(up(Primitives.cone(0.055f, 0.10f, 8, baseY = 0.26f)), ColorRole.FACTION),
            Part(
                build {
                    with(Primitives) {
                        // Bow: three chained thin segments forming a vertical arc at the side.
                        boxInto(0.115f, 0f, 0.012f, 0.10f, 0.012f, baseY = 0.10f)
                        boxInto(0.135f, 0f, 0.012f, 0.08f, 0.012f, baseY = 0.19f)
                        boxInto(0.135f, 0f, 0.012f, 0.08f, 0.012f, baseY = 0.03f)
                    }
                },
                ColorRole.TRUNK,
            ),
            Part(up(Primitives.boxAt(0.155f, 0f, 0.004f, 0.24f, 0.004f, baseY = 0.03f)), ColorRole.PIP),
            Part(up(Primitives.sphere(0.02f, 3, 6, centerY = 0.34f)), ColorRole.GOLD),
        )
        // Catapult: the only wide-low unit — chassis, wheels, angled arm + boulder.
        PieceKind.CATAPULT -> listOf(
            Part(
                build {
                    with(Primitives) {
                        boxInto(0f, 0f, 0.14f, 0.05f, 0.09f, baseY = 0.05f)
                        for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) {
                            cylinderInto(0.055f, 0.03f, 8, baseY = 0.03f, cx = sx * 0.12f, cz = sz * 0.10f)
                        }
                        boxInto(0f, 0.02f, 0.02f, 0.28f, 0.02f, baseY = 0.10f)
                    }
                },
                ColorRole.TRUNK,
            ),
            Part(up(Primitives.boxAt(0f, -0.02f, 0.10f, 0.03f, 0.02f, baseY = 0.10f)), ColorRole.STONE),
            Part(up(Primitives.sphere(0.045f, 3, 8, centerY = 0.42f)), ColorRole.STONE),
            Part(up(Primitives.boxAt(0f, 0.11f, 0.13f, 0.05f, 0.012f, baseY = 0.05f)), ColorRole.FACTION),
            Part(pips(1, 0.16f, firstY = 0.012f), ColorRole.PIP),
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

        // Naval (expansion). Boats float on sea tops; front faces -Z like all pieces.
        // Transport: wide flat-bottomed longboat — hull planks, raised bow/stern
        // posts, short mast with a square faction sail, two cargo crates. H ~0.40.
        PieceKind.BOAT -> listOf(
            Part(
                build {
                    with(Primitives) {
                        boxInto(0f, 0f, 0.10f, 0.05f, 0.20f) // hull slab
                        boxInto(0f, 0f, 0.12f, 0.035f, 0.23f, baseY = 0.05f) // gunwale flare
                        boxInto(0f, -0.235f, 0.035f, 0.12f, 0.035f) // bow post
                        boxInto(0f, 0.235f, 0.035f, 0.10f, 0.035f) // stern post
                        cylinderInto(0.014f, 0.30f, 6, baseY = 0.085f) // mast
                    }
                },
                ColorRole.TRUNK,
            ),
            Part(up(Primitives.boxAt(0f, 0.02f, 0.10f, 0.16f, 0.008f, baseY = 0.20f)), ColorRole.FACTION), // sail
            Part(
                build {
                    with(Primitives) {
                        boxInto(0f, -0.12f, 0.05f, 0.05f, 0.05f, baseY = 0.085f)
                        boxInto(0.04f, 0.13f, 0.04f, 0.04f, 0.04f, baseY = 0.085f)
                    }
                },
                ColorRole.STONE, // crates
            ),
            Part(up(Primitives.boxAt(0f, 0f, 0.125f, 0.012f, 0.24f, baseY = 0.073f)), ColorRole.PIP), // trim
        )
        // Warship: sleeker hull with a wedge ram, taller mast + crow's nest,
        // round faction shields along the gunwale, gold pennant. H ~0.52.
        PieceKind.WARSHIP -> listOf(
            Part(
                build {
                    with(Primitives) {
                        boxInto(0f, 0f, 0.09f, 0.05f, 0.24f) // hull slab
                        boxInto(0f, 0f, 0.105f, 0.035f, 0.26f, baseY = 0.05f) // gunwale flare
                        boxInto(0f, 0.26f, 0.03f, 0.10f, 0.03f) // stern post
                        cylinderInto(0.014f, 0.40f, 6, baseY = 0.085f) // mast
                        boxInto(0f, 0f, 0.035f, 0.02f, 0.035f, baseY = 0.40f) // crow's nest
                    }
                },
                ColorRole.TRUNK,
            ),
            Part(up(Primitives.wedgeAt(0f, -0.27f, 0.06f, 0.06f, 0.09f)), ColorRole.PIP), // ram
            Part(
                build {
                    with(Primitives) {
                        // Shield row along both gunwales.
                        for (sx in intArrayOf(-1, 1)) {
                            for (cz in floatArrayOf(-0.12f, 0f, 0.12f)) {
                                boxInto(sx * 0.105f, cz, 0.012f, 0.05f, 0.05f, baseY = 0.055f)
                            }
                        }
                    }
                },
                ColorRole.FACTION,
            ),
            Part(up(Primitives.boxAt(0f, 0.02f, 0.115f, 0.20f, 0.008f, baseY = 0.16f)), ColorRole.FACTION), // sail
            Part(up(Primitives.pennant(attachX = 0.014f, topY = 0.52f, drop = 0.045f, length = 0.09f)), ColorRole.GOLD),
        )

        // Port: stone quay + timber crane + faction-roof warehouse + gold barrel.
        PieceKind.PORT -> listOf(
            Part(
                build {
                    with(Primitives) {
                        boxInto(0f, 0.01f, 0.17f, 0.05f, 0.12f)
                        boxInto(0f, -0.07f, 0.10f, 0.03f, 0.025f)
                    }
                },
                ColorRole.STONE,
            ),
            Part(
                build {
                    with(Primitives) {
                        boxInto(0.06f, 0.045f, 0.075f, 0.11f, 0.065f, baseY = 0.05f) // warehouse
                        cylinderInto(0.018f, 0.30f, 6, baseY = 0.05f, cx = -0.08f, cz = 0.01f) // crane post
                        boxInto(-0.08f, -0.045f, 0.018f, 0.16f, 0.018f, baseY = 0.32f) // jib
                    }
                },
                ColorRole.TRUNK,
            ),
            Part(up(Primitives.wedgeAt(0.06f, 0.045f, 0.085f, 0.055f, 0.075f, baseY = 0.16f)), ColorRole.FACTION),
            Part(up(Primitives.cylinder(0.03f, 0.05f, 8, baseY = 0.05f, cx = 0.01f, cz = -0.03f)), ColorRole.GOLD),
            Part(up(Primitives.boxAt(-0.08f, -0.11f, 0.006f, 0.12f, 0.006f, baseY = 0.20f)), ColorRole.PIP),
        )

        // Fishery: stilt hut + faction roof + net rack + gold catch.
        PieceKind.FISHERY -> listOf(
            Part(
                build {
                    with(Primitives) {
                        boxInto(0f, -0.02f, 0.14f, 0.025f, 0.11f, baseY = 0.07f) // platform
                        boxInto(0.03f, 0.03f, 0.08f, 0.10f, 0.065f, baseY = 0.095f) // hut
                        boxInto(-0.11f, -0.09f, 0.012f, 0.15f, 0.012f, baseY = 0.095f) // rack post
                        boxInto(0.11f, -0.09f, 0.012f, 0.15f, 0.012f, baseY = 0.095f)
                        boxInto(0f, -0.09f, 0.12f, 0.012f, 0.012f, baseY = 0.235f) // rack bar
                    }
                },
                ColorRole.TRUNK,
            ),
            Part(up(Primitives.wedgeAt(0.03f, 0.03f, 0.095f, 0.075f, 0.07f, baseY = 0.195f)), ColorRole.FACTION),
            Part(up(Primitives.boxAt(0f, -0.09f, 0.11f, 0.045f, 0.003f, baseY = 0.14f)), ColorRole.PIP),
            Part(
                build {
                    with(Primitives) {
                        cylinderInto(0.018f, 0.03f, 6, baseY = 0.185f, cx = -0.05f, cz = -0.095f)
                        cylinderInto(0.018f, 0.03f, 6, baseY = 0.175f, cx = 0.04f, cz = -0.095f)
                    }
                },
                ColorRole.GOLD,
            ),
        )

        // Bridge: timber deck on stone pylons + railings + faction pennant.
        // Authored along Z; runtime yaw points it at the connected shores.
        PieceKind.BRIDGE -> listOf(
            Part(
                build {
                    with(Primitives) {
                        boxInto(0f, 0f, 0.08f, 0.035f, 0.42f, baseY = 0.085f) // deck
                        boxInto(0f, 0f, 0.09f, 0.045f, 0.15f, baseY = 0.08f) // camber
                        cylinderInto(0.008f, 0.14f, 6, baseY = 0.12f) // pennant mast
                    }
                },
                ColorRole.TRUNK,
            ),
            Part(
                build {
                    with(Primitives) {
                        for (cz in floatArrayOf(-0.26f, 0.26f)) {
                            boxInto(-0.07f, cz, 0.035f, 0.085f, 0.035f)
                            boxInto(0.07f, cz, 0.035f, 0.085f, 0.035f)
                        }
                    }
                },
                ColorRole.STONE,
            ),
            Part(
                build {
                    with(Primitives) {
                        boxInto(-0.07f, 0f, 0.006f, 0.014f, 0.39f, baseY = 0.155f)
                        boxInto(0.07f, 0f, 0.006f, 0.014f, 0.39f, baseY = 0.155f)
                    }
                },
                ColorRole.PIP,
            ),
            Part(up(Primitives.pennant(attachX = 0.008f, topY = 0.26f, drop = 0.04f, length = 0.08f)), ColorRole.FACTION),
        )

        // Tree + gravestone.
        PieceKind.TREE -> listOf(
            Part(up(Primitives.cylinder(0.05f, 0.16f, 7)), ColorRole.TRUNK),
            Part(up(Primitives.cone(0.2f, 0.26f, 8, baseY = 0.14f)), ColorRole.TREE_FOLIAGE),
            Part(up(Primitives.cone(0.14f, 0.2f, 8, baseY = 0.32f)), ColorRole.TREE_FOLIAGE),
        )
        PieceKind.GRAVESTONE -> listOf(
            Part(up(Primitives.boxAt(0f, 0f, 0.11f, 0.24f, 0.05f)), ColorRole.STONE),
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
        // Fish shoal: leaping fins + ripple rings at the hex edge (sea deposit).
        PieceKind.FISH_SHOAL -> listOf(
            Part(
                build {
                    with(Primitives) {
                        cylinderInto(0.055f, 0.008f, 10, cx = 0.22f, cz = 0.10f)
                        cylinderInto(0.045f, 0.008f, 10, cx = 0.06f, cz = -0.25f)
                        cylinderInto(0.05f, 0.008f, 10, cx = -0.21f, cz = 0.13f)
                    }
                },
                ColorRole.STONE,
            ),
            Part(
                build {
                    with(Primitives) {
                        boxInto(0.22f, 0.10f, 0.006f, 0.05f, 0.025f)
                        boxInto(0.06f, -0.25f, 0.006f, 0.04f, 0.02f)
                        boxInto(-0.21f, 0.13f, 0.006f, 0.045f, 0.022f)
                    }
                },
                ColorRole.PIP,
            ),
            Part(up(Primitives.cylinder(0.02f, 0.03f, 6, cx = -0.04f, cz = 0.24f)), ColorRole.GOLD),
        )
    }
}
