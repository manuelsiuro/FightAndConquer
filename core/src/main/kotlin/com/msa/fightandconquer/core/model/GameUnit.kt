package com.msa.fightandconquer.core.model

import com.msa.fightandconquer.core.hex.Hex
import kotlinx.serialization.Serializable

/** A unit token. Strength equals [tier] (1=Peasant, 2=Spearman, 3=Baron, 4=Knight). */
@Serializable
data class GameUnit(
    val id: UnitId,
    val owner: PlayerId,
    val tier: Int,
    val hex: Hex,
    val spent: Boolean = false,
)
