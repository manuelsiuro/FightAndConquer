package com.msa.fightandconquer.core.map

import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Terrain

/** Hard requirements every playable map must satisfy. Returns human-readable violations. */
object MapValidator {

    fun validate(map: MapDefinition, params: MapParams? = map.generatorParams): List<String> {
        val violations = ArrayList<String>()
        val land = map.tiles.filter { it.terrain == Terrain.LAND }.map { it.hex }.toSet()
        val sea = map.tiles.filter { it.terrain == Terrain.SEA }.map { it.hex }.toSet()

        if (land.isEmpty()) {
            violations.add("map has no land")
            return violations
        }
        if (map.tiles.size != land.size + sea.size) violations.add("duplicate tile definitions")

        // Land connectivity is shape-dependent: a CONTINENT (and authored maps)
        // must be one walkable mass; island shapes REQUIRE water-separated starts
        // — with every island reachable by boat and every player on their own.
        val components = HexMath.connectedComponents(land)
        val islandShape = params?.shape == MapShape.ISLANDS || params?.shape == MapShape.ARCHIPELAGO
        if (!islandShape && components.size != 1) {
            violations.add("landmass split into ${components.size} components")
        }
        if (islandShape) {
            val capitalIslands = map.capitals.map { cap -> components.indexOfFirst { cap in it } }
            if (capitalIslands.toSet().size != map.capitals.size) {
                violations.add("capitals share an island: $capitalIslands")
            }
            components.forEachIndexed { index, component ->
                val coastal = component.any { hex -> HexMath.neighbors(hex).any { it in sea } }
                if (!coastal) violations.add("island $index is landlocked (no adjacent sea)")
            }
        }

        violations += seaContractViolations(map, land, sea)

        // Capitals: present, marked, on owned tiles, spaced fairly.
        if (map.capitals.isEmpty()) violations.add("no capitals")
        params?.let {
            if (map.capitals.size != it.playerCount) {
                violations.add("expected ${it.playerCount} capitals, got ${map.capitals.size}")
            }
        }
        map.capitals.forEachIndexed { player, capital ->
            val tile = map.tiles.find { it.hex == capital }
            when {
                tile == null -> violations.add("capital $player off-map")
                tile.building != Building.CAPITAL -> violations.add("capital $player not marked on tile")
                tile.owner != player -> violations.add("capital $player on tile owned by ${tile.owner}")
            }
        }
        if (map.capitals.size >= 2) {
            val minDistance = MapGenerator.minPairwiseDistance(map.capitals)
            val required = MapGenerator.requiredCapitalDistance(land.size, map.capitals.size)
            if (minDistance < required) {
                violations.add("capitals too close: $minDistance < $required")
            }
        }

        // Equal starting regions.
        val regionSizes = map.capitals.indices.map { player ->
            map.tiles.count { it.owner == player }
        }
        if (regionSizes.toSet().size > 1) violations.add("unequal starting regions: $regionSizes")

        // No trees inside starting territory.
        if (map.tiles.any { it.owner != null && it.flora != null }) {
            violations.add("flora inside a starting region")
        }

        // Deposits: never in starting territory, never stacked with flora, fairly spread.
        // (Generation is fair by construction — these are tripwires, not retry drivers.)
        if (map.tiles.any { it.owner != null && it.deposit != null }) {
            violations.add("deposit inside a starting region")
        }
        if (map.tiles.any { it.flora != null && it.deposit != null }) {
            violations.add("deposit and flora on the same tile")
        }
        if (map.capitals.size >= 2) {
            val veins = map.tiles.filter { it.deposit == Deposit.GOLD_VEIN }.map { it.hex }
            if (veins.isNotEmpty()) {
                val nearest = map.capitals.map { c -> veins.minOf { HexMath.distance(c, it) } }
                if (nearest.max() - nearest.min() > 2) {
                    violations.add("unfair gold veins: nearest distances $nearest")
                }
            }
            val shoals = map.tiles.filter { it.deposit == Deposit.FISH_SHOAL }.map { it.hex }
            if (shoals.isNotEmpty()) {
                val nearest = map.capitals.map { c -> shoals.minOf { HexMath.distance(c, it) } }
                if (nearest.max() - nearest.min() > 2) {
                    violations.add("unfair fish shoals: nearest distances $nearest")
                }
            }
            val fertile = map.tiles.filter { it.deposit == Deposit.FERTILE }.map { it.hex }
            if (fertile.isNotEmpty()) {
                // Count each capital's fertile hexes inside its own Voronoi cell — a
                // fertile hex near the border of two territories belongs to the closer one.
                val counts = map.capitals.map { c ->
                    fertile.count { v ->
                        HexMath.distance(c, v) <= MapGenerator.FERTILE_FAIR_RADIUS &&
                            map.capitals.all { it == c || HexMath.distance(v, it) > HexMath.distance(v, c) }
                    }
                }
                if (counts.max() - counts.min() > 1) {
                    violations.add("unfair fertile spread: $counts")
                }
            }
        }
        return violations
    }

