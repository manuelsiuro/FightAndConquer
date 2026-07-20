package com.msa.fightandconquer.render.anim

/**
 * Minimal frame-driven tween runner, ticked from the Choreographer callback.
 * Jobs mutate entity transforms / material parameters through their onUpdate closure.
 */
class Animator {

    private class Job(
        val duration: Float,
        val easing: (Float) -> Float,
        val onUpdate: (Float) -> Unit,
        val onEnd: (() -> Unit)?,
    ) {
        var elapsed = 0f
    }

    private val jobs = ArrayList<Job>()
    private val incoming = ArrayList<Job>()

    val isIdle: Boolean get() = jobs.isEmpty() && incoming.isEmpty()

    fun tween(
        duration: Float,
        easing: (Float) -> Float = Easings::easeOutCubic,
        onEnd: (() -> Unit)? = null,
        onUpdate: (Float) -> Unit,
    ) {
        incoming.add(Job(duration, easing, onUpdate, onEnd))
    }

    fun update(dt: Float) {
        if (incoming.isNotEmpty()) {
            jobs.addAll(incoming)
            incoming.clear()
        }
        if (jobs.isEmpty()) return
        val iterator = jobs.iterator()
        val finished = ArrayList<Job>()
        while (iterator.hasNext()) {
            val job = iterator.next()
            job.elapsed += dt
            if (job.elapsed >= job.duration) {
                job.onUpdate(job.easing(1f))
                iterator.remove()
                finished.add(job)
            } else {
                job.onUpdate(job.easing(job.elapsed / job.duration))
            }
        }
        // onEnd may enqueue follow-up jobs; run after iteration to avoid concurrent modification.
        finished.forEach { it.onEnd?.invoke() }
    }

    /** Fast-forward: complete every pending job immediately (skip-animation input). */
    fun finishAll() {
        var guard = 0
        while ((jobs.isNotEmpty() || incoming.isNotEmpty()) && guard++ < 64) {
            jobs.addAll(incoming)
            incoming.clear()
            val snapshot = ArrayList(jobs)
            jobs.clear()
            for (job in snapshot) {
                job.onUpdate(job.easing(1f))
                job.onEnd?.invoke()
            }
        }
    }
}
