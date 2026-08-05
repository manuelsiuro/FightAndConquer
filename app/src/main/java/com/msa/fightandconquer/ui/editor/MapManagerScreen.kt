package com.msa.fightandconquer.ui.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.editor.CustomMapValidator
import com.msa.fightandconquer.core.share.ShareDecodeResult
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.resolve
import com.msa.fightandconquer.ui.share.MapShareManager

/**
 * The map library: every user-authored map with its playability badge, plus create,
 * delete, share and import. Follows `CampaignScreen`'s idiom — one scrolling column
 * of cards, back through the host — rather than introducing a new navigation style.
 */
@Composable
fun MapManagerScreen(
    maps: List<CustomMapDef>,
    share: MapShareManager,
    onNew: () -> Unit,
    onOpen: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onImported: (CustomMapDef) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf<CustomMapDef?>(null) }
    var shareTarget by remember { mutableStateOf<CustomMapDef?>(null) }
    var qrTarget by remember { mutableStateOf<CustomMapDef?>(null) }
    var showImport by remember { mutableStateOf(false) }

    fun handleImport(result: ShareDecodeResult) {
        when (result) {
            is ShareDecodeResult.Ok -> {
                onImported(result.def)
                Toast.makeText(
                    context,
                    context.getString(R.string.import_done, result.def.name),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            is ShareDecodeResult.Failed -> Toast.makeText(
                context,
                result.error.toUiText().resolve(context),
                Toast.LENGTH_LONG,
            ).show()
        }
        showImport = false
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { handleImport(share.importFile(it)) } }
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { handleImport(share.importImage(it)) } }

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
                        onShare = { shareTarget = map },
                        onDelete = { confirmDelete = map },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onNew, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.maps_new))
            }
            OutlinedButton(onClick = { showImport = true }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.maps_import))
            }
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

    shareTarget?.let { map ->
        ShareDialog(
            map = map,
            share = share,
            onQr = { qrTarget = map; shareTarget = null },
            onClose = { shareTarget = null },
        )
    }

    qrTarget?.let { map ->
        QrDialog(map, share) { qrTarget = null }
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text(stringResource(R.string.maps_import)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { handleImport(share.pasteCode()) }) {
                        Text(stringResource(R.string.import_paste))
                    }
                    TextButton(onClick = { fileLauncher.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.import_file))
                    }
                    TextButton(onClick = { imageLauncher.launch(arrayOf("image/*")) }) {
                        Text(stringResource(R.string.import_image))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImport = false }) {
                    Text(stringResource(R.string.maps_cancel))
                }
            },
        )
    }
}

@Composable
private fun ShareDialog(
    map: CustomMapDef,
    share: MapShareManager,
    onQr: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val qrFits = remember(map.id, map.modifiedAt) { share.qrFits(map) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.share_title, map.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    share.copyCode(map)
                    Toast.makeText(context, R.string.share_copied, Toast.LENGTH_SHORT).show()
                    onClose()
                }) {
                    Text(stringResource(R.string.share_copy_code))
                }
                TextButton(onClick = { share.shareFile(map); onClose() }) {
                    Text(stringResource(R.string.share_file))
                }
                TextButton(onClick = onQr, enabled = qrFits) {
                    Text(stringResource(R.string.share_qr))
                }
                if (!qrFits) {
                    Text(
                        stringResource(R.string.share_qr_too_big),
                        fontSize = 12.sp,
                        color = UiColors.inkMuted,
                    )
                }
                TextButton(onClick = { share.shareStegoImage(map); onClose() }) {
                    Text(stringResource(R.string.share_image))
                }
                Text(
                    stringResource(R.string.share_stego_hint),
                    fontSize = 12.sp,
                    color = UiColors.inkMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.maps_cancel)) }
        },
    )
}

@Composable
private fun QrDialog(map: CustomMapDef, share: MapShareManager, onClose: () -> Unit) {
    val qr = remember(map.id, map.modifiedAt) { share.qrBitmap(map) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(map.name, maxLines = 1) },
        text = {
            qr?.let { bitmap ->
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_qr),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.common_back)) }
        },
    )
}

@Composable
private fun MapRow(
    map: CustomMapDef,
    onOpen: () -> Unit,
    onShare: () -> Unit,
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
        IconButton(onClick = onShare) {
            Icon(
                Icons.Filled.Share,
                contentDescription = stringResource(R.string.cd_share_map),
                tint = UiColors.inkMuted,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.maps_delete_confirm),
                tint = UiColors.inkMuted,
            )
        }
    }
}
