package com.msa.fightandconquer.render.mesh

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.VertexBuffer
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.cross
import dev.romainguy.kotlin.math.dot
import dev.romainguy.kotlin.math.normalize
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** CPU-side mesh: flat-shaded positions + per-vertex tangent-frame quaternions. */
class MeshData(
    val positions: FloatArray,
    val tangents: FloatArray,
    val indices: ShortArray,
) {
    val vertexCount: Int get() = positions.size / 3

    fun boundingBox(): Box {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < positions.size) {
            minX = minOf(minX, positions[i]); maxX = maxOf(maxX, positions[i])
            minY = minOf(minY, positions[i + 1]); maxY = maxOf(maxY, positions[i + 1])
            minZ = minOf(minZ, positions[i + 2]); maxZ = maxOf(maxZ, positions[i + 2])
            i += 3
        }
        return Box(
            (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2,
            (maxX - minX) / 2 + 1e-4f, (maxY - minY) / 2 + 1e-4f, (maxZ - minZ) / 2 + 1e-4f,
        )
    }
}

/** GPU-side mesh shared by every renderable of one shape. */
class GpuMesh(
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val indexCount: Int,
    val aabb: Box,
) {
    fun destroy(engine: Engine) {
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
    }
}

fun MeshData.upload(engine: Engine): GpuMesh {
    val vb = VertexBuffer.Builder()
        .bufferCount(2)
        .vertexCount(vertexCount)
        .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
        .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4, 0, 16)
        .build(engine)
    vb.setBufferAt(engine, 0, floatBuffer(positions))
    vb.setBufferAt(engine, 1, floatBuffer(tangents))

    val ib = IndexBuffer.Builder()
        .indexCount(indices.size)
        .bufferType(IndexBuffer.Builder.IndexType.USHORT)
        .build(engine)
    val indexBytes = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder())
    indexBytes.asShortBuffer().put(indices)
    ib.setBuffer(engine, indexBytes)

    return GpuMesh(vb, ib, indices.size, boundingBox())
}

private fun floatBuffer(data: FloatArray): ByteBuffer {
    val buffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
    buffer.asFloatBuffer().put(data)
    return buffer
}

/**
 * Accumulates flat-shaded triangles. Winding is self-correcting: each face is wound so
 * its geometric normal agrees with the caller's [expectedDir], which guarantees both
 * correct lighting and correct front-face culling.
 */
class MeshBuilder {
    private val positions = ArrayList<Float>()
    private val tangents = ArrayList<Float>()
    private val indices = ArrayList<Short>()

    fun addTriangle(a: Float3, b: Float3, c: Float3, expectedDir: Float3) {
        var v1 = b
        var v2 = c
        var normal = normalize(cross(b - a, c - a))
        if (dot(normal, expectedDir) < 0f) {
            v1 = c
            v2 = b
            normal = -normal
        }
        val quat = TangentFrames.quatFromNormal(normal)
        val base = positions.size / 3
        for (v in arrayOf(a, v1, v2)) {
            positions.add(v.x); positions.add(v.y); positions.add(v.z)
            tangents.add(quat[0]); tangents.add(quat[1]); tangents.add(quat[2]); tangents.add(quat[3])
        }
        indices.add(base.toShort())
        indices.add((base + 1).toShort())
        indices.add((base + 2).toShort())
    }

    /** Quad with perimeter order a-b-c-d. */
    fun addQuad(a: Float3, b: Float3, c: Float3, d: Float3, expectedDir: Float3) {
        addTriangle(a, b, c, expectedDir)
        addTriangle(a, c, d, expectedDir)
    }

    fun build(): MeshData {
        check(positions.size / 3 <= 65535) { "mesh exceeds 16-bit index range" }
        return MeshData(positions.toFloatArray(), tangents.toFloatArray(), indices.toShortArray())
    }
}
