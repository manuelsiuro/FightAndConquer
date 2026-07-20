package com.msa.fightandconquer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.msa.fightandconquer.core.ai.AiPlayer
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEngine
import com.msa.fightandconquer.core.engine.LegalityResult
import com.msa.fightandconquer.core.engine.PurchaseOption
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.map.MapGenerator
import com.msa.fightandconquer.core.map.MapParams
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.UnitId
import com.msa.fightandconquer.core.persist.SaveCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class GameMode { VS_AI, PASS_AND_PLAY }

data class GameSetup(
    val playerCount: Int = 2,
    val mode: GameMode = GameMode.VS_AI,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val size: MapSize = MapSize.MEDIUM,
    val seed: Long = System.currentTimeMillis(),
)

data class HighlightSet(
    val selected: Hex? = null,
    val moves: Set<Hex> = emptySet(),
    val captures: Set<Hex> = emptySet(),
    val merges: Set<Hex> = emptySet(),
)

data class HudState(
    val playerCount: Int,
    val currentPlayer: Int,
    val currentIsHuman: Boolean,
    val aiThinking: Boolean,
    val treasury: Int,
    val income: Int,
    val upkeep: Int,
    val turnNumber: Int,
    val selectedUnitTier: Int?,
    val purchases: List<PurchaseOption>,
    val canUndo: Boolean,
    /** Pass-and-play: seat waiting behind the privacy banner; null = play freely. */
    val banner: Int?,
    val winner: Int?,
    val eliminated: List<Boolean>,
)

sealed interface Screen {
    data class Menu(val hasAutosave: Boolean, val generating: Boolean = false) : Screen
    data object Game : Screen
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val autosaveFile = File(application.filesDir, "autosave.json")

    private val _screen = MutableStateFlow<Screen>(Screen.Menu(autosaveFile.exists()))
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    var engine: GameEngine? = null
        private set

    private val _hud = MutableStateFlow<HudState?>(null)
    val hud: StateFlow<HudState?> = _hud.asStateFlow()

    private val _highlights = MutableStateFlow(HighlightSet())
    val highlights: StateFlow<HighlightSet> = _highlights.asStateFlow()

    /** Bumped when the board must resync without animation (undo, load). */
    private val _resync = MutableStateFlow(0)
    val resync: StateFlow<Int> = _resync.asStateFlow()

    private var selectedUnit: UnitId? = null
    private var selectedHex: Hex? = null
    private var banner: Int? = null
    private var aiJob: Job? = null
    private var aiThinking = false

    // ----- menu -----

    fun newGame(setup: GameSetup) {
        _screen.value = Screen.Menu(autosaveFile.exists(), generating = true)
        viewModelScope.launch(Dispatchers.Default) {
            val map = MapGenerator.generate(
                MapParams(seed = setup.seed, size = setup.size, playerCount = setup.playerCount),
            )
            val kinds = List(setup.playerCount) { index ->
                when {
                    setup.mode == GameMode.PASS_AND_PLAY -> PlayerKind.Human
                    index == 0 -> PlayerKind.Human
                    else -> PlayerKind.Ai(setup.difficulty)
                }
            }
            val state = map.newGame(gameSeed = setup.seed * 31 + 17, kinds = kinds)
            withContext(Dispatchers.Main.immediate) {
                startEngine(GameEngine(state), showOpeningBanner = setup.mode == GameMode.PASS_AND_PLAY)
            }
        }
    }

    fun continueGame() {
        viewModelScope.launch(Dispatchers.IO) {
            val save = runCatching { SaveCodec.decode(autosaveFile.readText()) }.getOrNull()
            withContext(Dispatchers.Main.immediate) {
                if (save == null) {
                    _screen.value = Screen.Menu(hasAutosave = false)
                } else {
                    startEngine(GameEngine.fromSave(save), showOpeningBanner = false)
                }
            }
        }
    }

    fun backToMenu() {
        aiJob?.cancel()
        engine = null
        _hud.value = null
        _highlights.value = HighlightSet()
        selectedUnit = null; selectedHex = null; banner = null
        _screen.value = Screen.Menu(autosaveFile.exists())
    }

    private fun startEngine(newEngine: GameEngine, showOpeningBanner: Boolean) {
        aiJob?.cancel()
        engine = newEngine
        selectedUnit = null; selectedHex = null
        banner = if (showOpeningBanner) 0 else null
        _screen.value = Screen.Game
        refreshHud()
        maybeRunAi()
    }

    // ----- interaction -----

    fun onHexTapped(hex: Hex) {
        val engine = engine ?: return
        val hudNow = _hud.value ?: return
        if (banner != null || !hudNow.currentIsHuman || hudNow.winner != null) return
        val state = engine.state.value

        val heldUnit = selectedUnit
        if (heldUnit != null && state.units.containsKey(heldUnit)) {
            val reach = engine.reachableFor(heldUnit)
            when (hex) {
                in reach.moveTargets, in reach.captureTargets -> {
                    submit(GameAction.MoveUnit(heldUnit, hex))
                    clearSelection()
                    return
                }
                in reach.mergeTargets -> {
                    submit(GameAction.MergeUnits(heldUnit, state.tiles.getValue(hex).unit!!))
                    clearSelection()
                    return
                }
            }
        }
        select(hex)
    }

    private fun select(hex: Hex) {
        val engine = engine ?: return
        val state = engine.state.value
        val tile = state.tiles[hex]
        val me = state.currentPlayer
        selectedUnit = null
        selectedHex = null

        if (tile?.owner == me) {
            val unit = tile.unit?.let { state.units[it] }
            if (unit != null && !unit.spent) {
                selectedUnit = unit.id
                selectedHex = hex
                val reach = engine.reachableFor(unit.id)
                _highlights.value = HighlightSet(hex, reach.moveTargets, reach.captureTargets, reach.mergeTargets)
                refreshHud()
                return
            }
            if (!tile.starving && tile.building == null) {
                selectedHex = hex
                _highlights.value = HighlightSet(selected = hex)
                refreshHud()
                return
            }
        }
        _highlights.value = HighlightSet()
        refreshHud()
    }

