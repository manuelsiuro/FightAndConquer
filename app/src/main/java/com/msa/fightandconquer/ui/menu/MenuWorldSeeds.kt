package com.msa.fightandconquer.ui.menu

import com.msa.fightandconquer.core.engine.Rng

/**
 * Picks the seed of the next menu backdrop. Pure — the clock reading is the caller's
 * (the app layer may read a clock; `:core` may not).
 *
 * The raw millisecond is a poor seed: two menu entries a second apart would differ by
 * 1000 and `MapGenerator` would draw near-identical worlds. Mixing it through SplitMix64
 * makes neighbouring milliseconds unrelated. And because a fast Back tap can land on the
 * very same millisecond as the previous entry, a collision with [previous] is advanced
 * again — the menu promises a *new* world every time it is shown.
 */
object MenuWorldSeeds {

    /** The seed for a menu entry at [nowMillis], never equal to [previous]. */
    fun next(nowMillis: Long, previous: Long?): Long {
        var state = Rng.advance(nowMillis)
        var seed = Rng.output(state)
        while (seed == previous) {
            state = Rng.advance(state)
            seed = Rng.output(state)
        }
        return seed
    }
}
