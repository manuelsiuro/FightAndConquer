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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.guide.FieldGuide

@Composable
fun MenuScreen(
    hasAutosave: Boolean,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onCampaign: () -> Unit,
    onMapEditor: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    var guideOpen by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
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

            if (hasAutosave) {
                PrimaryMenuButton(stringResource(R.string.menu_continue), onContinue)
                Spacer(Modifier.height(12.dp))
                SecondaryMenuButton(stringResource(R.string.menu_new_game), onNewGame)
            } else {
                PrimaryMenuButton(stringResource(R.string.menu_new_game), onNewGame)
            }
            Spacer(Modifier.height(12.dp))
            SecondaryMenuButton(stringResource(R.string.menu_campaign), onCampaign)
            Spacer(Modifier.height(12.dp))
            SecondaryMenuButton(stringResource(R.string.menu_map_editor), onMapEditor)
            Spacer(Modifier.height(12.dp))
            SecondaryMenuButton(stringResource(R.string.guide_menu_entry)) { guideOpen = true }
            Spacer(Modifier.height(12.dp))
            SecondaryMenuButton(stringResource(R.string.menu_settings), onSettings)
            Spacer(Modifier.height(12.dp))
            SecondaryMenuButton(stringResource(R.string.menu_about), onAbout)
        }

        if (guideOpen) {
            FieldGuide(onClose = { guideOpen = false })
        }
    }
}

@Composable
private fun PrimaryMenuButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = UiColors.faction(0)),
    ) { Text(label) }
}

@Composable
private fun SecondaryMenuButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(label, color = UiColors.ink) }
}
