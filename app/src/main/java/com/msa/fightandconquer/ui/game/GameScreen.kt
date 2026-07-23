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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
    val incomingProposals by viewModel.incomingProposals.collectAsState()
    val infoCard by viewModel.infoCard.collectAsState()
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
                }
            }
        }

        // ----- board wiring -----
        LaunchedEffect(engine) {
            engine.events.collect { event ->
                ref.scene?.apply(engine.state.value, listOf(event))
            }
        }
        LaunchedEffect(Unit) {
            viewModel.highlights.collect { h ->
                ref.scene?.showHighlights(h.selected, h.moves, h.captures, h.merges)
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
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TopBar(state, incomingProposals.size, viewModel, onOpenGuide = { openGuide(null) })
                if (state.currentIsHuman && state.banner == null && incomingProposals.isNotEmpty()) {
                    ProposalStrip(incomingProposals, viewModel)
                }
                Spacer(Modifier.weight(1f))
                BottomBar(state, infoCard, viewModel, onOpenGuide = openGuide)
            }

            economy?.let { EconomyPanel(it) }
            diplomacy?.let { DiplomacyPanel(it, viewModel) }
            ToastStack(toasts)

            state.banner?.let { seat ->
                TurnBanner(seat) { viewModel.beginTurn() }
            }
            state.winner?.let { winner ->
                GameOverOverlay(winner) { viewModel.backToMenu() }
            }
        }

        if (guideOpen) {
            FieldGuide(onClose = { guideOpen = false }, focusEntryId = guideFocus)
        }
    }
}

@Composable
private fun boardContentDescription(state: HudState): String = when {
    state.winner != null -> stringResource(R.string.game_over_winner, state.winner + 1)
    state.banner != null -> stringResource(R.string.banner_player, state.banner + 1)
    !state.currentIsHuman -> stringResource(R.string.hud_ai_player, state.currentPlayer + 1)
    else -> stringResource(R.string.hud_player, state.currentPlayer + 1)
}
