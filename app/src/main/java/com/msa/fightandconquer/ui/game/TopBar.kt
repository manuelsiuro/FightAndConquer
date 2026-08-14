package com.msa.fightandconquer.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.GameViewModel
import com.msa.fightandconquer.ui.HudState
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.civNameRes
import com.msa.fightandconquer.ui.setup.scaleClickable
import kotlinx.coroutines.delay

private val MinTouchTarget = 48.dp

@Composable
internal fun TopBar(
    state: HudState,
    proposalCount: Int,
    diplomacyOpen: Boolean,
    isCampaign: Boolean,
    viewModel: GameViewModel,
    onOpenGuide: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val factionDescription = stringResource(R.string.cd_faction_color, state.currentPlayer + 1)
    val economyDescription = stringResource(R.string.cd_open_economy)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = HudGutter, end = HudGutter, top = TopBarTopInset)
            .hudSurface(16.dp)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(UiColors.faction(state.currentPlayer), CircleShape)
                    .semantics { contentDescription = factionDescription },
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    seatLabel(state.currentPlayer, state.currentIsHuman),
                    fontWeight = FontWeight.Bold,
                    color = UiColors.ink,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        R.string.hud_identity_line,
                        stringResource(civNameRes(state.currentCiv)),
                        stringResource(R.string.hud_turn, state.turnNumber + 1),
                    ),
                    color = UiColors.inkMuted,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .scaleClickable { viewModel.toggleEconomyPanel() }
                    .semantics { contentDescription = economyDescription }
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_coin),
                    contentDescription = null,
                    Modifier.size(16.dp),
                    tint = UiColors.coin,
                )
                Text(
                    stringResource(R.string.hud_treasury, state.treasury),
                    color = UiColors.ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                val net = state.income - state.upkeep
                Text(
                    if (net >= 0) {
                        stringResource(R.string.hud_net_positive, net)
                    } else {
                        stringResource(R.string.hud_net_negative, net)
                    },
                    color = if (net >= 0) UiColors.positive else UiColors.alert,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(4.dp))
            TopBarCircle(
                glyph = painterResource(R.drawable.ic_pact),
                glyphSize = 20.dp,
                description = stringResource(R.string.cd_open_diplomacy),
                active = diplomacyOpen,
                badge = proposalCount > 0,
                onClick = { viewModel.toggleDiplomacyPanel() },
            )
            Spacer(Modifier.width(4.dp))
            Box {
                TopBarCircle(
                    glyph = null,
                    glyphSize = 20.dp,
                    description = stringResource(R.string.cd_open_menu),
                    active = menuOpen,
                    badge = false,
                    onClick = { menuOpen = true },
                )
                OverflowMenu(
                    expanded = menuOpen,
                    isCampaign = isCampaign,
                    onDismiss = { menuOpen = false },
                    onOpenGuide = onOpenGuide,
                    viewModel = viewModel,
                )
            }
        }
        when {
            state.aiThinking -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.hud_ai_thinking),
                    color = UiColors.inkMuted,
                    fontSize = 13.sp,
                )
            }
            state.currentIsHuman && state.banner == null && state.freshUnitCount > 0 -> {
                Spacer(Modifier.height(8.dp))
                FreshUnitsPill(state, onClick = { viewModel.focusNextFreshUnit() })
            }
        }
    }
}

/**
 * One of the top bar's two 48 dp entry-point circles. The circle flips to the
 * fixed filled-ink treatment while its surface (panel or menu) is open; a null
 * [glyph] renders the overflow ⋮ instead.
 */
