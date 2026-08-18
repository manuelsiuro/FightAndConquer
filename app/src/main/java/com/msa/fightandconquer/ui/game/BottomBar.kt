package com.msa.fightandconquer.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.engine.PurchaseOption
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.UnitType
import com.msa.fightandconquer.ui.GameViewModel
import com.msa.fightandconquer.ui.HudState
import com.msa.fightandconquer.ui.InfoCard
import com.msa.fightandconquer.ui.InfoCardAction
import com.msa.fightandconquer.ui.PieceIcons
import com.msa.fightandconquer.ui.ShopInfo
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.buildingNameRes
import com.msa.fightandconquer.ui.guide.GuideCatalog
import com.msa.fightandconquer.ui.label
import com.msa.fightandconquer.ui.resolve
import com.msa.fightandconquer.ui.setup.scaleClickable
import com.msa.fightandconquer.ui.unitNameRes
import kotlinx.coroutines.delay

@Composable
internal fun BottomBar(
    state: HudState,
    infoCard: InfoCard?,
    viewModel: GameViewModel,
    onOpenGuide: (String?) -> Unit,
) {
    Column(
        Modifier.padding(HudGutter),
        verticalArrangement = Arrangement.spacedBy(HudSpacing),
    ) {
        state.selectedUnitNameRes?.let { nameRes ->
            SelectedUnitStrip(state, nameRes, viewModel)
        }
        infoCard?.let { info ->
            InfoCardView(info, onAction = viewModel::performInfoAction)
        }
        if (state.purchases.isNotEmpty() && state.currentIsHuman && state.banner == null) {
            PurchaseTray(state, onOpenGuide, viewModel)
        }

        // End-turn-with-unmoved-units arms on the first FAB tap and commits on the
        // second (FAB again or "End anyway") — the HUD's no-dialog rule.
        var endTurnArmed by remember { mutableStateOf(false) }
        LaunchedEffect(endTurnArmed) {
            if (endTurnArmed) {
                delay(3000)
                endTurnArmed = false
            }
        }
        LaunchedEffect(state.turnNumber, state.currentPlayer, state.freshUnitCount) {
            endTurnArmed = false
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.canUndo && state.currentIsHuman) {
                UndoButton(onClick = { viewModel.undo() })
            }
            Spacer(Modifier.weight(1f))
            if (state.currentIsHuman && state.winner == null && state.banner == null) {
                EndTurnFab(
                    pastel = UiColors.faction(state.currentPlayer),
                    onClick = {
                        when {
                            state.freshUnitCount == 0 -> viewModel.endTurn()
                            endTurnArmed -> {
                                endTurnArmed = false
                                viewModel.endTurn()
                            }
                            else -> endTurnArmed = true
                        }
                    },
                )
            }
        }
        AnimatedVisibility(endTurnArmed) {
            ArmedEndTurnRow(
                freshUnitCount = state.freshUnitCount,
                onCancel = { endTurnArmed = false },
                onEndAnyway = {
                    endTurnArmed = false
                    viewModel.endTurn()
                },
            )
        }
    }
}

/** Scale-S sibling of the info card: the selected unit's name, stats and disband. */
@Composable
private fun SelectedUnitStrip(state: HudState, nameRes: Int, viewModel: GameViewModel) {
    Row(
        Modifier
            .hudSurface(14.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.selectedUnitIconRes?.let { icon ->
            PiecePlinth(icon, PlinthScale.S)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(nameRes),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = UiColors.ink,
            )
            SelectedUnitStats(state)
        }
        state.selectedUnitDisbandRefund?.let { refund ->
            val description = stringResource(R.string.cd_hud_disband)
            Spacer(Modifier.width(8.dp))
            OutlinedHudButton(
                text = stringResource(R.string.hud_disband, refund),
                height = 32.dp,
                radius = 16.dp,
                fontSize = 13.sp,
                modifier = Modifier.semantics { contentDescription = description },
                onClick = { viewModel.disbandSelectedUnit() },
            )
        }
    }
}

