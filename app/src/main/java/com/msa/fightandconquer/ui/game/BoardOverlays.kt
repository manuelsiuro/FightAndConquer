package com.msa.fightandconquer.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.render.scene.BoardScene
import com.msa.fightandconquer.ui.CoinPopup
import com.msa.fightandconquer.ui.LabelKind
import com.msa.fightandconquer.ui.OverlayLabel
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.resolve
import dev.romainguy.kotlin.math.Float2
import kotlin.math.roundToInt

private val ChipHalfWidth = 24.dp
private val ChipVerticalLift = 20.dp
private val PopupHalfWidth = 30.dp
private val PopupVerticalLift = 26.dp

@Composable
internal fun AnchorOverlay(scene: BoardScene?, labels: List<OverlayLabel>, popups: List<CoinPopup>) {
    if (scene == null || (labels.isEmpty() && popups.isEmpty())) return
    // Held as State and read inside the offset lambdas: anchors change every frame
    // while the camera moves, and a composition-scope read would recompose the whole
    // overlay instead of just re-placing it.
    val anchors = scene.anchors.collectAsState()
    val density = LocalDensity.current
    val chipDx = with(density) { ChipHalfWidth.roundToPx() }
    val chipDy = with(density) { ChipVerticalLift.roundToPx() }
    val popupDx = with(density) { PopupHalfWidth.roundToPx() }
    val popupDy = with(density) { PopupVerticalLift.roundToPx() }

    Box(Modifier.fillMaxSize()) {
        for (label in labels) {
            key(label.hex) {
                DefenseChip(label, anchorOffset(anchors, label.hex, chipDx, chipDy))
            }
        }
        for (popup in popups) {
            key(popup.id) {
                CoinPopupText(popup, anchorOffset(anchors, popup.hex, popupDx, popupDy))
            }
        }
    }
}

/** Places a widget at a hex's projected screen position, deferring the state read to layout. */
private fun anchorOffset(
    anchors: State<Map<Hex, Float2>>,
    hex: Hex,
    dx: Int,
    dy: Int,
): Modifier = Modifier.offset {
    val position = anchors.value[hex] ?: return@offset IntOffset(-10_000, -10_000)
    IntOffset(position.x.roundToInt() - dx, position.y.roundToInt() - dy)
}

@Composable
private fun DefenseChip(label: OverlayLabel, modifier: Modifier) {
    val background = when (label.kind) {
        LabelKind.CAPTURABLE -> UiColors.chipCapturable
        LabelKind.BLOCKED -> UiColors.chipBlocked
    }
    val description = when (label.kind) {
        LabelKind.CAPTURABLE -> stringResource(R.string.cd_defense_capturable, label.defense)
        LabelKind.BLOCKED -> stringResource(R.string.cd_defense_blocked, label.defense)
    }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn(tween(120))) {
        Surface(shape = RoundedCornerShape(50), color = background, shadowElevation = 2.dp) {
            Row(
                Modifier
                    .padding(horizontal = 7.dp, vertical = 2.dp)
                    .clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_shield),
                    contentDescription = null,
                    Modifier.size(11.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    stringResource(R.string.info_value_plain, label.defense),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CoinPopupText(popup: CoinPopup, modifier: Modifier) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(1100)) }
    Surface(
        modifier = modifier
            .offset { IntOffset(0, (-56f * rise.value).roundToInt()) }
            .graphicsLayer { alpha = 1f - rise.value * rise.value },
        shape = RoundedCornerShape(50),
        color = UiColors.panel,
        shadowElevation = 2.dp,
    ) {
        Text(
            popup.text.resolve(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = UiColors.positive,
        )
    }
}
