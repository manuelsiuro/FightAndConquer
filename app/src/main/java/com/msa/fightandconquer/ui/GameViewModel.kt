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
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.RuleConstants
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
    val fogOfWar: Boolean = false,
)

/** Fog of war render sets for the viewing seat; null everywhere means fog is off. */
data class BoardVisibility(
    val visible: Set<Hex>,
    val explored: Set<Hex>,
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
data class UpkeepRow(val nameRes: Int, val count: Int, val each: Int, val total: Int)

/** One income line per building type (farms, mines, markets, lumber camps). */
data class IncomeRow(val nameRes: Int, val count: Int, val total: Int)

data class EconomyBreakdown(
    val hexCount: Int,
    val hexIncome: Int,
    val hexIncomePerHex: Int,
    /** Extra income from FERTILE ground (bare hexes; farm bonuses ride the farm row). */
    val depositBonus: Int,
    val buildingRows: List<IncomeRow>,
    val tiers: List<UpkeepRow>,
    val income: Int,
    val upkeep: Int,
    val net: Int,
    val treasury: Int,
    val projected: Int,
    val starvingCount: Int,
    val bankruptcyImminent: Boolean,
    val upkeepRisk: Boolean,
)

/** Diplomacy panel: one row per opponent. */
enum class PactUiState { WAR, PACT, PROPOSAL_SENT, PROPOSAL_RECEIVED }
data class PactStatus(
    val playerIndex: Int,
    val isHuman: Boolean,
    val eliminated: Boolean,
    val state: PactUiState,
    /** Rounds left on the active pact (PACT only). */
    val turnsRemaining: Int?,
)
data class DiplomacyPanelState(
    val rows: List<PactStatus>,
    val tributeChoices: List<Int>,
    val pactDurationRounds: Int,
    val breakPenaltyPercent: Int,
    val treasury: Int,
)

/** A pending pact offer awaiting the current human's answer. */
data class IncomingProposal(val fromIndex: Int, val durationRounds: Int)

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
    val mineIncome: Int = 6,
    val marketIncomeMax: Int = 5,
    val lumberCampIncomeMax: Int = 8,
    val watchtowerVision: Int = 6,
    val archerUpkeep: Int = 4,
    val catapultUpkeep: Int = 10,
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
    /** Display-name resource of the selected unit (type-aware), null when none. */
    val selectedUnitNameRes: Int?,
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

    private val _diplomacy = MutableStateFlow<DiplomacyPanelState?>(null)
    val diplomacy: StateFlow<DiplomacyPanelState?> = _diplomacy.asStateFlow()

    private val _incomingProposals = MutableStateFlow<List<IncomingProposal>>(emptyList())
    val incomingProposals: StateFlow<List<IncomingProposal>> = _incomingProposals.asStateFlow()

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

    /** Fog of war sets for the viewing seat; null = fog off (or game over: fog lifts). */
    private val _visibility = MutableStateFlow<BoardVisibility?>(null)
    val visibility: StateFlow<BoardVisibility?> = _visibility.asStateFlow()

    private var selectedUnit: UnitId? = null
    private var selectedHex: Hex? = null
    private var banner: Int? = null
    /** Armed pact-break confirmation: capture of this partner hex proceeds on re-tap. */
    private var pendingPactBreak: Hex? = null
    /** Seat of the human who most recently played — the fog perspective during AI turns. */
    private var lastHumanSeat: Int? = null
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
            val state = map.newGame(
                gameSeed = setup.seed * 31 + 17,
                kinds = kinds,
                rules = RuleConstants(fogOfWar = setup.fogOfWar),
            )
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
        _diplomacy.value = null
        _incomingProposals.value = emptyList()
        _infoCard.value = null
        _toasts.value = emptyList()
        _popups.value = emptyList()
        selectedUnit = null; selectedHex = null; banner = null; pendingPactBreak = null
        lastHumanSeat = null
        _visibility.value = null
        _screen.value = Screen.Menu(autosaveFile.exists())
    }

    private fun startEngine(newEngine: GameEngine, showOpeningBanner: Boolean) {
        aiJob?.cancel()
        eventsJob?.cancel()
        engine = newEngine
        selectedUnit = null; selectedHex = null; pendingPactBreak = null
        _diplomacy.value = null
        lastHumanSeat = null
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
        _economy.value = null // board taps dismiss the glanceable panels
        _diplomacy.value = null
        if (banner != null || !hudNow.currentIsHuman || hudNow.winner != null) return
        val state = engine.state.value

        val heldUnit = selectedUnit
        if (heldUnit != null && state.units.containsKey(heldUnit)) {
            val reach = engine.reachableFor(heldUnit)
            when (hex) {
                in reach.moveTargets, in reach.captureTargets -> {
                    // Capturing a pact partner's hex breaks the pact — arm a
                    // second-tap confirmation instead of striking immediately.
                    val targetOwner = state.tiles[hex]?.owner
                    if (hex in reach.captureTargets && targetOwner != null &&
                        engine.pactBetween(state.currentPlayer, targetOwner) != null &&
                        pendingPactBreak != hex
                    ) {
                        pendingPactBreak = hex
                        val penalty = state.player(state.currentPlayer).treasury *
                            state.config.rules.pactBreakPenaltyPercent / 100
                        pushToast(UiText.of(R.string.toast_pact_break_confirm, penalty), ToastKind.WARNING)
                        return
                    }
                    pendingPactBreak = null
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
        // Not selectable: explain what was tapped instead. Fogged hexes never leak
        // their contents — explored memory gets a generic card, unseen land nothing.
        val vis = _visibility.value
        _infoCard.value = when {
            vis == null || hex in vis.visible -> tile?.let { infoCardFor(state, hex, it) }
            hex in vis.explored -> InfoCard(
                title = UiText.of(R.string.info_fog_title),
                subtitle = UiText.of(R.string.info_fog),
            )
            else -> null
        }
        _highlights.value = HighlightSet()
        _overlayLabels.value = emptyList()
        refreshHud()
    }

    private fun clearSelection() {
        selectedUnit = null
        selectedHex = null
        pendingPactBreak = null
        _highlights.value = HighlightSet()
        _overlayLabels.value = emptyList()
        _infoCard.value = null
    }

    fun buy(option: PurchaseOption) {
        val hex = selectedHex ?: return
        when (option) {
            is PurchaseOption.Unit -> submit(GameAction.BuyUnit(option.tier, hex, option.type))
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
        _diplomacy.value = null
        _economy.value = if (_economy.value == null) computeEconomy() else null
    }

    // ----- diplomacy -----

    fun toggleDiplomacyPanel() {
        _economy.value = null
        _diplomacy.value = if (_diplomacy.value == null) computeDiplomacy() else null
    }

    fun proposePact(playerIndex: Int) {
        val duration = _diplomacy.value?.pactDurationRounds ?: return
        submit(GameAction.ProposePact(PlayerId(playerIndex), duration))
    }

    fun acceptPact(fromIndex: Int) {
        submit(GameAction.RespondPact(PlayerId(fromIndex), accept = true))
    }

    fun declinePact(fromIndex: Int) {
        submit(GameAction.RespondPact(PlayerId(fromIndex), accept = false))
    }

    fun sendTribute(playerIndex: Int, amount: Int) {
        submit(GameAction.SendTribute(PlayerId(playerIndex), amount))
    }

    private fun computeDiplomacy(): DiplomacyPanelState? {
        val state = engine?.state?.value ?: return null
        val rules = state.config.rules
        if (!rules.diplomacyEnabled || state.phase !is GamePhase.Playing) return null
        val me = state.currentPlayer
        val d = state.diplomacy
        val rows = state.players.filter { it.id != me }.map { p ->
            val pact = d.pactBetween(me, p.id)
            PactStatus(
                playerIndex = p.id.value,
                isHuman = p.kind is PlayerKind.Human,
                eliminated = p.eliminated,
                state = when {
                    pact != null -> PactUiState.PACT
                    d.proposalBetween(me, p.id) != null -> PactUiState.PROPOSAL_SENT
                    d.proposalBetween(p.id, me) != null -> PactUiState.PROPOSAL_RECEIVED
                    else -> PactUiState.WAR
                },
                turnsRemaining = pact?.let { maxOf(0, it.expiresAtRound - state.turnNumber) },
            )
        }
        return DiplomacyPanelState(
            rows = rows,
            tributeChoices = listOf(10, 25, 50),
            pactDurationRounds = (rules.pactMinDurationRounds + rules.pactMaxDurationRounds) / 2,
            breakPenaltyPercent = rules.pactBreakPenaltyPercent,
            treasury = state.player(me).treasury,
        )
    }

    private fun computeEconomy(): EconomyBreakdown? {
        val state = engine?.state?.value ?: return null
        val me = state.currentPlayer
        val rules = state.config.rules
        var hexCount = 0
        var starving = 0
        var depositBonus = 0
        var farmCount = 0; var farmTotal = 0
        var mineCount = 0; var mineTotal = 0
        var marketCount = 0; var marketTotal = 0
        var campCount = 0; var campTotal = 0
        // Mirrors Rules.incomeFrom exactly so the panel rows always sum to `income`.
        for ((hex, tile) in state.tiles) {
            if (tile.owner != me) continue
            if (tile.starving) { starving++; continue }
            if (tile.flora != null) continue
            hexCount++
            val fertile = tile.deposit == com.msa.fightandconquer.core.model.Deposit.FERTILE
            if (fertile) depositBonus += rules.fertileHexBonus
            when (tile.building) {
                Building.FARM -> {
                    farmCount++
                    farmTotal += rules.farmIncome + (if (fertile) rules.fertileFarmBonus else 0)
                }
                Building.MINE -> { mineCount++; mineTotal += rules.mineIncome }
                Building.MARKET -> {
                    marketCount++
                    var neighbors = 0
                    HexMath.forEachNeighbor(hex) { n ->
                        val t = state.tiles[n]
                        if (t != null && t.owner == me && !t.starving && t.flora == null) neighbors++
                    }
                    marketTotal += rules.marketNeighborIncome * minOf(neighbors, rules.marketNeighborCap)
                }
                Building.LUMBER_CAMP -> {
                    campCount++
                    var trees = 0
                    HexMath.forEachNeighbor(hex) { n ->
                        val t = state.tiles[n]
                        if (t != null && t.owner == me && t.flora is Flora.Tree) trees++
                    }
                    campTotal += rules.lumberCampTreeIncome * minOf(trees, rules.lumberCampTreeCap)
                }
                else -> {}
            }
        }
        val buildingRows = listOfNotNull(
            IncomeRow(R.string.building_farm, farmCount, farmTotal).takeIf { farmCount > 0 },
            IncomeRow(R.string.building_mine, mineCount, mineTotal).takeIf { mineCount > 0 },
            IncomeRow(R.string.building_market, marketCount, marketTotal).takeIf { marketCount > 0 },
            IncomeRow(R.string.building_lumber_camp, campCount, campTotal).takeIf { campCount > 0 },
        )
        val soldierRows = (1..rules.maxTier).mapNotNull { tier ->
            val count = state.units.values.count {
                it.owner == me && it.type == com.msa.fightandconquer.core.model.UnitType.SOLDIER && it.tier == tier
            }
            if (count == 0) {
                null
            } else {
                UpkeepRow(unitNameRes(tier), count, rules.unitUpkeep[tier - 1], count * rules.unitUpkeep[tier - 1])
            }
        }
        val specialRows = listOf(
            Triple(com.msa.fightandconquer.core.model.UnitType.ARCHER, R.string.unit_archer, rules.archerUpkeep),
            Triple(com.msa.fightandconquer.core.model.UnitType.CATAPULT, R.string.unit_catapult, rules.catapultUpkeep),
        ).mapNotNull { (type, nameRes, each) ->
            val count = state.units.values.count { it.owner == me && it.type == type }
            if (count == 0) null else UpkeepRow(nameRes, count, each, count * each)
        }
        val tiers = soldierRows + specialRows
        val income = Rules.incomeOf(state, me)
        val upkeep = Rules.upkeepOf(state, me)
        val treasury = state.player(me).treasury
        val net = income - upkeep
        val projected = treasury + net
        return EconomyBreakdown(
            hexCount = hexCount,
            hexIncome = hexCount * rules.hexIncome,
            hexIncomePerHex = rules.hexIncome,
            depositBonus = depositBonus,
            buildingRows = buildingRows,
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

            is GameEvent.PactProposed -> {
                if (actorIsHuman) pushToast(UiText.of(R.string.toast_pact_sent, event.to.value + 1), ToastKind.INFO)
                // The recipient sees the persistent proposal strip on their turn.
            }

            is GameEvent.PactAccepted -> {
                if (state.players[event.a.value].kind is PlayerKind.Human) {
                    pushToast(UiText.of(R.string.toast_pact_accepted, event.b.value + 1), ToastKind.INFO)
                }
                if (state.players[event.b.value].kind is PlayerKind.Human) {
                    pushToast(UiText.of(R.string.toast_pact_accepted, event.a.value + 1), ToastKind.INFO)
                }
            }

            is GameEvent.PactDeclined -> {
                if (state.players[event.from.value].kind is PlayerKind.Human) {
                    pushToast(UiText.of(R.string.toast_pact_declined, event.to.value + 1), ToastKind.INFO)
                }
            }

            is GameEvent.PactExpired -> {
                if (state.players[event.a.value].kind is PlayerKind.Human) {
                    pushToast(UiText.of(R.string.toast_pact_expired, event.b.value + 1), ToastKind.INFO)
                }
                if (state.players[event.b.value].kind is PlayerKind.Human) {
                    pushToast(UiText.of(R.string.toast_pact_expired, event.a.value + 1), ToastKind.INFO)
                }
            }

            is GameEvent.PactBroken -> {
                if (state.players[event.victim.value].kind is PlayerKind.Human) {
                    pushToast(UiText.of(R.string.toast_pact_broken_by, event.breaker.value + 1), ToastKind.ALERT)
                }
                if (state.players[event.breaker.value].kind is PlayerKind.Human) {
                    pushToast(UiText.of(R.string.toast_pact_broken_penalty, event.penalty), ToastKind.WARNING)
                }
            }

            is GameEvent.TributeSent -> {
                if (state.players[event.to.value].kind is PlayerKind.Human) {
                    pushToast(
                        UiText.of(R.string.toast_tribute_received, event.from.value + 1, event.amount),
                        ToastKind.INFO,
                    )
                    state.players[event.to.value].capital?.let { capital ->
                        pushPopup(capital, UiText.of(R.string.popup_coins, event.amount))
                    }
                }
                if (state.players[event.from.value].kind is PlayerKind.Human && actorIsHuman) {
                    pushToast(UiText.of(R.string.toast_tribute_sent, event.amount), ToastKind.INFO)
                }
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
            val strength = Rules.strengthOf(unit, rules)
            val stats = buildList {
                add(InfoStat(UiText.of(R.string.info_stat_strength), UiText.of(R.string.info_value_plain, strength)))
                add(
                    InfoStat(
                        UiText.of(R.string.info_stat_upkeep),
                        UiText.of(R.string.info_value_per_turn, Rules.unitUpkeepOf(unit, rules)),
                    ),
                )
                when (unit.type) {
                    com.msa.fightandconquer.core.model.UnitType.ARCHER -> add(
                        InfoStat(
                            UiText.of(R.string.info_stat_defense),
                            UiText.of(R.string.info_value_defense_area, rules.archerAuraDefense),
                        ),
                    )
                    com.msa.fightandconquer.core.model.UnitType.CATAPULT -> add(
                        InfoStat(
                            UiText.of(R.string.info_stat_range),
                            UiText.of(R.string.info_value_plain, rules.catapultMoveRange),
                        ),
                    )
                    com.msa.fightandconquer.core.model.UnitType.SOLDIER -> {}
                }
            }
            return InfoCard(
                title = UiText.of(unitNameRes(unit.type, unit.tier)),
                subtitle = when {
                    unit.type == com.msa.fightandconquer.core.model.UnitType.ARCHER -> UiText.of(R.string.info_archer)
                    unit.type == com.msa.fightandconquer.core.model.UnitType.CATAPULT -> UiText.of(R.string.info_catapult)
                    own -> UiText.of(R.string.info_unit_spent)
                    else -> UiText.of(R.string.info_unit_enemy, strength)
                },
                stats = stats,
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
                            UiText.of(
                                R.string.info_value_income,
                                rules.farmIncome + if (tile.deposit == com.msa.fightandconquer.core.model.Deposit.FERTILE) rules.fertileFarmBonus else 0,
                            ),
                        ),
                    ),
                    ownerIndex,
                )
                Building.MINE -> InfoCard(
                    UiText.of(R.string.building_mine),
                    UiText.of(R.string.info_mine),
                    listOf(
                        InfoStat(
                            UiText.of(R.string.info_stat_income),
                            UiText.of(R.string.info_value_income, rules.mineIncome),
                        ),
                    ),
                    ownerIndex,
                )
                Building.MARKET -> InfoCard(
                    UiText.of(R.string.building_market),
                    UiText.of(R.string.info_market),
                    listOf(
                        InfoStat(
                            UiText.of(R.string.info_stat_income),
                            UiText.of(
                                R.string.info_value_income_max,
                                rules.marketNeighborIncome * rules.marketNeighborCap,
                            ),
                        ),
                    ),
                    ownerIndex,
                )
                Building.LUMBER_CAMP -> InfoCard(
                    UiText.of(R.string.building_lumber_camp),
                    UiText.of(R.string.info_lumber_camp),
                    listOf(
                        InfoStat(
                            UiText.of(R.string.info_stat_income),
                            UiText.of(
                                R.string.info_value_income_max,
                                rules.lumberCampTreeIncome * rules.lumberCampTreeCap,
                            ),
                        ),
                    ),
                    ownerIndex,
                )
                Building.WATCHTOWER -> InfoCard(
                    UiText.of(R.string.building_watchtower),
                    UiText.of(R.string.info_watchtower),
                    listOf(
                        InfoStat(
                            UiText.of(R.string.info_stat_vision),
                            UiText.of(R.string.info_value_plain, rules.watchtowerVisionRadius),
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
        when (tile.deposit) {
            com.msa.fightandconquer.core.model.Deposit.GOLD_VEIN -> return InfoCard(
                UiText.of(R.string.piece_gold_vein),
                UiText.of(R.string.info_gold_vein),
                listOf(
                    InfoStat(
                        UiText.of(R.string.info_stat_income),
                        UiText.of(R.string.info_value_income, rules.mineIncome),
                    ),
                ),
            )
            com.msa.fightandconquer.core.model.Deposit.FERTILE -> return InfoCard(
                UiText.of(R.string.piece_fertile),
                UiText.of(R.string.info_fertile),
                listOf(
                    InfoStat(
                        UiText.of(R.string.info_stat_income),
                        UiText.of(R.string.info_value_income, rules.fertileHexBonus),
                    ),
                ),
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
        val selectedName = selectedUnit?.let { state.units[it] }?.let { unitNameRes(it.type, it.tier) }
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
            selectedUnitNameRes = selectedName,
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
                mineIncome = rules.mineIncome,
                marketIncomeMax = rules.marketNeighborIncome * rules.marketNeighborCap,
                lumberCampIncomeMax = rules.lumberCampTreeIncome * rules.lumberCampTreeCap,
                watchtowerVision = rules.watchtowerVisionRadius,
                archerUpkeep = rules.archerUpkeep,
                catapultUpkeep = rules.catapultUpkeep,
            ),
        )
        // Live panels track every buy/move/undo.
        if (_economy.value != null) _economy.value = computeEconomy()
        if (_diplomacy.value != null) _diplomacy.value = computeDiplomacy()
        // Incoming proposals surface only to the acting human, never behind a banner.
        _incomingProposals.value = if (
            rules.diplomacyEnabled && banner == null && state.phase is GamePhase.Playing &&
            state.player(me).kind is PlayerKind.Human
        ) {
            engine.incomingProposals().map { IncomingProposal(it.from.value, it.durationRounds) }
        } else {
            emptyList()
        }
        // Keep highlights in sync with spent/moved units.
        if (selectedUnit?.let { !state.units.containsKey(it) } == true) clearSelection()
        // Fog of war: refreshHud runs after every state entry point (submit, undo,
        // load, AI actions), so the vision sets stay in lockstep with the board.
        if (state.player(me).kind is PlayerKind.Human) lastHumanSeat = me.value
        refreshVisibility(state)
    }

    // ----- fog of war -----

    private fun refreshVisibility(state: GameState) {
        // Fog off — or game over: the fog lifts so players can review the final board.
        if (!state.config.rules.fogOfWar || state.phase !is GamePhase.Playing) {
            if (_visibility.value != null) _visibility.value = null
            return
        }
        val viewer = viewPerspective(state)
        _visibility.value = BoardVisibility(
            visible = Rules.visibleHexes(state, viewer),
            explored = state.player(viewer).discovered,
        )
    }

    /**
     * Whose fog the board shows. During AI turns this stays on the human who most
     * recently played — never the next human, whose map would leak to the player
     * still holding the device in pass-and-play.
     */
    private fun viewPerspective(state: GameState): PlayerId {
        if (state.player(state.currentPlayer).kind is PlayerKind.Human) return state.currentPlayer
        lastHumanSeat?.let { seat ->
            val p = state.players[seat]
            if (p.kind is PlayerKind.Human && !p.eliminated) return p.id
        }
        return state.players.firstOrNull { it.kind is PlayerKind.Human && !it.eliminated }?.id
            ?: state.currentPlayer
    }

    // Note on anchors/popups under fog: overlay labels are frontier hexes (always
    // within vision) and coin popups fire only on the viewer's own actions, so the
    // Compose anchor overlay never reveals fogged activity by construction.

}
