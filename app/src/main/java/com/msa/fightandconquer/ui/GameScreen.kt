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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.engine.PurchaseOption
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.render.FilamentHost
import com.msa.fightandconquer.render.scene.BoardScene
import dev.romainguy.kotlin.math.Float2
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** HUD metrics — the overlays hang off the top bar, so its height is shared. */
private val TopBarHeight = 56.dp
private val HudGutter = 12.dp
private val MinTouchTarget = 48.dp
private val ChipHalfWidth = 24.dp
private val ChipVerticalLift = 20.dp
private val PopupHalfWidth = 30.dp
private val PopupVerticalLift = 26.dp

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

@Composable
private fun boardContentDescription(state: HudState): String = when {
    state.winner != null -> stringResource(R.string.game_over_winner, state.winner + 1)
    state.banner != null -> stringResource(R.string.banner_player, state.banner + 1)
    !state.currentIsHuman -> stringResource(R.string.hud_ai_player, state.currentPlayer + 1)
    else -> stringResource(R.string.hud_player, state.currentPlayer + 1)
}

// ---------- world-anchored overlay ----------

@Composable
private fun AnchorOverlay(scene: BoardScene?, labels: List<OverlayLabel>, popups: List<CoinPopup>) {
    if (scene == null || (labels.isEmpty() && popups.isEmpty())) return
    // Held as State and read inside the offset lambdas: anchors change every frame
    // while the camera moves, and a composition-scope read would recompose the whole
    // overlay instead of just re-placing it.
    val anchors = scene.anchors.collectAsState()
    val density = LocalDensity.current
    val chipDx = with(density) { ChipHalfWidth.roundToPx() }
    val chipDy = with(density) { ChipVerticalLift.roundToPx() }
    val popupDx = with(density) { PopupHalfWidth.roundToPx() }
    val popupDy = with(density) { PopupVerticalLift.roundToPx() }

    Box(Modifier.fillMaxSize()) {
        for (label in labels) {
            key(label.hex) {
                DefenseChip(label, anchorOffset(anchors, label.hex, chipDx, chipDy))
            }
        }
        for (popup in popups) {
            key(popup.id) {
                CoinPopupText(popup, anchorOffset(anchors, popup.hex, popupDx, popupDy))
            }
        }
    }
}

/** Places a widget at a hex's projected screen position, deferring the state read to layout. */
private fun anchorOffset(
    anchors: State<Map<Hex, Float2>>,
    hex: Hex,
    dx: Int,
    dy: Int,
): Modifier = Modifier.offset {
    val position = anchors.value[hex] ?: return@offset IntOffset(-10_000, -10_000)
    IntOffset(position.x.roundToInt() - dx, position.y.roundToInt() - dy)
}

