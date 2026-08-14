package com.msa.fightandconquer.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.map.MapShape
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.ui.UiColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal fun mapSizeLabelRes(size: MapSize) = when (size) {
    MapSize.SMALL -> R.string.map_size_small
    MapSize.MEDIUM -> R.string.map_size_medium
    MapSize.LARGE -> R.string.map_size_large
}

internal fun mapShapeLabelRes(shape: MapShape) = when (shape) {
    MapShape.CONTINENT -> R.string.map_shape_continent
    MapShape.ISLANDS -> R.string.map_shape_islands
    MapShape.ARCHIPELAGO -> R.string.map_shape_archipelago
}

/** Lowercase shape for running text ("Medium continent"). */
internal fun mapShapeLowercaseRes(shape: MapShape) = when (shape) {
    MapShape.CONTINENT -> R.string.setup_shape_lc_continent
    MapShape.ISLANDS -> R.string.setup_shape_lc_islands
    MapShape.ARCHIPELAGO -> R.string.setup_shape_lc_archipelago
}

/**
 * The one disclosure holding every world/rule option. Collapsed it still tells
 * the whole story through [summary] — folded, never hidden.
 */
@Composable
internal fun WorldRulesSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    summary: String,
    size: MapSize,
    onSize: (MapSize) -> Unit,
    shape: MapShape,
    onShape: (MapShape) -> Unit,
    fogOfWar: Boolean,
    onFog: (Boolean) -> Unit,
    specialUnits: Boolean,
    onSpecial: (Boolean) -> Unit,
    diplomacy: Boolean,
    onDiplomacy: (Boolean) -> Unit,
) {
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, tween(250), label = "chevron")
    Column(Modifier.fillMaxWidth().cardSurface(16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .scaleClickable(onClick = onToggle)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.setup_world_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = UiColors.ink,
                )
                Text(summary, fontSize = 11.sp, color = UiColors.inkMuted)
            }
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = UiColors.inkMuted,
                modifier = Modifier.rotate(chevron),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(250)) + fadeOut(tween(250)),
        ) {
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MapSizeSelector(size, onSize)
                MapTypeSelector(shape, onShape)
                Column {
                    RuleSwitchRow(stringResource(R.string.menu_section_fog), fogOfWar, onFog)
                    HorizontalDivider(color = UiColors.divider, thickness = 1.dp)
                    RuleSwitchRow(stringResource(R.string.menu_section_specials), specialUnits, onSpecial)
                    HorizontalDivider(color = UiColors.divider, thickness = 1.dp)
                    RuleSwitchRow(stringResource(R.string.menu_section_diplomacy), diplomacy, onDiplomacy)
                }
            }
        }
    }
}

@Composable
private fun MapSizeSelector(size: MapSize, onSize: (MapSize) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in MapSize.entries) {
            val selected = size == option
            val ground =
                if (selected) {
                    Modifier.background(UiColors.faction(3), RoundedCornerShape(11.dp))
                } else {
                    Modifier.cardSurface(11.dp)
                }
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .then(ground)
                    .scaleClickable { onSize(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(mapSizeLabelRes(option)),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) UiColors.onFaction else UiColors.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun MapTypeSelector(shape: MapShape, onShape: (MapShape) -> Unit) {
    val accent = UiColors.faction(3)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in MapShape.entries) {
            val selected = shape == option
            val corner = RoundedCornerShape(12.dp)
            val ground =
                if (selected) {
                    Modifier
                        .background(accent.copy(alpha = 0.16f), corner)
                        .border(1.dp, accent, corner)
                        .clip(corner)
                } else {
                    Modifier.cardSurface(12.dp)
                }
            Column(
                Modifier
                    .weight(1f)
                    .then(ground)
                    .scaleClickable { onShape(option) }
                    .padding(9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                HexCluster(option, tint = if (selected) accent else UiColors.inactiveGlyph)
                Text(
                    stringResource(mapShapeLabelRes(option)),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) UiColors.ink else UiColors.inkMuted,
                )
            }
        }
    }
}

/**
 * A tiny pictogram of the map shape as pointy-top hexes: one landmass, two
 * islands, or a scatter of mixed sizes — each hex sized by width, all
 * bottom-aligned like the board.
 */
@Composable
private fun HexCluster(shape: MapShape, tint: Color) {
    // Pairs of (hex width dp, gap-after dp).
    val spec = when (shape) {
        MapShape.CONTINENT -> listOf(14f to 2f, 14f to 2f, 14f to 0f)
        MapShape.ISLANDS -> listOf(14f to 5f, 14f to 0f)
        MapShape.ARCHIPELAGO -> listOf(11f to 3f, 14f to 3f, 9f to 0f)
    }
    val totalW = spec.sumOf { (w, g) -> (w + g).toDouble() }.toFloat()
    val maxH = spec.maxOf { (w, _) -> w } * 2f / SQRT3
    Canvas(Modifier.size(totalW.dp, maxH.dp)) {
        var x = 0f
        for ((w, gap) in spec) {
            val wPx = w.dp.toPx()
            val radius = wPx / SQRT3
            drawPath(hexPath(cx = x + wPx / 2f, cy = size.height - radius, radius = radius), tint)
            x += wPx + gap.dp.toPx()
        }
    }
}

private fun hexPath(cx: Float, cy: Float, radius: Float): Path {
    val path = Path()
    for (i in 0 until 6) {
        val angle = Math.toRadians(60.0 * i - 30.0)
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private val SQRT3 = sqrt(3f)

@Composable
private fun RuleSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = UiColors.ink)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = UiColors.faction(0),
                uncheckedTrackColor = UiColors.controlFill,
                uncheckedBorderColor = UiColors.hairline,
                uncheckedThumbColor = UiColors.inkMuted,
            ),
        )
    }
}
