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
class SceneEnvironment(private val engine: Engine, private val scene: Scene) {

    private companion object {
        // Day rig (the shipped look).
        val DAY_SUN = Float3(1.0f, 0.98f, 0.95f)
        const val DAY_SUN_LUX = 100_000f
        val DAY_AMBIENT = floatArrayOf(1.0f, 0.99f, 0.97f)
        const val DAY_AMBIENT_LUX = 25_000f

        // Night rig: a dim moon-blue sun + a cool ambient (see docs/rendering.md
        // "Day-night look"). The moon keeps the sun's direction — shadows stay
        // coherent through the transition.
        val NIGHT_SUN = Float3(0.62f, 0.70f, 1.0f)
        const val NIGHT_SUN_LUX = 12_000f
        val NIGHT_AMBIENT = floatArrayOf(0.55f, 0.65f, 0.90f)
        const val NIGHT_AMBIENT_LUX = 8_000f
    }

    private val sunEntity = EntityManager.get().create()

    /**
     * Two prebuilt indirect lights: an IndirectLight's irradiance color is
     * immutable after build, so a night COLOR shift swaps the whole light at
     * the transition midpoint (invisible under the moving sun lerp) while
     * intensity lerps continuously on whichever is attached.
     */
    private val dayAmbient: IndirectLight
    private val nightAmbient: IndirectLight

    init {
        val direction = normalize(Float3(1f, -1f, 0.4f))
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(DAY_SUN.x, DAY_SUN.y, DAY_SUN.z)
            .intensity(DAY_SUN_LUX)
            .direction(direction.x, direction.y, direction.z)
            .castShadows(true)
            .shadowOptions(
                LightManager.ShadowOptions().apply {
                    // 1024 is indistinguishable at tabletop zoom for soft toy
                    // shadows and re-renders every frame at a quarter the cost.
                    mapSize = 1024
                    normalBias = 1.0f
                    shadowFar = 60f
                },
            )
            .build(engine, sunEntity)
        scene.addEntity(sunEntity)

        // Flat neutral ambient: one SH band (constant term), slightly warm.
        dayAmbient = IndirectLight.Builder()
            .irradiance(1, DAY_AMBIENT)
            .intensity(DAY_AMBIENT_LUX)
            .build(engine)
        nightAmbient = IndirectLight.Builder()
            .irradiance(1, NIGHT_AMBIENT)
            .intensity(DAY_AMBIENT_LUX)
            .build(engine)
        scene.indirectLight = dayAmbient
    }

    /**
     * Sets the rig to [factor] between day (0) and night (1). Cheap enough to
     * call per-frame during the one-shot transition tween; a static factor
     * afterwards costs nothing (no per-frame work unless called).
     */
    fun setNight(factor: Float) {
        val f = factor.coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float) = a + (b - a) * f
        val sun = engine.lightManager.getInstance(sunEntity)
        engine.lightManager.setColor(
            sun,
            lerp(DAY_SUN.x, NIGHT_SUN.x),
            lerp(DAY_SUN.y, NIGHT_SUN.y),
            lerp(DAY_SUN.z, NIGHT_SUN.z),
        )
        engine.lightManager.setIntensity(sun, lerp(DAY_SUN_LUX, NIGHT_SUN_LUX))
        val ambient = if (f >= 0.5f) nightAmbient else dayAmbient
        ambient.intensity = lerp(DAY_AMBIENT_LUX, NIGHT_AMBIENT_LUX)
        if (scene.indirectLight !== ambient) scene.indirectLight = ambient
    }

    fun destroy() {
        engine.destroyIndirectLight(dayAmbient)
        engine.destroyIndirectLight(nightAmbient)
        engine.lightManager.destroy(sunEntity)
        EntityManager.get().destroy(sunEntity)
    }
}
