package com.msa.fightandconquer.core.map

import com.msa.fightandconquer.core.engine.Rng
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.RuleConstants
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Seeded procedural map generation. All randomness flows through a local SplitMix64
 * chain derived from [MapParams.seed] — identical params always produce the identical map.
 * Generation retries derived seeds until [MapValidator] accepts the result.
 */
object MapGenerator {

    private const val MAX_ATTEMPTS = 32

    fun generate(params: MapParams, rules: RuleConstants = RuleConstants()): MapDefinition {
        var lastFailure: List<String> = emptyList()
        for (attempt in 0 until MAX_ATTEMPTS) {
            val attemptSeed = Rng.output(Rng.advance(params.seed + attempt * 7919L))
            val map = tryGenerate(params, rules, attemptSeed)
            if (map != null) {
                val violations = MapValidator.validate(map, params)
                if (violations.isEmpty()) return map
                lastFailure = violations
            }
        }
        error("map generation failed after $MAX_ATTEMPTS attempts for $params: $lastFailure")
    }

    private class Chain(var state: Long) {
        fun roll(bound: Int): Int {
            state = Rng.advance(state)
            return Rng.nextInt(state, bound)
        }
    }

    private fun tryGenerate(params: MapParams, rules: RuleConstants, seed: Long): MapDefinition? {
        val rng = Chain(seed)
        val land = when (params.shape) {
            MapShape.CONTINENT -> growBlob(rng, Hex.of(0, 0), params.size.targetHexes)
            MapShape.ISLANDS -> islands(rng, params, blobCount = params.playerCount)
            MapShape.ARCHIPELAGO -> islands(rng, params, blobCount = params.playerCount + 2)
        }

        val capitals = placeCapitals(rng, land, params.playerCount) ?: return null

        // Start regions: capital + its full neighbor ring (constrained to land at selection).
        val regions = capitals.map { capital -> HexMath.range(capital, 1).toSet() }

        val tiles = HashMap<Hex, TileDef>()
        for (hex in land) tiles[hex] = TileDef(hex)
        regions.forEachIndexed { player, region ->
            for (hex in region) tiles[hex] = TileDef(hex, owner = player)
        }
        capitals.forEachIndexed { player, capital ->
            tiles[capital] = TileDef(capital, owner = player, building = Building.CAPITAL)
        }

        val protected = regions.flatMap { region -> region.flatMap { HexMath.neighbors(it) + it } }.toSet()

        // Terrain deposits, fair by construction (see MapValidator tripwires).
        val deposits = placeDeposits(rng, land, capitals, protected, params, rules)
        for ((hex, deposit) in deposits) {
            tiles[hex] = tiles.getValue(hex).copy(deposit = deposit)
        }

        // Initial trees on neutral land, away from start regions and deposits.
        val candidates = land.filter { it !in protected && it !in deposits }
            .sortedBy { it.packed }.toMutableList()
        val treeCount = land.size * rules.initialTreePercent / 100
        repeat(minOf(treeCount, candidates.size)) {
            val index = rng.roll(candidates.size)
            val hex = candidates.removeAt(index)
            tiles[hex] = tiles.getValue(hex).copy(flora = Flora.Tree)
        }

        return MapDefinition(
            name = "${params.shape.name.lowercase()}-${params.size.name.lowercase()}-${params.seed}",
            generatorParams = params,
            tiles = tiles.values.sortedBy { it.hex.packed },
            capitals = capitals,
        )
    }

