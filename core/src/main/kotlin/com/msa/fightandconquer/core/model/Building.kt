package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Building { CAPITAL, FARM, TOWER, STRONG_TOWER, MINE, MARKET, LUMBER_CAMP, WATCHTOWER }

/** Buildings a player can purchase (the Capital is never bought). */
@Serializable
enum class BuildingType(val building: Building) {
    FARM(Building.FARM),
    TOWER(Building.TOWER),
    STRONG_TOWER(Building.STRONG_TOWER),
    MINE(Building.MINE),
    MARKET(Building.MARKET),
    LUMBER_CAMP(Building.LUMBER_CAMP),
    WATCHTOWER(Building.WATCHTOWER),
}
