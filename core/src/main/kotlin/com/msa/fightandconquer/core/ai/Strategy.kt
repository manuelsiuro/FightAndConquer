package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.UnitId

/**
 * The strategic read of the board, computed once per [AiPlayer.chooseAction]
 * and threaded to the evaluator and move generator the way the frozen
 * visibility set is. Pure and stateless — the same state always yields the
 * same context (no caching: the ViewModel builds a fresh AiPlayer per action
 * while the simulation tests reuse one, and any instance memory would make the
 * two drive modes diverge).
 *
 * Everything here honors fog through [visible]: enemy ground and units outside
 * vision do not exist for this assessment (the Evaluator convention).
 */
data class StrategicContext(
    val profile: AiProfile,
    /** Own funded land hexes adjacent to enemy-owned land. */
    val frontHexes: Set<Hex>,
    /** BFS steps from each own land hex to the nearest front hex (0 on the front). */
    val distanceToFront: Map<Hex, Int>,
    /** The strongest living opponent — the one to contain before it snowballs. */
    val focusEnemy: PlayerId?,
    /**
     * Capturable enemy hex -> how many enemy tiles lose their food line when it
     * falls (the slicing move: a fed tile is flood-connected to its owner's
     * capital or an overseas port, so one captured chokepoint can starve a
     * whole province). Only hexes worth at least one starved tile appear.
     */
    val cutValues: Map<Hex, Int>,
    /** Visible enemy land units standing on or beside my territory, by id. */
    val threatUnits: List<UnitId>,
    /** Own hexes an adjacent visible enemy (or monster) currently out-attacks. */
    val defenseGaps: List<Hex>,
)

internal object Strategy {

    fun assess(
        state: GameState,
        me: PlayerId,
        profile: AiProfile,
        visible: Set<Hex>?,
    ): StrategicContext {
        val partners: Set<PlayerId> =
            if (state.config.rules.diplomacyEnabled) state.diplomacy.partnersOf(me) else emptySet()

        // --- Front line + own-territory distance field ---
        val front = ArrayList<Hex>()
        val ownLand = HashSet<Hex>()
        for ((hex, tile) in state.tiles) {
            if (tile.owner != me || tile.terrain != Terrain.LAND) continue
            ownLand.add(hex)
            if (tile.starving) continue
            var touchesEnemy = false
            HexMath.forEachNeighbor(hex) { n ->
                val owner = state.tiles[n]?.owner
                if (owner != null && owner != me && owner !in partners) touchesEnemy = true
            }
            if (touchesEnemy) front.add(hex)
        }
        front.sortBy { it.packed }
        val distanceToFront = HashMap<Hex, Int>()
        var frontier: List<Hex> = front
        for (hex in front) distanceToFront[hex] = 0
        var d = 0
        while (frontier.isNotEmpty() && d < 128) {
            d++
            val next = ArrayList<Hex>()
            for (hex in frontier) {
                HexMath.forEachNeighbor(hex) { n ->
                    if (n in ownLand && distanceToFront.putIfAbsent(n, d) == null) next.add(n)
                }
            }
            frontier = next
        }

        // --- Focus: the strongest living non-partner opponent ---
        val focusEnemy = state.players
            .filter { !it.eliminated && it.id != me && it.id !in partners }
            .map { it.id to DiplomacyPolicy.powerOf(state, me, it.id) }
            .filter { it.second > 0 }
            .maxWithOrNull(compareBy({ it.second }, { -it.first.value }))
            ?.first

        // --- Threat units + defense gaps (one neighbor sweep of my territory) ---
        val threatUnits = ArrayList<UnitId>()
        val defenseGaps = ArrayList<Hex>()
        val nearMine = HashSet<Hex>().also { set ->
            for (hex in ownLand) {
                set.add(hex)
                HexMath.forEachNeighbor(hex) { n -> set.add(n) }
            }
        }
        for (u in state.units.values) {
            if (u.owner == me || u.owner in partners || Rules.isNaval(u.type)) continue
            if (visible != null && u.hex !in visible) continue
            if (u.hex in nearMine) threatUnits.add(u.id)
        }
        threatUnits.sortBy { it.value }
        for (hex in ownLand) {
            var threat = 0
            HexMath.forEachNeighbor(hex) { n ->
                if (visible == null || n in visible) {
                    val enemy = state.unitAt(n)
                    if (enemy != null && enemy.owner != me && enemy.owner !in partners) {
                        threat = maxOf(threat, Rules.strengthOf(state, enemy))
                    }
                    state.tiles[n]?.monster?.let { threat = maxOf(threat, Rules.monsterAttackOf(it)) }
                }
            }
            if (threat > Rules.defenseOf(state, hex)) defenseGaps.add(hex)
        }
        defenseGaps.sortBy { it.packed }

        return StrategicContext(
            profile = profile,
            frontHexes = LinkedHashSet(front),
            distanceToFront = distanceToFront,
            focusEnemy = focusEnemy,
            cutValues = cutValues(state, me, partners, visible),
            threatUnits = threatUnits,
            defenseGaps = defenseGaps,
        )
    }

