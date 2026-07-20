package com.msa.fightandconquer.render

import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Scene
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.normalize

/**
 * The doc's lighting rig: one strong 45-degree sun with soft shadows + flat ambient.
 * No skybox/IBL asset — a single spherical-harmonics band provides neutral fill,
 * which matte materials can't tell apart from a real environment map.
 */
class SceneEnvironment(private val engine: Engine, scene: Scene) {

    private val sunEntity = EntityManager.get().create()
    private val indirectLight: IndirectLight

    init {
        val direction = normalize(Float3(1f, -1f, 0.4f))
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 0.98f, 0.95f)
            .intensity(100_000f)
            .direction(direction.x, direction.y, direction.z)
            .castShadows(true)
            .shadowOptions(
                LightManager.ShadowOptions().apply {
                    mapSize = 2048
                    normalBias = 1.0f
                    shadowFar = 60f
                },
            )
            .build(engine, sunEntity)
        scene.addEntity(sunEntity)

        // Flat neutral ambient: one SH band (constant term), slightly warm.
        indirectLight = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(1.0f, 0.99f, 0.97f))
            .intensity(25_000f)
            .build(engine)
        scene.indirectLight = indirectLight
    }

    fun destroy() {
        engine.destroyIndirectLight(indirectLight)
        engine.lightManager.destroy(sunEntity)
        EntityManager.get().destroy(sunEntity)
    }
}
