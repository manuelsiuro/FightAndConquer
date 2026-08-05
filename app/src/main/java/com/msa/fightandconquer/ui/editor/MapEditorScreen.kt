package com.msa.fightandconquer.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.campaign.Objective
import com.msa.fightandconquer.core.campaign.ObjectiveRow
import com.msa.fightandconquer.core.campaign.SeatDef
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitType
import com.msa.fightandconquer.render.FilamentHost
import com.msa.fightandconquer.render.scene.BoardScene
import com.msa.fightandconquer.ui.PieceIcons
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.campaign.label
import com.msa.fightandconquer.ui.resolve

private class SceneRef {
    var scene by mutableStateOf<BoardScene?>(null)
}

private enum class Panel { NONE, PLAYERS, RULES, GOALS }

/**
 * The editor canvas: the real 3D board (via the same `FilamentHost`/`BoardScene` stack
 * as play, in editor mode), a seat selector and brush palette below, and panels for
 * players, rules and goals. The scene lives in a plain holder, never Compose state —
 * the black-screen gotcha.
 */
@Composable
fun MapEditorScreen(
    session: EditorSession,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val ref = remember { SceneRef() }
    val ui by session.ui.collectAsState()
    var showIssues by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var paintLock by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(UiColors.background)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset -> ref.scene?.tap(offset.x, offset.y) }
                }
                .pointerInput(paintLock) {
                    if (paintLock) {
                        // Paint lock: a drag sweeps the brush; navigation is parked.
                        detectDragGestures(
                            onDragStart = { offset -> ref.scene?.tap(offset.x, offset.y) },
                            onDrag = { change, _ ->
                                change.consume()
                                ref.scene?.tap(change.position.x, change.position.y)
                            },
                        )
                    } else {
                        detectTransformGestures { _, pan, zoom, _ ->
                            ref.scene?.let {
                                if (zoom != 1f) it.zoom(zoom)
                                if (pan.x != 0f || pan.y != 0f) it.pan(pan.x, pan.y)
                            }
                        }
                    }
                },
        ) {
            FilamentHost(Modifier.fillMaxSize()) { renderEngine ->
                BoardScene(renderEngine, context, session.previewState()).also { scene ->
                    scene.pickVoid = true
                    scene.onTap = { hex -> session.paint(hex) }
                    scene.setGhosts(session.growthRing())
                    ref.scene = scene
                }
            }
        }

        // Every stroke re-renders the whole board state — reconcile is the truth.
        LaunchedEffect(ui.def) {
            ref.scene?.applyEditorState(session.previewState())
            ref.scene?.setGhosts(session.growthRing())
        }
        // The goal-painting brush shows its targets with the hint highlight.
        LaunchedEffect(ui.brush, ui.def) {
            val brush = ui.brush
            if (brush is EditorSession.Brush.ObjectiveHexes) {
                ref.scene?.showHighlights(
                    selected = null,
                    moves = emptySet(),
                    captures = emptySet(),
                    merges = emptySet(),
                    hintFocus = session.objectiveHexes(brush.index),
                )
            } else {
                ref.scene?.clearHighlights()
            }
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    // The 3D canvas behind is always light; without its own panel the
                    // bar's ink washes out in dark theme.
                    .background(UiColors.panel, RoundedCornerShape(14.dp))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = UiColors.ink,
                    )
                }
                Text(
                    ui.def.name,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showRename = true },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UiColors.ink,
                    maxLines = 1,
                )
                TextButton(onClick = session::undo, enabled = ui.canUndo) {
                    Text(
                        stringResource(R.string.editor_undo),
                        color = if (ui.canUndo) UiColors.ink else UiColors.inkFaint,
                        fontSize = 13.sp,
                    )
                }
                TextButton(onClick = { showIssues = true }, enabled = ui.violations.isNotEmpty()) {
                    Text(
                        stringResource(R.string.editor_issues, ui.violations.size),
                        color = if (ui.violations.isEmpty()) UiColors.positive else UiColors.alert,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            EditorDock(
                ui = ui,
                session = session,
                paintLock = paintLock,
                onPaintLock = { paintLock = it },
                onPanel = { panel = it },
            )
        }
    }

    if (showIssues) {
        AlertDialog(
            onDismissRequest = { showIssues = false },
            title = { Text(stringResource(R.string.editor_issues_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ui.violations.take(10).forEach { violation ->
                        Text(
                            violation.toUiText().resolve(),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIssues = false }) {
                    Text(stringResource(R.string.common_back))
                }
            },
        )
    }
    when (panel) {
        Panel.NONE -> Unit
        Panel.PLAYERS -> PlayersDialog(ui, session) { panel = Panel.NONE }
        Panel.RULES -> RulesDialog(ui, session) { panel = Panel.NONE }
        Panel.GOALS -> GoalsDialog(ui, session) { panel = Panel.NONE }
    }
    if (showRename) {
        var name by remember { mutableStateOf(ui.def.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.editor_rename_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { session.rename(name); showRename = false }) {
                    Text(stringResource(R.string.editor_rename_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(R.string.maps_cancel))
                }
            },
        )
    }
}

// ----- the bottom dock: panels row, seat row, tool rows -----

@Composable
private fun EditorDock(
    ui: EditorSession.Ui,
    session: EditorSession,
    paintLock: Boolean,
    onPaintLock: (Boolean) -> Unit,
    onPanel: (Panel) -> Unit,
) {
    val seats = ui.def.level.seats.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(UiColors.panel, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onPanel(Panel.PLAYERS) }) {
                Text(stringResource(R.string.editor_players), fontSize = 13.sp, color = UiColors.ink)
            }
            TextButton(onClick = { onPanel(Panel.RULES) }) {
                Text(stringResource(R.string.editor_rules), fontSize = 13.sp, color = UiColors.ink)
            }
            TextButton(onClick = { onPanel(Panel.GOALS) }) {
                Text(stringResource(R.string.editor_goals), fontSize = 13.sp, color = UiColors.ink)
            }
            FilterChip(
                selected = paintLock,
                onClick = { onPaintLock(!paintLock) },
                label = { Text(stringResource(R.string.editor_paint_lock), fontSize = 12.sp) },
            )
        }

        // Seat context: claim/capital/unit brushes apply to the selected seat.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(seats) { seat ->
                FilterChip(
                    selected = ui.activeSeat == seat,
                    onClick = { session.setActiveSeat(seat) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(UiColors.faction(seat), CircleShape))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(seatNameRes(seat)), fontSize = 12.sp)
                        }
                    },
                )
            }
            if (seats < EditorSession.MAX_SEATS) {
                FilterChip(
                    selected = ui.activeSeat == seats,
                    onClick = {
                        session.setActiveSeat(seats)
                        session.setBrush(EditorSession.Brush.Capital)
                    },
                    label = { Text(stringResource(R.string.editor_brush_add_player), fontSize = 12.sp) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextChip(ui, EditorSession.Brush.Land, stringResource(R.string.editor_brush_land), session)
            TextChip(ui, EditorSession.Brush.Sea, stringResource(R.string.editor_brush_sea), session)
            TextChip(ui, EditorSession.Brush.Erase, stringResource(R.string.editor_brush_erase), session)
            TextChip(ui, EditorSession.Brush.ClearProps, stringResource(R.string.editor_brush_clear), session)
            TextChip(ui, EditorSession.Brush.Owner, stringResource(R.string.editor_brush_claim), session)
            TextChip(ui, EditorSession.Brush.Capital, stringResource(R.string.editor_brush_capital_tool), session)
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tier in 1..4) {
                IconChip(
                    ui, EditorSession.Brush.UnitBrush(UnitType.SOLDIER, tier),
                    PieceIcons.unit(UnitType.SOLDIER, tier), soldierNameRes(tier), session,
                )
            }
            IconChip(ui, EditorSession.Brush.UnitBrush(UnitType.ARCHER), PieceIcons.unit(UnitType.ARCHER, 1), R.string.unit_archer, session)
            IconChip(ui, EditorSession.Brush.UnitBrush(UnitType.CATAPULT), PieceIcons.unit(UnitType.CATAPULT, 1), R.string.unit_catapult, session)
            IconChip(ui, EditorSession.Brush.UnitBrush(UnitType.TRANSPORT), PieceIcons.unit(UnitType.TRANSPORT, 1), R.string.unit_transport, session)
            IconChip(ui, EditorSession.Brush.UnitBrush(UnitType.WARSHIP), PieceIcons.unit(UnitType.WARSHIP, 1), R.string.unit_warship, session)
            for (building in EDITOR_BUILDINGS) {
                IconChip(
                    ui, EditorSession.Brush.Structure(building),
                    PieceIcons.building(building), buildingLabel(building), session,
                )
            }
            IconChip(ui, EditorSession.Brush.Plant(EditorSession.PlantKind.TREE), PieceIcons.tree, R.string.editor_brush_tree, session)
            IconChip(ui, EditorSession.Brush.Plant(EditorSession.PlantKind.GRAVE), PieceIcons.gravestone, R.string.editor_brush_grave, session)
            IconChip(ui, EditorSession.Brush.Resource(Deposit.GOLD_VEIN), PieceIcons.goldVein, R.string.editor_brush_gold, session)
            IconChip(ui, EditorSession.Brush.Resource(Deposit.FERTILE), PieceIcons.fertile, R.string.editor_brush_fertile, session)
            IconChip(ui, EditorSession.Brush.Resource(Deposit.FISH_SHOAL), PieceIcons.fishShoal, R.string.editor_brush_shoal, session)
        }
    }
}

