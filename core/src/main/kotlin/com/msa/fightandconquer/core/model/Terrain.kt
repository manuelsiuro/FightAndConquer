package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

/**
 * Tile terrain. [LAND] is the default (pre-naval saves carry no terrain key).
 *
 * [SEA] hexes are part of the map but not of any territory: they are never owned,
 * never produce income, and never carry flora or gravestones. Land units cannot
 * enter or capture them. The one exception is a sea hex holding a
 * [Building.BRIDGE], which is owned by the bridge's builder and walkable —
 * region flood-fills cross it like land.
 */
@Serializable
enum class Terrain { LAND, SEA }
