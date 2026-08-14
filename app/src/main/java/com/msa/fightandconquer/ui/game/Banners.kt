package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.civNameRes
import com.msa.fightandconquer.ui.setup.scaleClickable

/**
 * Full-screen overlay chrome: paper scrim at 92% with a single centered card
 * carrying all content — overlay text never floats on a bare scrim.
 */
@Composable
internal fun OverlayScrim(
    onTap: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(UiColors.overlayScrim)
            .pointerInput(onTap) { detectTapGestures { onTap?.invoke() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .hudSurface(20.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** 52 dp overlay button; [fill] null = outlined secondary treatment. */
@Composable
internal fun OverlayButton(
    text: String,
    fill: Color?,
    textColor: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(
                if (fill != null) {
                    Modifier.background(fill, shape)
                } else {
                    Modifier
                        .background(UiColors.surface, shape)
                        .border(1.dp, UiColors.ink, shape)
                },
            )
            .clip(shape)
            .scaleClickable(onClick = onClick)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = textColor)
    }
}

@Composable
internal fun TurnBanner(seat: Int, turnNumber: Int, civ: Civilization, onBegin: () -> Unit) {
    // The whole screen is the tap target.
    OverlayScrim(onTap = onBegin) {
        Box(Modifier.size(72.dp).background(UiColors.faction(seat), CircleShape))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HudMicroLabel(
                stringResource(R.string.banner_turn_micro, turnNumber + 1, stringResource(civNameRes(civ))),
            )
            Text(
                stringResource(R.string.banner_player, seat + 1),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = UiColors.ink,
            )
        }
        HorizontalDivider(Modifier.fillMaxWidth(), color = UiColors.divider)
        Text(
            stringResource(R.string.banner_tap_to_start),
            fontSize = 14.sp,
            color = UiColors.inkMuted,
        )
    }
}

@Composable
internal fun GameOverOverlay(winner: Int, onBackToMenu: () -> Unit) {
    OverlayScrim {
        Box(Modifier.size(72.dp).background(UiColors.faction(winner), CircleShape))
        // The winner's capital as the trophy on the plinth-L hero box.
        Box(
            Modifier
                .size(PlinthScale.L.plinth)
                .background(UiColors.controlFill, RoundedCornerShape(PlinthScale.L.radius))
                .border(1.dp, UiColors.hairline, RoundedCornerShape(PlinthScale.L.radius)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.piece_capital),
                contentDescription = null,
                Modifier.size(PlinthScale.L.render),
            )
        }
        Text(
            stringResource(R.string.game_over_winner, winner + 1),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = UiColors.ink,
            textAlign = TextAlign.Center,
        )
        OverlayButton(
            text = stringResource(R.string.game_over_back_to_menu),
            fill = UiColors.faction(winner),
            textColor = UiColors.onFaction,
            onClick = onBackToMenu,
        )
    }
}
