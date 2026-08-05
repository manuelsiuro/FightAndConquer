package com.msa.fightandconquer.ui.editor

import com.msa.fightandconquer.core.campaign.SeatDef
import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.editor.CustomMapValidator
import com.msa.fightandconquer.core.editor.EditorPreview
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.map.MapViolation
import com.msa.fightandconquer.core.map.TileDef
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.Terrain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The editor's working state: one immutable [CustomMapDef] evolved by brush strokes,
 * with a bounded undo stack and live validation. A plain holder owned by the
 * ViewModel (the `CampaignRepository` ownership precedent), pure JVM so every brush
 * rule is host-testable.
 *
 * Every stroke re-validates; violations never block anything here — a map with
 * violations is a draft, and the screen shows them as a list, not a wall.
 */
class EditorSession(
    private val store: CustomMapStore,
    initial: CustomMapDef,
) {

    sealed interface Brush {
        data object Land : Brush
        data object Sea : Brush
        data object Erase : Brush
        data class Owner(val seat: Int) : Brush
        data class Capital(val seat: Int) : Brush
    }

    data class Ui(
        val def: CustomMapDef,
        val brush: Brush,
        val canUndo: Boolean,
        val dirty: Boolean,
        val violations: List<MapViolation>,
    )

    private val undoStack = ArrayDeque<CustomMapDef>()

    private val _ui = MutableStateFlow(
        Ui(
            def = initial,
            brush = Brush.Land,
            canUndo = false,
            dirty = false,
            violations = CustomMapValidator.validate(initial),
        ),
    )
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun setBrush(brush: Brush) {
        _ui.value = _ui.value.copy(brush = brush)
    }

    /** Applies the active brush to [hex]; a stroke that changes nothing is not an undo step. */
    fun paint(hex: Hex) {
        val current = _ui.value.def
        val next = applyBrush(current, _ui.value.brush, hex) ?: return
        undoStack.addLast(current)
        while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        commit(next)
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        commit(previous)
    }

    fun rename(name: String) {
        val def = _ui.value.def
        if (name.isBlank() || name == def.name) return
        commit(def.copy(name = name, level = def.level.copy(map = def.level.map.copy(name = name))))
    }

    /** Persists the working state. Never blocked by violations — drafts are welcome. */
    fun save(now: Long) {
        val stamped = _ui.value.def.copy(modifiedAt = now)
        store.save(stamped)
        _ui.value = _ui.value.copy(def = stamped, dirty = false)
    }

    fun saveIfDirty(now: Long) {
        if (_ui.value.dirty) save(now)
    }

    /** The lenient render state for [com.msa.fightandconquer.render.scene.BoardScene]. */
    fun previewState(): GameState = _ui.value.def.level.let { level ->
        EditorPreview.state(level.map.tiles, level.seats.size, level.startingUnits)
    }

    /** Empty hexes bordering the map — the ghost ring the canvas offers for growth. */
    fun growthRing(): Set<Hex> {
        val present = _ui.value.def.level.map.tiles.mapTo(HashSet()) { it.hex }
        val ring = HashSet<Hex>()
        for (hex in present) {
            HexMath.forEachNeighbor(hex) { n -> if (n !in present) ring.add(n) }
        }
        return ring
    }

    private fun commit(def: CustomMapDef) {
        _ui.value = _ui.value.copy(
            def = def,
            canUndo = undoStack.isNotEmpty(),
            dirty = true,
            violations = CustomMapValidator.validate(def),
        )
    }

    private fun applyBrush(def: CustomMapDef, brush: Brush, hex: Hex): CustomMapDef? {
        val level = def.level
        val map = level.map
        val existing = map.tiles.firstOrNull { it.hex == hex }

        fun withTiles(tiles: List<TileDef>, capitals: List<Hex> = map.capitals) =
            def.copy(
                level = level.copy(
                    map = map.copy(tiles = tiles.sortedBy { it.hex.packed }, capitals = capitals),
                ),
            )

        fun replaced(tile: TileDef) = map.tiles.filter { it.hex != hex } + tile

        return when (brush) {
            Brush.Land ->
                if (existing != null && existing.terrain == Terrain.LAND) {
                    null
                } else {
                    // Sea -> land drops sea-only features (a fish shoal cannot beach).
                    withTiles(replaced(TileDef(hex = hex)))
                }

            Brush.Sea ->
                if (existing?.terrain == Terrain.SEA) {
                    null
                } else {
                    // The sea contract by construction: water is neutral and bare. A
                    // dangling capitals entry is fine — that is what drafts are for.
                    withTiles(replaced(TileDef(hex = hex, terrain = Terrain.SEA)))
                }

            Brush.Erase ->
                if (existing == null) {
                    null
                } else {
                    // Units standing on the erased hex would dangle off-map — drop them.
                    val remaining = level.startingUnits.filter { it.hex != hex }
                    def.copy(
                        level = level.copy(
                            map = map.copy(tiles = map.tiles.filter { it.hex != hex }),
                            startingUnits = remaining,
                        ),
                    )
                }

            is Brush.Owner -> {
                if (existing == null || existing.terrain != Terrain.LAND) return null
                if (brush.seat !in level.seats.indices) return null
                if (existing.owner == brush.seat) return null
                withTiles(replaced(existing.copy(owner = brush.seat)))
            }

            is Brush.Capital -> {
                if (existing == null || existing.terrain != Terrain.LAND) return null
                if (brush.seat > level.seats.size) return null
                if (map.capitals.getOrNull(brush.seat) == hex) return null

                val growing = brush.seat == level.seats.size
                var tiles = map.tiles
                if (!growing) {
                    // Moving a capital clears the old marker, if it still stands.
                    val old = map.capitals[brush.seat]
                    tiles = tiles.map { tile ->
                        if (tile.hex == old && tile.building == Building.CAPITAL) {
                            tile.copy(building = null)
                        } else {
                            tile
                        }
                    }
                }
                tiles = tiles.filter { it.hex != hex } +
                    existing.copy(owner = brush.seat, building = Building.CAPITAL)

                val capitals =
                    if (growing) map.capitals + hex
                    else map.capitals.mapIndexed { i, c -> if (i == brush.seat) hex else c }
                val seats =
                    if (growing) level.seats + SeatDef.Ai(Difficulty.NORMAL) else level.seats
                val treasury = level.startingTreasury?.let { purses ->
                    if (growing) purses + level.rules.startingTreasury else purses
                }
                def.copy(
                    level = level.copy(
                        map = map.copy(
                            tiles = tiles.sortedBy { it.hex.packed },
                            capitals = capitals,
                        ),
                        seats = seats,
                        startingTreasury = treasury,
                    ),
                )
            }
        }
    }

    companion object {
        /** Snapshots are cheap (immutable, structurally shared) — 50 strokes is plenty. */
        private const val UNDO_LIMIT = 50
    }
}