    /**
     * Places all terrain deposits. Fairness is by construction, not by retry:
     * - Every capital gets its gold vein(s) at a common per-attempt target distance
     *   (±1, clamped to the band), so nearest-vein distances differ by at most 2.
     * - Every capital gets its FERTILE hexes inside the same fixed band.
     * - Contested neutral deposits stay outside every capital's fair zone, so they
     *   never skew the per-player counts the validator checks.
     */
    private fun placeDeposits(
        rng: Chain,
        land: Set<Hex>,
        capitals: List<Hex>,
        protected: Set<Hex>,
        params: MapParams,
        rules: RuleConstants,
    ): Map<Hex, Deposit> {
        val deposits = HashMap<Hex, Deposit>()
        if (rules.goldVeinsPerPlayer <= 0 && rules.fertilePerPlayer <= 0 &&
            rules.goldVeinsNeutralPer150Hexes <= 0 && rules.fertileNeutralPercent <= 0
        ) {
            return deposits
        }

        // A capital's deposits live strictly inside its Voronoi cell — otherwise a
        // deposit placed "for" X can sit nearer to Y and break the fairness bounds.
        fun inCellOf(hex: Hex, capital: Hex): Boolean {
            val own = HexMath.distance(hex, capital)
            return capitals.all { it == capital || HexMath.distance(hex, it) > own }
        }

        fun candidatesNear(capital: Hex, min: Int, max: Int): List<Hex> =
            land.filter { hex ->
                hex !in protected && hex !in deposits &&
                    HexMath.distance(hex, capital) in min..max &&
                    inCellOf(hex, capital)
            }.sortedBy { it.packed }

        // Places [count] deposits in every capital's band, or NONE anywhere: on cramped
        // maps (many players, small landmass) there may be no room for fair deposits,
        // and zero-for-everyone is still fair — better than failing the whole map.
        fun placeFairly(count: Int, min: Int, max: Int, deposit: Deposit) {
            if (capitals.any { candidatesNear(it, min, max).size < count }) return
            for (capital in capitals) {
                repeat(count) {
                    val candidates = candidatesNear(capital, min, max)
                    deposits[candidates[rng.roll(candidates.size)]] = deposit
                }
            }
        }

        // Fair per-player veins around a common target distance.
        if (rules.goldVeinsPerPlayer > 0) {
            val band = rules.goldVeinBandMin..rules.goldVeinBandMax
            val target = band.first + rng.roll(band.last - band.first + 1)
            placeFairly(
                rules.goldVeinsPerPlayer,
                maxOf(band.first, target - 1),
                minOf(band.last, target + 1),
                Deposit.GOLD_VEIN,
            )
        }
        // Fair per-player FERTILE hexes (band 2..5; the protected ring keeps them off starts).
        if (rules.fertilePerPlayer > 0) {
            placeFairly(rules.fertilePerPlayer, 2, FERTILE_FAIR_RADIUS, Deposit.FERTILE)
        }

        // Contested neutral deposits, strictly outside every capital's fair zone.
        val neutralVeinFloor = maxOf(
            requiredCapitalDistance(land.size, params.playerCount) / 2,
            rules.goldVeinBandMax + 1,
        )
        val neutralVeins = land.size / 150 * rules.goldVeinsNeutralPer150Hexes
        if (neutralVeins > 0) {
            val middle = land.filter { hex ->
                hex !in protected && hex !in deposits &&
                    capitals.minOf { HexMath.distance(hex, it) } >= neutralVeinFloor
            }.sortedBy { it.packed }.toMutableList()
            repeat(minOf(neutralVeins, middle.size)) {
                val hex = middle.removeAt(rng.roll(middle.size))
                deposits[hex] = Deposit.GOLD_VEIN
            }
        }
        val neutralFertile = land.size * rules.fertileNeutralPercent / 100
        if (neutralFertile > 0) {
            val open = land.filter { hex ->
                hex !in protected && hex !in deposits &&
                    capitals.minOf { HexMath.distance(hex, it) } > FERTILE_FAIR_RADIUS
            }.sortedBy { it.packed }.toMutableList()
            repeat(minOf(neutralFertile, open.size)) {
                val hex = open.removeAt(rng.roll(open.size))
                deposits[hex] = Deposit.FERTILE
            }
        }
        return deposits
    }

    /** Radius of the per-capital FERTILE fairness zone (also checked by MapValidator). */
    internal const val FERTILE_FAIR_RADIUS = 5

    /**
     * Random-walk blob growth: repeatedly claim a frontier hex, weighted by
     * (1 + landNeighbors)^2 to favor compact but wiggly coastlines.
     */
    private fun growBlob(rng: Chain, start: Hex, target: Int): Set<Hex> {
        val land = HashSet<Hex>()
        val frontier = HashSet<Hex>()
        land.add(start)
        HexMath.forEachNeighbor(start) { frontier.add(it) }

        while (land.size < target && frontier.isNotEmpty()) {
            val sorted = frontier.sortedBy { it.packed }
            var totalWeight = 0
            val weights = IntArray(sorted.size)
            for (i in sorted.indices) {
                var landNeighbors = 0
                HexMath.forEachNeighbor(sorted[i]) { if (it in land) landNeighbors++ }
                val w = (1 + landNeighbors) * (1 + landNeighbors)
                weights[i] = w
                totalWeight += w
            }
            var pick = rng.roll(totalWeight)
            var chosen = sorted.last()
            for (i in sorted.indices) {
                pick -= weights[i]
                if (pick < 0) {
                    chosen = sorted[i]
                    break
                }
            }
            land.add(chosen)
            frontier.remove(chosen)
            HexMath.forEachNeighbor(chosen) { if (it !in land) frontier.add(it) }
        }
        return land
    }