    private fun clearSelection() {
        selectedUnit = null
        selectedHex = null
        _highlights.value = HighlightSet()
    }

    fun buy(option: PurchaseOption) {
        val hex = selectedHex ?: return
        when (option) {
            is PurchaseOption.Unit -> submit(GameAction.BuyUnit(option.tier, hex))
            is PurchaseOption.Structure -> submit(GameAction.BuyBuilding(option.type, hex))
        }
        clearSelection()
        refreshHud()
    }

    fun endTurn() {
        val engine = engine ?: return
        clearSelection()
        submit(GameAction.EndTurn)
        autosave()
        val state = engine.state.value
        if (state.phase is GamePhase.Playing) {
            val next = state.player(state.currentPlayer)
            if (next.kind is PlayerKind.Human && _hud.value?.let { it.playerCount > 1 } == true &&
                anyOtherHuman()
            ) {
                banner = state.currentPlayer.value
            }
        }
        refreshHud()
        maybeRunAi()
    }

    private fun anyOtherHuman(): Boolean {
        val state = engine?.state?.value ?: return false
        return state.players.count { it.kind is PlayerKind.Human } > 1
    }

    fun beginTurn() {
        banner = null
        refreshHud()
    }

    fun undo() {
        val engine = engine ?: return
        if (engine.undo()) {
            clearSelection()
            _resync.value++
            refreshHud()
        }
    }

    fun surrender() {
        submit(GameAction.Surrender)
        autosave()
        refreshHud()
        maybeRunAi()
    }

    private fun submit(action: GameAction): LegalityResult {
        val result = engine?.submit(action) ?: LegalityResult.Rejected("no game")
        refreshHud()
        return result
    }

    // ----- AI -----

    private fun maybeRunAi() {
        val engine = engine ?: return
        val state = engine.state.value
        if (state.phase !is GamePhase.Playing) return
        val kind = state.player(state.currentPlayer).kind
        if (kind !is PlayerKind.Ai || aiJob?.isActive == true) return

        aiThinking = true
        refreshHud()
        aiJob = viewModelScope.launch(Dispatchers.Default) {
            var guard = 0
            while (isActive) {
                val current = engine.state.value
                if (current.phase !is GamePhase.Playing) break
                val currentKind = current.player(current.currentPlayer).kind as? PlayerKind.Ai ?: break
                val action = AiPlayer(currentKind.difficulty).chooseAction(current)
                val turnEnds = action == GameAction.EndTurn || ++guard >= AiPlayer.MAX_ACTIONS_PER_TURN
                withContext(Dispatchers.Main.immediate) {
                    engine.submit(if (guard >= AiPlayer.MAX_ACTIONS_PER_TURN) GameAction.EndTurn else action)
                    refreshHud()
                }
                if (turnEnds) {
                    guard = 0
                    withContext(Dispatchers.Main.immediate) { autosave() }
                    val after = engine.state.value
                    if (after.phase !is GamePhase.Playing ||
                        after.player(after.currentPlayer).kind !is PlayerKind.Ai
                    ) {
                        break
                    }
                }
                delay(220) // pacing so board animations roughly keep up
            }
            withContext(Dispatchers.Main.immediate) {
                aiThinking = false
                refreshHud()
            }
        }
    }

    // ----- persistence -----

    private fun autosave() {
        val engine = engine ?: return
        val state = engine.state.value
        if (state.phase is GamePhase.Finished) {
            viewModelScope.launch(Dispatchers.IO) { autosaveFile.delete() }
            return
        }
        val save = engine.toSave()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { autosaveFile.writeText(SaveCodec.encode(save)) }
        }
    }

    /** Called from onStop so a mid-turn kill resumes exactly where it was. */
    fun persistNow() {
        val engine = engine ?: return
        if (engine.state.value.phase is GamePhase.Finished) return
        runCatching { autosaveFile.writeText(SaveCodec.encode(engine.toSave())) }
    }

    // ----- HUD -----

    private fun refreshHud() {
        val engine = engine ?: run { _hud.value = null; return }
        val state = engine.state.value
        val me = state.currentPlayer
        val summary = engine.incomeSummary(me)
        val selectedTier = selectedUnit?.let { state.units[it]?.tier }
        val purchases = if (selectedUnit == null) {
            selectedHex?.let { engine.buyableAt(it) } ?: emptyList()
        } else {
            emptyList()
        }
        _hud.value = HudState(
            playerCount = state.players.size,
            currentPlayer = me.value,
            currentIsHuman = state.player(me).kind is PlayerKind.Human,
            aiThinking = aiThinking,
            treasury = summary.treasury,
            income = summary.income,
            upkeep = summary.upkeep,
            turnNumber = state.turnNumber,
            selectedUnitTier = selectedTier,
            purchases = purchases,
            canUndo = engine.canUndo(),
            banner = banner,
            winner = (state.phase as? GamePhase.Finished)?.winner?.value,
            eliminated = state.players.map { it.eliminated },
        )
        // Keep highlights in sync with spent/moved units.
        if (selectedUnit?.let { !state.units.containsKey(it) } == true) clearSelection()
    }

    companion object {
        /** Upkeep per tier for the HUD (mirrors RuleConstants defaults). */
        fun unitName(tier: Int): String = when (tier) {
            1 -> "Peasant"; 2 -> "Spearman"; 3 -> "Baron"; else -> "Knight"
        }
    }
}
