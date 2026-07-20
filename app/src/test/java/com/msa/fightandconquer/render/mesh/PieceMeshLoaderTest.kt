package com.msa.fightandconquer.render.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
}
