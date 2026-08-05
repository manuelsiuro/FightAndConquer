package com.msa.fightandconquer.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.editor.CustomMapValidator
import com.msa.fightandconquer.ui.UiColors

/**
 * The map library: every user-authored map with its playability badge, plus create and
 * delete. Follows `CampaignScreen`'s idiom — one scrolling column of cards, back
 * through the host — rather than introducing a new navigation style.
 */
@Composable
fun MapManagerScreen(
    maps: List<CustomMapDef>,
    onNew: () -> Unit,
    onOpen: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf<CustomMapDef?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.maps_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = UiColors.ink,
        )
        Spacer(Modifier.height(12.dp))

        if (maps.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.maps_empty),
                    fontSize = 14.sp,
                    color = UiColors.inkSecondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(maps, key = { it.id }) { map ->
                    MapRow(
                        map = map,
                        onOpen = { onOpen(map.id) },
                        onDelete = { confirmDelete = map },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.maps_new))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.common_back))
        }
        Spacer(Modifier.height(12.dp))
    }

    confirmDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.maps_delete_title)) },
            text = { Text(stringResource(R.string.maps_delete_message, doomed.name)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; onDelete(doomed.id) }) {
                    Text(stringResource(R.string.maps_delete_confirm), color = UiColors.alert)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.maps_cancel))
                }
            },
        )
    }
}

@Composable
private fun MapRow(
    map: CustomMapDef,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    // Validation is cheap at library scale and the result only changes with the map.
    val playable = remember(map.id, map.modifiedAt) {
        CustomMapValidator.validate(map).isEmpty()
    }
    val rowLabel = stringResource(R.string.cd_maps_row, map.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UiColors.panel, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .semantics { contentDescription = rowLabel }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                map.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = UiColors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.maps_meta, map.level.seats.size, map.level.map.tiles.size),
                fontSize = 13.sp,
                color = UiColors.inkSecondary,
            )
        }
        Text(
            stringResource(if (playable) R.string.maps_badge_playable else R.string.maps_badge_draft),
            fontSize = 12.sp,
            color = if (playable) UiColors.positive else UiColors.inkMuted,
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.maps_delete_confirm),
                tint = UiColors.inkMuted,
            )
        }
    }
}
