package com.msa.fightandconquer.render.material

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.Material
import java.nio.ByteBuffer

/** Loads precompiled .filamat assets (see tools/compile-materials.sh) and caches them. */
class MaterialStore(private val context: Context, private val engine: Engine) {

    private val cache = HashMap<String, Material>()

    fun material(name: String): Material = cache.getOrPut(name) {
        val bytes = context.assets.open("materials/$name.filamat").use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes)
        buffer.flip()
        Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }

    fun destroy() {
        cache.values.forEach { engine.destroyMaterial(it) }
        cache.clear()
    }
}