@Composable
private fun DefenseChip(label: OverlayLabel, modifier: Modifier) {
    val background = when (label.kind) {
        LabelKind.CAPTURABLE -> UiColors.chipCapturable
        LabelKind.BLOCKED -> UiColors.chipBlocked
    }
    val description = when (label.kind) {
        LabelKind.CAPTURABLE -> stringResource(R.string.cd_defense_capturable, label.defense)
        LabelKind.BLOCKED -> stringResource(R.string.cd_defense_blocked, label.defense)
    }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn(tween(120))) {
        Surface(shape = RoundedCornerShape(50), color = background, shadowElevation = 2.dp) {
            Text(
                stringResource(R.string.overlay_defense_chip, label.defense),
                modifier = Modifier
                    .padding(horizontal = 7.dp, vertical = 2.dp)
                    .semantics { contentDescription = description },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun CoinPopupText(popup: CoinPopup, modifier: Modifier) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(1100)) }
    Surface(
        modifier = modifier
            .offset { IntOffset(0, (-56f * rise.value).roundToInt()) }
            .graphicsLayer { alpha = 1f - rise.value * rise.value },
        shape = RoundedCornerShape(50),
        color = UiColors.panel,
        shadowElevation = 2.dp,
    ) {
        Text(
            popup.text.resolve(),
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
            .padding(top = TopBarHeight + HudGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (toast in toasts) {
            key(toast.id) {
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                val (bg, fg, size) = when (toast.kind) {
                    ToastKind.INFO -> Triple(UiColors.panel, UiColors.ink, 13.sp)
                    ToastKind.WARNING -> Triple(UiColors.toastWarning, UiColors.ink, 13.sp)
                    ToastKind.ALERT -> Triple(UiColors.alert, Color.White, 15.sp)
                }
                val urgency = if (toast.kind == ToastKind.ALERT) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
                AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -it / 2 }) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bg,
                        shadowElevation = 4.dp,
                        modifier = Modifier.semantics { liveRegion = urgency },
                    ) {
                        Text(
                            toast.text.resolve(),
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
            .padding(start = HudGutter, top = TopBarHeight + HudGutter)
            .width(238.dp),
        shape = RoundedCornerShape(16.dp),
        color = UiColors.panel,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.economy_income), fontSize = 12.sp, color = UiColors.inkMuted)
            EconomyRow(
                stringResource(R.string.economy_hexes_row, economy.hexCount, economy.hexIncomePerHex),
                stringResource(R.string.economy_amount_positive, economy.hexIncome),
            )
            if (economy.depositBonus > 0) {
                EconomyRow(
                    stringResource(R.string.economy_fertile_row),
                    stringResource(R.string.economy_amount_positive, economy.depositBonus),
                )
            }
            for (row in economy.buildingRows) {
                EconomyRow(
                    stringResource(R.string.economy_building_row, row.count, stringResource(row.nameRes)),
                    stringResource(R.string.economy_amount_positive, row.total),
                )
            }
            if (economy.starvingCount > 0) {
                EconomyRow(
                    stringResource(R.string.economy_cut_off_row, economy.starvingCount),
                    stringResource(R.string.economy_cut_off_value),
                    valueColor = UiColors.alert,
                )
            }
            if (economy.tiers.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.economy_upkeep), fontSize = 12.sp, color = UiColors.inkMuted)
                for (tier in economy.tiers) {
                    EconomyRow(
                        stringResource(
                            R.string.economy_upkeep_row,
                            tier.count,
                            stringResource(unitNameRes(tier.tier)),
                            tier.each,
                        ),
                        stringResource(R.string.economy_amount_negative, tier.total),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            EconomyRow(
                stringResource(R.string.economy_net),
                if (economy.net >= 0) {
                    stringResource(R.string.economy_amount_positive, economy.net)
                } else {
                    stringResource(R.string.economy_amount_negative, -economy.net)
                },
                bold = true,
                valueColor = if (economy.net >= 0) UiColors.positive else UiColors.alert,
            )
            EconomyRow(
                stringResource(R.string.economy_treasury, economy.treasury),
                stringResource(R.string.economy_projection, economy.projected),
                bold = true,
            )
            when {
                economy.bankruptcyImminent ->
                    WarningStrip(stringResource(R.string.economy_warn_bankruptcy), UiColors.alert, Color.White)
                economy.upkeepRisk ->
                    WarningStrip(stringResource(R.string.economy_warn_upkeep), UiColors.toastWarning, UiColors.ink)
            }
        }
    }
}

@Composable
private fun EconomyRow(label: String, value: String, bold: Boolean = false, valueColor: Color = UiColors.ink) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = UiColors.ink,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            value,
            fontSize = 13.sp,
            color = valueColor,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun WarningStrip(text: String, background: Color, foreground: Color) {
    Spacer(Modifier.height(8.dp))
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = background,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
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
    val factionDescription = stringResource(R.string.cd_faction_color, state.currentPlayer + 1)
    val economyDescription = stringResource(R.string.cd_open_economy)
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.padding(HudGutter),
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
                        .background(UiColors.faction(state.currentPlayer), CircleShape)
                        .semantics { contentDescription = factionDescription },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.currentIsHuman) {
                        stringResource(R.string.hud_player, state.currentPlayer + 1)
                    } else {
                        stringResource(R.string.hud_ai_player, state.currentPlayer + 1)
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = UiColors.ink,
                )
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier
                        .clickable(role = Role.Button) { viewModel.toggleEconomyPanel() }
                        .semantics { contentDescription = economyDescription }
                        .defaultMinSize(minHeight = MinTouchTarget)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.hud_treasury, state.treasury),
                        color = UiColors.ink,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    val net = state.income - state.upkeep
                    Text(
                        if (net >= 0) {
                            stringResource(R.string.hud_net_positive, net)
                        } else {
                            stringResource(R.string.hud_net_negative, net)
                        },
                        color = if (net >= 0) UiColors.positive else UiColors.alert,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.hud_turn, state.turnNumber + 1),
                    color = UiColors.inkMuted,
                    fontSize = 13.sp,
                )
                if (state.currentIsHuman && state.banner == null && state.freshUnitCount > 0) {
                    Spacer(Modifier.width(10.dp))
                    val freshDescription =
                        stringResource(R.string.cd_fresh_units, state.freshUnitCount)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = UiColors.faction(state.currentPlayer).copy(alpha = 0.3f),
                        modifier = Modifier
                            .clickable(role = Role.Button) { viewModel.focusNextFreshUnit() }
                            .semantics { contentDescription = freshDescription }
                            .defaultMinSize(minHeight = MinTouchTarget),
                    ) {
                        Text(
                            stringResource(R.string.hud_fresh_units, state.freshUnitCount),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UiColors.ink,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                        )
                    }
                }
                if (state.aiThinking) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.hud_ai_thinking),
                        color = UiColors.inkMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(top = HudGutter, end = HudGutter)) {
            Surface(shape = CircleShape, color = UiColors.panel, shadowElevation = 4.dp) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_open_menu),
                        tint = UiColors.ink,
                    )
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(12.dp),
                containerColor = UiColors.panel,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hud_resign)) },
                    leadingIcon = {
                        // Null description: the adjacent label already names the action,
                        // so a description here would make TalkBack read it twice.
                        Icon(Icons.Default.Warning, contentDescription = null)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = UiColors.alert,
                        leadingIconColor = UiColors.alert,
                    ),
                    onClick = {
                        menuOpen = false
                        viewModel.surrender()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hud_exit)) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = UiColors.ink,
                        leadingIconColor = UiColors.inkSecondary,
                    ),
                    onClick = {
                        menuOpen = false
                        viewModel.backToMenu()
                    },
                )
            }
        }
    }
}

