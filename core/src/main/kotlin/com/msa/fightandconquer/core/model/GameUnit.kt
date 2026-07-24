package com.msa.fightandconquer.core.model

import com.msa.fightandconquer.core.hex.Hex
import kotlinx.serialization.Serializable

/**
 * A land unit snapshotted into a TRANSPORT's hold. The embarked unit leaves
 * [GameState.units] entirely (no two units on one hex, no dual-index headaches);
 * disembarking spawns a fresh [UnitId] — state, not identity, is authoritative.
 * Its upkeep still accrues through the carrying boat.
 */
@Serializable
data class CargoUnit(
    val tier: Int,
    @kotlinx.serialization.SerialName("unitType")
    val type: UnitType = UnitType.SOLDIER,
)

/**
 * A unit token. For SOLDIERs strength equals [tier] (1=Peasant, 2=Spearman,
 * 3=Baron, 4=Knight); special [type]s keep [tier] fixed at 1 and take their
 * strength from [RuleConstants] (see [UnitType]).
 *
 * [cargo] is non-null only on a TRANSPORT carrying a unit; it dies with the boat.
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
    val cargo: CargoUnit? = null,
)
