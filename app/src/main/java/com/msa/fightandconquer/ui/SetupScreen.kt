package com.msa.fightandconquer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.map.MapShape
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.Difficulty

@Composable
fun SetupScreen(
    generating: Boolean,
    onStart: (GameSetup) -> Unit,
    onBack: () -> Unit,
    /** Playable custom maps; empty hides the source row entirely. */
    customMaps: List<CustomMapDef> = emptyList(),
) {
    BackHandler(onBack = onBack)

    // rememberSaveable: the Activity is recreated on rotation / font-scale changes,
    // and losing the whole setup back to defaults is a silent, annoying reset.
    var playerCount by rememberSaveable { mutableIntStateOf(2) }
    var customMapId by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(GameMode.VS_AI) }
    var difficulty by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(Difficulty.NORMAL) }
    var size by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(MapSize.MEDIUM) }
    var shape by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(MapShape.CONTINENT) }
    var fogOfWar by rememberSaveable { mutableStateOf(false) }
    var specialUnits by rememberSaveable { mutableStateOf(true) }
    var diplomacy by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.background)
            .safeDrawingPadding()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.setup_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = UiColors.ink,
        )
        Spacer(Modifier.height(16.dp))

        if (generating) {
            CircularProgressIndicator(color = UiColors.faction(0))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.menu_generating), color = UiColors.ink)
            return@Column
        }

        if (customMaps.isNotEmpty()) {
            OptionRow(stringResource(R.string.menu_section_map_source)) {
                FilterChip(
                    selected = customMapId == null,
                    onClick = { customMapId = null },
                    label = { Text(stringResource(R.string.menu_source_generated)) },
                )
                FilterChip(
                    selected = customMapId != null,
                    onClick = { customMapId = customMaps.first().id },
                    label = { Text(stringResource(R.string.menu_source_custom)) },
                )
            }
        }
        if (customMapId != null) {
            // A custom scenario plays exactly as authored — seats, rules, treasuries
            // and goals are the map's own, so the generation options fall away.
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (map in customMaps) {
                        FilterChip(
                            selected = customMapId == map.id,
                            onClick = { customMapId = map.id },
                            label = { Text(map.name) },
                        )
                    }
                }
            }
        } else {
            OptionRow(stringResource(R.string.menu_section_opponents)) {
                for (count in 2..MAX_PLAYERS) {
                    val enemies = count - 1
                    FilterChip(
                        selected = playerCount == count,
                        onClick = { playerCount = count },
                        label = { Text(pluralStringResource(R.plurals.menu_enemy_count, enemies, enemies)) },
                    )
                }
            }
            OptionRow(stringResource(R.string.menu_section_mode)) {
                FilterChip(
                    selected = mode == GameMode.VS_AI,
                    onClick = { mode = GameMode.VS_AI },
                    label = { Text(stringResource(R.string.menu_mode_vs_ai)) },
                )
                FilterChip(
                    selected = mode == GameMode.PASS_AND_PLAY,
                    onClick = { mode = GameMode.PASS_AND_PLAY },
                    label = { Text(stringResource(R.string.menu_mode_pass_and_play)) },
                )
            }
            if (mode == GameMode.VS_AI) {
                OptionRow(stringResource(R.string.menu_section_difficulty)) {
                    for (option in Difficulty.selectable) {
                        FilterChip(
                            selected = difficulty == option,
                            onClick = { difficulty = option },
                            label = { Text(stringResource(option.labelRes())) },
                        )
                    }
                }
            }
            OptionRow(stringResource(R.string.menu_section_map_size)) {
                for (option in MapSize.entries) {
                    FilterChip(
                        selected = size == option,
                        onClick = { size = option },
                        label = { Text(stringResource(option.labelRes())) },
                    )
                }
            }
            OptionRow(stringResource(R.string.menu_section_map_type)) {
                for (option in MapShape.entries) {
                    FilterChip(
                        selected = shape == option,
                        onClick = { shape = option },
                        label = { Text(stringResource(option.labelRes())) },
                    )
                }
            }
            OptionRow(stringResource(R.string.menu_section_fog)) {
                FilterChip(
                    selected = !fogOfWar,
                    onClick = { fogOfWar = false },
                    label = { Text(stringResource(R.string.menu_fog_off)) },
                )
                FilterChip(
                    selected = fogOfWar,
                    onClick = { fogOfWar = true },
                    label = { Text(stringResource(R.string.menu_fog_on)) },
                )
            }
            OptionRow(stringResource(R.string.menu_section_specials)) {
                FilterChip(
                    selected = specialUnits,
                    onClick = { specialUnits = true },
                    label = { Text(stringResource(R.string.menu_toggle_on)) },
                )
                FilterChip(
                    selected = !specialUnits,
                    onClick = { specialUnits = false },
                    label = { Text(stringResource(R.string.menu_toggle_off)) },
                )
            }
            OptionRow(stringResource(R.string.menu_section_diplomacy)) {
                FilterChip(
                    selected = diplomacy,
                    onClick = { diplomacy = true },
                    label = { Text(stringResource(R.string.menu_toggle_on)) },
                )
                FilterChip(
                    selected = !diplomacy,
                    onClick = { diplomacy = false },
                    label = { Text(stringResource(R.string.menu_toggle_off)) },
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                onStart(
                    GameSetup(
                        playerCount,
                        mode,
                        difficulty,
                        size,
                        shape = shape,
                        fogOfWar = fogOfWar,
                        customMapId = customMapId,
                        specialUnits = specialUnits,
                        diplomacy = diplomacy,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = UiColors.faction(0)),
        ) { Text(stringResource(R.string.setup_start)) }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.common_back), color = UiColors.inkSecondary)
        }
    }
}

private const val MAX_PLAYERS = 4

/** Saves an enum by name so setup choices survive Activity recreation. */
private inline fun <reified T : Enum<T>> enumSaver(): Saver<T, String> =
    Saver(save = { it.name }, restore = { enumValueOf<T>(it) })

private fun Difficulty.labelRes() = difficultyLabelRes(this)

private fun MapSize.labelRes() = when (this) {
    MapSize.SMALL -> R.string.map_size_small
    MapSize.MEDIUM -> R.string.map_size_medium
    MapSize.LARGE -> R.string.map_size_large
}

private fun MapShape.labelRes() = when (this) {
    MapShape.CONTINENT -> R.string.map_shape_continent
    MapShape.ISLANDS -> R.string.map_shape_islands
    MapShape.ARCHIPELAGO -> R.string.map_shape_archipelago
}

@Composable
private fun OptionRow(label: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            color = UiColors.ink.copy(alpha = 0.6f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
