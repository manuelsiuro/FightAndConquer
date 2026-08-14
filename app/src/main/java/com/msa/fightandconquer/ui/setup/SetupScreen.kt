package com.msa.fightandconquer.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.map.MapShape
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.ui.GameMode
import com.msa.fightandconquer.ui.GameSetup
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.civNameRes
import com.msa.fightandconquer.ui.difficultyLabelRes
import com.msa.fightandconquer.ui.guide.FieldGuide
import com.msa.fightandconquer.ui.guide.GuideCatalog

/**
 * The "New Game" quick-start screen: a tableau summarizing the match, the four
 * primary decisions, everything else folded into one World & rules disclosure,
 * and Start pinned to the thumb zone — a default match is one tap away.
 */
@Composable
fun SetupScreen(
    generating: Boolean,
    onStart: (GameSetup) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    /** The whole library, drafts included; empty hides the source toggle entirely. */
    customMaps: List<CustomMapDef> = emptyList(),
) {
    // rememberSaveable: the Activity is recreated on rotation / font-scale changes,
    // and losing the whole setup back to defaults is a silent, annoying reset. All
    // state lives up here, above AnimatedContent, so the generating round-trip
    // (cancel included) hands the form back exactly as it was left.
    var playerCount by rememberSaveable { mutableIntStateOf(2) }
    var mode by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(GameMode.VS_AI) }
    var difficulty by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(Difficulty.NORMAL) }
    var size by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(MapSize.MEDIUM) }
    var shape by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(MapShape.CONTINENT) }
    var fogOfWar by rememberSaveable { mutableStateOf(false) }
    var specialUnits by rememberSaveable { mutableStateOf(true) }
    var diplomacy by rememberSaveable { mutableStateOf(true) }
    // Always MAX_PLAYERS long: shrinking the seat count parks the hidden picks,
    // growing it re-reveals them.
    var civs by rememberSaveable(stateSaver = civListSaver()) {
        mutableStateOf(List(MAX_PLAYERS) { Civilization.DEFAULT })
    }
    var sourceCustom by rememberSaveable { mutableStateOf(false) }
    var customMapId by rememberSaveable { mutableStateOf<String?>(null) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var pickerSeat by rememberSaveable { mutableStateOf<Int?>(null) }
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    var guideFocus by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    // A map deleted (or library emptied) since the id was picked must not linger.
    LaunchedEffect(customMaps) {
        if (customMapId != null && customMaps.none { it.id == customMapId }) customMapId = null
        if (customMaps.isEmpty()) sourceCustom = false
    }

    BackHandler { if (generating) onCancel() else onBack() }

    Box(Modifier.fillMaxSize().background(UiColors.background)) {
        AnimatedContent(
            targetState = generating,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "setupPane",
        ) { busy ->
            if (busy) {
                GeneratingPane(size, shape, playerCount, onCancel)
            } else {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        TopBar(onBack)
                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(scrollState)
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (customMaps.isNotEmpty()) {
                                MapSourceToggle(sourceCustom) { sourceCustom = it }
                            }
                            if (sourceCustom) {
                                CustomMapPane(customMaps, customMapId) { customMapId = it }
                            } else {
                                SetupTableauCard(
                                    civ = civs[0],
                                    playerCount = playerCount,
                                    title = tableauTitle(civs[0], playerCount, mode),
                                    subtitle = tableauSubtitle(mode, difficulty, size, shape),
                                )
                                OpponentCountSelector(playerCount) { playerCount = it }
                                ModeAndDifficultyRow(
                                    mode = mode,
                                    onMode = { mode = it },
                                    difficulty = difficulty,
                                    onDifficulty = { difficulty = it },
                                )
                                SeatCivGrid(playerCount, mode, civs) { pickerSeat = it }
                                WorldRulesSection(
                                    expanded = advancedExpanded,
                                    onToggle = { advancedExpanded = !advancedExpanded },
                                    summary = worldSummary(size, shape, fogOfWar, specialUnits, diplomacy),
                                    size = size, onSize = { size = it },
                                    shape = shape, onShape = { shape = it },
                                    fogOfWar = fogOfWar, onFog = { fogOfWar = it },
                                    specialUnits = specialUnits, onSpecial = { specialUnits = it },
                                    diplomacy = diplomacy, onDiplomacy = { diplomacy = it },
                                )
                            }
                            // Room to scroll the last section clear of the sticky bar.
                            Spacer(Modifier.height(150.dp))
                            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                        }
                    }
                    StartBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        label = startLabel(sourceCustom, customMapId, customMaps),
                        enabled = !sourceCustom || customMapId != null,
                        onStart = {
                            onStart(
                                GameSetup(
                                    playerCount = playerCount,
                                    mode = mode,
                                    difficulty = difficulty,
                                    size = size,
                                    shape = shape,
                                    fogOfWar = fogOfWar,
                                    customMapId = customMapId.takeIf { sourceCustom },
                                    specialUnits = specialUnits,
                                    diplomacy = diplomacy,
                                    civs = civs.take(playerCount),
                                ),
                            )
                        },
                        onBack = onBack,
                    )
                }
            }
        }

        pickerSeat?.let { seat ->
            CivPickerSheet(
                seat = seat,
                mode = mode,
                current = civs[seat],
                onPick = { civ ->
                    civs = civs.mapIndexed { i, c -> if (i == seat) civ else c }
                    pickerSeat = null
                },
                onLearn = { civ ->
                    // The guide overlay lives in the activity window, under the
                    // sheet's own window — close the sheet before opening it.
                    pickerSeat = null
                    guideFocus = GuideCatalog.civEntryId(civ)
                    guideOpen = true
                },
                onDismiss = { pickerSeat = null },
            )
        }

        if (guideOpen) FieldGuide(onClose = { guideOpen = false }, focusEntryId = guideFocus)
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(UiColors.controlFill, CircleShape)
                .scaleClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.setup_back_to_menu),
                tint = UiColors.ink,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            stringResource(R.string.setup_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = UiColors.ink,
        )
    }
}

