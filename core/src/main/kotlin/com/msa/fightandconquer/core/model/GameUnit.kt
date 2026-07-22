package com.msa.fightandconquer.core.model

import com.msa.fightandconquer.core.hex.Hex
import kotlinx.serialization.Serializable

/**
 * A unit token. For SOLDIERs strength equals [tier] (1=Peasant, 2=Spearman,
 * 3=Baron, 4=Knight); special [type]s keep [tier] fixed at 1 and take their
 * strength from [RuleConstants] (see [UnitType]).
 */
@Serializable
data class GameUnit(
    val id: UnitId,
    val owner: PlayerId,
    val tier: Int,
    val hex: Hex,
    val spent: Boolean = false,
    // "unitType" keeps the serialized key distinct from polymorphic discriminators.
    @kotlinx.serialization.SerialName("unitType")
    val type: UnitType = UnitType.SOLDIER,
)
