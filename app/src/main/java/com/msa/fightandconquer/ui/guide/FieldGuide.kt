package com.msa.fightandconquer.ui.guide

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.UiColors

/** Flattened list model so section headers and entries share one scrollable [LazyColumn]. */
private sealed interface GuideRow {
    data class Header(@StringRes val titleRes: Int) : GuideRow
    data class Entry(val entry: GuideEntry) : GuideRow
}

private fun guideRows(): List<GuideRow> = buildList {
    for (section in GuideCatalog.sections) {
        add(GuideRow.Header(section.titleRes))
        for (entry in section.entries) add(GuideRow.Entry(entry))
    }
}

/**
 * Full-screen, browsable reference for every unit, building and resource. A self-contained
 * overlay driven by [GuideCatalog] — hosts hoist a boolean and render this on top; there is
 * no ViewModel or navigation state involved. [focusEntryId] scrolls straight to one entry
 * (used when a purchase card taps through to "learn more").
 */
@Composable
internal fun FieldGuide(onClose: () -> Unit, focusEntryId: String? = null) {
    // System Back closes the guide rather than falling through to the host screen.
    BackHandler(onBack = onClose)
    val rows = remember { guideRows() }
    val listState = rememberLazyListState()
    LaunchedEffect(focusEntryId) {
        if (focusEntryId != null) {
            val index = rows.indexOfFirst { it is GuideRow.Entry && it.entry.id == focusEntryId }
            if (index >= 0) listState.scrollToItem(index)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            // Tapping the scrim outside the panel dismisses.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = UiColors.background,
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .heightIn(max = 640.dp)
                // Consume clicks so taps on the panel don't fall through to the scrim.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column {
                GuideHeader(onClose)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(rows) { row ->
                        when (row) {
                            is GuideRow.Header -> SectionHeader(row.titleRes)
                            is GuideRow.Entry -> EntryCard(row.entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideHeader(onClose: () -> Unit) {
    val closeCd = stringResource(R.string.cd_guide_close)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.guide_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = UiColors.ink,
            )
            Text(
                stringResource(R.string.guide_subtitle),
                fontSize = 13.sp,
                color = UiColors.inkMuted,
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = closeCd }) {
            Icon(Icons.Default.Close, contentDescription = null, tint = UiColors.inkSecondary)
        }
    }
}

@Composable
private fun SectionHeader(@StringRes titleRes: Int) {
    Text(
        stringResource(titleRes).uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = UiColors.inkFaint,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun EntryCard(entry: GuideEntry) {
    Surface(shape = RoundedCornerShape(14.dp), color = UiColors.panel, shadowElevation = 1.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                entry.iconRes?.let { icon ->
                    Box(
                        Modifier
                            .size(52.dp)
                            .background(UiColors.background, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(icon), contentDescription = null, Modifier.size(46.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(entry.nameRes),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UiColors.ink,
                    )
                    Text(
                        stringResource(entry.descRes),
                        fontSize = 13.sp,
                        color = UiColors.inkSecondary,
                    )
                }
            }

            if (entry.stats.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                StatRow(entry)
            }

            entry.requirementRes?.let { req ->
                Spacer(Modifier.height(8.dp))
                RequirementChip(req)
            }

            entry.howToRes?.let { how ->
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.guide_how_label).uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = UiColors.inkFaint,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(how),
                    fontSize = 13.sp,
                    color = UiColors.ink,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun StatRow(entry: GuideEntry) {
    Row {
        entry.stats.forEachIndexed { index, stat ->
            if (index > 0) {
                Text(
                    stringResource(R.string.info_stat_separator),
                    fontSize = 12.sp,
                    color = UiColors.inkFaint,
                )
            }
            val value = if (stat.arg != null) {
                stringResource(stat.valueRes, stat.arg)
            } else {
                stringResource(stat.valueRes)
            }
            Text(
                stringResource(R.string.info_stat_pair, stringResource(stat.labelRes), value),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = UiColors.inkSecondary,
            )
        }
    }
}

@Composable
private fun RequirementChip(@StringRes requirementRes: Int) {
    Surface(shape = RoundedCornerShape(8.dp), color = UiColors.toastWarning) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.guide_req_label).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = UiColors.inkMuted,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(requirementRes),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = UiColors.ink,
            )
        }
    }
}
