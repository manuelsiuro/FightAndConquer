package com.msa.fightandconquer.render

import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.Fence
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

        /** ~20 fps ambience while the scene is still (water shimmer, boat bob). */
        private const val IDLE_FRAME_INTERVAL_NANOS = 50_000_000L
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

    /**
     * Idle throttle: while this returns false the loop renders at the ambience
     * rate ([IDLE_FRAME_INTERVAL_NANOS]) instead of every vsync. A turn-based
     * board spends most of its life perfectly still — rendering it at the
     * display rate is what cooks the phone. Null (no scene attached) = full rate.
     */
    var isSceneBusy: (() -> Boolean)? = null
    private var lastFrameNanos = 0L

    /**
     * A SurfaceView keeps its hole in the window shut (the Compose background shows
     * through) until every SurfaceHolder.Callback2 reports its first draw finished.
     * UiHelper registers a plain Callback, which counts as finished at once, so the hole
     * opened onto the surface's black before Filament had presented anything: a black
     * blink every time a host appeared. This callback holds the answer until a fence
     * behind a rendered frame signals, i.e. the driver thread is past that frame's swap.
     */
    private var pendingDrawFinished: Runnable? = null
    private var drawFinishedFence: Fence? = null

    private val drawFinishedCallback = object : SurfaceHolder.Callback2 {
        override fun surfaceCreated(holder: SurfaceHolder) = Unit
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
        override fun surfaceDestroyed(holder: SurfaceHolder) = releaseDrawFinished()
        override fun surfaceRedrawNeeded(holder: SurfaceHolder) = Unit
        override fun surfaceRedrawNeededAsync(holder: SurfaceHolder, drawingFinished: Runnable) {
            // Each request expects exactly one answer: a newer one releases the older, and
            // the platform is never kept waiting on a loop that is not running.
            releaseDrawFinished()
            if (running) pendingDrawFinished = drawingFinished else drawingFinished.run()
        }
    }

    private var framesLogged = 0
    private var fpsWindowStart = 0L
    private var fpsWindowFrames = 0
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            choreographer.postFrameCallback(this)
            // Polled every vsync, ahead of the idle throttle, so the answer isn't held
            // back up to a full ambience interval.
            pollDrawFinished()
            // Ambience pacing: a still board skips vsyncs (water/bob advance by
            // the accumulated dt on the frames that do draw, so motion stays
            // smooth-slow rather than fast-choppy).
            if (isSceneBusy?.invoke() == false &&
                frameTimeNanos - lastFrameNanos < IDLE_FRAME_INTERVAL_NANOS
            ) {
                return
            }
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
                if (pendingDrawFinished != null && drawFinishedFence == null) {
                    drawFinishedFence = engine.createFence()
                }
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
            // LOW halves the AO taps; on flat pastel tiles the difference is
            // invisible while the full-screen pass runs every drawn frame.
            quality = View.QualityLevel.LOW
        }
        setClearColor(Palette.BACKGROUND)
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
                    // Island maps spread far apart; the portrait fit can push the
                    // camera past 300 units out (Filament's reversed-Z keeps depth
                    // precision fine at this range).
                    800.0,
                    Camera.Fov.VERTICAL,
                )
            }
        }
        uiHelper.attachTo(surfaceView)
        surfaceView.holder.addCallback(drawFinishedCallback)
    }

    /** Answers the pending redraw once the fenced frame is through the driver (or failed). */
    private fun pollDrawFinished() {
        val fence = drawFinishedFence ?: return
        if (fence.wait(Fence.Mode.FLUSH, 0) != Fence.FenceStatus.TIMEOUT_EXPIRED) {
            releaseDrawFinished()
        }
    }

    private fun releaseDrawFinished() {
        drawFinishedFence?.let { engine.destroyFence(it) }
        drawFinishedFence = null
        pendingDrawFinished?.run()
        pendingDrawFinished = null
    }

    /** The tabletop "sky" behind the board — the day-night tween shifts it. */
    fun setClearColor(color: dev.romainguy.kotlin.math.Float3) {
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(color.x.toDouble(), color.y.toDouble(), color.z.toDouble(), 1.0)
        }
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
        // No frame will come to answer it, so answer now rather than stall the window.
        releaseDrawFinished()
    }

    /** Destroys everything this class created. Scene content must be destroyed by its owner first. */
    fun destroy() {
        pause()
        surfaceView.holder.removeCallback(drawFinishedCallback)
        uiHelper.detach()
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        engine.entityManager.destroy(cameraEntity)
        engine.destroy()
    }
}