@Composable
private fun TextChip(
    ui: EditorSession.Ui,
    brush: EditorSession.Brush,
    label: String,
    session: EditorSession,
) {
    FilterChip(
        selected = ui.brush == brush,
        onClick = { session.setBrush(brush) },
        label = { Text(label, fontSize = 12.sp) },
    )
}

@Composable
private fun IconChip(
    ui: EditorSession.Ui,
    brush: EditorSession.Brush,
    iconRes: Int,
    nameRes: Int,
    session: EditorSession,
) {
    FilterChip(
        selected = ui.brush == brush,
        onClick = { session.setBrush(brush) },
        label = {
            Image(
                painterResource(iconRes),
                contentDescription = stringResource(nameRes),
                modifier = Modifier.size(26.dp),
            )
        },
    )
}

// ----- panels -----

@Composable
private fun PlayersDialog(ui: EditorSession.Ui, session: EditorSession, onClose: () -> Unit) {
    val level = ui.def.level
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.editor_players)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                level.seats.forEachIndexed { seat, kind ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(UiColors.faction(seat), CircleShape))
                            Spacer(Modifier.size(6.dp))
                            Text(
                                stringResource(seatNameRes(seat)),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = kind is SeatDef.Player,
                                onClick = { session.setSeatKind(seat, SeatDef.Player) },
                                label = { Text(stringResource(R.string.editor_seat_human), fontSize = 12.sp) },
                            )
                            for (difficulty in Difficulty.entries) {
                                FilterChip(
                                    selected = (kind as? SeatDef.Ai)?.difficulty == difficulty,
                                    onClick = { session.setSeatKind(seat, SeatDef.Ai(difficulty)) },
                                    label = { Text(stringResource(difficultyRes(difficulty)), fontSize = 12.sp) },
                                )
                            }
                        }
                        TreasuryField(ui, session, seat)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.common_back)) }
        },
    )
}

