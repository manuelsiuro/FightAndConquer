package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Building {
    CAPITAL, FARM, TOWER, STRONG_TOWER, MINE, MARKET, LUMBER_CAMP, WATCHTOWER, PORT,
    /** Harvests adjacent FISH_SHOAL sea hexes (coastal land building). */
    FISHERY,
    /**
     * The one building that stands ON a sea hex — and owns it. A bridge hex is
     * walkable land-for-movement: region flood-fills cross it, units stand on
     * and fight over it (capture PRESERVES the bridge), and boats cannot pass
     * under it. Warship bombardment destroys it (the hex reverts to open sea).
     */
    BRIDGE,
}

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
    /** Coastal harbor: builds boats on adjacent sea, feeds its region (overseas supply). */
    PORT(Building.PORT),
    FISHERY(Building.FISHERY),
    BRIDGE(Building.BRIDGE),
}