/** The strip's 12 sp stats line: attack · defense · upkeep (the spec'd slot, Atk/Def pair). */
@Composable
private fun SelectedUnitStats(state: HudState) {
    val attack = state.selectedUnitAttack ?: return
    val defense = state.selectedUnitDefense ?: return
    val upkeep = state.selectedUnitUpkeep ?: return
    // A loaded transport fights with its cargo; an empty one cannot attack at all.
    val attackText = when {
        state.selectedUnitCargoAttack != null ->
            stringResource(R.string.info_value_cargo_attack, state.selectedUnitCargoAttack)
        attack == 0 -> stringResource(R.string.info_value_none)
        else -> stringResource(R.string.info_value_plain, attack)
    }
    val description = stringResource(R.string.cd_unit_stats, attackText, defense, upkeep)
    Row(
        Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_sword),
            contentDescription = null,
            Modifier.size(12.dp),
            tint = UiColors.inkMuted,
        )
        Spacer(Modifier.width(3.dp))
        Text(attackText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = UiColors.inkMuted)
        Text(stringResource(R.string.hud_stat_separator), fontSize = 12.sp, color = UiColors.inkMuted)
        Icon(
            painterResource(R.drawable.ic_shield),
            contentDescription = null,
            Modifier.size(12.dp),
            tint = UiColors.inkMuted,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            stringResource(R.string.info_value_plain, defense),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = UiColors.inkMuted,
        )
        Text(stringResource(R.string.hud_stat_separator), fontSize = 12.sp, color = UiColors.inkMuted)
        Text(
            // The tray's compact upkeep idiom — the spelled-out label wraps the strip.
            stringResource(R.string.shop_upkeep_per_turn, upkeep),
            fontSize = 12.sp,
            color = UiColors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCardView(info: InfoCard, onAction: (InfoCardAction) -> Unit) {
    Column(
        Modifier
            .hudSurface(16.dp)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            info.iconRes?.let { icon ->
                PiecePlinth(icon, PlinthScale.M)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        info.title.resolve(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = UiColors.ink,
                    )
                    info.factionIndex?.let { index ->
                        val description = stringResource(R.string.cd_faction_color, index + 1)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(UiColors.faction(index), CircleShape)
                                .semantics { contentDescription = description },
                        )
                    }
                }
                Text(info.subtitle.resolve(), fontSize = 12.sp, color = UiColors.inkMuted)
                if (info.stats.isNotEmpty()) {
                    // FlowRow so each pair wraps as a whole; a plain Row squeezes
                    // overflowing stats to letter-per-line.
                    FlowRow {
                        info.stats.forEachIndexed { index, stat ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (index > 0) {
                                    Text(
                                        stringResource(R.string.info_stat_separator),
                                        fontSize = 12.sp,
                                        color = UiColors.inkMuted,
                                    )
                                }
                                stat.iconRes?.let { icon ->
                                    Icon(
                                        painterResource(icon),
                                        contentDescription = null,
                                        Modifier.size(12.dp),
                                        tint = UiColors.inkMuted,
                                    )
                                    Spacer(Modifier.width(3.dp))
                                }
                                Text(
                                    stringResource(
                                        R.string.info_stat_pair,
                                        stat.label.resolve(),
                                        stat.value.resolve(),
                                    ),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = UiColors.inkMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (info.actions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = UiColors.divider)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                info.actions.forEachIndexed { index, action ->
                    val description = stringResource(
                        when (action) {
                            is InfoCardAction.RotateBridge -> R.string.cd_info_action_rotate
                            is InfoCardAction.Demolish -> R.string.cd_info_action_destroy
                            is InfoCardAction.Disband -> R.string.cd_hud_disband
                        },
                    )
                    // First action is the outlined primary; the rest take the
                    // controlFill secondary treatment — one idiom, two emphases.
                    if (index == 0) {
                        OutlinedHudButton(
                            text = action.label().resolve(),
                            modifier = Modifier.semantics { contentDescription = description },
                            onClick = { onAction(action) },
                        )
                    } else {
                        FilledHudButton(
                            text = action.label().resolve(),
                            fill = UiColors.controlFill,
                            textColor = UiColors.inkMuted,
                            modifier = Modifier.semantics { contentDescription = description },
                            onClick = { onAction(action) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseTray(
    state: HudState,
    onOpenGuide: (String?) -> Unit,
    viewModel: GameViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // A header floating over the board never runs bare — surface chip idiom.
        Box(
            Modifier
                .hudSurface(8.dp)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            HudMicroLabel(stringResource(R.string.hud_recruit))
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in state.purchases) {
                PurchaseCard(
                    option,
                    state.shopInfo,
                    civ = state.currentCiv,
                    affordable = option.cost <= state.treasury,
                    onLearn = onOpenGuide,
                    onBuy = { viewModel.buy(option) },
                )
            }
        }
    }
}

@Composable
private fun PurchaseCard(
    option: PurchaseOption,
    shop: ShopInfo,
    civ: Civilization,
    affordable: Boolean,
    onLearn: (String?) -> Unit,
    onBuy: () -> Unit,
) {
    val guideEntry = when (option) {
        is PurchaseOption.Unit -> GuideCatalog.forUnit(option.type)
        is PurchaseOption.Structure -> GuideCatalog.forStructure(option.type)
    }
    val nameRes = when (option) {
        is PurchaseOption.Unit -> unitNameRes(option.type, option.tier)
        is PurchaseOption.Structure -> buildingNameRes(option.type)
    }
    val iconRes = when (option) {
        is PurchaseOption.Unit -> PieceIcons.unit(civ, option.type, option.tier)
        is PurchaseOption.Structure -> PieceIcons.building(civ, option.type.building)
    }
    val detail = when (option) {
        is PurchaseOption.Unit -> stringResource(
            R.string.shop_upkeep_per_turn,
            when (option.type) {
                UnitType.ARCHER -> shop.archerUpkeep
                UnitType.CATAPULT -> shop.catapultUpkeep
                UnitType.TRANSPORT -> shop.transportUpkeep
                UnitType.WARSHIP -> shop.warshipUpkeep
                UnitType.FISHING_BOAT -> shop.fishingBoatUpkeep
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
            BuildingType.PORT -> stringResource(R.string.shop_income_per_turn, shop.portIncome)
            BuildingType.FISHERY -> stringResource(R.string.shop_income_up_to, shop.fisheryIncomeMax)
            BuildingType.BRIDGE -> stringResource(R.string.shop_walkway)
        }
    }
    val name = stringResource(nameRes)
    val description = when {
        option is PurchaseOption.Unit && affordable ->
            stringResource(R.string.cd_purchase_unit, name, option.cost, option.strength, option.defense)
        option is PurchaseOption.Unit ->
            stringResource(R.string.cd_purchase_unit_unaffordable, name, option.cost, option.strength, option.defense)
        affordable -> stringResource(R.string.cd_purchase_card, name, option.cost)
        else -> stringResource(R.string.cd_purchase_unaffordable, name, option.cost)
    }
    val learnDescription = stringResource(R.string.cd_guide_learn, name)
    Box(Modifier.size(128.dp)) {
        // An unaffordable card keeps full container opacity and stays tappable —
        // the engine's rejection surfaces the "not enough coins" toast.
        Column(
            Modifier
                .size(128.dp)
                .hudSurface(16.dp)
                .scaleClickable(onClick = onBuy)
                .semantics {
                    role = Role.Button
                    contentDescription = description
                }
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // The 128 dp card is the one fixed box — line heights are pinned so
            // plinth + name + cost + upkeep always fit without clipping.
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PiecePlinth(iconRes, PlinthScale.M, desaturated = !affordable)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    name,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = if (affordable) UiColors.ink else UiColors.inactiveGlyph,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_coin),
                        contentDescription = null,
                        Modifier.size(14.dp),
                        tint = if (affordable) UiColors.coin else UiColors.alert,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        stringResource(R.string.info_value_plain, option.cost),
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (affordable) UiColors.ink else UiColors.alert,
                    )
                    // Units carry their upkeep beside the cost so the third line is
                    // free for the combat pair — the 128 dp box has no room for four.
                    if (option is PurchaseOption.Unit) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            detail.uppercase(),
                            fontSize = 10.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            maxLines = 1,
                            color = if (affordable) UiColors.inkMuted else UiColors.inactiveGlyph,
                        )
                    }
                }
                if (option is PurchaseOption.Unit && option.type == UnitType.FISHING_BOAT) {
                    // No combat pair to show (0/0) — the third line sells the trade.
                    val statTint = if (affordable) UiColors.inkMuted else UiColors.inactiveGlyph
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_coin),
                            contentDescription = null,
                            Modifier.size(12.dp),
                            tint = statTint,
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            // The short form: the long "+N/turn on a shoal" clips on a 128 dp
                            // card (verified on device); the upkeep line already says /TURN.
                            stringResource(R.string.shop_income_on_shoal, shop.fishingBoatIncome),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = statTint,
                        )
                    }
                } else if (option is PurchaseOption.Unit) {
                    val statTint = if (affordable) UiColors.inkMuted else UiColors.inactiveGlyph
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_sword),
                            contentDescription = null,
                            Modifier.size(12.dp),
                            tint = statTint,
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            stringResource(R.string.info_value_plain, option.strength),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = statTint,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painterResource(R.drawable.ic_shield),
                            contentDescription = null,
                            Modifier.size(12.dp),
                            tint = statTint,
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            stringResource(R.string.info_value_plain, option.defense),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = statTint,
                        )
                    }
                } else {
                    Text(
                        detail.uppercase(),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        color = if (affordable) UiColors.inkMuted else UiColors.inactiveGlyph,
                    )
                }
            }
        }
        // Tap-through to the full Field Guide entry: 28 dp glyph, 48 dp target.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .clip(CircleShape)
                .scaleClickable { onLearn(guideEntry.id) }
                .semantics {
                    role = Role.Button
                    contentDescription = learnDescription
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(UiColors.controlFill, CircleShape)
                    .border(1.dp, UiColors.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    Modifier.size(15.dp),
                    tint = if (affordable) UiColors.inkMuted else UiColors.inactiveGlyph,
                )
            }
        }
    }
}

