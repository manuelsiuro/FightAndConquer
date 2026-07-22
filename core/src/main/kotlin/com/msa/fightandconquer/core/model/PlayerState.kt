package com.msa.fightandconquer.core.model

import com.msa.fightandconquer.core.hex.Hex
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PlayerKind {
    @Serializable
    @SerialName("human")
    data object Human : PlayerKind

    @Serializable
    @SerialName("ai")
    data class Ai(val difficulty: Difficulty) : PlayerKind
}

@Serializable
data class PlayerState(
    val id: PlayerId,
    val kind: PlayerKind,
    val treasury: Int,
    val capital: Hex?,
    val eliminated: Boolean = false,
    /**
     * Fog of war explored memory: every hex this player has ever had in vision.
     * Grows monotonically; always empty when fog is off. Kept sorted by
     * [Hex.packed] so serialized state is byte-stable (determinism tests).
     */
    val discovered: Set<Hex> = emptySet(),
)
