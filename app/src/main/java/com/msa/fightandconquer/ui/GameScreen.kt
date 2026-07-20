package com.msa.fightandconquer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.core.engine.PurchaseOption
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.render.FilamentHost
import com.msa.fightandconquer.render.scene.BoardScene
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
    val infoCard by viewModel.infoCard.collectAsState()
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
                    scene.onTapMiss = { viewModel.cancelSelection() }
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
                TopBar(state, viewModel)
                Spacer(Modifier.weight(1f))
                BottomBar(state, infoCard, viewModel)
            }

            economy?.let { EconomyPanel(it) }
            ToastStack(toasts)

            state.banner?.let { seat ->
                TurnBanner(seat) { viewModel.beginTurn() }
            }
            state.winner?.let { winner ->
                GameOverOverlay(winner) { viewModel.backToMenu() }
            }
        }
    }
}

// ---------- world-anchored overlay ----------

@Composable
private fun AnchorOverlay(scene: BoardScene?, labels: List<OverlayLabel>, popups: List<CoinPopup>) {
    if (scene == null || (labels.isEmpty() && popups.isEmpty())) return
    val anchors by scene.anchors.collectAsState()
    Box(Modifier.fillMaxSize()) {
        for (label in labels) {
            val position = anchors[label.hex] ?: continue
            DefenseChip(
                label,
                Modifier.offset { IntOffset(position.x.roundToInt() - 24, position.y.roundToInt() - 20) },
            )
        }
        for (popup in popups) {
            val position = anchors[popup.hex] ?: continue
            CoinPopupText(
                popup,
                Modifier.offset { IntOffset(position.x.roundToInt() - 30, position.y.roundToInt() - 26) },
            )
        }
    }
}

