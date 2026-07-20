package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

/** Index into [GameState.players]. */
@JvmInline
@Serializable
value class PlayerId(val value: Int)

@JvmInline
@Serializable
value class UnitId(val value: Int)

@Serializable
enum class Difficulty { EASY, NORMAL, HARD }
