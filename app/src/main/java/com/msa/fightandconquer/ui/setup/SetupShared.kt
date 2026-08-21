package com.msa.fightandconquer.ui.setup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.UnitType
import com.msa.fightandconquer.ui.GameMode
import com.msa.fightandconquer.ui.PieceIcons
import com.msa.fightandconquer.ui.UiColors

/** Pointy-top hexagon outline centered at ([cx], [cy]) — the setup screens' shared glyph. */
internal fun hexPath(cx: Float, cy: Float, radius: Float): androidx.compose.ui.graphics.Path {
    val path = androidx.compose.ui.graphics.Path()
    for (i in 0 until 6) {
        val angle = Math.toRadians(60.0 * i - 30.0)
        val x = cx + radius * kotlin.math.cos(angle).toFloat()
        val y = cy + radius * kotlin.math.sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Saves an enum by name so setup choices survive Activity recreation. */
internal inline fun <reified T : Enum<T>> enumSaver(): Saver<T, String> =
    Saver(save = { it.name }, restore = { enumValueOf<T>(it) })

/** Saves the per-seat civilization picks by name, same contract as [enumSaver]. */
internal fun civListSaver(): Saver<List<Civilization>, ArrayList<String>> = Saver(
    save = { ArrayList(it.map(Civilization::name)) },
    restore = { saved -> saved.map(Civilization::valueOf) },
)

/**
 * Card ground shared by every setup surface: fill + 1 dp hairline border. The
 * design elevates with borders rather than shadows, unlike the HUD panels.
 */
@Composable
internal fun Modifier.cardSurface(radius: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return background(UiColors.surface, shape)
        .border(1.dp, UiColors.hairline, shape)
        .clip(shape)
}

/**
 * Clickable with the design's press feedback: standard ripple plus a 0.96 scale,
 * both driven by one interaction source so they stay in step.
 */
@Composable
internal fun Modifier.scaleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "pressScale")
    return graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
}

/** Uppercase micro-label above a section (10 sp, wide tracking). */
@Composable
internal fun SetupMicroLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = UiColors.inkMuted,
    )
}

/**
 * The knight/castle/tower trio of a civilization's baked renders, bottom-aligned
 * with the castle as the tall centerpiece — the tableau in miniature.
 * [itemWidth] null stretches the three across the row (tableau); set, it pins
 * each piece's width (picker rows).
 */
@Composable
internal fun PieceTrio(
    civ: Civilization,
    heights: List<Dp>,
    gap: Dp,
    itemWidth: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val icons = listOf(
        PieceIcons.unit(civ, UnitType.SOLDIER, 4),
        PieceIcons.building(civ, Building.STRONG_TOWER),
        PieceIcons.building(civ, Building.TOWER),
    )
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom,
    ) {
        icons.forEachIndexed { index, res ->
            Image(
                painterResource(res),
                contentDescription = null,
                modifier = Modifier
                    .then(if (itemWidth != null) Modifier.width(itemWidth) else Modifier.weight(1f))
                    .height(heights[index]),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** "Yours" / "AI n" in vs AI, "Player n" in pass-and-play. */
@Composable
internal fun seatLabel(seat: Int, mode: GameMode): String = when {
    mode == GameMode.PASS_AND_PLAY -> stringResource(R.string.setup_seat_player, seat + 1)
    seat == 0 -> stringResource(R.string.setup_seat_you)
    else -> stringResource(R.string.setup_seat_ai, seat)
}

/** The sheet header's name for a seat — "You" rather than "Yours". */
@Composable
internal fun seatSheetName(seat: Int, mode: GameMode): String = when {
    mode == GameMode.PASS_AND_PLAY -> stringResource(R.string.setup_seat_player, seat + 1)
    seat == 0 -> stringResource(R.string.setup_sheet_seat_you)
    else -> stringResource(R.string.setup_seat_ai, seat)
}

/** Folds parts into "a · b · c" through the localized joiner resource. */
@Composable
internal fun joinDots(parts: List<String>): String {
    val fmt = stringResource(R.string.setup_join_dot)
    return parts.reduce { acc, part -> String.format(fmt, acc, part) }
}
