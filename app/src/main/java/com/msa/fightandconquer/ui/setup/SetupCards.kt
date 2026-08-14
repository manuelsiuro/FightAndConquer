package com.msa.fightandconquer.ui.setup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.ui.GameMode
import com.msa.fightandconquer.ui.PieceIcons
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.civNameRes
import com.msa.fightandconquer.ui.difficultyLabelRes

/**
 * The hero card: the human civ's piece trio over a one-line summary of the whole
 * match, plus one pastel dot per seat — the "what will start if I tap Start" card.
 */
@Composable
internal fun SetupTableauCard(
    civ: Civilization,
    playerCount: Int,
    title: String,
    subtitle: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .cardSurface(20.dp)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 14.dp),
    ) {
        PieceTrio(civ, heights = listOf(64.dp, 74.dp, 58.dp), gap = 10.dp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = UiColors.divider, thickness = 1.dp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = UiColors.ink)
                Text(subtitle, fontSize = 12.sp, color = UiColors.inkMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(playerCount) { seat ->
                    Box(Modifier.size(14.dp).background(UiColors.faction(seat), CircleShape))
                }
            }
        }
    }
}

/** Three cards, one per enemy count; the selected one wears the human pastel. */
@Composable
internal fun OpponentCountSelector(playerCount: Int, onChange: (Int) -> Unit) {
    Column {
        SetupMicroLabel(stringResource(R.string.setup_section_enemies))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (count in 2..MAX_PLAYERS) {
                val enemies = count - 1
                val selected = playerCount == count
                val cd = pluralStringResource(R.plurals.menu_enemy_count, enemies, enemies)
                val ground =
                    if (selected) {
                        Modifier.background(UiColors.faction(0), RoundedCornerShape(14.dp))
                    } else {
                        Modifier.cardSurface(14.dp)
                    }
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .then(ground)
                        .scaleClickable { onChange(count) }
                        .semantics { contentDescription = cd },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$enemies",
                            fontSize = 16.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selected) UiColors.onFaction else UiColors.inkSecondary,
                        )
                        if (selected) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(Modifier.size(6.dp).background(UiColors.onFaction, CircleShape))
                                repeat(enemies) {
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .background(UiColors.onFaction.copy(alpha = 0.35f), CircleShape),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// The mode chips read as a segmented ink control: the same dark fill in both
// themes (the onFaction philosophy — the control's identity, not the theme's).
internal val FilledInk = androidx.compose.ui.graphics.Color(0xFF3E3A36)
internal val OnFilledInk = androidx.compose.ui.graphics.Color(0xFFF7F4F0)

/**
 * Mode and difficulty side by side. In pass-and-play the difficulty column
 * animates away and the mode column widens to fill the row.
 */
@Composable
internal fun ModeAndDifficultyRow(
    mode: GameMode,
    onMode: (GameMode) -> Unit,
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
) {
    // Animating the weight itself collapses the column without leaving a gap.
    val difficultyWeight by animateFloatAsState(
        targetValue = if (mode == GameMode.VS_AI) 1f else 0.001f,
        animationSpec = tween(250),
        label = "difficultyWeight",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            SetupMicroLabel(stringResource(R.string.menu_section_mode))
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OptionPill(
                    label = stringResource(R.string.menu_mode_vs_ai),
                    selected = mode == GameMode.VS_AI,
                    fill = FilledInk,
                    onFill = OnFilledInk,
                ) { onMode(GameMode.VS_AI) }
                OptionPill(
                    label = stringResource(R.string.menu_mode_pass_and_play),
                    selected = mode == GameMode.PASS_AND_PLAY,
                    fill = FilledInk,
                    onFill = OnFilledInk,
                ) { onMode(GameMode.PASS_AND_PLAY) }
            }
        }
        if (difficultyWeight > 0.01f) {
            Column(Modifier.weight(difficultyWeight).alpha(difficultyWeight.coerceIn(0f, 1f))) {
                SetupMicroLabel(stringResource(R.string.menu_section_difficulty))
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (option in Difficulty.selectable) {
                        OptionPill(
                            label = stringResource(difficultyLabelRes(option)),
                            selected = difficulty == option,
                            fill = UiColors.faction(2),
                            onFill = UiColors.onFaction,
                        ) { onDifficulty(option) }
                    }
                }
            }
        }
    }
}

/** One 40 dp option row of a stacked selector column. */
@Composable
private fun OptionPill(
    label: String,
    selected: Boolean,
    fill: androidx.compose.ui.graphics.Color,
    onFill: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val ground =
        if (selected) {
            Modifier.background(fill, RoundedCornerShape(12.dp))
        } else {
            Modifier.cardSurface(12.dp)
        }
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(ground)
            .scaleClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) onFill else UiColors.inkSecondary,
        )
    }
}

/**
 * One tappable card per seat, capped with the seat's pastel. Two seats sit in a
 * single roomy row; three or four fall into a tighter two-column grid.
 */
@Composable
internal fun SeatCivGrid(
    playerCount: Int,
    mode: GameMode,
    civs: List<Civilization>,
    onSeatTap: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SetupMicroLabel(stringResource(R.string.setup_section_civs), Modifier.weight(1f))
            Text(stringResource(R.string.setup_civ_hint), fontSize = 11.sp, color = UiColors.inkMuted)
        }
        Spacer(Modifier.height(8.dp))
        if (playerCount == 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (seat in 0 until 2) {
                    SeatCivCard(seat, civs[seat], mode, large = true, Modifier.weight(1f)) { onSeatTap(seat) }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until playerCount).chunked(2).forEach { rowSeats ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (seat in rowSeats) {
                            SeatCivCard(seat, civs[seat], mode, large = false, Modifier.weight(1f)) {
                                onSeatTap(seat)
                            }
                        }
                        if (rowSeats.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SeatCivCard(
    seat: Int,
    civ: Civilization,
    mode: GameMode,
    large: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val radius = if (large) 16.dp else 14.dp
    val cap = if (large) 6.dp else 5.dp
    val render = if (large) 46.dp else 38.dp
    val label = seatLabel(seat, mode)
    val cd = stringResource(R.string.cd_setup_seat, label)
    Column(
        modifier
            .cardSurface(radius)
            .scaleClickable(onClick = onClick)
            .semantics { contentDescription = cd },
    ) {
        Box(Modifier.fillMaxWidth().height(cap).background(UiColors.faction(seat)))
        Row(
            Modifier.padding(if (large) 10.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (large) 10.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(PieceIcons.building(civ, Building.CAPITAL)),
                contentDescription = null,
                modifier = Modifier.size(render),
                contentScale = ContentScale.Fit,
            )
            Column {
                Text(
                    label.uppercase(),
                    fontSize = if (large) 10.sp else 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = UiColors.inkMuted,
                )
                Text(
                    stringResource(civNameRes(civ)),
                    fontSize = if (large) 13.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = UiColors.ink,
                )
            }
        }
    }
}

internal const val MAX_PLAYERS = 4
