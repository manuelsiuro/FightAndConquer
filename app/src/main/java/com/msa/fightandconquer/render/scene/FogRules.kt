package com.msa.fightandconquer.render.scene

import com.msa.fightandconquer.core.hex.Hex

/**
 * Pure fog-presentation decisions, kept off [BoardScene] so they are testable
 * without Filament. `visible == null` means fog is off (nothing hides).
 *
 * Events are never filtered (that would break the reconcile gate) — these rules
 * only decide what the animation layer *renders* while the logical piece state
 * marches on regardless.
 */
object FogRules {

    fun hexHidden(visible: Set<Hex>?, hex: Hex): Boolean =
        visible != null && hex !in visible

    /**
     * A piece animating a segment renders only while BOTH ends are inside the
     * viewer's vision — otherwise an enemy unit would visibly march through the
     * fog for the whole animation (it only vanished at the next reconcile).
     * The single rim segment where a unit steps out of / into vision still
     * animates, which is the accepted "revealed a beat early" nuance.
     */
    fun segmentHidden(visible: Set<Hex>?, from: Hex, to: Hex): Boolean =
        visible != null && (from !in visible || to !in visible)

    /**
     * Defense rings must come from sources the viewer can see: an enemy archer
     * or tower standing inside the fog would otherwise paint (and move!) a ring
     * on a visible rim hex — live tracking of hidden movement.
     */
    fun auraSourceHidden(visible: Set<Hex>?, source: Hex): Boolean =
        hexHidden(visible, source)
}
