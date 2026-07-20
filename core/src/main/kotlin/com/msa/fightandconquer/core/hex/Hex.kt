package com.msa.fightandconquer.core.hex

import kotlinx.serialization.Serializable

/**
 * Axial hex coordinate packed into a single Int: q in the high 16 bits, r in the low 16 bits
 * (both signed, range -32768..32767). Cheap to hash, compare and serialize; suitable as a map key.
 *
 * Orientation (pointy-top vs flat-top) is a rendering concern — the core only relies on topology.
 */
@JvmInline
@Serializable
value class Hex(val packed: Int) {
    val q: Int get() = packed shr 16
    val r: Int get() = (packed shl 16) shr 16

    /** Third cube coordinate, s = -q - r. */
    val s: Int get() = -q - r

    override fun toString(): String = "Hex($q, $r)"

    companion object {
        fun of(q: Int, r: Int): Hex {
            require(q in -32768..32767 && r in -32768..32767) { "coordinate out of range: ($q, $r)" }
            return Hex((q shl 16) or (r and 0xFFFF))
        }
    }
}