@Composable
private fun TreasuryField(ui: EditorSession.Ui, session: EditorSession, seat: Int) {
    val level = ui.def.level
    val current = level.startingTreasury?.getOrNull(seat) ?: level.rules.startingTreasury
    var text by remember(seat, current) { mutableStateOf(current.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value.filter(Char::isDigit).take(5)
            text.toIntOrNull()?.let { session.setTreasury(seat, it) }
        },
        label = { Text(stringResource(R.string.editor_treasury), fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun RulesDialog(ui: EditorSession.Ui, session: EditorSession, onClose: () -> Unit) {
    val rules = ui.def.level.rules
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.editor_rules)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RuleSwitch(R.string.menu_section_fog, rules.fogOfWar, session::setFogOfWar)
                RuleSwitch(R.string.menu_section_specials, rules.specialUnitsEnabled, session::setSpecialUnits)
                RuleSwitch(R.string.menu_section_diplomacy, rules.diplomacyEnabled, session::setDiplomacy)
                RuleSwitch(R.string.editor_rule_naval, rules.navalEnabled, session::setNaval)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.common_back)) }
        },
    )
}

@Composable
private fun RuleSwitch(nameRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(nameRes), Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private enum class GoalKind { CAPTURE, HOLD, SURVIVE, OWN_HEXES, TREASURY, ELIMINATE }

@Composable
private fun GoalsDialog(ui: EditorSession.Ui, session: EditorSession, onClose: () -> Unit) {
    val level = ui.def.level
    var addKind by remember { mutableStateOf<GoalKind?>(null) }
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.editor_goals)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                level.objectives.forEachIndexed { index, objective ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ObjectiveRow(objective, 0, 1).label().resolve(),
                            Modifier.weight(1f),
                            fontSize = 13.sp,
                        )
                        if (objective is Objective.CaptureHexes || objective is Objective.HoldHexes) {
                            TextButton(onClick = {
                                session.setBrush(EditorSession.Brush.ObjectiveHexes(index))
                                onClose()
                            }) {
                                Text(stringResource(R.string.editor_goal_pick_hexes), fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = { session.removeObjective(index) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.maps_delete_confirm),
                                tint = UiColors.inkMuted,
                            )
                        }
                    }
                }

                TurnLimitField(ui, session)

                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (kind in GoalKind.entries) {
                        FilterChip(
                            selected = addKind == kind,
                            onClick = { addKind = kind },
                            label = { Text(stringResource(goalKindRes(kind)), fontSize = 12.sp) },
                        )
                    }
                }
                addKind?.let { kind ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (kind != GoalKind.CAPTURE) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it.filter(Char::isDigit).take(4) },
                                label = { Text(stringResource(goalValueRes(kind)), fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        TextButton(onClick = {
                            val n = value.toIntOrNull()
                            val objective = buildGoal(kind, n, level.seats.size) ?: return@TextButton
                            session.addObjective(objective)
                            addKind = null
                            value = ""
                            // Hex-targeted goals continue on the board.
                            if (objective is Objective.CaptureHexes || objective is Objective.HoldHexes) onClose()
                        }) {
                            Text(stringResource(R.string.editor_goal_add), fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.common_back)) }
        },
    )
}

