package com.msa.fightandconquer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.msa.fightandconquer.core.ai.AiPlayer
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEngine
import com.msa.fightandconquer.core.engine.GameEvent
import com.msa.fightandconquer.core.engine.LegalityResult
import com.msa.fightandconquer.core.engine.PurchaseOption
import com.msa.fightandconquer.core.engine.ReachResult
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.map.MapGenerator
import com.msa.fightandconquer.core.map.MapParams
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.GameUnit
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.UnitId
import com.msa.fightandconquer.core.persist.SaveCodec
import com.msa.fightandconquer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

/** Defense numbers shown on frontier hexes while a unit is selected. */
enum class LabelKind { CAPTURABLE, BLOCKED }
data class OverlayLabel(val hex: Hex, val defense: Int, val kind: LabelKind)

/** Coin counter breakdown panel. */
data class TierUpkeep(val tier: Int, val count: Int, val each: Int, val total: Int)
data class EconomyBreakdown(
    val hexCount: Int,
    val hexIncome: Int,
    val hexIncomePerHex: Int,
    val farmCount: Int,
    val farmIncome: Int,
    val farmIncomePerFarm: Int,
    val tiers: List<TierUpkeep>,
    val income: Int,
    val upkeep: Int,
    val net: Int,
    val treasury: Int,
    val projected: Int,
    val starvingCount: Int,
    val bankruptcyImminent: Boolean,
    val upkeepRisk: Boolean,
)

/** Transient top-center notifications. */
enum class ToastKind { INFO, WARNING, ALERT }
data class HudToast(val id: Long, val text: UiText, val kind: ToastKind)

/** World-anchored floating text (e.g. +3 on a tree clear). */
data class CoinPopup(val id: Long, val hex: Hex, val text: UiText)

/** Bottom card describing a tapped piece that isn't selectable. */
data class InfoStat(val label: UiText, val value: UiText)
data class InfoCard(
    val title: UiText,
    val subtitle: UiText,
    val stats: List<InfoStat> = emptyList(),
    val factionIndex: Int? = null,
)

