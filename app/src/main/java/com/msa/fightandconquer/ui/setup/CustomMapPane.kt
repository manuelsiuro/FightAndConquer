package com.msa.fightandconquer.ui.setup

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.editor.CustomMapValidator
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.share.MinimapRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Segmented Generated / My-maps control. Only composed when the player has
 * authored maps at all — an empty library keeps the screen single-purpose.
 */
@Composable
internal fun MapSourceToggle(sourceCustom: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).cardSurface(14.dp).padding(3.dp)) {
        SourceSegment(stringResource(R.string.menu_source_generated), selected = !sourceCustom, Modifier.weight(1f)) {
            onChange(false)
        }
        SourceSegment(stringResource(R.string.menu_source_custom), selected = sourceCustom, Modifier.weight(1f)) {
            onChange(true)
        }
    }
}

@Composable
private fun SourceSegment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .then(if (selected) Modifier.background(FilledInk, shape) else Modifier)
            .clip(shape)
            .scaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) OnFilledInk else UiColors.inkSecondary,
        )
    }
}

/**
 * The custom-map list that replaces the generation form: an info card
 * explaining the as-authored contract, then one row per stored map. Drafts show
 * dimmed with their issue count and cannot be picked.
 */
@Composable
internal fun CustomMapPane(
    maps: List<CustomMapDef>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().cardSurface(16.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(18.dp).background(UiColors.faction(2), CircleShape))
            Text(
                stringResource(R.string.setup_custom_info),
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = UiColors.inkSecondary,
            )
        }
        for (map in maps) {
            CustomMapRow(map, selected = map.id == selectedId, onSelect = onSelect)
        }
    }
}

@Composable
private fun CustomMapRow(map: CustomMapDef, selected: Boolean, onSelect: (String) -> Unit) {
    val violations = remember(map.id, map.modifiedAt) { CustomMapValidator.validate(map) }
    val playable = violations.isEmpty()
    val thumb by produceState<ImageBitmap?>(null, map.id, map.modifiedAt) {
        value = withContext(Dispatchers.Default) {
            MinimapRenderer.render(map, size = THUMB_PX, caption = false).asImageBitmap()
        }
    }

    val shape = RoundedCornerShape(16.dp)
    val border =
        if (selected) Modifier.border(2.dp, UiColors.faction(0), shape)
        else Modifier.border(1.dp, UiColors.hairline, shape)
    Row(
        Modifier
            .fillMaxWidth()
            .background(UiColors.surface, shape)
            .then(border)
            .clip(shape)
            .then(
                if (playable) Modifier.scaleClickable { onSelect(map.id) }
                else Modifier.alpha(0.72f),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(UiColors.controlFill)) {
            thumb?.let {
                Image(it, contentDescription = null, modifier = Modifier.size(52.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(map.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = UiColors.ink)
            if (playable) {
                val seats = map.level.seats.size
                val hexes = map.level.map.tiles.size
                val shapeName = map.level.map.generatorParams?.shape
                Text(
                    if (shapeName != null) {
                        stringResource(
                            R.string.setup_map_meta_shaped,
                            seats, hexes, stringResource(mapShapeLabelRes(shapeName)),
                        )
                    } else {
                        stringResource(R.string.setup_map_meta, seats, hexes)
                    },
                    fontSize = 11.sp,
                    color = UiColors.inkMuted,
                )
            } else {
                Text(
                    pluralStringResource(R.plurals.setup_map_draft, violations.size, violations.size),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UiColors.alert,
                )
            }
        }
        if (playable) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(map.level.seats.size) { seat ->
                    Box(Modifier.size(11.dp).background(UiColors.faction(seat), CircleShape))
                }
            }
        }
    }
}

/** 52 dp thumbnail at 3x density. */
private const val THUMB_PX = 156
