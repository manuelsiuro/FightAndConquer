package com.msa.fightandconquer.render.material

import dev.romainguy.kotlin.math.Float3
import kotlin.math.pow

/**
 * Neo-pastel palette from docs/game-idea.md section 7, converted to linear space
 * (Filament material parameters expect linear RGB).
 */
object Palette {
    // Raw sRGB sources (0xRRGGBB) for the tones other layers reuse — UiColors
    // wraps the factions as Compose colors, the minimap paints the board tones
    // in ARGB. One definition; each consumer converts to its own space.
    const val NEUTRAL_SRGB = 0xEAE6E1     // Pale Ash / Oatmeal
    const val BACKGROUND_SRGB = 0xF4F2EF  // off-white tabletop
    const val SEA_SRGB = 0xA3C6CC         // pale lagoon (water shallow tone)

    /** Faction pastels by player index, sRGB: Soft Sage Green, Dusty Coral, Muted Ochre, Slate Blue, Dusty Mauve, Moss Olive. */
    val FACTION_SRGB = intArrayOf(0x8FA89B, 0xDE9B8B, 0xE6C594, 0x8FA3B5, 0xB59BAD, 0xA8B58F)

    val NEUTRAL = linear(NEUTRAL_SRGB)
    val TREE = linear(0x3D5A4C)           // Deep Juniper Green
    val BACKGROUND = linear(BACKGROUND_SRGB)
    val PIECE_NEUTRAL = linear(0xF7F4F0)  // unit/building body fallback
    val GOLD = linear(0xD9B36C)           // Baron cap accent
    val TRUNK = linear(0x8A6B4F)          // tree trunk
    val STONE = linear(0xCFC9C2)          // tower masonry / gravestones
    val INK = linear(0x4A453F)            // tier pips, castle gate, flag poles
    val SEA = linear(SEA_SRGB)
    val SEA_DEEP = linear(0x86A9B4)       // water shimmer deep tone

    /** Faction colors by player index (doc defines 4; extended in the same spirit). */
    val FACTIONS = FACTION_SRGB.map { linear(it) }

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