/** Sticky Start + back link over a fade-in scrim so content passes underneath. */
@Composable
private fun StartBar(
    modifier: Modifier,
    label: String,
    enabled: Boolean,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(UiColors.background.copy(alpha = 0f), UiColors.background),
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(UiColors.background)
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 26.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onStart,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UiColors.faction(0),
                    contentColor = UiColors.onFaction,
                    disabledContainerColor = UiColors.faction(0).copy(alpha = 0.4f),
                    disabledContentColor = UiColors.onFaction.copy(alpha = 0.5f),
                ),
            ) {
                Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.setup_back_to_menu), fontSize = 13.sp, color = UiColors.inkMuted)
            }
        }
    }
}

@Composable
private fun tableauTitle(civ: Civilization, playerCount: Int, mode: GameMode): String {
    val name = stringResource(civNameRes(civ))
    return if (mode == GameMode.PASS_AND_PLAY) {
        pluralStringResource(R.plurals.setup_tableau_players, playerCount, name, playerCount)
    } else {
        val enemies = playerCount - 1
        pluralStringResource(R.plurals.setup_tableau_vs, enemies, name, enemies)
    }
}

@Composable
private fun tableauSubtitle(
    mode: GameMode,
    difficulty: Difficulty,
    size: MapSize,
    shape: MapShape,
): String {
    val sizeShape = stringResource(
        R.string.setup_size_shape,
        stringResource(mapSizeLabelRes(size)),
        stringResource(mapShapeLowercaseRes(shape)),
    )
    val source = stringResource(R.string.setup_source_generated_label)
    val parts =
        if (mode == GameMode.VS_AI) {
            listOf(stringResource(difficultyLabelRes(difficulty)), sizeShape, source)
        } else {
            listOf(sizeShape, source)
        }
    return joinDots(parts)
}

@Composable
private fun worldSummary(
    size: MapSize,
    shape: MapShape,
    fogOfWar: Boolean,
    specialUnits: Boolean,
    diplomacy: Boolean,
): String = joinDots(
    listOf(
        stringResource(mapSizeLabelRes(size)),
        stringResource(mapShapeLabelRes(shape)),
        stringResource(if (fogOfWar) R.string.setup_sum_fog_on else R.string.setup_sum_fog_off),
        stringResource(if (specialUnits) R.string.setup_sum_special_on else R.string.setup_sum_special_off),
        stringResource(if (diplomacy) R.string.setup_sum_diplo_on else R.string.setup_sum_diplo_off),
    ),
)

@Composable
private fun startLabel(
    sourceCustom: Boolean,
    customMapId: String?,
    customMaps: List<CustomMapDef>,
): String {
    val selected = customMaps.firstOrNull { it.id == customMapId }
    return if (sourceCustom && selected != null) {
        stringResource(R.string.setup_play_map, selected.name)
    } else {
        stringResource(R.string.setup_start)
    }
}
