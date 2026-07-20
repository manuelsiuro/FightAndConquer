package com.msa.fightandconquer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.core.engine.PurchaseOption
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.render.FilamentHost
import com.msa.fightandconquer.render.scene.BoardScene

private class SceneRef {
    var scene: BoardScene? = null
}

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val context = LocalContext.current
    val ref = remember { SceneRef() }
    val hud by viewModel.hud.collectAsState()
    val engine = viewModel.engine ?: return

    Box(Modifier.fillMaxSize()) {
        // ----- 3D board + gestures -----
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                    ref.scene = scene
                }
            }
        }

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

        // ----- HUD -----
        hud?.let { state ->
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TopBar(state, viewModel)
                Spacer(Modifier.weight(1f))
                BottomBar(state, viewModel)
            }

            state.banner?.let { seat ->
                TurnBanner(seat) { viewModel.beginTurn() }
            }
            state.winner?.let { winner ->
                GameOverOverlay(winner) { viewModel.backToMenu() }
            }
        }
    }
}

@Composable
private fun TopBar(state: HudState, viewModel: GameViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = UiColors.panel,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(UiColors.faction(state.currentPlayer), CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.currentIsHuman) "Player ${state.currentPlayer + 1}" else "AI ${state.currentPlayer + 1}",
                fontWeight = FontWeight.SemiBold,
                color = UiColors.ink,
            )
            Spacer(Modifier.width(14.dp))
            Text("🪙 ${state.treasury}", color = UiColors.ink, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            val net = state.income - state.upkeep
            Text(
                if (net >= 0) "+$net" else "$net",
                color = if (net >= 0) Color(0xFF5B7F5E) else Color(0xFFB05B4C),
                fontSize = 14.sp,
            )
            Spacer(Modifier.width(14.dp))
            Text("Turn ${state.turnNumber + 1}", color = UiColors.ink.copy(alpha = 0.6f), fontSize = 13.sp)
            if (state.aiThinking) {
                Spacer(Modifier.width(10.dp))
                Text("thinking…", color = UiColors.ink.copy(alpha = 0.5f), fontSize = 13.sp)
            }
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(
            modifier = Modifier.padding(top = 12.dp, end = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = UiColors.panel,
            shadowElevation = 4.dp,
        ) {
            Column {
                Text(
                    "⋯",
                    modifier = Modifier
                        .clickable { menuOpen = !menuOpen }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 20.sp,
                    color = UiColors.ink,
                )
                if (menuOpen) {
                    Text(
                        "Resign",
                        modifier = Modifier
                            .clickable { menuOpen = false; viewModel.surrender() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color(0xFFB05B4C),
                        fontSize = 14.sp,
                    )
                    Text(
                        "Exit",
                        modifier = Modifier
                            .clickable { menuOpen = false; viewModel.backToMenu() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = UiColors.ink,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBar(state: HudState, viewModel: GameViewModel) {
    Column(Modifier.padding(12.dp)) {
        state.selectedUnitTier?.let { tier ->
            Surface(shape = RoundedCornerShape(12.dp), color = UiColors.panel, shadowElevation = 3.dp) {
                Text(
                    "${GameViewModel.unitName(tier)} — pick a highlighted hex",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = UiColors.ink,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (state.purchases.isNotEmpty() && state.currentIsHuman && state.banner == null) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in state.purchases) {
                    PurchaseCard(option, affordable = option.cost <= state.treasury) {
                        viewModel.buy(option)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.canUndo && state.currentIsHuman) {
                OutlinedButton(onClick = { viewModel.undo() }) { Text("Undo", color = UiColors.ink) }
            }
            Spacer(Modifier.weight(1f))
            if (state.currentIsHuman && state.winner == null && state.banner == null) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.endTurn() },
                    containerColor = UiColors.faction(state.currentPlayer),
                    contentColor = Color.White,
                ) { Text("End Turn", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PurchaseCard(option: PurchaseOption, affordable: Boolean, onBuy: () -> Unit) {
    val (name, emoji) = when (option) {
        is PurchaseOption.Unit -> GameViewModel.unitName(option.tier) to listOf("♟", "♞", "♜", "♚")[option.tier - 1]
        is PurchaseOption.Structure -> when (option.type) {
            BuildingType.FARM -> "Farm" to "🌾"
            BuildingType.TOWER -> "Tower" to "🗼"
            BuildingType.STRONG_TOWER -> "Castle" to "🏰"
        }
    }
    Card(
        modifier = Modifier.clickable(enabled = affordable, onClick = onBuy),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (affordable) UiColors.panel else UiColors.panel.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 18.sp)
            Text(name, fontSize = 13.sp, color = UiColors.ink, fontWeight = FontWeight.Medium)
            Text(
                "${option.cost} 🪙",
                fontSize = 12.sp,
                color = if (affordable) UiColors.ink.copy(alpha = 0.7f) else Color(0xFFB05B4C),
            )
        }
    }
}

@Composable
private fun TurnBanner(seat: Int, onBegin: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6FFFFFF))
            .pointerInput(Unit) { detectTapGestures { onBegin() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp).background(UiColors.faction(seat), CircleShape))
            Spacer(Modifier.height(16.dp))
            Text(
                "Player ${seat + 1}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = UiColors.ink,
            )
            Text("Tap to start your turn", color = UiColors.ink.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun GameOverOverlay(winner: Int, onBackToMenu: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6FFFFFF))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(56.dp).background(UiColors.faction(winner), CircleShape))
            Spacer(Modifier.height(16.dp))
            Text("Player ${winner + 1} conquers!", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = UiColors.ink)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onBackToMenu,
                colors = ButtonDefaults.buttonColors(containerColor = UiColors.faction(winner)),
            ) { Text("Back to menu") }
        }
    }
}