@Composable
private fun UndoButton(onClick: () -> Unit) {
    OutlinedHudButton(text = stringResource(R.string.hud_undo), onClick = onClick)
}

/** The 56 dp end-turn FAB in the current player's pastel. */
@Composable
private fun EndTurnFab(pastel: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val description = stringResource(R.string.hud_end_turn)
    Column(
        Modifier
            .size(56.dp)
            .shadow(2.dp, shape, ambientColor = UiColors.boardShadow, spotColor = UiColors.boardShadow)
            .background(pastel, shape)
            .border(1.dp, Color(0x243E3A36), shape)
            .clip(shape)
            .scaleClickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.hud_end),
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = UiColors.onFaction,
        )
        Text(
            stringResource(R.string.hud_end_turn_micro).uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = UiColors.onFaction.copy(alpha = 0.62f),
        )
    }
}

/** The armed end-turn confirm — one surface, replacing the old three-idiom row. */
@Composable
private fun ArmedEndTurnRow(
    freshUnitCount: Int,
    onCancel: () -> Unit,
    onEndAnyway: () -> Unit,
) {
    val cancelDescription = stringResource(R.string.cd_cancel_end_turn)
    Row(
        Modifier
            .fillMaxWidth()
            .hudSurface(20.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            HudMicroLabel(
                pluralStringResource(R.plurals.hud_units_unmoved, freshUnitCount, freshUnitCount),
            )
            Text(
                stringResource(R.string.hud_tap_again_to_end),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = UiColors.ink,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .size(48.dp)
                .background(UiColors.controlFill, RoundedCornerShape(16.dp))
                .border(1.dp, UiColors.hairline, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .scaleClickable(onClick = onCancel)
                .semantics {
                    role = Role.Button
                    contentDescription = cancelDescription
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, contentDescription = null, Modifier.size(18.dp), tint = UiColors.ink)
        }
        FilledHudButton(
            text = stringResource(R.string.hud_end_anyway),
            fill = UiColors.alert,
            textColor = UiColors.onAlert,
            height = 48.dp,
            radius = 16.dp,
            fontWeight = FontWeight.ExtraBold,
            onClick = onEndAnyway,
        )
    }
}

/** 44 dp outlined button — surface fill, 1 dp ink stroke, boardLift. */
@Composable
private fun OutlinedHudButton(
    text: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 44.dp,
    radius: androidx.compose.ui.unit.Dp = 14.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .height(height)
            .shadow(2.dp, shape, ambientColor = UiColors.boardShadow, spotColor = UiColors.boardShadow)
            .background(UiColors.surface, shape)
            .border(1.dp, UiColors.ink, shape)
            .clip(shape)
            .scaleClickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = fontSize, fontWeight = FontWeight.Bold, color = UiColors.ink, maxLines = 1)
    }
}

/** Filled sibling of [OutlinedHudButton] for secondary/destructive emphasis. */
@Composable
private fun FilledHudButton(
    text: String,
    fill: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 44.dp,
    radius: androidx.compose.ui.unit.Dp = 14.dp,
    fontWeight: FontWeight = FontWeight.Bold,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .height(height)
            .background(fill, shape)
            .clip(shape)
            .scaleClickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = fontWeight, color = textColor, maxLines = 1)
    }
}