@Composable
private fun TopBarCircle(
    glyph: Painter?,
    glyphSize: androidx.compose.ui.unit.Dp,
    description: String,
    active: Boolean,
    badge: Boolean,
    onClick: () -> Unit,
) {
    Box {
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(CircleShape)
                .background(if (active) UiColors.filledInk else UiColors.controlFill)
                .scaleClickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (active) UiColors.onFilledInk else UiColors.inkMuted
            if (glyph != null) {
                Icon(glyph, contentDescription = null, Modifier.size(glyphSize), tint = tint)
            } else {
                Icon(Icons.Default.MoreVert, contentDescription = null, Modifier.size(glyphSize), tint = tint)
            }
        }
        if (badge) {
            // Pending proposals: coin-gold dot with a surface ring so it reads
            // against the circle fill (replaces the old pact-proposals pill).
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(11.dp)
                    .background(UiColors.surface, CircleShape)
                    .padding(1.dp)
                    .background(UiColors.coin, CircleShape),
            )
        }
    }
}

@Composable
private fun FreshUnitsPill(state: HudState, onClick: () -> Unit) {
    val freshDescription = stringResource(R.string.cd_fresh_units, state.freshUnitCount)
    val pastel = UiColors.faction(state.currentPlayer)
    // Dark theme raises the pill to solid pastel: at 30% over a dark surface the
    // tint all but vanishes. onFaction ink is correct on both fills.
    val fill = if (isSystemInDarkTheme()) pastel else pastel.copy(alpha = 0.3f)
    val content = if (isSystemInDarkTheme()) UiColors.onFaction else UiColors.ink
    Surface(
        shape = RoundedCornerShape(50),
        color = fill,
        modifier = Modifier
            .scaleClickable(onClick = onClick)
            .semantics { contentDescription = freshDescription },
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.hud_fresh_units, state.freshUnitCount),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = content,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painterResource(R.drawable.ic_flag),
                contentDescription = null,
                Modifier.size(13.dp),
                tint = content,
            )
        }
    }
}

@Composable
private fun OverflowMenu(
    expanded: Boolean,
    isCampaign: Boolean,
    onDismiss: () -> Unit,
    onOpenGuide: () -> Unit,
    viewModel: GameViewModel,
) {
    // Resign arms on the first tap and commits on the second — the HUD's no-dialog
    // rule. Disarms after 3 s or when the menu closes.
    var resignArmed by remember { mutableStateOf(false) }
    LaunchedEffect(resignArmed) {
        if (resignArmed) {
            delay(3000)
            resignArmed = false
        }
    }
    LaunchedEffect(expanded) { if (!expanded) resignArmed = false }
    val itemHeight = Modifier.height(MinTouchTarget)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = UiColors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, UiColors.hairline),
        shadowElevation = 2.dp,
    ) {
        DropdownMenuItem(
            modifier = itemHeight,
            text = { Text(stringResource(R.string.guide_menu_entry), fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
            colors = MenuDefaults.itemColors(
                textColor = UiColors.ink,
                leadingIconColor = UiColors.inkMuted,
            ),
            onClick = {
                onDismiss()
                onOpenGuide()
            },
        )
        if (isCampaign) {
            DropdownMenuItem(
                modifier = itemHeight,
                text = { Text(stringResource(R.string.hud_objectives), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                colors = MenuDefaults.itemColors(
                    textColor = UiColors.ink,
                    leadingIconColor = UiColors.inkMuted,
                ),
                onClick = {
                    onDismiss()
                    viewModel.showObjectivesPanel()
                },
            )
        }
        DropdownMenuItem(
            modifier = itemHeight,
            text = {
                Text(
                    stringResource(
                        if (resignArmed) R.string.hud_resign_confirm else R.string.hud_resign,
                    ),
                    fontSize = 14.sp,
                    fontWeight = if (resignArmed) FontWeight.Bold else FontWeight.Normal,
                )
            },
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
                if (resignArmed) {
                    onDismiss()
                    viewModel.surrender()
                } else {
                    resignArmed = true
                }
            },
        )
        DropdownMenuItem(
            modifier = itemHeight,
            text = { Text(stringResource(R.string.hud_exit), fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
            colors = MenuDefaults.itemColors(
                textColor = UiColors.ink,
                leadingIconColor = UiColors.inkMuted,
            ),
            onClick = {
                onDismiss()
                viewModel.backToMenu()
            },
        )
    }
}
