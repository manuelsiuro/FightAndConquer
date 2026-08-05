package com.msa.fightandconquer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.msa.fightandconquer.core.ai.AiPlayer
import com.msa.fightandconquer.core.campaign.CampaignSave
import com.msa.fightandconquer.core.campaign.CampaignSaveRef
import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.ui.editor.CustomMapStore
import com.msa.fightandconquer.ui.editor.EditorSession
import com.msa.fightandconquer.ui.editor.MapTemplates
import java.util.UUID
import com.msa.fightandconquer.core.campaign.CampaignStatus
import com.msa.fightandconquer.core.campaign.CampaignTracker
import com.msa.fightandconquer.core.campaign.FailCondition
import com.msa.fightandconquer.core.campaign.Hints
import com.msa.fightandconquer.core.campaign.LevelCondition
import com.msa.fightandconquer.core.campaign.LevelDef
import com.msa.fightandconquer.core.campaign.LevelFactory
import com.msa.fightandconquer.core.campaign.Objectives
import com.msa.fightandconquer.core.campaign.Scripts
import com.msa.fightandconquer.core.campaign.Verdict
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
import com.msa.fightandconquer.core.model.Terrain
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
import com.msa.fightandconquer.core.persist.SaveGame
import com.msa.fightandconquer.ui.campaign.CampaignProgressStore
import com.msa.fightandconquer.ui.campaign.CampaignRepository
import com.msa.fightandconquer.ui.campaign.CampaignText
import com.msa.fightandconquer.ui.campaign.counter
import com.msa.fightandconquer.ui.campaign.label
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
    val shape: com.msa.fightandconquer.core.map.MapShape = com.msa.fightandconquer.core.map.MapShape.CONTINENT,
    val seed: Long = System.currentTimeMillis(),
    val fogOfWar: Boolean = false,
    val specialUnits: Boolean = true,
    val diplomacy: Boolean = true,
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
    /**
     * Hexes the campaign coach is pointing at ("land on the marked sand"). Independent
     * of selection, so it survives taps and keeps the prose free of coordinates.
     */
    val hintFocus: Set<Hex> = emptySet(),
)

/** Defense numbers shown on frontier hexes while a unit is selected. */
enum class LabelKind { CAPTURABLE, BLOCKED }
data class OverlayLabel(val hex: Hex, val defense: Int, val kind: LabelKind)

/** Coin counter breakdown panel. */
data class UpkeepRow(val nameRes: Int, val count: Int, val each: Int, val total: Int, val iconRes: Int? = null)

/** One income line per building type (farms, mines, markets, lumber camps). */
data class IncomeRow(val nameRes: Int, val count: Int, val total: Int, val iconRes: Int? = null)

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
    /** Pre-rendered piece thumbnail; null for abstract cards (fog, cut-off). */
    val iconRes: Int? = null,
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
    val transportUpkeep: Int = 4,
    val warshipUpkeep: Int = 8,
    val portIncome: Int = 2,
    val fisheryIncomeMax: Int = 9,
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
    /** Baked render of the selected unit for the hint card, null when none. */
    val selectedUnitIconRes: Int?,
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
    data class Menu(val hasAutosave: Boolean) : Screen
    data class Setup(val generating: Boolean = false) : Screen
    data object Campaign : Screen

    /** The pre-mission card: story, objectives, and what is new this time. */
    data class Briefing(val campaignId: String, val levelId: String) : Screen

    /** The map library. [revision] bumps after a create/delete so the list recomposes. */
    data class MapManager(val revision: Long = 0L) : Screen

    /** The editor canvas; null [mapId] would be a fresh map (creation always saves first). */
    data class MapEditor(val mapId: String) : Screen
    data object Settings : Screen
    data object About : Screen
    data object Game : Screen
}

/**
 * The teaching moments a coach step can wait on that no board state implies. Names must
 * match the `uiSignal` values authored in the level JSON — `CampaignTextTest` checks the
 * catalogue against this list, so a typo in a level fails the build rather than leaving a
 * hint stuck on screen forever.
 */
object UiSignals {
    const val UNIT_SELECTED = "unitSelected"
    const val ECONOMY_OPENED = "economyOpened"
    const val DIPLOMACY_OPENED = "diplomacyOpened"

    val all = setOf(UNIT_SELECTED, ECONOMY_OPENED, DIPLOMACY_OPENED)
}

/** One line of the in-game objectives strip. */
data class ObjectiveLine(val text: UiText, val counter: UiText?, val done: Boolean)

/** The coach card: a teaching step with the hexes it is pointing at. */
data class CoachCard(val text: UiText, val dismissible: Boolean)

/** How a mission ended, and where the player can go from here. */
data class CampaignOutcome(
    val won: Boolean,
    val stars: Int,
    val rounds: Int,
    val reason: UiText?,
    val debrief: Int,
    val nextLevelId: String?,
)

