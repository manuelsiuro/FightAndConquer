package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.EconomyBreakdown
import com.msa.fightandconquer.ui.PieceIcons
import com.msa.fightandconquer.ui.UiColors

@Composable
internal fun EconomyPanel(economy: EconomyBreakdown, topAnchor: Dp) {
    HudSidePanel(topAnchor) {
        Column {
            PanelHeader(stringResource(R.string.economy_income))
            EconomyRow(
                stringResource(R.string.economy_hexes_row, economy.hexCount, economy.hexIncomePerHex),
                stringResource(R.string.economy_amount_positive, economy.hexIncome),
                iconRes = R.drawable.ic_coin,
                tint = UiColors.positive,
            )
            if (economy.depositBonus > 0) {
                EconomyRow(
                    stringResource(R.string.economy_fertile_row),
                    stringResource(R.string.economy_amount_positive, economy.depositBonus),
                    iconRes = PieceIcons.fertile,
                    tintable = false,
                    tint = UiColors.positive,
                )
            }
            for (row in economy.buildingRows) {
                EconomyRow(
                    stringResource(R.string.economy_building_row, row.count, stringResource(row.nameRes)),
                    stringResource(R.string.economy_amount_positive, row.total),
                    iconRes = row.iconRes,
                    tintable = false,
                    tint = UiColors.positive,
                )
            }
            if (economy.starvingCount > 0) {
                EconomyRow(
                    stringResource(R.string.economy_cut_off_row, economy.starvingCount),
                    stringResource(R.string.economy_cut_off_value),
                    valueColor = UiColors.alert,
                )
            }
        }
        if (economy.tiers.isNotEmpty()) {
            Column {
                PanelHeader(stringResource(R.string.economy_upkeep))
                for (row in economy.tiers) {
                    EconomyRow(
                        stringResource(
                            R.string.economy_upkeep_row,
                            row.count,
                            stringResource(row.nameRes),
                            row.each,
                        ),
                        stringResource(R.string.economy_amount_negative, row.total),
                        iconRes = row.iconRes,
                        tintable = false,
                        tint = UiColors.alert,
                    )
                }
            }
        }
        // Emphasis block: the two numbers the panel exists for.
        Column(
            Modifier
                .fillMaxWidth()
                .background(UiColors.controlFill, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.economy_net),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = UiColors.ink,
                )
                Text(
                    if (economy.net >= 0) {
                        stringResource(R.string.economy_amount_positive, economy.net)
                    } else {
                        stringResource(R.string.economy_amount_negative, -economy.net)
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (economy.net >= 0) UiColors.positive else UiColors.alert,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.economy_treasury_next),
                    fontSize = 12.sp,
                    color = UiColors.inkMuted,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_coin),
                        contentDescription = null,
                        Modifier.size(14.dp),
                        tint = UiColors.coin,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.info_value_plain, economy.projected),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = UiColors.ink,
                    )
                }
            }
        }
        when {
            economy.bankruptcyImminent ->
                WarningStrip(stringResource(R.string.economy_warn_bankruptcy), UiColors.alert)
            economy.upkeepRisk ->
                WarningStrip(stringResource(R.string.economy_warn_upkeep), UiColors.coin)
        }
    }
}

@Composable
private fun EconomyRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = UiColors.ink,
    iconRes: Int? = null,
    tintable: Boolean = true,
    tint: Color? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            // One 18 dp icon slot per row: tintable vectors get the 30% tinted
            // square, baked piece renders sit in the same slot untinted.
            if (iconRes != null && tint != null) {
                Box(
                    Modifier
                        .size(18.dp)
                        .background(tint.copy(alpha = 0.3f), RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (tintable) {
                        Icon(
                            painterResource(iconRes),
                            contentDescription = null,
                            Modifier.size(12.dp),
                            tint = tint,
                        )
                    } else {
                        Image(painterResource(iconRes), contentDescription = null, Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                fontSize = 13.sp,
                color = UiColors.ink,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Text(
            value,
            fontSize = 13.sp,
            color = valueColor,
            fontWeight = FontWeight.Bold,
        )
    }
    HorizontalDivider(color = UiColors.divider)
}

@Composable
private fun WarningStrip(text: String, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            Modifier.size(14.dp),
            tint = UiColors.ink,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = UiColors.ink,
        )
    }
}
