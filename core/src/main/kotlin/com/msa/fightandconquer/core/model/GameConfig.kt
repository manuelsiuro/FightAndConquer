package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GameConfig(
    val seed: Long,
    val rules: RuleConstants = RuleConstants(),
)
