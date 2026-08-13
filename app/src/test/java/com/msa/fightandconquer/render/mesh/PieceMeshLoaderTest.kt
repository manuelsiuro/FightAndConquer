package com.msa.fightandconquer.render.mesh

import com.msa.fightandconquer.core.model.Civilization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PieceMeshLoaderTest {

    private fun pmesh(vararg parts: Pair<Int, List<FloatArray>>): ByteArray {
        val size = 6 + parts.sumOf { 3 + it.second.size * 36 }
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put('P'.code.toByte()).put('M'.code.toByte()).put('S'.code.toByte()).put('H'.code.toByte())
        buffer.put(1) // version
        buffer.put(parts.size.toByte())
        for ((roleId, tris) in parts) {
            buffer.put(roleId.toByte())
            buffer.putShort(tris.size.toShort())
            for (tri in tris) tri.forEach { buffer.putFloat(it) }
        }
        return buffer.array()
    }

    private val triangle = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)

    @Test
    fun `parses parts with roles and flat-shaded triangles`() {
        val bytes = pmesh(
            0 to listOf(triangle, triangle), // FACTION, 2 tris
            5 to listOf(triangle), // PIP, 1 tri
        )
        val parts = PieceMeshLoader.parse(bytes)
        assertEquals(2, parts.size)
        assertEquals(ColorRole.FACTION, parts[0].first)
        assertEquals(ColorRole.PIP, parts[1].first)
        // 3 verts per tri, duplicated flat-shaded
        assertEquals(6, parts[0].second.vertexCount)
        assertEquals(3, parts[1].second.vertexCount)
        assertEquals(6, parts[0].second.indices.size)
        // tangent quats present: 4 floats per vertex
        assertEquals(6 * 4, parts[0].second.tangents.size)
    }

    @Test
    fun `degenerate triangles are dropped`() {
        val degenerate = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
        val bytes = pmesh(0 to listOf(triangle, degenerate))
        val parts = PieceMeshLoader.parse(bytes)
        assertEquals(3, parts[0].second.vertexCount)
    }

    @Test
    fun `rejects malformed input`() {
        assertThrows(IllegalArgumentException::class.java) {
            PieceMeshLoader.parse(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        }
        // unknown role id
        val badRole = pmesh(9 to listOf(triangle))
        assertThrows(IllegalArgumentException::class.java) { PieceMeshLoader.parse(badRole) }
        // truncated payload
        val truncated = pmesh(0 to listOf(triangle)).copyOfRange(0, 20)
        assertThrows(Exception::class.java) { PieceMeshLoader.parse(truncated) }
    }

    /**
     * Baked-asset regression gate: every checked-in .pmesh must parse and respect the
     * converter budgets (tools/glb2pmesh.py), and every [PieceKind] that has shipped a
     * model must keep shipping it — a renamed enum value or missing bake fails here.
     *
     * Layout contract: flat files are the KINGDOM set and must map to PieceKind names;
     * one level of `pieces/<civ>/` subdirectories carries the other civs' art —
     * directory names must be non-Kingdom [Civilization]s (lowercase) and base names
     * must be civ-forked (player-owned) kinds; neutral markers never fork.
     */
    @Test
    fun `checked-in piece assets parse and respect the budgets`() {
        val dir = java.io.File("src/main/assets/pieces")
        val flat = dir.listFiles { f -> f.isFile && f.name.endsWith(".pmesh") }.orEmpty()
        org.junit.Assert.assertTrue("no baked assets found at ${dir.absolutePath}", flat.isNotEmpty())
        val kindNames = PieceKind.entries.map { it.name.lowercase() }.toSet()
        val civDirNames = Civilization.entries
            .filter { it != Civilization.KINGDOM }
            .map { it.name.lowercase() }
            .toSet()
        val forkedNames = PieceMeshes.CIV_FORKED_KINDS.map { it.name.lowercase() }.toSet()

        val baked = ArrayList<Pair<String, java.io.File>>() // display name -> file
        for (file in flat) {
            val name = file.name.removeSuffix(".pmesh")
            org.junit.Assert.assertTrue("$name.pmesh has no matching PieceKind", name in kindNames)
            baked.add(name to file)
        }
        for (sub in dir.listFiles { f -> f.isDirectory }.orEmpty()) {
            org.junit.Assert.assertTrue(
                "pieces/${sub.name}/ is not a civilization directory (Kingdom stays flat)",
                sub.name in civDirNames,
            )
            for (file in sub.listFiles { f -> f.name.endsWith(".pmesh") }.orEmpty()) {
                val name = file.name.removeSuffix(".pmesh")
                org.junit.Assert.assertTrue(
                    "${sub.name}/$name.pmesh is not a civ-forked (player-owned) kind",
                    name in forkedNames,
                )
                baked.add("${sub.name}/$name" to file)
            }
        }

        for ((name, file) in baked) {
            val parts = PieceMeshLoader.parse(file.readBytes())
            org.junit.Assert.assertTrue("$name: no parts", parts.isNotEmpty())
            var tris = 0
            var maxAbsXZ = 0f
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for ((_, mesh) in parts) {
                tris += mesh.indices.size / 3
                var i = 0
                while (i < mesh.positions.size) {
                    maxAbsXZ = maxOf(maxAbsXZ, kotlin.math.abs(mesh.positions[i]), kotlin.math.abs(mesh.positions[i + 2]))
                    minY = minOf(minY, mesh.positions[i + 1])
                    maxY = maxOf(maxY, mesh.positions[i + 1])
                    i += 3
                }
            }
            org.junit.Assert.assertTrue("$name: $tris tris > 600", tris <= 600)
            org.junit.Assert.assertTrue("$name: radius $maxAbsXZ > 0.45", maxAbsXZ <= 0.45f)
            org.junit.Assert.assertTrue("$name: height $maxY > 0.75", maxY <= 0.75f)
            org.junit.Assert.assertTrue("$name: minY $minY < -0.01", minY >= -0.01f)
        }
        // Every original kind stays shipped (Kingdom = the flat set); expansion kinds
        // join this list as their Blender models land.
        val shipped = flat.map { it.name.removeSuffix(".pmesh") }.toSet()
        for (kind in listOf(
            "unit_t1", "unit_t2", "unit_t3", "unit_t4",
            "capital", "farm", "tower", "strong_tower", "tree", "gravestone",
        )) {
            org.junit.Assert.assertTrue("missing baked asset for $kind", kind in shipped)
        }
    }

    // ----- (civilization, kind) resolution: the exact production fallback/ownership
    // logic ([CivArtTable] behind PieceMeshes), exercised on the JVM without Filament
    // by substituting the part payload -----

    private fun table(
        civAsset: (Civilization, PieceKind) -> String? = { _, _ -> null },
        flatAsset: (PieceKind) -> String? = { kind -> "kingdom/${kind.name.lowercase()}" },
    ) = CivArtTable(civAsset, flatAsset) { kind -> "procedural/${kind.name.lowercase()}" }

    @Test
    fun `a civ with no baked assets resolves to the shared Kingdom parts instance`() {
        val flatLoads = ArrayList<PieceKind>()
        val table = table(flatAsset = { kind ->
            flatLoads.add(kind)
            "kingdom/${kind.name.lowercase()}"
        })
        val kingdom = table.get(Civilization.KINGDOM, PieceKind.UNIT_T1)
        // Same instance, not a copy: loaded once, destroyed once.
        assertSame(kingdom, table.get(Civilization.VIKINGS, PieceKind.UNIT_T1))
        assertEquals(Civilization.KINGDOM, table.artCiv(Civilization.VIKINGS, PieceKind.UNIT_T1))
        // The falling-back civ did not re-load the Kingdom set.
        assertEquals(PieceKind.entries.size, flatLoads.size)
    }

    @Test
    fun `a civ's own asset wins and neutral kinds never fork`() {
        val civLoads = ArrayList<PieceKind>()
        val table = table(civAsset = { civ, kind ->
            civLoads.add(kind)
            if (civ == Civilization.VIKINGS && kind == PieceKind.CAPITAL) "vikings/capital" else null
        })
        assertEquals("vikings/capital", table.get(Civilization.VIKINGS, PieceKind.CAPITAL))
        assertEquals(Civilization.VIKINGS, table.artCiv(Civilization.VIKINGS, PieceKind.CAPITAL))
        // Neutral markers never even consult the civ loader and stay the Kingdom instance.
        assertTrue(civLoads.none { it in PieceMeshes.NEUTRAL_KINDS })
        assertSame(
            table.get(Civilization.KINGDOM, PieceKind.TREE),
            table.get(Civilization.VIKINGS, PieceKind.TREE),
        )
    }

    @Test
    fun `kingdom falls back to procedural parts when even the flat asset is missing`() {
        val table = table(flatAsset = { null })
        assertEquals("procedural/unit_t2", table.get(Civilization.KINGDOM, PieceKind.UNIT_T2))
        // And a civ falling back lands on the same procedural instance.
        assertSame(
            table.get(Civilization.KINGDOM, PieceKind.UNIT_T2),
            table.get(Civilization.SHOGUNATE, PieceKind.UNIT_T2),
        )
    }

    @Test
    fun `release frees each loaded parts list exactly once across civs`() {
        val table = table(civAsset = { civ, kind ->
            if (civ == Civilization.VIKINGS && kind == PieceKind.CAPITAL) "vikings/capital" else null
        })
        table.preload(setOf(Civilization.KINGDOM, Civilization.VIKINGS, Civilization.SULTANATE))
        val released = ArrayList<String>()
        table.releaseOwned { released.add(it) }
        // Kingdom's full set + the single Vikings fork; fallback shares are never re-freed.
        assertEquals(PieceKind.entries.size + 1, released.size)
        assertEquals(released.size, released.toSet().size)
    }
}
