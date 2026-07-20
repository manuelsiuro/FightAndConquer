package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Building { CAPITAL, FARM, TOWER, STRONG_TOWER }

/** Buildings a player can purchase (the Capital is never bought). */
@Serializable
enum class BuildingType(val building: Building) {
    FARM(Building.FARM),
    TOWER(Building.TOWER),
    STRONG_TOWER(Building.STRONG_TOWER),
}
