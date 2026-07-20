package com.msa.fightandconquer.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface GamePhase {
    @Serializable
    @SerialName("playing")
    data object Playing : GamePhase

    @Serializable
    @SerialName("finished")
    data class Finished(val winner: PlayerId) : GamePhase
}
