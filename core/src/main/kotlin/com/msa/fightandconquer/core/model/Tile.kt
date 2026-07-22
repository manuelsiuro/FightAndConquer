package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

/**
 * One land hex. Water hexes are simply absent from [GameState.tiles].
 *
 * [unit] mirrors [GameUnit.hex] — the reducer keeps both indexes consistent
 * (verified by invariant checks in tests).
 *
 * [starving]: owned but disconnected from the owner's capital — produces no income,
 * and any unit on it dies at the owner's next turn start.
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
    val deposit: Deposit? = null,
)