    /**
     * Hard requirements for a **hand-authored** map (a campaign level).
     *
     * Deliberately narrower than [validate]: an authored level is asymmetric on purpose,
     * so capital spacing, equal starting regions, deposit fairness and "no flora in a
     * start region" are all fair game for a level designer and are not checked. What
     * remains are the invariants the *engine* relies on — the sea contract, reachable
     * land, capitals that actually exist — plus one playability rule of its own: every
     * seat must start on a single connected region, or it begins the level starving.
     */
    fun validateAuthored(map: MapDefinition): List<String> {
        val violations = ArrayList<String>()
        val land = map.tiles.filter { it.terrain == Terrain.LAND }.map { it.hex }.toSet()
        val sea = map.tiles.filter { it.terrain == Terrain.SEA }.map { it.hex }.toSet()

        if (land.isEmpty()) {
            violations.add("map has no land")
            return violations
        }
        if (map.tiles.size != land.size + sea.size) violations.add("duplicate tile definitions")
        violations += seaContractViolations(map, land, sea)

        // Every landmass must be reachable: connected to the rest by ground, or coastal
        // so a boat can get there. An unreachable island is dead level geometry.
        val components = HexMath.connectedComponents(land)
        components.forEachIndexed { index, component ->
            val coastal = component.any { hex -> HexMath.neighbors(hex).any { it in sea } }
            if (components.size > 1 && !coastal) {
                violations.add("landmass $index is unreachable (no adjacent sea)")
            }
        }

        if (map.capitals.isEmpty()) violations.add("no capitals")
        if (map.capitals.size != map.capitals.toSet().size) violations.add("duplicate capitals")
        map.capitals.forEachIndexed { player, capital ->
            val tile = map.tiles.find { it.hex == capital }
            when {
                tile == null -> violations.add("capital $player off-map")
                tile.building != Building.CAPITAL -> violations.add("capital $player not marked on tile")
                tile.owner != player -> violations.add("capital $player on tile owned by ${tile.owner}")
            }
        }

        // A seat's opening territory must be one region reachable from its capital,
        // otherwise the level starts with tiles already cut off and starving.
        val owned = map.tiles.filter { it.owner != null }.groupBy({ it.owner!! }, { it.hex })
        map.capitals.forEachIndexed { player, capital ->
            val mine = owned[player].orEmpty().toSet()
            if (mine.isEmpty()) {
                violations.add("seat $player owns no hexes")
                return@forEachIndexed
            }
            val reached = HexMath.floodFill(capital) { it in mine }
            if (reached.size != mine.size) {
                violations.add("seat $player starts with ${mine.size - reached.size} cut-off hexes")
            }
        }
        owned.keys.filter { it !in map.capitals.indices }.forEach {
            violations.add("tiles owned by seat $it, which has no capital")
        }
        return violations
    }

    /**
     * The sea contract shared by generated and authored maps: sea is neutral and empty,
     * FISH_SHOAL is its only deposit, and the water is one navigable body joined to the
     * land into a single surface.
     */
    private fun seaContractViolations(
        map: MapDefinition,
        land: Set<com.msa.fightandconquer.core.hex.Hex>,
        sea: Set<com.msa.fightandconquer.core.hex.Hex>,
    ): List<String> {
        val violations = ArrayList<String>()
        map.tiles.filter { it.terrain == Terrain.SEA }.forEach { tile ->
            if (tile.owner != null) violations.add("sea tile ${tile.hex} has an owner")
            if (tile.building != null) violations.add("sea tile ${tile.hex} has a building")
            if (tile.flora != null) violations.add("sea tile ${tile.hex} has flora")
            if (tile.deposit != null && tile.deposit != Deposit.FISH_SHOAL) {
                violations.add("sea tile ${tile.hex} has a land deposit")
            }
        }
        if (map.tiles.any { it.terrain == Terrain.LAND && it.deposit == Deposit.FISH_SHOAL }) {
            violations.add("fish shoal on land")
        }
        if (sea.isNotEmpty()) {
            val seaComponents = HexMath.connectedComponents(sea)
            if (seaComponents.size != 1) {
                violations.add("sea split into ${seaComponents.size} components")
            }
            if (HexMath.connectedComponents(land + sea).size != 1) {
                violations.add("map is not one connected land+sea surface")
            }
        }
        return violations
    }
}
