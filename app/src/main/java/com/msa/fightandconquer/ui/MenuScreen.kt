package com.msa.fightandconquer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.Difficulty

@Composable
fun MenuScreen(
    hasAutosave: Boolean,
    generating: Boolean,
    onNewGame: (GameSetup) -> Unit,
    onContinue: () -> Unit,
) {
    // rememberSaveable: the Activity is recreated on rotation / font-scale changes,
    // and losing the whole setup back to defaults is a silent, annoying reset.
    var playerCount by rememberSaveable { mutableIntStateOf(2) }
    var mode by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(GameMode.VS_AI) }
    var difficulty by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(Difficulty.NORMAL) }
    var size by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(MapSize.MEDIUM) }
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
            stringResource(R.string.menu_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = UiColors.ink,
        )
        Text(
            stringResource(R.string.menu_subtitle),
            fontSize = 16.sp,
            color = UiColors.ink.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(24.dp))

        // Decorative tableau of the actual game pieces (baked renders).
        Box(
            Modifier
                .fillMaxWidth()
                .background(UiColors.panel, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Row(
                Modifier.padding(top = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Image(
                    painterResource(R.drawable.piece_unit_t4),
                    contentDescription = null,
                    Modifier
                        .size(72.dp)
                        .offset(x = 10.dp),
                )
                Image(
                    painterResource(R.drawable.piece_capital),
                    contentDescription = null,
                    Modifier.size(110.dp),
                )
                Image(
                    painterResource(R.drawable.piece_tower),
                    contentDescription = null,
                    Modifier
                        .size(72.dp)
                        .offset(x = (-10).dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))

        if (generating) {
            CircularProgressIndicator(color = UiColors.faction(0))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.menu_generating), color = UiColors.ink)
            return@Column
        }

        if (hasAutosave) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = UiColors.faction(0)),
            ) { Text(stringResource(R.string.menu_continue)) }
            Spacer(Modifier.height(24.dp))
        }

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
                for (option in Difficulty.entries) {
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

        Spacer(Modifier.height(28.dp))
        val setup = GameSetup(
            playerCount,
            mode,
            difficulty,
            size,
            fogOfWar = fogOfWar,
            specialUnits = specialUnits,
            diplomacy = diplomacy,
        )
        if (hasAutosave) {
            OutlinedButton(
                onClick = { onNewGame(setup) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.menu_new_game), color = UiColors.ink) }
        } else {
            Button(
                onClick = { onNewGame(setup) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = UiColors.faction(0)),
            ) { Text(stringResource(R.string.menu_new_game)) }
        }
    }
}

private const val MAX_PLAYERS = 4

/** Saves an enum by name so setup choices survive Activity recreation. */
private inline fun <reified T : Enum<T>> enumSaver(): Saver<T, String> =
    Saver(save = { it.name }, restore = { enumValueOf<T>(it) })

private fun Difficulty.labelRes() = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.NORMAL -> R.string.difficulty_normal
    Difficulty.HARD -> R.string.difficulty_hard
}

private fun MapSize.labelRes() = when (this) {
    MapSize.SMALL -> R.string.map_size_small
    MapSize.MEDIUM -> R.string.map_size_medium
    MapSize.LARGE -> R.string.map_size_large
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
