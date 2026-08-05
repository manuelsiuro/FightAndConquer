package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

/**
 * One hex of the map. Hexes absent from [GameState.tiles] are off-map void.
 *
 * [terrain] distinguishes ownable land from open sea (see [Terrain] for the sea
 * tile contract). Defaulted to LAND so pre-naval saves decode unchanged.
 *
 * [unit] mirrors [GameUnit.hex] — the reducer keeps both indexes consistent
 * (verified by invariant checks in tests).
 *
 * [starving]: owned but disconnected from the owner's capital — produces no income,
 * and any unit on it dies at the owner's next turn start.
 *
 * [graceTurns]: landing stores on a sea-captured beachhead. While > 0, the
 * starving region this tile belongs to skips starvation deaths at its owner's
 * turn start (burning one turn of stores per grace tile); income and purchase
 * rules are unaffected. Zeroed the moment the hex is fed normally.
 *
 * [deposit]: permanent terrain resource (see [Deposit]); survives capture.
 */
@Serializable
data class Tile(
    val owner: PlayerId? = null,
    val unit: UnitId? = null,
    val building: Building? = null,
    val flora: Flora? = null,
    val starving: Boolean = false,
    val graceTurns: Int = 0,
    val deposit: Deposit? = null,
    val terrain: Terrain = Terrain.LAND,
)
