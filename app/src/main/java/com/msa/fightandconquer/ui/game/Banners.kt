package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.UiColors

@Composable
internal fun TurnBanner(seat: Int, onBegin: () -> Unit) {
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
internal fun GameOverOverlay(winner: Int, onBackToMenu: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6FFFFFF))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The winner's capital as the trophy, on their faction-color disc.
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(72.dp).background(UiColors.faction(winner), CircleShape))
                Image(
                    painterResource(R.drawable.piece_capital),
                    contentDescription = null,
                    Modifier.size(96.dp),
                )
            }
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
