package com.msa.fightandconquer.render

import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.msa.fightandconquer.render.material.Palette

/**
 * Owns the Filament core objects and the Choreographer render loop.
 * Created once per game screen; must be destroyed in reverse creation order.
 *
 * Per-frame work (animation, camera) hooks in via [onFrame].
 */
class RenderEngine(private val surfaceView: SurfaceView) {

    companion object {
        private const val TAG = "FightRender"
        init {
            Filament.init()
        }
        /** Vertical FOV in degrees — narrow for the near-orthographic tabletop look. */
        const val FOV_DEGREES = 30.0
    }

    // OpenGL backend: reliable on emulators (Vulkan-on-emulator is flaky).
    val engine: Engine = Engine.Builder().backend(Engine.Backend.OPENGL).build()
    val renderer: Renderer = engine.createRenderer()
    val scene: Scene = engine.createScene()
    val view: View = engine.createView()
    private val cameraEntity = engine.entityManager.create()
    val camera: Camera = engine.createCamera(cameraEntity)

    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val displayHelper = DisplayHelper(surfaceView.context)
    private val choreographer = Choreographer.getInstance()
    private var swapChain: SwapChain? = null
    private var running = false

    /** Called every frame with (frameTimeNanos, deltaSeconds) before rendering. */
    var onFrame: ((Long, Float) -> Unit)? = null
    private var lastFrameNanos = 0L

    private var framesLogged = 0
    private var fpsWindowStart = 0L
    private var fpsWindowFrames = 0
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            choreographer.postFrameCallback(this)
            val dt = if (lastFrameNanos == 0L) 0f else (frameTimeNanos - lastFrameNanos) / 1e9f
            lastFrameNanos = frameTimeNanos
            // Rolling FPS probe (debug builds log every ~5s).
            if (fpsWindowStart == 0L) fpsWindowStart = frameTimeNanos
            fpsWindowFrames++
            if (frameTimeNanos - fpsWindowStart >= 5_000_000_000L) {
                val fps = fpsWindowFrames * 1e9 / (frameTimeNanos - fpsWindowStart)
                android.util.Log.d(TAG, "fps=%.1f".format(fps))
                fpsWindowStart = frameTimeNanos
                fpsWindowFrames = 0
            }
            onFrame?.invoke(frameTimeNanos, dt.coerceAtMost(0.1f))
            val sc = swapChain
            if (sc == null) {
                if (framesLogged < 3) { android.util.Log.d(TAG, "frame: no swapchain"); framesLogged++ }
                return
            }
            if (uiHelper.isReadyToRender && renderer.beginFrame(sc, frameTimeNanos)) {
                if (framesLogged < 3) { android.util.Log.d(TAG, "frame: rendering"); framesLogged++ }
                renderer.render(view)
                renderer.endFrame()
            } else if (framesLogged < 3) {
                android.util.Log.d(TAG, "frame: not ready (ready=${uiHelper.isReadyToRender})")
                framesLogged++
            }
        }
    }

    init {
        view.scene = scene
        view.camera = camera
        view.colorGrading = ColorGrading.Builder()
            .toneMapper(ToneMapper.Linear()) // ACES would crush the pastel palette
            .build(engine)
        view.multiSampleAntiAliasingOptions = View.MultiSampleAntiAliasingOptions().apply {
            enabled = true
            sampleCount = 4
        }
        view.ambientOcclusionOptions = View.AmbientOcclusionOptions().apply {
            enabled = true
            radius = 0.3f
            intensity = 1.0f
            power = 1.0f
            quality = View.QualityLevel.MEDIUM
        }
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(
                Palette.BACKGROUND.x.toDouble(),
                Palette.BACKGROUND.y.toDouble(),
                Palette.BACKGROUND.z.toDouble(),
                1.0,
            )
        }
        // Manual exposure tuned for ~100k lux sun + linear tone mapping.
        camera.setExposure(16f, 1f / 125f, 100f)

        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                android.util.Log.d(TAG, "onNativeWindowChanged")
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
                displayHelper.attach(renderer, surfaceView.display)
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                camera.setProjection(
                    FOV_DEGREES,
                    width.toDouble() / height.toDouble(),
                    0.1,
                    200.0,
                    Camera.Fov.VERTICAL,
                )
            }
        }
        uiHelper.attachTo(surfaceView)
    }

    fun resume() {
        android.util.Log.d(TAG, "resume (was running=$running)")
        if (!running) {
            running = true
            lastFrameNanos = 0L
            framesLogged = 0
            choreographer.postFrameCallback(frameCallback)
        }
    }

    fun pause() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    /** Destroys everything this class created. Scene content must be destroyed by its owner first. */
    fun destroy() {
        pause()
        uiHelper.detach()
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        engine.entityManager.destroy(cameraEntity)
        engine.destroy()
    }
}
