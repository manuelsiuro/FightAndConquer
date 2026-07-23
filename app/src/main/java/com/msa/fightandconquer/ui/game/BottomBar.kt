package com.msa.fightandconquer.ui.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.engine.PurchaseOption
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.UnitType
import com.msa.fightandconquer.ui.GameViewModel
import com.msa.fightandconquer.ui.HudState
import com.msa.fightandconquer.ui.InfoCard
import com.msa.fightandconquer.ui.PieceIcons
import com.msa.fightandconquer.ui.ShopInfo
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.guide.GuideCatalog
import com.msa.fightandconquer.ui.resolve
import com.msa.fightandconquer.ui.unitNameRes
import kotlinx.coroutines.delay

@Composable
internal fun BottomBar(
    state: HudState,
    infoCard: InfoCard?,
    viewModel: GameViewModel,
    onOpenGuide: (String?) -> Unit,
) {
    Column(Modifier.padding(HudGutter)) {
        infoCard?.let { info ->
            InfoCardView(info)
            Spacer(Modifier.height(8.dp))
        }
        state.selectedUnitNameRes?.let { nameRes ->
            Surface(shape = RoundedCornerShape(12.dp), color = UiColors.panel, shadowElevation = 3.dp) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    state.selectedUnitIconRes?.let { icon ->
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(UiColors.background, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(painterResource(icon), contentDescription = null, Modifier.size(40.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    Column {
                        Text(
                            stringResource(nameRes),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = UiColors.ink,
                        )
                        Text(
                            stringResource(R.string.hud_selected_unit_hint),
                            fontSize = 12.sp,
                            color = UiColors.inkSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (state.purchases.isNotEmpty() && state.currentIsHuman && state.banner == null) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in state.purchases) {
                    PurchaseCard(
                        option,
                        state.shopInfo,
                        affordable = option.cost <= state.treasury,
                        onLearn = onOpenGuide,
                        onBuy = { viewModel.buy(option) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        var confirmEndTurn by remember { mutableStateOf(false) }
        LaunchedEffect(confirmEndTurn) {
            if (confirmEndTurn) {
                delay(3000)
                confirmEndTurn = false
            }
        }
        LaunchedEffect(state.turnNumber, state.currentPlayer, state.freshUnitCount) {
            confirmEndTurn = false
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.canUndo && state.currentIsHuman) {
                OutlinedButton(onClick = { viewModel.undo() }) {
                    Text(stringResource(R.string.hud_undo), color = UiColors.ink)
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.currentIsHuman && state.winner == null && state.banner == null) {
                AnimatedContent(confirmEndTurn, label = "endTurnConfirm") { confirming ->
                    if (!confirming) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                if (state.freshUnitCount == 0) viewModel.endTurn() else confirmEndTurn = true
                            },
                            containerColor = UiColors.faction(state.currentPlayer),
                            contentColor = UiColors.ink,
                        ) { Text(stringResource(R.string.hud_end_turn), fontWeight = FontWeight.Bold) }
                    } else {
                        val cancelDescription = stringResource(R.string.cd_cancel_end_turn)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                pluralStringResource(
                                    R.plurals.hud_units_unmoved,
                                    state.freshUnitCount,
                                    state.freshUnitCount,
                                ),
                                fontSize = 13.sp,
                                color = UiColors.inkSecondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { confirmEndTurn = false },
                                modifier = Modifier.semantics { contentDescription = cancelDescription },
                            ) {
                                Text(stringResource(R.string.hud_cancel_symbol), color = UiColors.ink)
                            }
                            Spacer(Modifier.width(8.dp))
                            ExtendedFloatingActionButton(
                                onClick = { confirmEndTurn = false; viewModel.endTurn() },
                                containerColor = UiColors.alert,
                                contentColor = Color.White,
                            ) { Text(stringResource(R.string.hud_end_anyway), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCardView(info: InfoCard) {
    Surface(shape = RoundedCornerShape(12.dp), color = UiColors.panel, shadowElevation = 3.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            info.iconRes?.let { icon ->
                // Plinth behind the transparent render so it reads on the panel.
                Box(
                    Modifier
                        .size(64.dp)
                        .background(UiColors.background, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(icon), contentDescription = null, Modifier.size(60.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        info.title.resolve(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = UiColors.ink,
                    )
                    info.factionIndex?.let { index ->
                        val description = stringResource(R.string.cd_faction_color, index + 1)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(UiColors.faction(index), CircleShape)
                                .semantics { contentDescription = description },
                        )
                    }
                }
                Text(info.subtitle.resolve(), fontSize = 12.sp, color = UiColors.inkSecondary)
                if (info.stats.isNotEmpty()) {
                    Row {
                        info.stats.forEachIndexed { index, stat ->
                            if (index > 0) {
                                Text(
                                    stringResource(R.string.info_stat_separator),
                                    fontSize = 12.sp,
                                    color = UiColors.inkFaint,
                                )
                            }
                            Text(
                                stringResource(
                                    R.string.info_stat_pair,
                                    stat.label.resolve(),
                                    stat.value.resolve(),
                                ),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = UiColors.inkSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseCard(
    option: PurchaseOption,
    shop: ShopInfo,
    affordable: Boolean,
    onLearn: (String?) -> Unit,
    onBuy: () -> Unit,
) {
    val guideEntry = when (option) {
        is PurchaseOption.Unit -> GuideCatalog.forUnit(option.type)
        is PurchaseOption.Structure -> GuideCatalog.forStructure(option.type)
    }
    // Only a placement requirement is worth the cramped card space; the "specials
    // enabled" meta-requirement on units is redundant here (they're already on sale).
    val requirementRes = (option as? PurchaseOption.Structure)?.let { guideEntry.requirementRes }
    val nameRes = when (option) {
        is PurchaseOption.Unit -> unitNameRes(option.type, option.tier)
        is PurchaseOption.Structure -> when (option.type) {
            BuildingType.FARM -> R.string.building_farm
            BuildingType.TOWER -> R.string.building_tower
            BuildingType.STRONG_TOWER -> R.string.building_castle
            BuildingType.MINE -> R.string.building_mine
            BuildingType.MARKET -> R.string.building_market
            BuildingType.LUMBER_CAMP -> R.string.building_lumber_camp
            BuildingType.WATCHTOWER -> R.string.building_watchtower
        }
    }
    val iconRes = when (option) {
        is PurchaseOption.Unit -> PieceIcons.unit(option.type, option.tier)
        is PurchaseOption.Structure -> PieceIcons.building(option.type.building)
    }
    val detail = when (option) {
        is PurchaseOption.Unit -> stringResource(
            R.string.shop_upkeep_per_turn,
            when (option.type) {
                UnitType.ARCHER -> shop.archerUpkeep
                UnitType.CATAPULT -> shop.catapultUpkeep
                UnitType.SOLDIER -> shop.unitUpkeep[option.tier - 1]
            },
        )
        is PurchaseOption.Structure -> when (option.type) {
            BuildingType.FARM -> stringResource(R.string.shop_income_per_turn, shop.farmIncome)
            BuildingType.TOWER -> stringResource(R.string.shop_defense, shop.towerDefense)
            BuildingType.STRONG_TOWER -> stringResource(R.string.shop_defense, shop.strongTowerDefense)
            BuildingType.MINE -> stringResource(R.string.shop_income_per_turn, shop.mineIncome)
            BuildingType.MARKET -> stringResource(R.string.shop_income_up_to, shop.marketIncomeMax)
            BuildingType.LUMBER_CAMP -> stringResource(R.string.shop_income_up_to, shop.lumberCampIncomeMax)
            BuildingType.WATCHTOWER -> stringResource(R.string.shop_vision, shop.watchtowerVision)
        }
    }
    val name = stringResource(nameRes)
    val description = if (affordable) {
        stringResource(R.string.cd_purchase_card, name, option.cost)
    } else {
        stringResource(R.string.cd_purchase_unaffordable, name, option.cost)
    }
    val learnDescription = stringResource(R.string.cd_guide_learn, name)
    Box {
        Card(
            modifier = Modifier
                .width(92.dp)
                .clickable(enabled = affordable, role = Role.Button, onClick = onBuy)
                .semantics { contentDescription = description },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (affordable) UiColors.panel else UiColors.panel.copy(alpha = 0.5f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painterResource(iconRes),
                    contentDescription = null,
                    Modifier.size(44.dp),
                    alpha = if (affordable) 1f else 0.35f,
                    colorFilter = if (affordable) {
                        null
                    } else {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                    },
                )
                Text(name, fontSize = 13.sp, color = UiColors.ink, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_coin),
                        contentDescription = null,
                        Modifier.size(12.dp),
                        tint = if (affordable) UiColors.coin else UiColors.alert,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        stringResource(R.string.info_value_plain, option.cost),
                        fontSize = 12.sp,
                        color = if (affordable) UiColors.inkSecondary else UiColors.alert,
                    )
                }
                Text(detail, fontSize = 12.sp, color = UiColors.inkMuted)
                requirementRes?.let { req ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(req),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = UiColors.inkFaint,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        // Tap-through to the full Field Guide entry for this piece.
        IconButton(
            onClick = { onLearn(guideEntry.id) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(26.dp)
                .semantics { contentDescription = learnDescription },
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                Modifier.size(15.dp),
                tint = UiColors.inkFaint,
            )
        }
    }
}
