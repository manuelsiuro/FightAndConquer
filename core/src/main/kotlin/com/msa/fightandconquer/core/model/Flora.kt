package com.msa.fightandconquer.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Flora {
    /** Left behind by a starved/bankrupted unit; becomes a [Tree] one full round later. */
    @Serializable
    @SerialName("grave")
    data class Gravestone(val createdRound: Int) : Flora

    /** Blocks the hex's income and spreads to adjacent hexes. */
    @Serializable
    @SerialName("tree")
    data object Tree : Flora
}
