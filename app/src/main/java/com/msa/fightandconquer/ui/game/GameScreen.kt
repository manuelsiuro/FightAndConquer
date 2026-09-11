package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.msa.fightandconquer.R
import com.msa.fightandconquer.render.FilamentHost
import com.msa.fightandconquer.render.scene.BoardScene
import com.msa.fightandconquer.ui.GameViewModel
import com.msa.fightandconquer.ui.HudState
import com.msa.fightandconquer.ui.guide.FieldGuide

private class SceneRef {
    var scene by mutableStateOf<BoardScene?>(null)
}

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val context = LocalContext.current
    val ref = remember { SceneRef() }
    val hud by viewModel.hud.collectAsState()
    val overlayLabels by viewModel.overlayLabels.collectAsState()
    val popups by viewModel.popups.collectAsState()
    val toasts by viewModel.toasts.collectAsState()
    val economy by viewModel.economy.collectAsState()
    val diplomacy by viewModel.diplomacy.collectAsState()
    val research by viewModel.research.collectAsState()
    val objectivesOpen by viewModel.objectivesOpen.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val incomingProposals by viewModel.incomingProposals.collectAsState()
    val infoCard by viewModel.infoCard.collectAsState()
    val campaignRun by viewModel.campaignRun.collectAsState()
    val engine = viewModel.engine ?: return

    // Field Guide overlay state (local UI only — no ViewModel/navigation involvement).
    var guideOpen by remember { mutableStateOf(false) }
    var guideFocus by remember { mutableStateOf<String?>(null) }
    val openGuide: (String?) -> Unit = { focus -> guideFocus = focus; guideOpen = true }

    Box(Modifier.fillMaxSize()) {
        // ----- 3D board + gestures -----
        val boardDescription = hud?.let { boardContentDescription(it) } ?: ""
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = boardDescription }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> ref.scene?.tap(offset.x, offset.y) }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        ref.scene?.let {
                            if (zoom != 1f) it.zoom(zoom)
                            if (pan.x != 0f || pan.y != 0f) it.pan(pan.x, pan.y)
                        }
                    }
                },
        ) {
            FilamentHost(Modifier.fillMaxSize()) { renderEngine ->
                BoardScene(renderEngine, context, engine.state.value).also { scene ->
                    scene.onTap = { hex -> viewModel.onHexTapped(hex) }
                    scene.onTapMiss = { viewModel.cancelSelection() }
                    // Fog must cover the board from the very first frame.
                    viewModel.visibility.value.let { vis ->
                        scene.setFog(vis?.visible, vis?.explored)
                    }
                    ref.scene = scene
                    // The ViewModel feeds this board synchronously from submit().
                    viewModel.attachBoard(scene)
                }
            }
        }

        // ----- board wiring -----
        DisposableEffect(Unit) {
            onDispose { ref.scene?.let(viewModel::detachBoard) }
        }
        LaunchedEffect(Unit) {
            viewModel.highlights.collect { h ->
                ref.scene?.showHighlights(
                    h.selected, h.moves, h.captures, h.merges, h.hintFocus, h.fishingRange,
                )
            }
        }
        LaunchedEffect(Unit) {
            var last = viewModel.resync.value
            viewModel.resync.collect { tick ->
                if (tick != last) {
                    last = tick
                    ref.scene?.skipAnimations()
                    ref.scene?.apply(engine.state.value, emptyList())
                }
            }
        }
        LaunchedEffect(Unit) {
            viewModel.cameraJumps.collect { hex -> ref.scene?.jumpTo(hex, targetDistance = 10f) }
        }
        LaunchedEffect(Unit) {
            viewModel.visibility.collect { vis ->
                ref.scene?.setFog(vis?.visible, vis?.explored)
            }
        }
        LaunchedEffect(overlayLabels, popups) {
            val tracked = overlayLabels.mapTo(HashSet()) { it.hex }
            popups.mapTo(tracked) { it.hex }
            ref.scene?.setTrackedAnchors(tracked)
        }

        // ----- world-anchored overlay (defense chips + coin popups), px space -----
        AnchorOverlay(ref.scene, overlayLabels, popups)

        // ----- HUD -----
        hud?.let { state ->
            // Panels, toasts and the proposal strip anchor to the top chrome's
            // measured bottom + 8 dp — never a constant (the bar is content-sized).
            var topAnchorPx by remember { mutableStateOf(0f) }
            val topAnchor = with(LocalDensity.current) { topAnchorPx.toDp() }
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(
                    Modifier.onGloballyPositioned {
                        topAnchorPx = it.positionInRoot().y + it.size.height
                    },
                ) {
                    TopBar(
                        state,
                        viewModel = viewModel,
                        onOpenGuide = { openGuide(null) },
                    )
                    if (state.currentIsHuman && state.banner == null && state.winner == null) {
                        ActionBar(
                            state = state,
                            proposalCount = incomingProposals.size,
                            economyOpen = economy != null,
                            diplomacyOpen = diplomacy != null,
                            researchOpen = research != null,
                            statsOpen = stats != null,
                            isCampaign = campaignRun != null,
                            objectivesOpen = objectivesOpen,
                            viewModel = viewModel,
                        )
                    }
                    if (state.currentIsHuman && state.banner == null && incomingProposals.isNotEmpty()) {
                        ProposalStrip(incomingProposals, viewModel)
                    }
                }
                Spacer(Modifier.weight(1f))
                campaignRun?.coach?.let { CoachCardView(it, viewModel::dismissCoachCard) }
                BottomBar(state, infoCard, viewModel, onOpenGuide = openGuide)
            }

            // The glanceable surfaces are mutually exclusive bottom sheets (the
            // ViewModel enforces one-at-a-time). Content rides rememberRetained so
            // it survives the slide-out after its flow nulls. Toasts render after
            // the sheets — a rejection toast must read above the scrim.
            val economyRetained = rememberRetained(economy)
            HudBottomSheet(
                visible = economy != null,
                onDismiss = viewModel::closePanels,
                pinned = { economyRetained?.let { EconomySummary(it) } },
            ) {
                economyRetained?.let { EconomySheetContent(it) }
            }
            val diplomacyRetained = rememberRetained(diplomacy)
            HudBottomSheet(visible = diplomacy != null, onDismiss = viewModel::closePanels) {
                diplomacyRetained?.let { DiplomacySheetContent(it, viewModel) }
            }
            val researchRetained = rememberRetained(research)
            HudBottomSheet(
                visible = research != null,
                onDismiss = viewModel::closePanels,
                pinned = {
                    researchRetained?.let { ResearchSheetFooter(it, state.currentPlayer) }
                },
            ) {
                researchRetained?.let { ResearchSheetBody(it, state.currentPlayer, viewModel) }
            }
            val objectivesRetained = rememberRetained(campaignRun)
            HudBottomSheet(
                visible = objectivesOpen && campaignRun != null,
                onDismiss = viewModel::closePanels,
            ) {
                objectivesRetained?.let { ObjectivesSheetContent(it) }
            }
            val statsRetained = rememberRetained(stats)
            HudBottomSheet(visible = stats != null, onDismiss = viewModel::closePanels) {
                statsRetained?.let { GameStatsSheet(it) }
            }
            ToastStack(toasts, topAnchor)

            state.banner?.let { seat ->
                TurnBanner(seat, state.turnNumber, state.currentCiv) { viewModel.beginTurn() }
            }
            val outcome = campaignRun?.outcome
            val onDebrief: (() -> Unit)? =
                if (viewModel.debriefAvailable) viewModel::openDebrief else null
            when {
                outcome != null -> CampaignOutcomeOverlay(
                    outcome = outcome,
                    missionName = campaignRun!!.levelNameText
                        ?: stringResource(campaignRun!!.levelName),
                    onNext = viewModel::startNextLevel,
                    onRetry = viewModel::retryLevel,
                    onDebrief = onDebrief,
                    onMenu = viewModel::backToMenu,
                )
                // A campaign level reports against its own terms, never "player N wins".
                campaignRun == null -> state.winner?.let { winner ->
                    GameOverOverlay(
                        winner,
                        winnerIsHuman = state.winnerIsHuman ?: true,
                        onDebrief = onDebrief,
                    ) { viewModel.backToMenu() }
                }
            }
        }

        if (guideOpen) {
            FieldGuide(onClose = { guideOpen = false }, focusEntryId = guideFocus)
        }
    }
}

@Composable
private fun boardContentDescription(state: HudState): String = when {
    state.winner != null ->
        stringResource(R.string.debrief_conquers, seatLabel(state.winner, state.winnerIsHuman ?: true))
    state.banner != null -> stringResource(R.string.banner_player, state.banner + 1)
    else -> seatLabel(state.currentPlayer, state.currentIsHuman)
}