/**
 * Everything the HUD needs to know about the mission in progress. Null in a skirmish,
 * which is how every existing HUD path stays exactly as it was.
 */
data class CampaignRunState(
    val campaignId: String,
    val levelId: String,
    val levelName: Int,
    val objectives: List<ObjectiveLine>,
    val coach: CoachCard?,
    val turnLimit: Int?,
    val round: Int,
    val outcome: CampaignOutcome?,
)

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

    /** Mission state while a campaign level is being played; null in a skirmish. */
    private val _campaignRun = MutableStateFlow<CampaignRunState?>(null)
    val campaignRun: StateFlow<CampaignRunState?> = _campaignRun.asStateFlow()

    val campaigns = CampaignRepository(application)
    val campaignProgress = CampaignProgressStore(File(application.filesDir, "campaign_progress.json"))
    val customMaps = CustomMapStore(File(application.filesDir, "maps"))

    private var selectedUnit: UnitId? = null
    private var selectedHex: Hex? = null
    private var banner: Int? = null
    /** Armed pact-break confirmation: capture of this partner hex proceeds on re-tap. */
    private var pendingPactBreak: Hex? = null
    /** Seat of the human who most recently played — the fog perspective during AI turns. */
    private var lastHumanSeat: Int? = null
    private var aiJob: Job? = null
    private var eventsJob: Job? = null
    private var mapGenJob: Job? = null
    private var aiThinking = false
    private var nextToastId = 0L
    private var freshUnitCursor = 0

    // Event-feedback accumulators (reset per human round).
    private var aiCapturedFromHumans = 0
    private var cutOffWarned = false
    private var knownStarving: Set<Hex> = emptySet()

    // ----- campaign director state -----
    private var activeLevel: LevelDef? = null
    private var activeCampaignId: String? = null
    private var tracker = CampaignTracker()
    /** Teaching moments the board cannot imply (a unit picked up, a panel opened). */
    private var uiSignals = mutableSetOf<String>()
    /** The scoreboard as of the current turn's start — what an autosave must carry. */
    private var turnStartTracker: CampaignTracker? = null
    /** Re-entrancy guard: firing a story beat re-enters refreshCampaign through submit. */
    private var firingScript = false

    // ----- menu -----

    fun newGame(setup: GameSetup) {
        clearCampaignRun()
        _screen.value = Screen.Setup(generating = true)
        mapGenJob = viewModelScope.launch(Dispatchers.Default) {
            val map = MapGenerator.generate(
                MapParams(
                    seed = setup.seed,
                    size = setup.size,
                    playerCount = setup.playerCount,
                    shape = setup.shape,
                ),
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
                rules = RuleConstants(
                    fogOfWar = setup.fogOfWar,
                    specialUnitsEnabled = setup.specialUnits,
                    diplomacyEnabled = setup.diplomacy,
                ),
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
                    return@withContext
                }
                // A campaign autosave resumes as a campaign: the mission is named in the
                // save, and the tracker is rebuilt by re-folding the replayed turn — so a
                // resumed level scores exactly as one that was never interrupted.
                val ref = save.campaign
                val level = ref?.let { campaigns.level(it.campaignId, it.levelId) }
                if (ref != null && level != null) {
                    activeCampaignId = ref.campaignId
                    activeLevel = level
                    tracker = CampaignSave.restoreTracker(save, level)
                    turnStartTracker = ref.tracker
                    uiSignals = ref.uiSignals.toMutableSet()
                } else {
                    activeCampaignId = null
                    activeLevel = null
                    tracker = CampaignTracker()
                    uiSignals = mutableSetOf()
                }
                startEngine(GameEngine.fromSave(save), showOpeningBanner = false)
            }
        }
    }

    fun openSetup() {
        _screen.value = Screen.Setup()
    }

    fun openCampaign() {
        _screen.value = Screen.Campaign
    }

    fun openBriefing(campaignId: String, levelId: String) {
        _screen.value = Screen.Briefing(campaignId, levelId)
    }

    /** Starts (or restarts) a campaign mission from its opening position. */
    fun startLevel(campaignId: String, levelId: String) {
        val level = campaigns.level(campaignId, levelId) ?: return
        // The previous mission's run state is a latch: if its outcome survived
        // into this mission, refreshCampaign would republish it (frozen board,
        // stale victory overlay, "Next" looping back to the same briefing).
        _campaignRun.value = null
        firingScript = false
        activeCampaignId = campaignId
        activeLevel = level
        tracker = CampaignTracker()
        turnStartTracker = tracker
        uiSignals = mutableSetOf()
        startEngine(GameEngine(LevelFactory.instantiate(level)), showOpeningBanner = false)
    }

    /**
     * The next mission after the one just finished, or null at the end of a campaign.
     * Exposed so the outcome overlay can offer "Next mission" without the composable
     * having to know about the repository.
     */
    fun startNextLevel() {
        val campaignId = activeCampaignId ?: return
        val next = _campaignRun.value?.outcome?.nextLevelId ?: return
        openBriefing(campaignId, next)
    }

    fun retryLevel() {
        val campaignId = activeCampaignId ?: return
        val levelId = activeLevel?.id ?: return
        startLevel(campaignId, levelId)
    }

    /** The live editing session; non-null exactly while Screen.MapEditor shows. */
    var editor: EditorSession? = null
        private set

    fun openMapEditor() {
        closeEditorSession()
        _screen.value = Screen.MapManager()
    }

    /** Creates a starter map on disk immediately, so the editor always has a real id. */
    fun newMap() {
        val now = System.currentTimeMillis()
        val def = MapTemplates.starter(
            id = UUID.randomUUID().toString(),
            name = getApplication<Application>().getString(R.string.maps_new_default_name),
            createdAt = now,
        )
        customMaps.save(def)
        editor = EditorSession(customMaps, def)
        _screen.value = Screen.MapEditor(def.id)
    }

    fun editMap(id: String) {
        val def = customMaps.load(id) ?: return openMapEditor()
        editor = EditorSession(customMaps, def)
        _screen.value = Screen.MapEditor(id)
    }

    /** Leaves the canvas, autosaving a dirty draft — back never discards work. */
    fun closeEditor() {
        closeEditorSession()
        _screen.value = Screen.MapManager()
    }

    private fun closeEditorSession() {
        editor?.saveIfDirty(System.currentTimeMillis())
        editor = null
    }

    fun deleteMap(id: String) {
        customMaps.delete(id)
        bumpMapManager()
    }

    /** Adopts a decoded shared map under a fresh id, so imports never collide. */
    fun importMap(def: CustomMapDef) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        customMaps.save(
            def.copy(id = id, createdAt = now, modifiedAt = now, level = def.level.copy(id = id)),
        )
        bumpMapManager()
    }

    private fun bumpMapManager() {
        val revision = (_screen.value as? Screen.MapManager)?.revision ?: 0L
        _screen.value = Screen.MapManager(revision + 1)
    }

    fun openSettings() {
        _screen.value = Screen.Settings
    }

    fun openAbout() {
        _screen.value = Screen.About
    }

    fun backToMenu() {
        closeEditorSession()
        mapGenJob?.cancel()
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
        clearCampaignRun()
        // Synchronous backstop: the finished-game deletion above is async, and
        // the menu must never offer to continue a game that is already over.
        if (engine?.state?.value?.phase is GamePhase.Finished) autosaveFile.delete()
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
        if (_campaignRun.value?.outcome != null) return
        val state = engine.state.value

        val heldUnit = selectedUnit
        if (heldUnit != null && state.units.containsKey(heldUnit)) {
            val held = state.units.getValue(heldUnit)
            // Naval specials first: land the cargo / bombard the shore.
            if (held.cargo != null &&
                com.msa.fightandconquer.core.engine.Legality.check(
                    state,
                    GameAction.Disembark(heldUnit, hex),
                ) is com.msa.fightandconquer.core.engine.LegalityResult.Ok
            ) {
                submit(GameAction.Disembark(heldUnit, hex))
                clearSelection()
                refreshHud()
                return
            }
            if (held.type == com.msa.fightandconquer.core.model.UnitType.WARSHIP &&
                com.msa.fightandconquer.core.engine.Legality.check(
                    state,
                    GameAction.Bombard(heldUnit, hex),
                ) is com.msa.fightandconquer.core.engine.LegalityResult.Ok
            ) {
                // Bombarding a pact partner (their coast, or their boat on open
                // sea) is aggression like a capture — arm the same second-tap
                // confirmation instead of firing immediately.
                val bombardVictim = state.tiles[hex]?.owner ?: state.unitAt(hex)?.owner
                if (bombardVictim != null &&
                    engine.pactBetween(state.currentPlayer, bombardVictim) != null &&
                    pendingPactBreak != hex
                ) {
                    pendingPactBreak = hex
                    val penalty = state.player(state.currentPlayer).treasury *
                        state.config.rules.pactBreakPenaltyPercent / 100
                    pushToast(UiText.of(R.string.toast_pact_break_confirm, penalty), ToastKind.WARNING)
                    return
                }
                pendingPactBreak = null
                submit(GameAction.Bombard(heldUnit, hex))
                clearSelection()
                refreshHud()
                return
            }
            val reach = engine.reachableFor(heldUnit)
            when (hex) {
                in reach.moveTargets, in reach.captureTargets, in reach.embarkTargets -> {
                    // Capturing a pact partner's hex (or sinking their boat)
                    // breaks the pact — arm a second-tap confirmation instead
                    // of striking immediately.
                    val targetOwner = state.tiles[hex]?.owner ?: state.unitAt(hex)?.owner
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

        // Own fresh unit anywhere — including a boat afloat on neutral sea.
        val unit = tile?.unit?.let { state.units[it] }
        if (unit != null && unit.owner == me && !unit.spent) {
            selectedUnit = unit.id
            selectedHex = hex
            signalUi(UiSignals.UNIT_SELECTED)
            val reach = engine.reachableFor(unit.id)
            // Naval extras: landings for a loaded transport, raids for a warship.
            var friendly = emptySet<Hex>()
            var hostile = emptySet<Hex>()
            if (com.msa.fightandconquer.core.engine.Rules.isNaval(unit.type)) {
                fun legal(action: GameAction) =
                    com.msa.fightandconquer.core.engine.Legality.check(state, action) is
                        com.msa.fightandconquer.core.engine.LegalityResult.Ok
                val neighbors = com.msa.fightandconquer.core.hex.HexMath.neighbors(hex)
                if (unit.cargo != null) {
                    val landings = neighbors.filter { legal(GameAction.Disembark(unit.id, it)) }
                    friendly = landings.filter { state.tiles[it]?.owner == me }.toSet()
                    hostile = landings.filter { state.tiles[it]?.owner != me }.toSet()
                }
                if (unit.type == com.msa.fightandconquer.core.model.UnitType.WARSHIP) {
                    hostile = hostile + neighbors.filter { legal(GameAction.Bombard(unit.id, it)) }
                }
            }
            _highlights.value = HighlightSet(
                hex,
                reach.moveTargets + reach.embarkTargets + friendly,
                reach.captureTargets + hostile,
                reach.mergeTargets,
            )
            _overlayLabels.value = computeOverlay(state, unit, reach)
            refreshHud()
            return
        }
        if (tile?.owner == me) {
            if (!tile.starving && tile.building == null && tile.unit == null) {
                selectedHex = hex
                _highlights.value = HighlightSet(selected = hex)
                _overlayLabels.value = emptyList()
                refreshHud()
                return
            }
        }
        // Open sea beside an own port: the boat yard (purchase tray on a sea hex).
        if (tile != null && tile.terrain == com.msa.fightandconquer.core.model.Terrain.SEA &&
            tile.unit == null && tile.building == null && engine.buyableAt(hex).isNotEmpty()
        ) {
            selectedHex = hex
            _highlights.value = HighlightSet(selected = hex)
            _overlayLabels.value = emptyList()
            refreshHud()
            return
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

    /** The player acknowledged a coach card that waits on nothing but being read. */
    fun dismissCoachCard() {
        val level = activeLevel ?: return
        if (tracker.hintIndex >= level.hints.size) return
        tracker = tracker.withHintIndex(tracker.hintIndex + 1)
        refreshCampaign()
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
        val engine = engine
            ?: return LegalityResult.Rejected(com.msa.fightandconquer.core.engine.RejectionReason.NO_GAME)
        val before = engine.state.value
        val result = engine.submit(action)
        foldCampaign(before, engine, action)
        // A game can finish mid-turn (capturing the last capital) — the turn-
        // boundary autosave sites never run then, so the stale resume file
        // must be dropped here or the menu keeps offering Continue Game.
        if (engine.state.value.phase is GamePhase.Finished) autosave()
        refreshHud()
        return result
    }

    /**
     * Advances the campaign scoreboard across one accepted action.
     *
     * It reads [GameEngine.lastEvents] rather than the events flow because the flow is
     * drop-oldest by design — fine for a renderer that reconciles from state, fatal for a
     * tally of facts no later state reveals (a boat sunk, a unit lost).
     */
    private fun foldCampaign(before: GameState, engine: GameEngine, action: GameAction) {
        val level = activeLevel ?: return
        tracker = CampaignTracker.step(
            prev = tracker,
            before = before,
            after = engine.state.value,
            events = engine.lastEvents,
            seat = level.playerSeat,
            objectives = level.objectives,
        )
        // The engine rebases its save snapshot on a turn boundary; the scoreboard the
        // save carries has to be rebased with it (see trackerAtTurnStart).
        if (action is GameAction.EndTurn || action is GameAction.Surrender) {
            turnStartTracker = tracker
        }
    }

    private fun clearCampaignRun() {
        activeLevel = null
        activeCampaignId = null
        tracker = CampaignTracker()
        turnStartTracker = null
        uiSignals = mutableSetOf()
        _campaignRun.value = null
    }

    /** Records a teaching moment the board cannot imply, and re-checks the coach script. */
    private fun signalUi(name: String) {
        if (activeLevel == null || !uiSignals.add(name)) return
        refreshCampaign()
    }

    // ----- threat overlay -----

    private fun computeOverlay(state: GameState, unit: GameUnit, reach: ReachResult): List<OverlayLabel> {
        // Chips live only on the unit's own reach — the frontier it can touch
        // this action — so the overlay reads as "what this unit can do here".
        return (reach.captureTargets + reach.blockedTargets).mapNotNull { hex ->
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
        if (_economy.value != null) signalUi(UiSignals.ECONOMY_OPENED)
    }

    // ----- diplomacy -----

    fun toggleDiplomacyPanel() {
        _economy.value = null
        _diplomacy.value = if (_diplomacy.value == null) computeDiplomacy() else null
        if (_diplomacy.value != null) signalUi(UiSignals.DIPLOMACY_OPENED)
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
        var portCount = 0; var portTotal = 0
        var fisheryCount = 0; var fisheryTotal = 0
        // Mirrors Rules.incomeFrom exactly so the panel rows always sum to `income`.
        for ((hex, tile) in state.tiles) {
            if (tile.owner != me) continue
            if (tile.starving) { starving++; continue }
            if (tile.flora != null) continue
            if (tile.terrain == com.msa.fightandconquer.core.model.Terrain.SEA) continue
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
                Building.PORT -> { portCount++; portTotal += rules.portIncome }
                Building.FISHERY -> {
                    fisheryCount++
                    var shoals = 0
                    HexMath.forEachNeighbor(hex) { n ->
                        val t = state.tiles[n]
                        if (t != null && t.terrain == com.msa.fightandconquer.core.model.Terrain.SEA &&
                            t.deposit == com.msa.fightandconquer.core.model.Deposit.FISH_SHOAL
                        ) {
                            shoals++
                        }
                    }
                    fisheryTotal += rules.fisheryShoalIncome * minOf(shoals, rules.fisheryShoalCap)
                }
                else -> {}
            }
        }
        val buildingRows = listOfNotNull(
            IncomeRow(R.string.building_farm, farmCount, farmTotal, PieceIcons.building(Building.FARM))
                .takeIf { farmCount > 0 },
            IncomeRow(R.string.building_mine, mineCount, mineTotal, PieceIcons.building(Building.MINE))
                .takeIf { mineCount > 0 },
            IncomeRow(R.string.building_market, marketCount, marketTotal, PieceIcons.building(Building.MARKET))
                .takeIf { marketCount > 0 },
            IncomeRow(R.string.building_lumber_camp, campCount, campTotal, PieceIcons.building(Building.LUMBER_CAMP))
                .takeIf { campCount > 0 },
            IncomeRow(R.string.building_port, portCount, portTotal, PieceIcons.building(Building.PORT))
                .takeIf { portCount > 0 },
            IncomeRow(R.string.building_fishery, fisheryCount, fisheryTotal, PieceIcons.building(Building.FISHERY))
                .takeIf { fisheryCount > 0 },
        )
        // Cargo riding a transport still pays its own upkeep — count it with its
        // tier so the rows keep summing exactly to `upkeep`.
        fun cargoCount(type: com.msa.fightandconquer.core.model.UnitType, tier: Int? = null) =
            state.units.values.count { u ->
                u.owner == me && u.cargo?.let { c ->
                    c.type == type && (tier == null || c.tier == tier)
                } == true
            }
        val soldierRows = (1..rules.maxTier).mapNotNull { tier ->
            val count = state.units.values.count {
                it.owner == me && it.type == com.msa.fightandconquer.core.model.UnitType.SOLDIER && it.tier == tier
            } + cargoCount(com.msa.fightandconquer.core.model.UnitType.SOLDIER, tier)
            if (count == 0) {
                null
            } else {
                UpkeepRow(
                    unitNameRes(tier),
                    count,
                    rules.unitUpkeep[tier - 1],
                    count * rules.unitUpkeep[tier - 1],
                    PieceIcons.unit(com.msa.fightandconquer.core.model.UnitType.SOLDIER, tier),
                )
            }
        }
        val specialRows = listOf(
            Triple(com.msa.fightandconquer.core.model.UnitType.ARCHER, R.string.unit_archer, rules.archerUpkeep),
            Triple(com.msa.fightandconquer.core.model.UnitType.CATAPULT, R.string.unit_catapult, rules.catapultUpkeep),
            Triple(com.msa.fightandconquer.core.model.UnitType.TRANSPORT, R.string.unit_transport, rules.transportUpkeep),
            Triple(com.msa.fightandconquer.core.model.UnitType.WARSHIP, R.string.unit_warship, rules.warshipUpkeep),
        ).mapNotNull { (type, nameRes, each) ->
            val count = state.units.values.count { it.owner == me && it.type == type } +
                if (type != com.msa.fightandconquer.core.model.UnitType.TRANSPORT &&
                    type != com.msa.fightandconquer.core.model.UnitType.WARSHIP
                ) {
                    cargoCount(type)
                } else {
                    0
                }
            if (count == 0) null else UpkeepRow(nameRes, count, each, count * each, PieceIcons.unit(type, 1))
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
                    com.msa.fightandconquer.core.model.UnitType.SOLDIER -> add(
                        InfoStat(
                            UiText.of(R.string.info_stat_range),
                            UiText.of(R.string.info_value_plain, Rules.moveRangeOf(unit, rules)),
                        ),
                    )
                    com.msa.fightandconquer.core.model.UnitType.ARCHER -> {
                        add(
                            InfoStat(
                                UiText.of(R.string.info_stat_defense),
                                UiText.of(R.string.info_value_defense_area, rules.archerAuraDefense),
                            ),
                        )
                        add(
                            InfoStat(
                                UiText.of(R.string.info_stat_range),
                                UiText.of(R.string.info_value_plain, rules.archerMoveRange),
                            ),
                        )
                    }
                    com.msa.fightandconquer.core.model.UnitType.CATAPULT -> add(
                        InfoStat(
                            UiText.of(R.string.info_stat_range),
                            UiText.of(R.string.info_value_plain, rules.catapultMoveRange),
                        ),
                    )
                    com.msa.fightandconquer.core.model.UnitType.TRANSPORT -> {
                        add(
                            InfoStat(
                                UiText.of(R.string.info_stat_range),
                                UiText.of(R.string.info_value_plain, rules.transportMoveRange),
                            ),
                        )
                        add(
                            InfoStat(
                                UiText.of(R.string.info_stat_cargo),
                                unit.cargo?.let { UiText.of(unitNameRes(it.type, it.tier)) }
                                    ?: UiText.of(R.string.info_value_cargo_empty),
                            ),
                        )
                    }
                    com.msa.fightandconquer.core.model.UnitType.WARSHIP -> add(
                        InfoStat(
                            UiText.of(R.string.info_stat_range),
                            UiText.of(R.string.info_value_plain, rules.warshipMoveRange),
                        ),
                    )
                }
            }
            return InfoCard(
                title = UiText.of(unitNameRes(unit.type, unit.tier)),
                subtitle = when {
                    unit.type == com.msa.fightandconquer.core.model.UnitType.ARCHER -> UiText.of(R.string.info_archer)
                    unit.type == com.msa.fightandconquer.core.model.UnitType.CATAPULT -> UiText.of(R.string.info_catapult)
                    unit.type == com.msa.fightandconquer.core.model.UnitType.TRANSPORT -> UiText.of(R.string.info_transport)
                    unit.type == com.msa.fightandconquer.core.model.UnitType.WARSHIP -> UiText.of(R.string.info_warship)
                    own -> UiText.of(R.string.info_unit_spent)
                    else -> UiText.of(R.string.info_unit_enemy, strength)
                },
                stats = stats,
                factionIndex = unit.owner.value,
                iconRes = PieceIcons.unit(unit.type, unit.tier),
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
                    iconRes = PieceIcons.building(building),
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
                    iconRes = PieceIcons.building(building),
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
                    iconRes = PieceIcons.building(building),
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
                    iconRes = PieceIcons.building(building),
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
                    iconRes = PieceIcons.building(building),
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
                    iconRes = PieceIcons.building(building),
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
                    iconRes = PieceIcons.building(building),
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
                    iconRes = PieceIcons.building(building),
                )
                Building.PORT -> InfoCard(
                    UiText.of(R.string.building_port),
                    UiText.of(R.string.info_port),
                    listOf(
                        InfoStat(
                            UiText.of(R.string.info_stat_income),
                            UiText.of(R.string.info_value_income, rules.portIncome),
                        ),
                    ),
                    ownerIndex,
                    iconRes = PieceIcons.building(building),
                )
                Building.FISHERY -> InfoCard(
                    UiText.of(R.string.building_fishery),
                    UiText.of(R.string.info_fishery),
                    listOf(
                        InfoStat(
                            UiText.of(R.string.info_stat_income),
                            UiText.of(
                                R.string.info_value_income_max,
                                rules.fisheryShoalIncome * rules.fisheryShoalCap,
                            ),
                        ),
                    ),
                    ownerIndex,
                    iconRes = PieceIcons.building(building),
                )
                Building.BRIDGE -> InfoCard(
                    UiText.of(R.string.building_bridge),
                    UiText.of(R.string.info_bridge),
                    factionIndex = ownerIndex,
                    iconRes = PieceIcons.building(building),
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
                iconRes = PieceIcons.tree,
            )
            is Flora.Gravestone -> return InfoCard(
                UiText.of(R.string.piece_gravestone),
                UiText.of(R.string.info_gravestone),
                iconRes = PieceIcons.gravestone,
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
                iconRes = PieceIcons.goldVein,
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
                iconRes = PieceIcons.fertile,
            )
            com.msa.fightandconquer.core.model.Deposit.FISH_SHOAL -> return InfoCard(
                UiText.of(R.string.piece_fish_shoal),
                UiText.of(R.string.info_fish_shoal),
                listOf(
                    InfoStat(
                        UiText.of(R.string.info_stat_income),
                        UiText.of(R.string.info_value_income, rules.fisheryShoalIncome),
                    ),
                ),
                iconRes = PieceIcons.fishShoal,
            )
            null -> {}
        }
        if (tile.terrain == com.msa.fightandconquer.core.model.Terrain.SEA) {
            return InfoCard(
                UiText.of(R.string.tile_sea),
                UiText.of(R.string.info_sea),
            )
        }
        if (tile.owner == me && tile.starving && tile.graceTurns > 0) {
            return InfoCard(
                UiText.of(R.string.tile_beachhead),
                UiText.plural(R.plurals.info_beachhead, tile.graceTurns, tile.graceTurns),
                factionIndex = me.value,
            )
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
        // A finished mission stops the clock — endTurn() reaches here after the director
        // has already settled the level, and the AI must not play on past the overlay.
        if (_campaignRun.value?.outcome != null) return
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
                    val before = engine.state.value
                    engine.submit(if (guard >= AiPlayer.MAX_ACTIONS_PER_TURN) GameAction.EndTurn else action)
                    // The scoreboard counts the AI's turn too — a boat it sinks is a unit
                    // the player lost.
                    foldCampaign(before, engine, action)
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
                // An AI can win mid-turn; the loop breaks before its turn-end
                // autosave, so drop the stale resume file (autosave deletes
                // when the game is finished).
                if (engine.state.value.phase is GamePhase.Finished) autosave()
                refreshHud()
            }
        }
    }

    // ----- persistence -----

    private fun autosave() {
        val engine = engine ?: return
        val state = engine.state.value
        if (state.phase is GamePhase.Finished || _campaignRun.value?.outcome != null) {
            viewModelScope.launch(Dispatchers.IO) { autosaveFile.delete() }
            return
        }
        val save = saveWithCampaign(engine)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { autosaveFile.writeText(SaveCodec.encode(save)) }
        }
    }

    /** Called from onStop so a mid-turn kill resumes exactly where it was. */
    fun persistNow() {
        // The editor's equivalent of the autosave: onStop must not lose a draft.
        editor?.saveIfDirty(System.currentTimeMillis())
        val engine = engine ?: return
        if (engine.state.value.phase is GamePhase.Finished) return
        if (_campaignRun.value?.outcome != null) return
        runCatching { autosaveFile.writeText(SaveCodec.encode(saveWithCampaign(engine))) }
    }

    /**
     * The engine's save plus, for a mission, which level it belongs to and the scoreboard
     * as it stood at the snapshot's turn start — the tracker is re-folded across the
     * replayed actions on load, exactly as the state itself is.
     */
    private fun saveWithCampaign(engine: GameEngine): SaveGame {
        val save = engine.toSave()
        val level = activeLevel ?: return save
        val campaignId = activeCampaignId ?: return save
        return save.copy(
            campaign = CampaignSaveRef(
                campaignId = campaignId,
                levelId = level.id,
                tracker = trackerAtTurnStart(),
                uiSignals = uiSignals.toSortedSet(),
            ),
        )
    }

    /**
     * The tracker as it stood at the snapshot's turn start.
     *
     * A save is `turnStartState` plus the turn's actions, and loading replays those
     * actions — so the stored tracker must be the pre-turn one, or the replay would fold
     * this turn's kills and losses a second time on top of an already-current tally.
     */
    private fun trackerAtTurnStart(): CampaignTracker = turnStartTracker ?: tracker

    // ----- HUD -----

    private fun refreshHud() {
        val engine = engine ?: run { _hud.value = null; return }
        val state = engine.state.value
        val me = state.currentPlayer
        val rules = state.config.rules
        val summary = engine.incomeSummary(me)
        val selected = selectedUnit?.let { state.units[it] }
        val selectedName = selected?.let { unitNameRes(it.type, it.tier) }
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
            selectedUnitIconRes = selected?.let { PieceIcons.unit(it.type, it.tier) },
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
                transportUpkeep = rules.transportUpkeep,
                warshipUpkeep = rules.warshipUpkeep,
                portIncome = rules.portIncome,
                fisheryIncomeMax = rules.fisheryShoalIncome * rules.fisheryShoalCap,
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
        refreshCampaign()
    }

    // ----- campaign director -----

    /**
     * Scores the mission, fires any story beat that has come due, advances the coach and
     * publishes [campaignRun]. Runs after every state change (see [refreshHud]).
     *
     * Story beats fire only on the player's own turn: `RunScript` lands in the current
     * turn's action log, and a beat that went off mid-AI-turn would both interleave with
     * the AI's animation pacing and be attributed to the wrong turn on replay. One beat
     * per pass — each gets its own animation, and the next pass picks up the one after.
     */
    private fun refreshCampaign() {
        val level = activeLevel ?: return
        val campaignId = activeCampaignId ?: return
        val engine = engine ?: return
        val seat = level.playerSeat
        val state = engine.state.value
        val settled = _campaignRun.value?.outcome != null

        var status = Objectives.evaluate(state, tracker, level, seat)

        if (!settled && !firingScript && state.currentPlayer == seat && banner == null) {
            Scripts.next(level.scripts, state, seat, status, tracker)?.let { beat ->
                firingScript = true
                try {
                    if (submit(beat.action) is LegalityResult.Ok) {
                        tracker = tracker.withScriptFired(beat.id)
                        CampaignText.script(level.id, beat.id)?.let {
                            pushToast(UiText.of(it), ToastKind.INFO)
                        }
                    }
                } finally {
                    firingScript = false
                }
                status = Objectives.evaluate(engine.state.value, tracker, level, seat)
            }
        }

        val hintIndex = Hints.advance(level.hints, tracker.hintIndex, state, seat, status, uiSignals)
        if (hintIndex != tracker.hintIndex) tracker = tracker.withHintIndex(hintIndex)
        val step = level.hints.getOrNull(tracker.hintIndex)
        val coach = step?.let { hint ->
            CampaignText.hint(level.id, hint.id)?.let {
                CoachCard(UiText.of(it), dismissible = hint.until == LevelCondition.Acknowledged)
            }
        }

        val previousOutcome = _campaignRun.value?.outcome
        val outcome = previousOutcome ?: outcomeFor(campaignId, level, status, state.turnNumber)
        if (outcome != null && previousOutcome == null) onMissionSettled(level, outcome)

        _campaignRun.value = CampaignRunState(
            campaignId = campaignId,
            levelId = level.id,
            levelName = CampaignText.level(level.id)?.name ?: R.string.campaign_title,
            objectives = status.rows.map {
                ObjectiveLine(it.label(), it.counter(), it.done)
            },
            coach = coach.takeIf { outcome == null },
            turnLimit = level.failures.filterIsInstance<FailCondition.TurnLimit>().minOfOrNull { it.rounds },
            round = state.turnNumber,
            outcome = outcome,
        )
        // The coach points with the board, not with coordinates in the prose — and so do
        // the objectives: "take the marked ground" has to actually mark it. Hexes an
        // objective has already secured drop out of the set, so the ring always shows
        // what is left to do.
        val focus = if (outcome != null) {
            emptySet()
        } else {
            step?.focus.orEmpty().toSet() + objectiveMarks(level, status, state, seat)
        }
        if (_highlights.value.hintFocus != focus) {
            _highlights.value = _highlights.value.copy(hintFocus = focus)
        }
    }

    /**
     * The hexes a mission's own wording points at: ground an unfinished objective still
     * wants, and ground a defeat clause tells the player to protect. Marking these is
     * what lets objective copy stay geography-free ("take the marked ground") and
     * translatable.
     */
    private fun objectiveMarks(
        level: LevelDef,
        status: CampaignStatus,
        state: GameState,
        seat: PlayerId,
    ): Set<Hex> {
        val marks = HashSet<Hex>()
        status.rows.forEach { row ->
            if (row.done) return@forEach
            val wanted = when (val objective = row.objective) {
                is com.msa.fightandconquer.core.campaign.Objective.CaptureHexes -> objective.hexes
                is com.msa.fightandconquer.core.campaign.Objective.HoldHexes -> objective.hexes
                else -> emptyList()
            }
            wanted.filterTo(marks) { state.tiles[it]?.owner != seat }
        }
        level.failures.filterIsInstance<FailCondition.LoseHexes>().forEach { marks += it.hexes }
        return marks
    }

    private fun outcomeFor(
        campaignId: String,
        level: LevelDef,
        status: CampaignStatus,
        rounds: Int,
    ): CampaignOutcome? = when (val verdict = status.verdict) {
        Verdict.InProgress -> null
        Verdict.Won -> CampaignOutcome(
            won = true,
            stars = level.starsFor(rounds),
            rounds = rounds,
            reason = null,
            debrief = CampaignText.level(level.id)?.debrief ?: R.string.outcome_victory,
            nextLevelId = campaigns.nextLevel(campaignId, level.id)?.id,
        )
        is Verdict.Lost -> CampaignOutcome(
            won = false,
            stars = 0,
            rounds = rounds,
            reason = verdict.reason.label(),
            debrief = CampaignText.level(level.id)?.debrief ?: R.string.outcome_defeat,
            nextLevelId = null,
        )
    }

    /**
     * A settled mission stops the clock: the AI loop is cancelled (it would keep playing
     * a game the player has already finished) and the resume file is dropped, because
     * Continue must never reopen a level that is over.
     */
    private fun onMissionSettled(level: LevelDef, outcome: CampaignOutcome) {
        aiJob?.cancel()
        aiThinking = false
        if (outcome.won) campaignProgress.record(level.id, outcome.stars, outcome.rounds)
        viewModelScope.launch(Dispatchers.IO) { autosaveFile.delete() }
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
