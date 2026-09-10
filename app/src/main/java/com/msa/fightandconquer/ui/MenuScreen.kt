package com.msa.fightandconquer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.render.FilamentHost
import com.msa.fightandconquer.render.scene.BoardScene
import com.msa.fightandconquer.ui.game.TopBarTopInset
import com.msa.fightandconquer.ui.game.hudSurface
import com.msa.fightandconquer.ui.guide.FieldGuide
import com.msa.fightandconquer.ui.menu.MenuEntry
import com.msa.fightandconquer.ui.menu.MenuLayout
import com.msa.fightandconquer.ui.setup.scaleClickable

/** One full turn of the menu world per minute — scenery pace, not a carousel. */
private const val MENU_ORBIT_RAD_PER_SEC = (2.0 * Math.PI / 60.0).toFloat()

/**
 * The game's own 55 deg: lower reads more scenic but flattens the world into a thin
 * strip on a portrait screen — at 55 the band covers ~2/3 of the height at every yaw.
 */
private val MENU_PITCH_RADIANS = Math.toRadians(55.0).toFloat()

/**
 * Under 1 on purpose: the world overflows the screen sides so it fills the width and
 * reads as a landscape the menu floats above, not a token in the middle. The sea rim
 * clipping left and right as it turns is the intended look.
 */
private const val MENU_ORBIT_FIT_MARGIN = 0.45f

/** Every floating menu surface shares one radius, so shadow and hairline coincide. */
private val MenuSurfaceRadius = 16.dp

/** The hairline a faction pastel wears (the end-turn FAB's), never the neutral one. */
private val PastelHairline = Color(0x243E3A36)

/** The tile geometry, all of it decided (and tested) in [MenuLayout]. */
private val MenuTileSize = MenuLayout.TILE_DP.dp
private val MenuTileGap = MenuLayout.TILE_GAP_DP.dp
private val MenuBlockBottomInset = MenuLayout.SCREEN_GUTTER_DP.dp
private val MenuRowWidth = MenuLayout.rowWidthDp().dp
private val MenuContinueBarHeight = MenuLayout.CONTINUE_BAR_HEIGHT_DP.dp

/** The title chip hangs at the HUD top bar's inset, so both screens start at one line. */
private val MenuTitleTopInset = TopBarTopInset

@Composable
fun MenuScreen(
    world: GameState?,
    hasAutosave: Boolean,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onCampaign: () -> Unit,
    onMapEditor: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // One place maps an entry to its navigation; the tiles below are a dumb mapping over
    // MenuLayout and never decide anything themselves.
    val onEntry: (MenuEntry) -> Unit = { entry ->
        when (entry) {
            MenuEntry.CONTINUE -> onContinue()
            MenuEntry.NEW_GAME -> onNewGame()
            MenuEntry.CAMPAIGN -> onCampaign()
            MenuEntry.MAP_EDITOR -> onMapEditor()
            MenuEntry.GUIDE -> guideOpen = true
            MenuEntry.SETTINGS -> onSettings()
            MenuEntry.ABOUT -> onAbout()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(UiColors.background),
    ) {
        // The backdrop: a freshly generated world nobody plays, orbiting behind the
        // chrome. Decorative, so no gesture modifiers — and the scene is never held in
        // Compose state (the FilamentHost holder owns it).
        if (world != null) {
            FilamentHost(Modifier.fillMaxSize()) { renderEngine ->
                BoardScene(renderEngine, context, world).apply {
                    autoOrbitRadPerSec = MENU_ORBIT_RAD_PER_SEC
                    fitForOrbit = true
                    orbitFitMargin = MENU_ORBIT_FIT_MARGIN
                    rig.pitch = MENU_PITCH_RADIANS
                }
            }
        }

        // The chrome hugs the safe area's top and bottom edges and leaves the middle band
        // to the world; only a screen too short for that falls back to a scrolling column.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            if (MenuLayout.anchored(maxHeight.value.toInt())) {
                MenuTitleChip(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = MenuTitleTopInset),
                )
                MenuActionBlock(
                    hasAutosave = hasAutosave,
                    onEntry = onEntry,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = MenuBlockBottomInset),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = MenuTitleTopInset),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MenuTitleChip()
                    Spacer(Modifier.height(MenuTileGap * 2))
                    MenuActionBlock(hasAutosave = hasAutosave, onEntry = onEntry)
                }
            }
        }

        if (guideOpen) {
            FieldGuide(onClose = { guideOpen = false })
        }
    }
}

/** The game's name on the usual floating surface — the menu's only non-action chrome. */
@Composable
private fun MenuTitleChip(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .hudSurface(radius = MenuSurfaceRadius)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.menu_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = UiColors.ink,
        )
        Text(
            stringResource(R.string.menu_subtitle),
            fontSize = 16.sp,
            color = UiColors.ink.copy(alpha = 0.6f),
        )
    }
}

/** The Continue bar (autosave only) over the 3x2 tile grid, as [MenuLayout] orders them. */
@Composable
private fun MenuActionBlock(
    hasAutosave: Boolean,
    onEntry: (MenuEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MenuTileGap),
    ) {
        MenuLayout.continueBar(hasAutosave)?.let { entry ->
            MenuContinueBar(entry) { onEntry(entry) }
        }
        val primary = MenuLayout.primary(hasAutosave)
        MenuLayout.rows(MenuLayout.tiles()).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MenuTileGap)) {
                row.forEach { entry ->
                    MenuTile(entry, primary = entry == primary) { onEntry(entry) }
                }
            }
        }
    }
}

/**
 * A square action tile: glyph over label on the universal HUD chrome. Merging the
 * descendants makes the whole tile one button-shaped focus target that reads out its
 * visible label, instead of a bare surface with a floating piece of text.
 */
@Composable
private fun MenuTile(entry: MenuEntry, primary: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(MenuTileSize)
            // Chrome first, then the press scale, then the padding — as ActionCircle does.
            .hudSurface(
                radius = MenuSurfaceRadius,
                fill = if (primary) UiColors.faction(0) else UiColors.surface,
                border = if (primary) PastelHairline else UiColors.hairline,
            )
            .scaleClickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(entry.iconRes),
            contentDescription = null,
            Modifier.size(24.dp),
            tint = if (primary) UiColors.onFaction else UiColors.inkMuted,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(entry.labelRes),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (primary) UiColors.onFaction else UiColors.ink,
        )
    }
}

/** Resuming is the one row-wide action: a pastel bar as wide as a full tile row. */
@Composable
private fun MenuContinueBar(entry: MenuEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(MenuRowWidth)
            .height(MenuContinueBarHeight)
            .hudSurface(
                radius = MenuSurfaceRadius,
                fill = UiColors.faction(0),
                border = PastelHairline,
            )
            .scaleClickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(entry.iconRes),
            contentDescription = null,
            Modifier.size(20.dp),
            tint = UiColors.onFaction,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(entry.labelRes),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = UiColors.onFaction,
        )
    }
}
