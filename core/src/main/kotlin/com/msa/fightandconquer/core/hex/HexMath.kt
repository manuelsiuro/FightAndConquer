package com.msa.fightandconquer.core.hex

import kotlin.math.abs

object HexMath {

    /** The six axial direction offsets, in ring order. */
    val DIRECTIONS: List<Pair<Int, Int>> = listOf(
        1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1,
    )

    fun neighbors(hex: Hex): List<Hex> = DIRECTIONS.map { (dq, dr) -> Hex.of(hex.q + dq, hex.r + dr) }

    /** Allocation-free neighbor iteration for hot loops. */
    inline fun forEachNeighbor(hex: Hex, action: (Hex) -> Unit) {
        val q = hex.q
        val r = hex.r
        action(Hex.of(q + 1, r)); action(Hex.of(q + 1, r - 1)); action(Hex.of(q, r - 1))
        action(Hex.of(q - 1, r)); action(Hex.of(q - 1, r + 1)); action(Hex.of(q, r + 1))
    }

    /** Cube distance between two hexes. */
    fun distance(a: Hex, b: Hex): Int {
        val dq = a.q - b.q
        val dr = a.r - b.r
        return (abs(dq) + abs(dq + dr) + abs(dr)) / 2
    }

    fun areAdjacent(a: Hex, b: Hex): Boolean = distance(a, b) == 1

    /** All hexes within [radius] of [center], including the center itself. */
    fun range(center: Hex, radius: Int): List<Hex> {
        val out = ArrayList<Hex>()
        for (dq in -radius..radius) {
            val rMin = maxOf(-radius, -dq - radius)
            val rMax = minOf(radius, -dq + radius)
            for (dr in rMin..rMax) out.add(Hex.of(center.q + dq, center.r + dr))
        }
        return out
    }

    /** The ring of hexes at exactly [radius] from [center]. radius must be >= 1. */
    fun ring(center: Hex, radius: Int): List<Hex> {
        require(radius >= 1)
        val out = ArrayList<Hex>(6 * radius)
        var q = center.q + DIRECTIONS[4].first * radius
        var r = center.r + DIRECTIONS[4].second * radius
        for (side in 0 until 6) {
            repeat(radius) {
                out.add(Hex.of(q, r))
                q += DIRECTIONS[side].first
                r += DIRECTIONS[side].second
            }
        }
        return out
    }

    /**
     * BFS flood fill from [start] over hexes accepted by [canEnter].
     * [start] is included in the result iff canEnter(start).
     */
    fun floodFill(start: Hex, canEnter: (Hex) -> Boolean): Set<Hex> {
        if (!canEnter(start)) return emptySet()
        val visited = HashSet<Hex>()
        val queue = ArrayDeque<Hex>()
        visited.add(start)
        queue.add(start)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            forEachNeighbor(current) { n ->
                if (n !in visited && canEnter(n)) {
                    visited.add(n)
                    queue.add(n)
                }
            }
        }
        return visited
    }

    /** Partition [hexes] into connected components. */
    fun connectedComponents(hexes: Set<Hex>): List<Set<Hex>> {
        val remaining = HashSet(hexes)
        val components = ArrayList<Set<Hex>>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.first()
            val component = floodFill(seed) { it in remaining }
            remaining.removeAll(component)
            components.add(component)
        }
        return components
    }
}
