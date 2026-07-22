package com.msa.fightandconquer.core.map

import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Deposit

/** Hard requirements every playable map must satisfy. Returns human-readable violations. */
object MapValidator {

    fun validate(map: MapDefinition, params: MapParams? = map.generatorParams): List<String> {
        val violations = ArrayList<String>()
        val land = map.tiles.map { it.hex }.toSet()

        if (land.isEmpty()) {
            violations.add("map has no land")
            return violations
        }
        if (map.tiles.size != land.size) violations.add("duplicate tile definitions")

        // Single connected landmass (Slay movement makes disconnected land unreachable).
        val components = HexMath.connectedComponents(land)
        if (components.size != 1) violations.add("landmass split into ${components.size} components")

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
}