/** Rules snapshot the purchase tray needs for upkeep/defense lines. */
data class ShopInfo(
    val unitUpkeep: List<Int> = listOf(2, 6, 18, 54),
    val towerDefense: Int = 2,
    val strongTowerDefense: Int = 3,
    val farmIncome: Int = 4,
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
    val freshUnitCount: Int,
    val shopInfo: ShopInfo,
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

    private val _overlayLabels = MutableStateFlow<List<OverlayLabel>>(emptyList())
    val overlayLabels: StateFlow<List<OverlayLabel>> = _overlayLabels.asStateFlow()

    private val _economy = MutableStateFlow<EconomyBreakdown?>(null)
    val economy: StateFlow<EconomyBreakdown?> = _economy.asStateFlow()

    private val _toasts = MutableStateFlow<List<HudToast>>(emptyList())
    val toasts: StateFlow<List<HudToast>> = _toasts.asStateFlow()

    private val _popups = MutableStateFlow<List<CoinPopup>>(emptyList())
    val popups: StateFlow<List<CoinPopup>> = _popups.asStateFlow()

    private val _infoCard = MutableStateFlow<InfoCard?>(null)
    val infoCard: StateFlow<InfoCard?> = _infoCard.asStateFlow()

    /** One-shot camera glide requests (units-left helper). */
    private val _cameraJumps = MutableSharedFlow<Hex>(extraBufferCapacity = 4)
    val cameraJumps: SharedFlow<Hex> = _cameraJumps.asSharedFlow()

    /** Bumped when the board must resync without animation (undo, load). */
    private val _resync = MutableStateFlow(0)
    val resync: StateFlow<Int> = _resync.asStateFlow()

    private var selectedUnit: UnitId? = null
    private var selectedHex: Hex? = null
    private var banner: Int? = null
    private var aiJob: Job? = null
    private var eventsJob: Job? = null
    private var aiThinking = false
    private var nextToastId = 0L
    private var freshUnitCursor = 0

    // Event-feedback accumulators (reset per human round).
    private var aiCapturedFromHumans = 0
    private var cutOffWarned = false
    private var knownStarving: Set<Hex> = emptySet()

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
        eventsJob?.cancel()
        engine = null
        _hud.value = null
        _highlights.value = HighlightSet()
        _overlayLabels.value = emptyList()
        _economy.value = null
        _infoCard.value = null
        _toasts.value = emptyList()
        _popups.value = emptyList()
        selectedUnit = null; selectedHex = null; banner = null
        _screen.value = Screen.Menu(autosaveFile.exists())
    }

    private fun startEngine(newEngine: GameEngine, showOpeningBanner: Boolean) {
        aiJob?.cancel()
        eventsJob?.cancel()
        engine = newEngine
        selectedUnit = null; selectedHex = null
        banner = if (showOpeningBanner) 0 else null
        freshUnitCursor = 0
        aiCapturedFromHumans = 0
        cutOffWarned = false
        knownStarving = currentHumanStarving(newEngine.state.value)
        _toasts.value = emptyList()
        _popups.value = emptyList()
        eventsJob = viewModelScope.launch { newEngine.events.collect(::onEngineEvent) }
        _screen.value = Screen.Game
        refreshHud()
        maybeRunAi()
    }

    // ----- interaction -----

    fun onHexTapped(hex: Hex) {
        val engine = engine ?: return
        val hudNow = _hud.value ?: return
        _economy.value = null // board taps dismiss the glanceable panel
        if (banner != null || !hudNow.currentIsHuman || hudNow.winner != null) return
        val state = engine.state.value

        val heldUnit = selectedUnit
        if (heldUnit != null && state.units.containsKey(heldUnit)) {
            val reach = engine.reachableFor(heldUnit)
            when (hex) {
                in reach.moveTargets, in reach.captureTargets -> {
                    submit(GameAction.MoveUnit(heldUnit, hex))
                    clearSelection()
                    refreshHud()
                    return
                }
                in reach.mergeTargets -> {
                    submit(GameAction.MergeUnits(heldUnit, state.tiles.getValue(hex).unit!!))
                    clearSelection()
                    refreshHud()
                    return
                }
            }
        }
        select(hex)
    }

    /** Board tap that missed the board entirely (the void): cancel any selection. */
    fun cancelSelection() {
        clearSelection()
        refreshHud()
    }

    private fun select(hex: Hex) {
        val engine = engine ?: return
        val state = engine.state.value
        val tile = state.tiles[hex]
        val me = state.currentPlayer
        selectedUnit = null
        selectedHex = null
        _infoCard.value = null

        if (tile?.owner == me) {
            val unit = tile.unit?.let { state.units[it] }
            if (unit != null && !unit.spent) {
                selectedUnit = unit.id
                selectedHex = hex
                val reach = engine.reachableFor(unit.id)
                _highlights.value = HighlightSet(hex, reach.moveTargets, reach.captureTargets, reach.mergeTargets)
                _overlayLabels.value = computeOverlay(state, unit, reach)
                refreshHud()
                return
            }
            if (!tile.starving && tile.building == null && tile.unit == null) {
                selectedHex = hex
                _highlights.value = HighlightSet(selected = hex)
                _overlayLabels.value = emptyList()
                refreshHud()
                return
            }
        }
        // Not selectable: explain what was tapped instead.
        _infoCard.value = tile?.let { infoCardFor(state, hex, it) }
        _highlights.value = HighlightSet()
        _overlayLabels.value = emptyList()
        refreshHud()
    }

    private fun clearSelection() {
        selectedUnit = null
        selectedHex = null
        _highlights.value = HighlightSet()
        _overlayLabels.value = emptyList()
        _infoCard.value = null
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
        _economy.value = null
        freshUnitCursor = 0
        submit(GameAction.EndTurn)
        autosave()
        val state = engine.state.value
        if (state.phase is GamePhase.Playing) {
            val next = state.player(state.currentPlayer)
            if (next.kind is PlayerKind.Human && anyOtherHuman()) {
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
            _economy.value?.let { _economy.value = computeEconomy() }
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
        val result = engine?.submit(action)
            ?: LegalityResult.Rejected(com.msa.fightandconquer.core.engine.RejectionReason.NO_GAME)
        refreshHud()
        return result
    }

    // ----- threat overlay -----

    private fun computeOverlay(state: GameState, unit: GameUnit, reach: ReachResult): List<OverlayLabel> {
        val region = Rules.region(state, unit.hex)
        val frontier = HashSet<Hex>()
        for (h in region) {
            HexMath.forEachNeighbor(h) { n ->
                val t = state.tiles[n]
                if (t != null && t.owner != unit.owner) frontier.add(n)
            }
        }
        return frontier.mapNotNull { hex ->
            val defense = Rules.defenseOf(state, hex)
            val capturable = hex in reach.captureTargets
            when {
                capturable && defense == 0 -> null // undefended: the highlight disc already says it
                else -> OverlayLabel(hex, defense, if (capturable) LabelKind.CAPTURABLE else LabelKind.BLOCKED)
            }
        }
    }

    // ----- economy panel -----

    fun toggleEconomyPanel() {
        _economy.value = if (_economy.value == null) computeEconomy() else null
    }

    private fun computeEconomy(): EconomyBreakdown? {
        val state = engine?.state?.value ?: return null
        val me = state.currentPlayer
        val rules = state.config.rules
        var hexCount = 0
        var farmCount = 0
        var starving = 0
        for (tile in state.tiles.values) {
            if (tile.owner != me) continue
            if (tile.starving) { starving++; continue }
            if (tile.flora != null) continue
            hexCount++
            if (tile.building == Building.FARM) farmCount++
        }
        val tiers = (1..rules.maxTier).mapNotNull { tier ->
            val count = state.units.values.count { it.owner == me && it.tier == tier }
            if (count == 0) {
                null
            } else {
                TierUpkeep(tier, count, rules.unitUpkeep[tier - 1], count * rules.unitUpkeep[tier - 1])
            }
        }
        val income = Rules.incomeOf(state, me)
        val upkeep = Rules.upkeepOf(state, me)
        val treasury = state.player(me).treasury
        val net = income - upkeep
        val projected = treasury + net
        return EconomyBreakdown(
            hexCount = hexCount,
            hexIncome = hexCount * rules.hexIncome,
            hexIncomePerHex = rules.hexIncome,
            farmCount = farmCount,
            farmIncome = farmCount * rules.farmIncome,
            farmIncomePerFarm = rules.farmIncome,
            tiers = tiers,
            income = income,
            upkeep = upkeep,
            net = net,
            treasury = treasury,
            projected = projected,
            starvingCount = starving,
            bankruptcyImminent = projected < 0,
            upkeepRisk = projected >= 0 && projected < upkeep,
        )
    }

    // ----- units-left helper -----

    fun focusNextFreshUnit() {
        val state = engine?.state?.value ?: return
        val fresh = state.units.values
            .filter { it.owner == state.currentPlayer && !it.spent }
            .sortedBy { it.id.value }
        if (fresh.isEmpty()) return
        val unit = fresh[freshUnitCursor++ % fresh.size]
        select(unit.hex) // internal select: never submits an action
        _cameraJumps.tryEmit(unit.hex)
    }

    // ----- event feedback -----

    private fun onEngineEvent(event: GameEvent) {
        val engine = engine ?: return
        val state = engine.state.value
        val actorIsHuman = !state.player(state.currentPlayer).eliminated &&
            state.player(state.currentPlayer).kind is PlayerKind.Human

        when (event) {
            is GameEvent.TreeCleared -> {
                if (actorIsHuman) pushPopup(event.hex, UiText.of(R.string.popup_coins, event.bonus))
            }

            is GameEvent.CapitalMoved -> {
                if (event.loot > 0) {
                    if (actorIsHuman) {
                        pushToast(UiText.of(R.string.toast_looted, event.loot), ToastKind.INFO)
                    }
                    if (state.players[event.player.value].kind is PlayerKind.Human) {
                        pushToast(UiText.of(R.string.toast_capital_looted, event.loot), ToastKind.WARNING)
                    }
                }
            }

            is GameEvent.HexCaptured -> {
                val oldOwnerHuman = event.oldOwner?.let { state.players[it.value].kind is PlayerKind.Human } == true
                if (!actorIsHuman && oldOwnerHuman) aiCapturedFromHumans++
                if (oldOwnerHuman) {
                    val nowStarving = currentHumanStarving(state)
                    if ((nowStarving - knownStarving).isNotEmpty() && !cutOffWarned) {
                        pushToast(UiText.of(R.string.toast_territory_cut_off), ToastKind.WARNING)
                        cutOffWarned = true
                    }
                    knownStarving = nowStarving
                }
            }

            is GameEvent.TurnStarted -> {
                if (state.players[event.player.value].kind is PlayerKind.Human) {
                    if (aiCapturedFromHumans > 0) {
                        pushToast(
                            UiText.plural(R.plurals.toast_ai_captured, aiCapturedFromHumans, aiCapturedFromHumans),
                            ToastKind.WARNING,
                        )
                    }
                    aiCapturedFromHumans = 0
                    cutOffWarned = false
                    knownStarving = currentHumanStarving(state)
                }
            }

            is GameEvent.Bankruptcy -> {
                if (state.players[event.player.value].kind is PlayerKind.Human) {
                    pushToast(UiText.of(R.string.toast_bankruptcy), ToastKind.ALERT)
                }
            }

            is GameEvent.ActionRejected -> {
                if (actorIsHuman) pushToast(event.reason.toUiText(event.amount), ToastKind.INFO)
            }

            else -> Unit
        }
    }

    private fun currentHumanStarving(state: GameState): Set<Hex> =
        state.tiles.filterValues { tile ->
            tile.starving && tile.owner?.let { state.players[it.value].kind is PlayerKind.Human } == true
        }.keys

    private fun pushToast(text: UiText, kind: ToastKind) {
        val toast = HudToast(nextToastId++, text, kind)
        _toasts.value = (_toasts.value + toast).takeLast(3)
        viewModelScope.launch {
            delay(2500)
            _toasts.value = _toasts.value.filterNot { it.id == toast.id }
        }
    }

    private fun pushPopup(hex: Hex, text: UiText) {
        val popup = CoinPopup(nextToastId++, hex, text)
        _popups.value = _popups.value + popup
        viewModelScope.launch {
            delay(1200)
            _popups.value = _popups.value.filterNot { it.id == popup.id }
        }
    }

    // ----- info cards -----

    private fun infoCardFor(state: GameState, hex: Hex, tile: com.msa.fightandconquer.core.model.Tile): InfoCard? {
        val rules = state.config.rules
        val me = state.currentPlayer
        val unit = tile.unit?.let { state.units[it] }
        if (unit != null) {
            val own = unit.owner == me
            return InfoCard(
                title = UiText.of(unitNameRes(unit.tier)),
                subtitle = if (own) {
                    UiText.of(R.string.info_unit_spent)
                } else {
                    UiText.of(R.string.info_unit_enemy, unit.tier)
                },
                stats = listOf(
                    InfoStat(UiText.of(R.string.info_stat_strength), UiText.of(R.string.info_value_plain, unit.tier)),
                    InfoStat(
                        UiText.of(R.string.info_stat_upkeep),
                        UiText.of(R.string.info_value_per_turn, rules.unitUpkeep[unit.tier - 1]),
                    ),
                ),
                factionIndex = unit.owner.value,
            )
        }
        tile.building?.let { building ->
            val ownerIndex = tile.owner?.value
            return when (building) {
                Building.CAPITAL -> InfoCard(
                    UiText.of(R.string.building_capital),
                    UiText.of(R.string.info_capital, rules.capitalLootPercent),
                    listOf(
                        InfoStat(
                            UiText.of(R.string.info_stat_defense),
                            UiText.of(R.string.info_value_defense_area, rules.capitalDefense),
                        ),
                    ),
                    ownerIndex,
                )
                Building.TOWER -> InfoCard(
                    UiText.of(R.string.building_tower),
                    UiText.of(R.string.info_tower),
                    listOf(
                        InfoStat(
                            UiText.of(R.string.info_stat_defense),
                            UiText.of(R.string.info_value_plain, rules.towerDefense),
                        ),
                    ),
                    ownerIndex,
                )
                Building.STRONG_TOWER -> InfoCard(
                    UiText.of(R.string.building_castle),
                    UiText.of(R.string.info_castle),
                    listOf(
                        InfoStat(
                            UiText.of(R.string.info_stat_defense),
                            UiText.of(R.string.info_value_plain, rules.strongTowerDefense),
                        ),
                    ),
                    ownerIndex,
                )
                Building.FARM -> InfoCard(
                    UiText.of(R.string.building_farm),
                    UiText.of(R.string.info_farm),
                    listOf(
                        InfoStat(
                            UiText.of(R.string.info_stat_income),
                            UiText.of(R.string.info_value_income, rules.farmIncome),
                        ),
                    ),
                    ownerIndex,
                )
            }
        }
        when (tile.flora) {
            is Flora.Tree -> return InfoCard(
                UiText.of(R.string.piece_tree),
                UiText.of(R.string.info_tree),
                listOf(
                    InfoStat(
                        UiText.of(R.string.info_stat_clear_bonus),
                        UiText.of(R.string.info_value_coins, rules.treeClearBonus),
                    ),
                ),
            )
            is Flora.Gravestone -> return InfoCard(
                UiText.of(R.string.piece_gravestone),
                UiText.of(R.string.info_gravestone),
            )
            null -> {}
        }
        if (tile.owner == me && tile.starving) {
            return InfoCard(
                UiText.of(R.string.tile_cut_off),
                UiText.of(R.string.info_cut_off),
                factionIndex = me.value,
            )
        }
        return null
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
        val rules = state.config.rules
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
            freshUnitCount = state.units.values.count { it.owner == me && !it.spent },
            shopInfo = ShopInfo(
                unitUpkeep = rules.unitUpkeep,
                towerDefense = rules.towerDefense,
                strongTowerDefense = rules.strongTowerDefense,
                farmIncome = rules.farmIncome,
            ),
        )
        // Live panel tracks every buy/move/undo.
        if (_economy.value != null) _economy.value = computeEconomy()
        // Keep highlights in sync with spent/moved units.
        if (selectedUnit?.let { !state.units.containsKey(it) } == true) clearSelection()
    }

}