// ---------- bottom bar ----------

@Composable
private fun BottomBar(state: HudState, infoCard: InfoCard?, viewModel: GameViewModel) {
    Column(Modifier.padding(HudGutter)) {
        infoCard?.let { info ->
            InfoCardView(info)
            Spacer(Modifier.height(8.dp))
        }
        state.selectedUnitTier?.let { tier ->
            Surface(shape = RoundedCornerShape(12.dp), color = UiColors.panel, shadowElevation = 3.dp) {
                Text(
                    stringResource(R.string.hud_selected_unit_hint, stringResource(unitNameRes(tier))),
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
                OutlinedButton(onClick = { viewModel.undo() }) {
                    Text(stringResource(R.string.hud_undo), color = UiColors.ink)
                }
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
                            contentColor = UiColors.ink,
                        ) { Text(stringResource(R.string.hud_end_turn), fontWeight = FontWeight.Bold) }
                    } else {
                        val cancelDescription = stringResource(R.string.cd_cancel_end_turn)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                pluralStringResource(
                                    R.plurals.hud_units_unmoved,
                                    state.freshUnitCount,
                                    state.freshUnitCount,
                                ),
                                fontSize = 13.sp,
                                color = UiColors.inkSecondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { confirmEndTurn = false },
                                modifier = Modifier.semantics { contentDescription = cancelDescription },
                            ) {
                                Text(stringResource(R.string.hud_cancel_symbol), color = UiColors.ink)
                            }
                            Spacer(Modifier.width(8.dp))
                            ExtendedFloatingActionButton(
                                onClick = { confirmEndTurn = false; viewModel.endTurn() },
                                containerColor = UiColors.alert,
                                contentColor = Color.White,
                            ) { Text(stringResource(R.string.hud_end_anyway), fontWeight = FontWeight.Bold) }
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
            info.factionIndex?.let { index ->
                val description = stringResource(R.string.cd_faction_color, index + 1)
                Box(
                    Modifier
                        .size(12.dp)
                        .background(UiColors.faction(index), CircleShape)
                        .semantics { contentDescription = description },
                )
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Text(
                    info.title.resolve(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = UiColors.ink,
                )
                Text(info.subtitle.resolve(), fontSize = 12.sp, color = UiColors.inkSecondary)
                if (info.stats.isNotEmpty()) {
                    Row {
                        info.stats.forEachIndexed { index, stat ->
                            if (index > 0) {
                                Text(
                                    stringResource(R.string.info_stat_separator),
                                    fontSize = 12.sp,
                                    color = UiColors.inkFaint,
                                )
                            }
                            Text(
                                stringResource(
                                    R.string.info_stat_pair,
                                    stat.label.resolve(),
                                    stat.value.resolve(),
                                ),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = UiColors.inkSecondary,
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
    val nameRes = when (option) {
        is PurchaseOption.Unit -> unitNameRes(option.tier)
        is PurchaseOption.Structure -> when (option.type) {
            BuildingType.FARM -> R.string.building_farm
            BuildingType.TOWER -> R.string.building_tower
            BuildingType.STRONG_TOWER -> R.string.building_castle
            BuildingType.MINE -> R.string.building_mine
            BuildingType.MARKET -> R.string.building_market
            BuildingType.LUMBER_CAMP -> R.string.building_lumber_camp
            BuildingType.WATCHTOWER -> R.string.building_watchtower
        }
    }
    val emojiRes = when (option) {
        is PurchaseOption.Unit -> when (option.tier) {
            1 -> R.string.emoji_peasant
            2 -> R.string.emoji_spearman
            3 -> R.string.emoji_baron
            else -> R.string.emoji_knight
        }
        is PurchaseOption.Structure -> when (option.type) {
            BuildingType.FARM -> R.string.emoji_farm
            BuildingType.TOWER -> R.string.emoji_tower
            BuildingType.STRONG_TOWER -> R.string.emoji_castle
            BuildingType.MINE -> R.string.emoji_mine
            BuildingType.MARKET -> R.string.emoji_market
            BuildingType.LUMBER_CAMP -> R.string.emoji_lumber_camp
            BuildingType.WATCHTOWER -> R.string.emoji_watchtower
        }
    }
    val detail = when (option) {
        is PurchaseOption.Unit -> stringResource(
            R.string.shop_upkeep_per_turn,
            shop.unitUpkeep[option.tier - 1],
        )
        is PurchaseOption.Structure -> when (option.type) {
            BuildingType.FARM -> stringResource(R.string.shop_income_per_turn, shop.farmIncome)
            BuildingType.TOWER -> stringResource(R.string.shop_defense, shop.towerDefense)
            BuildingType.STRONG_TOWER -> stringResource(R.string.shop_defense, shop.strongTowerDefense)
            BuildingType.MINE -> stringResource(R.string.shop_income_per_turn, shop.mineIncome)
            BuildingType.MARKET -> stringResource(R.string.shop_income_up_to, shop.marketIncomeMax)
            BuildingType.LUMBER_CAMP -> stringResource(R.string.shop_income_up_to, shop.lumberCampIncomeMax)
            BuildingType.WATCHTOWER -> stringResource(R.string.shop_vision, shop.watchtowerVision)
        }
    }
    val name = stringResource(nameRes)
    val description = if (affordable) {
        stringResource(R.string.cd_purchase_card, name, option.cost)
    } else {
        stringResource(R.string.cd_purchase_unaffordable, name, option.cost)
    }
    Card(
        modifier = Modifier
            .clickable(enabled = affordable, role = Role.Button, onClick = onBuy)
            .semantics { contentDescription = description },
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
            Text(stringResource(emojiRes), fontSize = 18.sp)
            Text(name, fontSize = 13.sp, color = UiColors.ink, fontWeight = FontWeight.Medium)
            Text(
                stringResource(R.string.shop_cost, option.cost),
                fontSize = 12.sp,
                color = if (affordable) UiColors.inkSecondary else UiColors.alert,
            )
            Text(detail, fontSize = 12.sp, color = UiColors.inkMuted)
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
                stringResource(R.string.banner_player, seat + 1),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = UiColors.ink,
            )
            Text(stringResource(R.string.banner_tap_to_start), color = UiColors.inkSecondary)
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
            Text(
                stringResource(R.string.game_over_winner, winner + 1),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = UiColors.ink,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onBackToMenu,
                colors = ButtonDefaults.buttonColors(
                    containerColor = UiColors.faction(winner),
                    contentColor = UiColors.ink,
                ),
            ) { Text(stringResource(R.string.game_over_back_to_menu)) }
        }
    }
}
