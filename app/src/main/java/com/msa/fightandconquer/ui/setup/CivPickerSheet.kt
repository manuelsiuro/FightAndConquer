package com.msa.fightandconquer.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.ui.GameMode
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.civNameRes

internal fun civNoteRes(civ: Civilization) = when (civ) {
    Civilization.KINGDOM -> R.string.setup_civ_note_kingdom
    Civilization.VIKINGS -> R.string.setup_civ_note_vikings
    Civilization.SULTANATE -> R.string.setup_civ_note_sultanate
    Civilization.SHOGUNATE -> R.string.setup_civ_note_shogunate
}

/**
 * The per-seat civilization picker. Tapping a row applies it and dismisses —
 * no confirm step, the no-dialog idiom. The "?" on non-current rows deep-links
 * the Field Guide entry (the host closes the sheet first: the guide overlay
 * lives in the activity window, under this sheet's own window).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CivPickerSheet(
    seat: Int,
    mode: GameMode,
    current: Civilization,
    onPick: (Civilization) -> Unit,
    onLearn: (Civilization) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = UiColors.surface,
        scrimColor = Color(0x6B2E2A26),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(34.dp, 4.dp)
                    .background(UiColors.hairline, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(14.dp).background(UiColors.faction(seat), CircleShape))
                Text(
                    stringResource(R.string.setup_civ_sheet_title, seatSheetName(seat, mode)),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = UiColors.ink,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (civ in Civilization.entries) {
                    CivRow(civ, current = civ == current, onPick = { onPick(civ) }, onLearn = { onLearn(civ) })
                }
            }
            Text(
                stringResource(R.string.setup_sheet_hint),
                modifier = Modifier.fillMaxWidth(),
                fontSize = 11.sp,
                color = UiColors.inkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CivRow(
    civ: Civilization,
    current: Boolean,
    onPick: () -> Unit,
    onLearn: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val border =
        if (current) Modifier.border(2.dp, UiColors.faction(0), shape)
        else Modifier.border(1.dp, UiColors.hairline, shape)
    val civName = stringResource(civNameRes(civ))
    val learnCd = stringResource(R.string.cd_guide_learn, civName)
    Row(
        Modifier
            .fillMaxWidth()
            .background(UiColors.surface, shape)
            .then(border)
            .scaleClickable(onClick = onPick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PieceTrio(civ, heights = listOf(30.dp, 36.dp, 26.dp), gap = 4.dp, itemWidth = 26.dp)
        Column(Modifier.weight(1f)) {
            Text(civName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = UiColors.ink)
            Text(stringResource(civNoteRes(civ)), fontSize = 11.sp, color = UiColors.inkMuted)
        }
        if (current) {
            Box(
                Modifier.size(20.dp).background(UiColors.faction(0), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = UiColors.onFaction,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Box(
                Modifier
                    .size(28.dp)
                    .scaleClickable(onClick = onLearn)
                    .semantics { contentDescription = learnCd },
                contentAlignment = Alignment.Center,
            ) {
                Text("?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = UiColors.inkMuted)
            }
        }
    }
}
