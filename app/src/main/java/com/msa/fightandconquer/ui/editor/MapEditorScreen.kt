package com.msa.fightandconquer.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.render.FilamentHost
import com.msa.fightandconquer.render.scene.BoardScene
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.resolve

private class SceneRef {
    var scene by mutableStateOf<BoardScene?>(null)
}

/**
 * The editor canvas: the real 3D board (via the same `FilamentHost`/`BoardScene` stack
 * as play, in editor mode), a brush palette below, and a live issues counter. The
 * scene lives in a plain holder, never Compose state — the black-screen gotcha.
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

    Box(Modifier.fillMaxSize().background(UiColors.background)) {
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

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
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
                    modifier = Modifier.weight(1f),
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

            BrushPalette(ui, session)
        }
    }

    if (showIssues) {
        AlertDialog(
            onDismissRequest = { showIssues = false },
            title = { Text(stringResource(R.string.editor_issues_title)) },
            text = {
                Column {
                    ui.violations.take(8).forEach { violation ->
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
}

@Composable
private fun BrushPalette(ui: EditorSession.Ui, session: EditorSession) {
    val seats = ui.def.level.seats.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UiColors.panel, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrushChip(ui, EditorSession.Brush.Land, stringResource(R.string.editor_brush_land), session)
        BrushChip(ui, EditorSession.Brush.Sea, stringResource(R.string.editor_brush_sea), session)
        BrushChip(ui, EditorSession.Brush.Erase, stringResource(R.string.editor_brush_erase), session)
        repeat(seats) { seat ->
            BrushChip(
                ui,
                EditorSession.Brush.Owner(seat),
                stringResource(R.string.editor_brush_owner, seat + 1),
                session,
                swatchSeat = seat,
            )
        }
        repeat(seats) { seat ->
            BrushChip(
                ui,
                EditorSession.Brush.Capital(seat),
                stringResource(R.string.editor_brush_capital, seat + 1),
                session,
                swatchSeat = seat,
            )
        }
        if (seats < MAX_EDITOR_SEATS) {
            BrushChip(
                ui,
                EditorSession.Brush.Capital(seats),
                stringResource(R.string.editor_brush_add_player),
                session,
            )
        }
    }
}

@Composable
private fun BrushChip(
    ui: EditorSession.Ui,
    brush: EditorSession.Brush,
    label: String,
    session: EditorSession,
    swatchSeat: Int? = null,
) {
    FilterChip(
        selected = ui.brush == brush,
        onClick = { session.setBrush(brush) },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                swatchSeat?.let { seat ->
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(UiColors.faction(seat), CircleShape),
                    )
                    Spacer(Modifier.size(4.dp))
                }
                Text(label, fontSize = 12.sp)
            }
        },
    )
}

/** MapParams allows 2..6 players; the palette stops offering "add" at the ceiling. */
private const val MAX_EDITOR_SEATS = 6