@Composable
private fun TurnLimitField(ui: EditorSession.Ui, session: EditorSession) {
    val current = ui.def.level.failures
        .filterIsInstance<com.msa.fightandconquer.core.campaign.FailCondition.TurnLimit>()
        .firstOrNull()?.rounds
    var text by remember(current) { mutableStateOf(current?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value.filter(Char::isDigit).take(3)
            session.setTurnLimit(text.toIntOrNull())
        },
        label = { Text(stringResource(R.string.editor_turn_limit), fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ----- small pure helpers -----

private fun buildGoal(kind: GoalKind, value: Int?, seats: Int): Objective? = when (kind) {
    GoalKind.CAPTURE -> Objective.CaptureHexes(emptyList())
    GoalKind.HOLD -> value?.takeIf { it > 0 }?.let { Objective.HoldHexes(emptyList(), it) }
    GoalKind.SURVIVE -> value?.takeIf { it > 0 }?.let { Objective.SurviveRounds(it) }
    GoalKind.OWN_HEXES -> value?.takeIf { it > 0 }?.let { Objective.OwnHexCount(it) }
    GoalKind.TREASURY -> value?.takeIf { it > 0 }?.let { Objective.ReachTreasury(it) }
    GoalKind.ELIMINATE ->
        value?.takeIf { it in 1..seats }?.let { Objective.EliminatePlayer(PlayerId(it - 1)) }
}

private fun goalKindRes(kind: GoalKind): Int = when (kind) {
    GoalKind.CAPTURE -> R.string.editor_goal_capture
    GoalKind.HOLD -> R.string.editor_goal_hold
    GoalKind.SURVIVE -> R.string.editor_goal_survive
    GoalKind.OWN_HEXES -> R.string.editor_goal_own
    GoalKind.TREASURY -> R.string.editor_goal_treasury
    GoalKind.ELIMINATE -> R.string.editor_goal_eliminate
}

private fun goalValueRes(kind: GoalKind): Int = when (kind) {
    GoalKind.ELIMINATE -> R.string.editor_goal_value_player
    GoalKind.TREASURY -> R.string.editor_goal_value_coins
    GoalKind.OWN_HEXES -> R.string.editor_goal_value_hexes
    else -> R.string.editor_goal_value_rounds
}

private fun seatNameRes(seat: Int): Int = when (seat) {
    0 -> R.string.seat_name_1
    1 -> R.string.seat_name_2
    2 -> R.string.seat_name_3
    3 -> R.string.seat_name_4
    4 -> R.string.seat_name_5
    else -> R.string.seat_name_6
}

private fun difficultyRes(difficulty: Difficulty): Int = when (difficulty) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.NORMAL -> R.string.difficulty_normal
    Difficulty.HARD -> R.string.difficulty_hard
    Difficulty.PASSIVE -> R.string.difficulty_passive
}

private fun soldierNameRes(tier: Int): Int = when (tier) {
    1 -> R.string.unit_peasant
    2 -> R.string.unit_spearman
    3 -> R.string.unit_baron
    else -> R.string.unit_knight
}

private fun buildingLabel(building: Building): Int = when (building) {
    Building.FARM -> R.string.building_farm
    Building.TOWER -> R.string.building_tower
    Building.STRONG_TOWER -> R.string.building_castle
    Building.MINE -> R.string.building_mine
    Building.MARKET -> R.string.building_market
    Building.LUMBER_CAMP -> R.string.building_lumber_camp
    Building.WATCHTOWER -> R.string.building_watchtower
    Building.PORT -> R.string.building_port
    Building.FISHERY -> R.string.building_fishery
    else -> R.string.building_farm // CAPITAL/BRIDGE never reach the palette
}

/** Every placeable building: capitals have their own tool, bridges are built in play. */
private val EDITOR_BUILDINGS = listOf(
    Building.FARM,
    Building.TOWER,
    Building.STRONG_TOWER,
    Building.MINE,
    Building.MARKET,
    Building.LUMBER_CAMP,
    Building.WATCHTOWER,
    Building.PORT,
    Building.FISHERY,
)
