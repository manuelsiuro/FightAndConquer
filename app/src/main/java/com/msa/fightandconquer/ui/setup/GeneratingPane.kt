package com.msa.fightandconquer.ui.setup

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.map.MapShape
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.ui.UiColors
import kotlin.math.sin

/**
 * Replaces the whole form (bottom bar included) while the generator runs: three
 * breathing hexes, the flavor line, a sweeping progress bar and a Cancel that
 * abandons the job and restores the form untouched.
 */
@Composable
internal fun GeneratingPane(
    size: MapSize,
    shape: MapShape,
    seatCount: Int,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterVertically),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BreathingHex(26.dp, 30.dp, UiColors.faction(0), delayMs = 0)
            BreathingHex(34.dp, 39.dp, UiColors.faction(2), delayMs = 150)
            BreathingHex(26.dp, 30.dp, UiColors.faction(3), delayMs = 300)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.setup_generating_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = UiColors.ink,
            )
            val sizeShape = stringResource(
                R.string.setup_size_shape,
                stringResource(mapSizeLabelRes(size)),
                stringResource(mapShapeLowercaseRes(shape)),
            )
            Text(
                joinDots(listOf(sizeShape, pluralStringResource(R.plurals.setup_seat_count, seatCount, seatCount))),
                fontSize = 13.sp,
                color = UiColors.inkMuted,
            )
        }
        ProgressLine()
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.setup_cancel), fontSize = 13.sp, color = UiColors.inkMuted)
        }
    }
}

@Composable
private fun BreathingHex(width: Dp, height: Dp, color: Color, delayMs: Int) {
    val transition = rememberInfiniteTransition(label = "hexBreath")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "hexPhase",
    )
    // One soft pulse per cycle: sin swings scale 0.85..1 and alpha 0.55..1.
    val pulse = sin(Math.PI * t).toFloat()
    Canvas(
        Modifier
            .size(width, height)
            .graphicsLayer {
                val scale = 0.85f + 0.15f * pulse
                scaleX = scale
                scaleY = scale
                alpha = 0.55f + 0.45f * pulse
            },
    ) {
        val radius = this.size.height / 2f
        drawPath(hexPath(cx = this.size.width / 2f, cy = this.size.height / 2f, radius = radius), color)
    }
}

/** 180 × 4 dp track with a sage segment sweeping it indeterminately. */
@Composable
private fun ProgressLine() {
    val transition = rememberInfiniteTransition(label = "progress")
    val t by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "progressSweep",
    )
    Box(
        Modifier
            .size(180.dp, 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(UiColors.progressTrack),
    ) {
        Box(
            Modifier
                .offset(x = 180.dp * t)
                .size(63.dp, 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(UiColors.faction(0)),
        )
    }
}
