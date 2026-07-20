package com.msa.fightandconquer.core.engine

/**
 * SplitMix64 — implemented locally (NOT kotlin.random.Random) because its algorithm is
 * fixed forever: saved seeds and action-log replays must reproduce bit-for-bit across
 * Kotlin/platform versions.
 *
 * Usage: advance the state, then derive values from the new state:
 * `val s = Rng.advance(rngState); val roll = Rng.nextInt(s, 100)`
 */
object Rng {
    private const val GOLDEN: Long = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

    fun advance(state: Long): Long = state + GOLDEN

    /** The SplitMix64 output function for a given state. */
    fun output(state: Long): Long {
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    /** Uniform-ish value in [0, bound). bound must be > 0. */
    fun nextInt(state: Long, bound: Int): Int {
        require(bound > 0)
        return ((output(state) ushr 1) % bound).toInt()
    }
}
