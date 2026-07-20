package com.msa.fightandconquer.render

import android.content.Context
import com.google.android.filament.EntityManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.msa.fightandconquer.render.material.MaterialStore
import com.msa.fightandconquer.render.material.Palette
import com.msa.fightandconquer.render.mesh.GpuMesh
import com.msa.fightandconquer.render.mesh.Primitives
import com.msa.fightandconquer.render.mesh.upload

/**
 * P6 verification scene: one sage-tinted beveled hex prism slowly rotating over a
 * neutral ground plane — proves materials, lighting, shadows, SSAO, bevel highlights,
 * and the render loop end-to-end. Replaced by the real board scene in P7.
 */
class BringUpScene(engine: RenderEngine, context: Context) : SceneController {

    private val filament = engine.engine
    private val materials = MaterialStore(context, filament)
    private val environment = SceneEnvironment(filament, engine.scene)

    private val hexMesh: GpuMesh = Primitives.hexPrism().upload(filament)
    private val groundMesh: GpuMesh = Primitives.groundPlane(6f).upload(filament)
    private val hexInstance: MaterialInstance
    private val groundInstance: MaterialInstance
    private val hexEntity = EntityManager.get().create()
    private val groundEntity = EntityManager.get().create()

    private var angle = 0f

    init {
        val piece = materials.material("piece")
        hexInstance = piece.createInstance().apply {
            val c = Palette.faction(0)
            setParameter("baseColor", c.x, c.y, c.z)
            setParameter("roughness", 0.85f)
        }
        groundInstance = piece.createInstance().apply {
            val c = Palette.NEUTRAL
            setParameter("baseColor", c.x, c.y, c.z)
            setParameter("roughness", 0.9f)
        }

        RenderableManager.Builder(1)
            .boundingBox(hexMesh.aabb)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, hexMesh.vertexBuffer, hexMesh.indexBuffer)
            .material(0, hexInstance)
            .castShadows(true)
            .receiveShadows(true)
            .build(filament, hexEntity)

        RenderableManager.Builder(1)
            .boundingBox(groundMesh.aabb)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, groundMesh.vertexBuffer, groundMesh.indexBuffer)
            .material(0, groundInstance)
            .castShadows(false)
            .receiveShadows(true)
            .build(filament, groundEntity)

        engine.scene.addEntity(hexEntity)
        engine.scene.addEntity(groundEntity)

        engine.camera.lookAt(
            2.6, 2.4, 2.6,
            0.0, 0.1, 0.0,
            0.0, 1.0, 0.0,
        )
    }

    override fun onFrame(frameTimeNanos: Long, deltaSeconds: Float) {
        angle += deltaSeconds * 0.6f
        val tm = filament.transformManager
        val instance = tm.getInstance(hexEntity)
        if (instance != 0) {
            tm.setTransform(instance, Transforms.trs(0f, 0.3f, 0f, angleYRadians = angle))
        }
    }

    override fun destroy() {
        filament.destroyEntity(hexEntity)
        filament.destroyEntity(groundEntity)
        EntityManager.get().destroy(hexEntity)
        EntityManager.get().destroy(groundEntity)
        filament.destroyMaterialInstance(hexInstance)
        filament.destroyMaterialInstance(groundInstance)
        hexMesh.destroy(filament)
        groundMesh.destroy(filament)
        environment.destroy()
        materials.destroy()
    }
}
