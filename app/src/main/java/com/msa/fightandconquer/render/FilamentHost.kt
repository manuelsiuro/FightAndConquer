package com.msa.fightandconquer.render

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Scene content plugged into a [RenderEngine]'s frame loop. */
interface SceneController {
    fun onFrame(frameTimeNanos: Long, deltaSeconds: Float)
    fun destroy()
}

private class HostHolder {
    var engine: RenderEngine? = null
    var controller: SceneController? = null
}

/**
 * Hosts Filament in Compose: a SurfaceView underneath the Compose HUD.
 *
 * Ownership lives in a stable holder — NOT in Compose state. A state-keyed effect
 * would re-run when the factory publishes the engine and tear down the live engine
 * (observed as a permanently black surface). The lifecycle observer is keyed on the
 * lifecycle owner only; view teardown happens in AndroidView.onRelease.
 */
@Composable
fun FilamentHost(
    modifier: Modifier = Modifier,
    createScene: (RenderEngine) -> SceneController,
) {
    val holder = remember { HostHolder() }
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).also { surfaceView ->
                val engine = RenderEngine(surfaceView)
                val controller = createScene(engine)
                engine.onFrame = controller::onFrame
                holder.engine = engine
                holder.controller = controller
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    engine.resume()
                }
            }
        },
        onRelease = {
            holder.engine?.pause()
            holder.controller?.destroy()
            holder.engine?.destroy()
            holder.controller = null
            holder.engine = null
        },
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.engine?.resume()
                Lifecycle.Event.ON_PAUSE -> holder.engine?.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