    /**
     * The slicing read: for each capturable enemy border hex, how many of that
     * enemy's tiles drop off their food line (capital flood-fill or an overseas
     * port, mirroring the reducer's recomputeStarving model) once the hex is mine. Under
     * fog an enemy whose capital is out of sight is not analyzed — guessing at
     * unseen interiors would be omniscience.
     */
    private fun cutValues(
        state: GameState,
        me: PlayerId,
        partners: Set<PlayerId>,
        visible: Set<Hex>?,
    ): Map<Hex, Int> {
        // Enemy hexes on my frontier, grouped by owner (the only capture targets).
        val targetsByOwner = HashMap<PlayerId, MutableList<Hex>>()
        for ((hex, tile) in state.tiles) {
            if (tile.owner != me || tile.starving) continue
            HexMath.forEachNeighbor(hex) { n ->
                val t = state.tiles[n]
                val owner = t?.owner
                if (t != null && t.terrain == Terrain.LAND && owner != null &&
                    owner != me && owner !in partners
                ) {
                    targetsByOwner.getOrPut(owner) { ArrayList() }.add(n)
                }
            }
        }
        if (targetsByOwner.isEmpty()) return emptyMap()

        val out = HashMap<Hex, Int>()
        for ((owner, targets) in targetsByOwner.entries.sortedBy { it.key.value }) {
            val capital = state.player(owner).capital ?: continue
            if (state.tiles[capital]?.owner != owner) continue
            if (visible != null && capital !in visible) continue
            val fedNow = fedTiles(state, owner, excluding = null)
            for (hex in targets.distinct()) {
                if (hex == capital) continue // capital capture is priced by its own terms
                if (hex !in fedNow) continue // already starving — nothing left to cut
                val fedAfter = fedTiles(state, owner, excluding = hex)
                // -1: the captured hex itself changes hands, it doesn't starve.
                val starved = fedNow.size - 1 - fedAfter.size
                if (starved > 0) out[hex] = starved
            }
        }
        return out
    }

    /** [owner]'s fed tiles if [excluding] were no longer theirs — the engine's feeding model. */
    private fun fedTiles(state: GameState, owner: PlayerId, excluding: Hex?): Set<Hex> {
        fun owned(hex: Hex): Boolean = hex != excluding && state.tiles[hex]?.owner == owner
        val capital = state.player(owner).capital ?: return emptySet()
        val fed = HashSet<Hex>()
        if (owned(capital)) fed += HexMath.floodFill(capital) { owned(it) }
        val homeland = HexMath.floodFill(capital) { state.tiles[it]?.terrain == Terrain.LAND }
        for ((hex, tile) in state.tiles) {
            if (owned(hex) && tile.building == Building.PORT && hex !in fed && hex !in homeland) {
                fed += HexMath.floodFill(hex) { owned(it) }
            }
        }
        return fed
    }
}
