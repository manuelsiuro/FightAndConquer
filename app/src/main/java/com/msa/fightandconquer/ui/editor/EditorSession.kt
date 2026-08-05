package com.msa.fightandconquer.ui.editor

import com.msa.fightandconquer.core.campaign.FailCondition
import com.msa.fightandconquer.core.campaign.Objective
import com.msa.fightandconquer.core.campaign.SeatDef
import com.msa.fightandconquer.core.campaign.UnitPlacement
import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.editor.CustomMapValidator
import com.msa.fightandconquer.core.editor.EditorPreview
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.map.MapViolation
import com.msa.fightandconquer.core.map.TileDef
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.UnitType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The editor's working state: one immutable [CustomMapDef] evolved by brush strokes
 * and panel edits, with a bounded undo stack and live validation. A plain holder
 * owned by the ViewModel (the `CampaignRepository` ownership precedent), pure JVM so
 * every brush rule is host-testable.
 *
 * Brushes that need a player context (claim, capital, units) read [Ui.activeSeat]
 * rather than carrying a seat themselves — one seat selector serves every tool.
 * Violations never block anything here: a map with violations is a draft.
 */
class EditorSession(
    private val store: CustomMapStore,
    initial: CustomMapDef,
) {

    enum class PlantKind { TREE, GRAVE }

    sealed interface Brush {
        data object Land : Brush
        data object Sea : Brush
        data object Erase : Brush

        /** Strips flora/deposit/unit and any non-capital building, keeping terrain. */
        data object ClearProps : Brush
        data object Owner : Brush
        data object Capital : Brush
        data class Structure(val building: Building) : Brush
        data class Plant(val kind: PlantKind) : Brush
        data class Resource(val deposit: Deposit) : Brush
        data class UnitBrush(val type: UnitType, val tier: Int = 1) : Brush

        /** Toggles hexes of the capture/hold objective at [index]. */
        data class ObjectiveHexes(val index: Int) : Brush
    }

    data class Ui(
        val def: CustomMapDef,
        val brush: Brush,
        val activeSeat: Int,
        val canUndo: Boolean,
        val dirty: Boolean,
        val violations: List<MapViolation>,
    )

    private val undoStack = ArrayDeque<CustomMapDef>()

    private val _ui = MutableStateFlow(
        Ui(
            def = initial,
            brush = Brush.Land,
            activeSeat = 0,
            canUndo = false,
            dirty = false,
            violations = CustomMapValidator.validate(initial),
        ),
    )
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun setBrush(brush: Brush) {
        _ui.value = _ui.value.copy(brush = brush)
    }

    /** [seat] == seats.size selects a pending new seat: only the capital tool creates it. */
    fun setActiveSeat(seat: Int) {
        val max = _ui.value.def.level.seats.size
        _ui.value = _ui.value.copy(activeSeat = seat.coerceIn(0, max))
    }

    /** Applies the active brush to [hex]; a stroke that changes nothing is not an undo step. */
    fun paint(hex: Hex) {
        mutate { def -> applyBrush(def, _ui.value.brush, _ui.value.activeSeat, hex) }
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        commit(previous)
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        mutate { def ->
            if (name == def.name) null
            else def.copy(name = name, level = def.level.copy(map = def.level.map.copy(name = name)))
        }
    }

    // ----- seats & rules & goals (panel edits, all undoable) -----

    /** Sets a seat's kind; making a seat human converts the previous human to AI. */
    fun setSeatKind(seat: Int, kind: SeatDef) {
        mutate { def ->
            val seats = def.level.seats
            if (seat !in seats.indices || seats[seat] == kind) return@mutate null
            val updated = seats.mapIndexed { i, existing ->
                when {
                    i == seat -> kind
                    kind is SeatDef.Player && existing is SeatDef.Player ->
                        SeatDef.Ai(Difficulty.NORMAL)
                    else -> existing
                }
            }
            def.copy(level = def.level.copy(seats = updated))
        }
    }

    fun setTreasury(seat: Int, coins: Int) {
        mutate { def ->
            val level = def.level
            if (seat !in level.seats.indices || coins < 0) return@mutate null
            val purses = level.startingTreasury
                ?: List(level.seats.size) { level.rules.startingTreasury }
            if (purses.getOrNull(seat) == coins) return@mutate null
            def.copy(
                level = level.copy(
                    startingTreasury = purses.mapIndexed { i, c -> if (i == seat) coins else c },
                ),
            )
        }
    }

    fun setFogOfWar(on: Boolean) = setRules { it.copy(fogOfWar = on) }
    fun setSpecialUnits(on: Boolean) = setRules { it.copy(specialUnitsEnabled = on) }
    fun setDiplomacy(on: Boolean) = setRules { it.copy(diplomacyEnabled = on) }
    fun setNaval(on: Boolean) = setRules { it.copy(navalEnabled = on) }

    private fun setRules(transform: (com.msa.fightandconquer.core.model.RuleConstants) -> com.msa.fightandconquer.core.model.RuleConstants) {
        mutate { def ->
            val updated = transform(def.level.rules)
            if (updated == def.level.rules) null else def.copy(level = def.level.copy(rules = updated))
        }
    }

    /** Adds a goal and returns its index; hex-targeted goals start empty for painting. */
    fun addObjective(objective: Objective): Int {
        val index = _ui.value.def.level.objectives.size
        mutate { def -> def.copy(level = def.level.copy(objectives = def.level.objectives + objective)) }
        if (objective is Objective.CaptureHexes || objective is Objective.HoldHexes) {
            setBrush(Brush.ObjectiveHexes(index))
        }
        return index
    }

    fun removeObjective(index: Int) {
        mutate { def ->
            if (index !in def.level.objectives.indices) return@mutate null
            def.copy(
                level = def.level.copy(
                    objectives = def.level.objectives.filterIndexed { i, _ -> i != index },
                ),
            )
        }
        // Objective indices shifted; a stale hex brush would edit the wrong goal.
        if (_ui.value.brush is Brush.ObjectiveHexes) setBrush(Brush.Land)
    }

    /** The only failure the editor authors in v1; null clears it. */
    fun setTurnLimit(rounds: Int?) {
        mutate { def ->
            val failures = listOfNotNull(rounds?.takeIf { it > 0 }?.let { FailCondition.TurnLimit(it) })
            if (failures == def.level.failures) null
            else def.copy(level = def.level.copy(failures = failures))
        }
    }

    // ----- persistence & preview -----

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

    /** The hexes of the goal the [Brush.ObjectiveHexes] brush is editing, for highlights. */
    fun objectiveHexes(index: Int): Set<Hex> =
        when (val o = _ui.value.def.level.objectives.getOrNull(index)) {
            is Objective.CaptureHexes -> o.hexes.toSet()
            is Objective.HoldHexes -> o.hexes.toSet()
            else -> emptySet()
        }

    // ----- internals -----

    private fun mutate(transform: (CustomMapDef) -> CustomMapDef?) {
        val current = _ui.value.def
        val next = transform(current) ?: return
        undoStack.addLast(current)
        while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        commit(next)
    }

    private fun commit(def: CustomMapDef) {
        _ui.value = _ui.value.copy(
            def = def,
            canUndo = undoStack.isNotEmpty(),
            dirty = true,
            violations = CustomMapValidator.validate(def),
        )
    }

    private fun applyBrush(def: CustomMapDef, brush: Brush, seat: Int, hex: Hex): CustomMapDef? {
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
                    // Sea -> land drops sea-only features (a fish shoal cannot beach,
                    // and a boat cannot stand in a meadow).
                    withTiles(replaced(TileDef(hex = hex))).withoutUnitAt(hex)
                }

            Brush.Sea ->
                if (existing?.terrain == Terrain.SEA) {
                    null
                } else {
                    // The sea contract by construction: water is neutral and bare. A
                    // dangling capitals entry is fine — that is what drafts are for.
                    withTiles(replaced(TileDef(hex = hex, terrain = Terrain.SEA)))
                        .withoutUnitAt(hex)
                }

            Brush.Erase ->
                if (existing == null) {
                    null
                } else {
                    withTiles(map.tiles.filter { it.hex != hex }).withoutUnitAt(hex)
                }

            Brush.ClearProps -> {
                if (existing == null) return null
                val keepBuilding = existing.building?.takeIf { it == Building.CAPITAL }
                val cleared = existing.copy(building = keepBuilding, flora = null, deposit = null)
                val hasUnit = level.startingUnits.any { it.hex == hex }
                if (cleared == existing && !hasUnit) return null
                withTiles(replaced(cleared)).withoutUnitAt(hex)
            }

            Brush.Owner -> {
                if (existing == null || existing.terrain != Terrain.LAND) return null
                if (seat !in level.seats.indices) return null
                if (existing.owner == seat) return null
                withTiles(replaced(existing.copy(owner = seat)))
            }

            Brush.Capital -> applyCapital(def, seat, hex, existing)

            is Brush.Structure -> {
                if (existing == null || existing.terrain != Terrain.LAND) return null
                if (existing.owner == null) return null // campaign rule: no neutral buildings
                if (existing.building == Building.CAPITAL) return null // moved by its own tool
                if (existing.building == brush.building) return null
                withTiles(replaced(existing.copy(building = brush.building)))
            }

            is Brush.Plant -> {
                if (existing == null || existing.terrain != Terrain.LAND) return null
                val flora = when (brush.kind) {
                    PlantKind.TREE -> Flora.Tree
                    PlantKind.GRAVE -> Flora.Gravestone(0)
                }
                if (existing.flora == flora) return null
                withTiles(replaced(existing.copy(flora = flora)))
            }

            is Brush.Resource -> {
                if (existing == null) return null
                val wantsSea = brush.deposit == Deposit.FISH_SHOAL
                if (wantsSea != (existing.terrain == Terrain.SEA)) return null
                if (existing.deposit == brush.deposit) return null
                withTiles(replaced(existing.copy(deposit = brush.deposit)))
            }

            is Brush.UnitBrush -> {
                if (existing == null) return null
                if (seat !in level.seats.indices) return null
                val naval = Rules.isNaval(brush.type)
                val grounded =
                    if (naval) existing.terrain == Terrain.SEA
                    else existing.terrain == Terrain.LAND && existing.owner == seat
                if (!grounded) return null
                val placement = UnitPlacement(seat = seat, hex = hex, type = brush.type, tier = brush.tier)
                if (placement in level.startingUnits) return null
                def.copy(
                    level = level.copy(
                        startingUnits = level.startingUnits.filter { it.hex != hex } + placement,
                    ),
                )
            }

            is Brush.ObjectiveHexes -> {
                if (existing == null) return null
                val objectives = level.objectives
                val target = objectives.getOrNull(brush.index) ?: return null
                val updated = when (target) {
                    is Objective.CaptureHexes -> target.copy(hexes = target.hexes.toggled(hex))
                    is Objective.HoldHexes -> target.copy(hexes = target.hexes.toggled(hex))
                    else -> return null
                }
                def.copy(
                    level = level.copy(
                        objectives = objectives.mapIndexed { i, o -> if (i == brush.index) updated else o },
                    ),
                )
            }
        }
    }

    private fun applyCapital(def: CustomMapDef, seat: Int, hex: Hex, existing: TileDef?): CustomMapDef? {
        val level = def.level
        val map = level.map
        if (existing == null || existing.terrain != Terrain.LAND) return null
        if (seat > level.seats.size || level.seats.size >= MAX_SEATS && seat == level.seats.size) return null
        if (map.capitals.getOrNull(seat) == hex) return null

        val growing = seat == level.seats.size
        var tiles = map.tiles
        if (!growing) {
            // Moving a capital clears the old marker, if it still stands.
            val old = map.capitals[seat]
            tiles = tiles.map { tile ->
                if (tile.hex == old && tile.building == Building.CAPITAL) tile.copy(building = null) else tile
            }
        }
        tiles = tiles.filter { it.hex != hex } +
            existing.copy(owner = seat, building = Building.CAPITAL)

        val capitals =
            if (growing) map.capitals + hex
            else map.capitals.mapIndexed { i, c -> if (i == seat) hex else c }
        val seats = if (growing) level.seats + SeatDef.Ai(Difficulty.NORMAL) else level.seats
        val treasury = level.startingTreasury?.let { purses ->
            if (growing) purses + level.rules.startingTreasury else purses
        }
        return def.copy(
            level = level.copy(
                map = map.copy(tiles = tiles.sortedBy { it.hex.packed }, capitals = capitals),
                seats = seats,
                startingTreasury = treasury,
            ),
        )
    }

    private fun CustomMapDef.withoutUnitAt(hex: Hex): CustomMapDef =
        if (level.startingUnits.none { it.hex == hex }) {
            this
        } else {
            copy(level = level.copy(startingUnits = level.startingUnits.filter { it.hex != hex }))
        }

    private fun List<Hex>.toggled(hex: Hex): List<Hex> =
        if (hex in this) filter { it != hex } else this + hex

    companion object {
        /** Snapshots are cheap (immutable, structurally shared) — 50 strokes is plenty. */
        private const val UNDO_LIMIT = 50

        /** MapParams allows 2..6 players; the editor honors the same ceiling. */
        const val MAX_SEATS = 6
    }
}