@Composable
private fun DefenseChip(label: OverlayLabel, modifier: Modifier) {
    val background = when (label.kind) {
        LabelKind.CAPTURABLE -> UiColors.chipCapturable
        LabelKind.BLOCKED -> UiColors.chipBlocked
    }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn(tween(120))) {
        Surface(shape = RoundedCornerShape(50), color = background, shadowElevation = 2.dp) {
            Text(
                "🛡${label.text}",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun CoinPopupText(popup: CoinPopup, modifier: Modifier) {
    val rise = remember(popup.id) { Animatable(0f) }
    LaunchedEffect(popup.id) { rise.animateTo(1f, tween(1100)) }
    Surface(
        modifier = modifier
            .offset { IntOffset(0, (-56f * rise.value).roundToInt()) }
            .graphicsLayer { alpha = 1f - rise.value * rise.value },
        shape = RoundedCornerShape(50),
        color = UiColors.panel,
        shadowElevation = 2.dp,
    ) {
        Text(
            popup.text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = UiColors.positive,
        )
    }
}

// ---------- toasts ----------

@Composable
private fun ToastStack(toasts: List<HudToast>) {
    Column(
        Modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (toast in toasts) {
            androidx.compose.runtime.key(toast.id) {
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                val (bg, fg, size) = when (toast.kind) {
                    ToastKind.INFO -> Triple(UiColors.panel, UiColors.ink, 13.sp)
                    ToastKind.WARNING -> Triple(UiColors.toastWarning, UiColors.ink, 13.sp)
                    ToastKind.ALERT -> Triple(UiColors.alert, Color.White, 15.sp)
                }
                AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -it / 2 }) {
                    Surface(shape = RoundedCornerShape(12.dp), color = bg, shadowElevation = 4.dp) {
                        Text(
                            toast.text,
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = fg,
                            fontSize = size,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// ---------- economy panel ----------

@Composable
private fun EconomyPanel(economy: EconomyBreakdown) {
    Surface(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(start = 12.dp, top = 68.dp)
            .width(238.dp),
        shape = RoundedCornerShape(16.dp),
        color = UiColors.panel,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Income", fontSize = 12.sp, color = UiColors.ink.copy(alpha = 0.55f))
            EconomyRow("${economy.hexCount} hexes × 1", "+${economy.hexIncome}")
            if (economy.farmCount > 0) {
                EconomyRow("${economy.farmCount} farms × ${economy.farmIncome / maxOf(economy.farmCount, 1)}", "+${economy.farmIncome}")
            }
            if (economy.starvingCount > 0) {
                EconomyRow("${economy.starvingCount} hexes cut off", "·0", valueColor = UiColors.alert)
            }
            if (economy.tiers.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Upkeep", fontSize = 12.sp, color = UiColors.ink.copy(alpha = 0.55f))
                for (tier in economy.tiers) {
                    EconomyRow("${tier.count} ${tier.name}${if (tier.count > 1) "s" else ""} × ${tier.each}", "−${tier.total}")
                }
            }
            Spacer(Modifier.height(6.dp))
            EconomyRow(
                "Net",
                if (economy.net >= 0) "+${economy.net}" else "${economy.net}",
                bold = true,
                valueColor = if (economy.net >= 0) UiColors.positive else UiColors.alert,
            )
            EconomyRow("Treasury ${economy.treasury}", "→ ${economy.projected} next turn", bold = true)
            when {
                economy.bankruptcyImminent -> WarningStrip("Bankruptcy next turn — all units will die!", UiColors.alert, Color.White)
                economy.upkeepRisk -> WarningStrip("Treasury won't cover another round of upkeep", UiColors.toastWarning, UiColors.ink)
            }
        }
    }
}

@Composable
private fun EconomyRow(label: String, value: String, bold: Boolean = false, valueColor: Color = UiColors.ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = UiColors.ink, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        Text(value, fontSize = 13.sp, color = valueColor, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun WarningStrip(text: String, background: Color, foreground: Color) {
    Spacer(Modifier.height(8.dp))
    Surface(shape = RoundedCornerShape(8.dp), color = background, modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
        )
    }
}

// ---------- top bar ----------

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
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier.clickable { viewModel.toggleEconomyPanel() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🪙 ${state.treasury}", color = UiColors.ink, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    val net = state.income - state.upkeep
                    Text(
                        if (net >= 0) "+$net" else "$net",
                        color = if (net >= 0) UiColors.positive else UiColors.alert,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("Turn ${state.turnNumber + 1}", color = UiColors.ink.copy(alpha = 0.6f), fontSize = 13.sp)
                if (state.currentIsHuman && state.banner == null && state.freshUnitCount > 0) {
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = UiColors.faction(state.currentPlayer).copy(alpha = 0.3f),
                        modifier = Modifier.clickable { viewModel.focusNextFreshUnit() },
                    ) {
                        Text(
                            "${state.freshUnitCount} ⚑",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UiColors.ink,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
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
                        color = UiColors.alert,
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

// ---------- bottom bar ----------

@Composable
private fun BottomBar(state: HudState, infoCard: InfoCard?, viewModel: GameViewModel) {
    Column(Modifier.padding(12.dp)) {
        infoCard?.let { info ->
            InfoCardView(info)
            Spacer(Modifier.height(8.dp))
        }
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
                    PurchaseCard(option, state.shopInfo, affordable = option.cost <= state.treasury) {
                        viewModel.buy(option)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        var confirmEndTurn by remember { mutableStateOf(false) }
        LaunchedEffect(confirmEndTurn) {
            if (confirmEndTurn) {
                delay(3000)
                confirmEndTurn = false
            }
        }
        LaunchedEffect(state.turnNumber, state.currentPlayer, state.freshUnitCount) {
            confirmEndTurn = false
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.canUndo && state.currentIsHuman) {
                OutlinedButton(onClick = { viewModel.undo() }) { Text("Undo", color = UiColors.ink) }
            }
            Spacer(Modifier.weight(1f))
            if (state.currentIsHuman && state.winner == null && state.banner == null) {
                AnimatedContent(confirmEndTurn, label = "endTurnConfirm") { confirming ->
                    if (!confirming) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                if (state.freshUnitCount == 0) viewModel.endTurn() else confirmEndTurn = true
                            },
                            containerColor = UiColors.faction(state.currentPlayer),
                            contentColor = Color.White,
                        ) { Text("End Turn", fontWeight = FontWeight.Bold) }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${state.freshUnitCount} unit${if (state.freshUnitCount > 1) "s" else ""} unmoved",
                                fontSize = 13.sp,
                                color = UiColors.ink.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { confirmEndTurn = false }) {
                                Text("✕", color = UiColors.ink)
                            }
                            Spacer(Modifier.width(8.dp))
                            ExtendedFloatingActionButton(
                                onClick = { confirmEndTurn = false; viewModel.endTurn() },
                                containerColor = UiColors.alert,
                                contentColor = Color.White,
                            ) { Text("End anyway", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCardView(info: InfoCard) {
    Surface(shape = RoundedCornerShape(12.dp), color = UiColors.panel, shadowElevation = 3.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            info.factionIndex?.let {
                Box(Modifier.size(12.dp).background(UiColors.faction(it), CircleShape))
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Text(info.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = UiColors.ink)
                Text(info.subtitle, fontSize = 12.sp, color = UiColors.ink.copy(alpha = 0.65f))
                if (info.stats.isNotEmpty()) {
                    Row {
                        info.stats.forEachIndexed { index, stat ->
                            if (index > 0) {
                                Text("  ·  ", fontSize = 12.sp, color = UiColors.ink.copy(alpha = 0.4f))
                            }
                            Text(
                                "${stat.label} ${stat.value}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = UiColors.ink.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseCard(option: PurchaseOption, shop: ShopInfo, affordable: Boolean, onBuy: () -> Unit) {
    val (name, emoji) = when (option) {
        is PurchaseOption.Unit -> GameViewModel.unitName(option.tier) to listOf("♟", "♞", "♜", "♚")[option.tier - 1]
        is PurchaseOption.Structure -> when (option.type) {
            BuildingType.FARM -> "Farm" to "🌾"
            BuildingType.TOWER -> "Tower" to "🗼"
            BuildingType.STRONG_TOWER -> "Castle" to "🏰"
        }
    }
    val detail = when (option) {
        is PurchaseOption.Unit -> "${shop.unitUpkeep[option.tier - 1]}/turn"
        is PurchaseOption.Structure -> when (option.type) {
            BuildingType.FARM -> "+${shop.farmIncome}/turn"
            BuildingType.TOWER -> "Def ${shop.towerDefense}"
            BuildingType.STRONG_TOWER -> "Def ${shop.strongTowerDefense}"
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
                color = if (affordable) UiColors.ink.copy(alpha = 0.7f) else UiColors.alert,
            )
            Text(detail, fontSize = 10.sp, color = UiColors.ink.copy(alpha = 0.55f))
        }
    }
}

// ---------- overlays ----------

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
