package com.msa.fightandconquer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.Difficulty

@Composable
fun MenuScreen(
    hasAutosave: Boolean,
    generating: Boolean,
    onNewGame: (GameSetup) -> Unit,
    onContinue: () -> Unit,
) {
    var playerCount by remember { mutableStateOf(2) }
    var mode by remember { mutableStateOf(GameMode.VS_AI) }
    var difficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    var size by remember { mutableStateOf(MapSize.MEDIUM) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.background)
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Fight & Conquer",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = UiColors.ink,
        )
        Text(
            "hex conquest",
            fontSize = 16.sp,
            color = UiColors.ink.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(36.dp))

        if (generating) {
            CircularProgressIndicator(color = UiColors.faction(0))
            Spacer(Modifier.height(16.dp))
            Text("Generating map…", color = UiColors.ink)
            return@Column
        }

        if (hasAutosave) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = UiColors.faction(0)),
            ) { Text("Continue") }
            Spacer(Modifier.height(24.dp))
        }

        OptionRow("Opponents") {
            for (count in 2..4) {
                FilterChip(
                    selected = playerCount == count,
                    onClick = { playerCount = count },
                    label = { Text("${count - 1} enem${if (count == 2) "y" else "ies"}") },
                )
            }
        }
        OptionRow("Mode") {
            FilterChip(
                selected = mode == GameMode.VS_AI,
                onClick = { mode = GameMode.VS_AI },
                label = { Text("vs AI") },
            )
            FilterChip(
                selected = mode == GameMode.PASS_AND_PLAY,
                onClick = { mode = GameMode.PASS_AND_PLAY },
                label = { Text("Pass & play") },
            )
        }
        if (mode == GameMode.VS_AI) {
            OptionRow("Difficulty") {
                for (d in Difficulty.entries) {
                    FilterChip(
                        selected = difficulty == d,
                        onClick = { difficulty = d },
                        label = { Text(d.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }
        OptionRow("Map size") {
            for (s in MapSize.entries) {
                FilterChip(
                    selected = size == s,
                    onClick = { size = s },
                    label = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        OutlinedButton(
            onClick = { onNewGame(GameSetup(playerCount, mode, difficulty, size)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("New game", color = UiColors.ink) }
    }
}

@Composable
private fun OptionRow(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 13.sp, color = UiColors.ink.copy(alpha = 0.6f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
