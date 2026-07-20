package com.msa.fightandconquer.render.mesh

import android.content.Context
import com.google.android.filament.Engine
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.cross
import dev.romainguy.kotlin.math.length
import dev.romainguy.kotlin.math.normalize
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loads Blender-authored piece models baked to the .pmesh format by tools/glb2pmesh.py.
 *
 * The format is deliberately dumb — role-tagged triangle soups — so the geometry flows
 * through the exact same [MeshBuilder] path as the procedural meshes (flat shading,
 * tangent quats, self-correcting winding), keeping tinting/dimming/animation unchanged.
 */
object PieceMeshLoader {

    private val ROLES = arrayOf(
        ColorRole.FACTION, ColorRole.GOLD, ColorRole.TREE_FOLIAGE,
        ColorRole.TRUNK, ColorRole.STONE, ColorRole.PIP,
    )
    private const val MAGIC = 0x504D5348 // "PMSH" big-endian read

    /** Pure parser (unit-testable): returns (role, mesh) per part, or throws on malformed data. */
    fun parse(bytes: ByteArray): List<Pair<ColorRole, MeshData>> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = (buffer.get().toInt() and 0xFF shl 24) or
            (buffer.get().toInt() and 0xFF shl 16) or
            (buffer.get().toInt() and 0xFF shl 8) or
            (buffer.get().toInt() and 0xFF)
        require(magic == MAGIC) { "not a .pmesh file" }
        val version = buffer.get().toInt()
        require(version == 1) { "unsupported .pmesh version $version" }
        val partCount = buffer.get().toInt() and 0xFF
        require(partCount in 1..16) { "implausible part count $partCount" }

        val parts = ArrayList<Pair<ColorRole, MeshData>>(partCount)
        repeat(partCount) {
            val roleId = buffer.get().toInt() and 0xFF
            require(roleId < ROLES.size) { "unknown role id $roleId" }
            val triCount = buffer.short.toInt() and 0xFFFF
            require(triCount in 1..2048) { "implausible tri count $triCount" }
            val builder = MeshBuilder()
            repeat(triCount) {
                val a = Float3(buffer.float, buffer.float, buffer.float)
                val b = Float3(buffer.float, buffer.float, buffer.float)
                val c = Float3(buffer.float, buffer.float, buffer.float)
                val n = cross(b - a, c - a)
                if (length(n) > 1e-9f) {
                    // The exporter's winding is authoritative; its geometric normal
                    // doubles as expectedDir so MeshBuilder keeps it verbatim.
                    builder.addTriangle(a, b, c, normalize(n))
                }
            }
            parts.add(ROLES[roleId] to builder.build())
        }
        require(!buffer.hasRemaining()) { "trailing bytes in .pmesh" }
        return parts
    }

    /** Loads pieces/<name>.pmesh from assets and uploads it; null if the asset is absent. */
    fun load(context: Context, engine: Engine, name: String): List<Part>? {
        val bytes = try {
            context.assets.open("pieces/$name.pmesh").use { it.readBytes() }
        } catch (_: java.io.IOException) {
            return null
        }
        return parse(bytes).map { (role, mesh) -> Part(mesh.upload(engine), role) }
    }
}
