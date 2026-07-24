package com.msa.fightandconquer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R

/** Temporary screen for features that are announced in the menu but not built yet. */
@Composable
fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.background)
            .safeDrawingPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = UiColors.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.placeholder_coming_soon),
            fontSize = 16.sp,
            color = UiColors.ink.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onBack) {
            Text(stringResource(R.string.common_back), color = UiColors.ink)
        }
    }
}
