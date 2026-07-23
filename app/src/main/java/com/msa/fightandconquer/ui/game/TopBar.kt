package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.GameViewModel
import com.msa.fightandconquer.ui.HudState
import com.msa.fightandconquer.ui.UiColors

private val MinTouchTarget = 48.dp

@Composable
internal fun TopBar(state: HudState, proposalCount: Int, viewModel: GameViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    val factionDescription = stringResource(R.string.cd_faction_color, state.currentPlayer + 1)
    val economyDescription = stringResource(R.string.cd_open_economy)
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.padding(HudGutter),
            shape = RoundedCornerShape(16.dp),
            color = UiColors.panel,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(UiColors.faction(state.currentPlayer), CircleShape)
                        .semantics { contentDescription = factionDescription },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.currentIsHuman) {
                        stringResource(R.string.hud_player, state.currentPlayer + 1)
                    } else {
                        stringResource(R.string.hud_ai_player, state.currentPlayer + 1)
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = UiColors.ink,
                )
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier
                        .clickable(role = Role.Button) { viewModel.toggleEconomyPanel() }
                        .semantics { contentDescription = economyDescription }
                        .defaultMinSize(minHeight = MinTouchTarget)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_coin),
                        contentDescription = null,
                        Modifier.size(16.dp),
                        tint = UiColors.coin,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.hud_treasury, state.treasury),
                        color = UiColors.ink,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    val net = state.income - state.upkeep
                    Text(
                        if (net >= 0) {
                            stringResource(R.string.hud_net_positive, net)
                        } else {
                            stringResource(R.string.hud_net_negative, net)
                        },
                        color = if (net >= 0) UiColors.positive else UiColors.alert,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.hud_turn, state.turnNumber + 1),
                    color = UiColors.inkMuted,
                    fontSize = 13.sp,
                )
                if (state.currentIsHuman && state.banner == null && state.freshUnitCount > 0) {
                    Spacer(Modifier.width(10.dp))
                    val freshDescription =
                        stringResource(R.string.cd_fresh_units, state.freshUnitCount)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = UiColors.faction(state.currentPlayer).copy(alpha = 0.3f),
                        modifier = Modifier
                            .clickable(role = Role.Button) { viewModel.focusNextFreshUnit() }
                            .semantics { contentDescription = freshDescription }
                            .defaultMinSize(minHeight = MinTouchTarget),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.hud_fresh_units, state.freshUnitCount),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UiColors.ink,
                            )
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                painterResource(R.drawable.ic_flag),
                                contentDescription = null,
                                Modifier.size(13.dp),
                                tint = UiColors.ink,
                            )
                        }
                    }
                }
                if (state.currentIsHuman && state.banner == null && proposalCount > 0) {
                    Spacer(Modifier.width(10.dp))
                    val pactDescription = stringResource(R.string.cd_open_diplomacy)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = UiColors.toastWarning.copy(alpha = 0.35f),
                        modifier = Modifier
                            .clickable(role = Role.Button) { viewModel.toggleDiplomacyPanel() }
                            .semantics { contentDescription = pactDescription }
                            .defaultMinSize(minHeight = MinTouchTarget),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_pact),
                                contentDescription = null,
                                Modifier.size(14.dp),
                                tint = UiColors.ink,
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                stringResource(R.string.hud_pact_badge, proposalCount),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UiColors.ink,
                            )
                        }
                    }
                }
                if (state.aiThinking) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.hud_ai_thinking),
                        color = UiColors.inkMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(top = HudGutter, end = HudGutter)) {
            Surface(shape = CircleShape, color = UiColors.panel, shadowElevation = 4.dp) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_open_menu),
                        tint = UiColors.ink,
                    )
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(12.dp),
                containerColor = UiColors.panel,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hud_diplomacy)) },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_pact), contentDescription = null)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = UiColors.ink,
                        leadingIconColor = UiColors.inkSecondary,
                    ),
                    onClick = {
                        menuOpen = false
                        viewModel.toggleDiplomacyPanel()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hud_resign)) },
                    leadingIcon = {
                        // Null description: the adjacent label already names the action,
                        // so a description here would make TalkBack read it twice.
                        Icon(Icons.Default.Warning, contentDescription = null)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = UiColors.alert,
                        leadingIconColor = UiColors.alert,
                    ),
                    onClick = {
                        menuOpen = false
                        viewModel.surrender()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hud_exit)) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = UiColors.ink,
                        leadingIconColor = UiColors.inkSecondary,
                    ),
                    onClick = {
                        menuOpen = false
                        viewModel.backToMenu()
                    },
                )
            }
        }
    }
}