    /** Player islands on a circle, then land bridges so the map is one connected mass. */
    private fun islands(rng: Chain, params: MapParams, blobCount: Int): Set<Hex> {
        val perBlob = params.size.targetHexes / blobCount
        // Roughly space blob centers on a hex "circle" whose radius scales with blob size.
        val radius = maxOf(6, (sqrt(perBlob.toDouble()) * 1.9).roundToInt())
        val centers = (0 until blobCount).map { i ->
            val angle = 2.0 * Math.PI * i / blobCount
            val q = (radius * kotlin.math.cos(angle)).roundToInt()
            val r = (radius * kotlin.math.sin(angle) - q / 2.0).roundToInt()
            Hex.of(q, r)
        }
        val land = HashSet<Hex>()
        centers.forEach { land.addAll(growBlob(rng, it, perBlob)) }
        // Bridge each island to the next (ring topology keeps it simple and connected).
        for (i in centers.indices) {
            val from = centers[i]
            val to = centers[(i + 1) % centers.size]
            land.addAll(hexLine(from, to))
        }
        return land
    }

    /** All hexes on the straight line from a to b (cube lerp + rounding). */
    internal fun hexLine(a: Hex, b: Hex): List<Hex> {
        val n = HexMath.distance(a, b)
        if (n == 0) return listOf(a)
        // Tiny nudge avoids ties landing exactly on hex borders (standard hex-line trick).
        return (0..n).map { i ->
            val t = i.toDouble() / n
            axialRound(
                a.q + 1e-6 + (b.q - a.q) * t,
                a.r + 1e-6 + (b.r - a.r) * t,
            )
        }
    }

    internal fun axialRound(qf: Double, rf: Double): Hex {
        val sf = -qf - rf
        var q = Math.round(qf).toInt()
        var r = Math.round(rf).toInt()
        val s = Math.round(sf).toInt()
        val dq = Math.abs(q - qf)
        val dr = Math.abs(r - rf)
        val ds = Math.abs(s - sf)
        if (dq > dr && dq > ds) {
            q = -r - s
        } else if (dr > ds) {
            r = -q - s
        }
        return Hex.of(q, r)
    }

    /**
     * Farthest-point sampling of capitals among hexes whose whole neighbor ring is land.
     * Returns null (retry) if not enough viable spots or spacing is impossible.
     */
    private fun placeCapitals(rng: Chain, land: Set<Hex>, count: Int): List<Hex>? {
        val viable = land.filter { hex -> HexMath.neighbors(hex).all { it in land } }.sortedBy { it.packed }
        if (viable.size < count) return null

        // Deterministic centroid; seed FPS from the viable hex farthest from it (a rim
        // point) so the greedy spread starts wide instead of from a random interior pick.
        val cq = land.sumOf { it.q } / land.size
        val cr = land.sumOf { it.r } / land.size
        val centroid = Hex.of(cq, cr)

        val capitals = ArrayList<Hex>(count)
        capitals.add(viable.maxByOrNull { HexMath.distance(it, centroid) }!!)
        repeat(count - 1) {
            val next = viable
                .filter { it !in capitals }
                .maxByOrNull { candidate -> capitals.minOf { HexMath.distance(candidate, it) } }
                ?: return null
            capitals.add(next)
        }
        val minDistance = minPairwiseDistance(capitals)
        if (minDistance < requiredCapitalDistance(land.size, count)) return null
        return capitals
    }

    internal fun minPairwiseDistance(hexes: List<Hex>): Int {
        var min = Int.MAX_VALUE
        for (i in hexes.indices) {
            for (j in i + 1 until hexes.size) {
                min = minOf(min, HexMath.distance(hexes[i], hexes[j]))
            }
        }
        return min
    }

    /** Fairness floor that scales with per-player share of the land. */
    internal fun requiredCapitalDistance(landSize: Int, playerCount: Int): Int =
        maxOf(5, (sqrt(landSize.toDouble() / playerCount) * 0.9).roundToInt())
}
