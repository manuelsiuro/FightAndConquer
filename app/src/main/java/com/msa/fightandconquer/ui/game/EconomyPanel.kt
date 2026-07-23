package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.EconomyBreakdown
import com.msa.fightandconquer.ui.PieceIcons
import com.msa.fightandconquer.ui.UiColors

@Composable
internal fun EconomyPanel(economy: EconomyBreakdown) {
    Surface(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(start = HudGutter, top = TopBarHeight + HudGutter)
            .width(264.dp),
        shape = RoundedCornerShape(16.dp),
        color = UiColors.panel,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.economy_income),
                fontSize = 12.sp,
                color = UiColors.inkMuted,
                letterSpacing = 0.8.sp,
            )
            EconomyRow(
                stringResource(R.string.economy_hexes_row, economy.hexCount, economy.hexIncomePerHex),
                stringResource(R.string.economy_amount_positive, economy.hexIncome),
                iconRes = R.drawable.ic_coin,
                iconTint = UiColors.inkMuted,
            )
            if (economy.depositBonus > 0) {
                EconomyRow(
                    stringResource(R.string.economy_fertile_row),
                    stringResource(R.string.economy_amount_positive, economy.depositBonus),
                    iconRes = PieceIcons.fertile,
                )
            }
            for (row in economy.buildingRows) {
                EconomyRow(
                    stringResource(R.string.economy_building_row, row.count, stringResource(row.nameRes)),
                    stringResource(R.string.economy_amount_positive, row.total),
                    iconRes = row.iconRes,
                )
            }
            if (economy.starvingCount > 0) {
                EconomyRow(
                    stringResource(R.string.economy_cut_off_row, economy.starvingCount),
                    stringResource(R.string.economy_cut_off_value),
                    valueColor = UiColors.alert,
                )
            }
            if (economy.tiers.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.economy_upkeep),
                    fontSize = 12.sp,
                    color = UiColors.inkMuted,
                    letterSpacing = 0.8.sp,
                )
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
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = UiColors.ink.copy(alpha = 0.12f))
            Spacer(Modifier.height(6.dp))
            EconomyRow(
                stringResource(R.string.economy_net),
                if (economy.net >= 0) {
                    stringResource(R.string.economy_amount_positive, economy.net)
                } else {
                    stringResource(R.string.economy_amount_negative, -economy.net)
                },
                bold = true,
                valueColor = if (economy.net >= 0) UiColors.positive else UiColors.alert,
            )
            EconomyRow(
                stringResource(R.string.economy_treasury, economy.treasury),
                stringResource(R.string.economy_projection, economy.projected),
                bold = true,
            )
            when {
                economy.bankruptcyImminent ->
                    WarningStrip(stringResource(R.string.economy_warn_bankruptcy), UiColors.alert, Color.White)
                economy.upkeepRisk ->
                    WarningStrip(stringResource(R.string.economy_warn_upkeep), UiColors.toastWarning, UiColors.ink)
            }
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
    iconTint: Color? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            iconRes?.let { icon ->
                if (iconTint != null) {
                    Icon(painterResource(icon), contentDescription = null, Modifier.size(16.dp), tint = iconTint)
                } else {
                    Image(painterResource(icon), contentDescription = null, Modifier.size(20.dp))
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                fontSize = 13.sp,
                color = UiColors.ink,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        Text(
            value,
            fontSize = if (bold) 14.sp else 13.sp,
            color = valueColor,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun WarningStrip(text: String, background: Color, foreground: Color) {
    Spacer(Modifier.height(8.dp))
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = background,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
        )
    }
}
