package com.msa.fightandconquer.render.material

import dev.romainguy.kotlin.math.Float3
import kotlin.math.pow

/**
 * Neo-pastel palette from docs/game-idea.md section 7, converted to linear space
 * (Filament material parameters expect linear RGB).
 */
object Palette {
    val NEUTRAL = linear(0xEAE6E1)        // Pale Ash / Oatmeal
    val TREE = linear(0x3D5A4C)           // Deep Juniper Green
    val BACKGROUND = linear(0xF4F2EF)     // off-white tabletop
    val PIECE_NEUTRAL = linear(0xF7F4F0)  // unit/building body fallback
    val GOLD = linear(0xD9B36C)           // Baron cap accent
    val GRAVESTONE = linear(0xB8B2AA)
    val TRUNK = linear(0x8A6B4F)          // tree trunk
    val STONE = linear(0xCFC9C2)          // tower masonry / gravestones

    /** Faction colors by player index (doc defines 4; extended in the same spirit). */
    val FACTIONS = listOf(
        linear(0x8FA89B), // Soft Sage Green
        linear(0xDE9B8B), // Dusty Coral
        linear(0xE6C594), // Muted Ochre
        linear(0x8FA3B5), // Slate Blue
        linear(0xB59BAD), // Dusty Mauve
        linear(0xA8B58F), // Moss Olive
    )

    fun faction(index: Int): Float3 = FACTIONS[index % FACTIONS.size]

    /** sRGB 0xRRGGBB -> linear Float3 via the exact sRGB EOTF. */
    fun linear(srgb: Int): Float3 = Float3(
        channel((srgb shr 16) and 0xFF),
        channel((srgb shr 8) and 0xFF),
        channel(srgb and 0xFF),
    )

    private fun channel(value: Int): Float {
        val c = value / 255f
        return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
}
