package com.msa.fightandconquer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.render.FilamentHost
import com.msa.fightandconquer.render.scene.BoardScene
import com.msa.fightandconquer.ui.game.hudSurface
import com.msa.fightandconquer.ui.guide.FieldGuide

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
private val MenuSurfaceRadius = 20.dp

/** The hairline a faction pastel wears (the end-turn FAB's), never the neutral one. */
private val PastelHairline = Color(0x243E3A36)

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

    Box(
        Modifier
            .fillMaxSize()
            .background(UiColors.background),
    ) {
        // The backdrop: a freshly generated world nobody plays, orbiting behind the
        // buttons. Decorative, so no gesture modifiers — and the scene is never held in
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier
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
            Spacer(Modifier.height(28.dp))

            if (hasAutosave) {
                PrimaryMenuButton(stringResource(R.string.menu_continue), onContinue)
                Spacer(Modifier.height(12.dp))
                SecondaryMenuButton(stringResource(R.string.menu_new_game), onNewGame)
            } else {
                PrimaryMenuButton(stringResource(R.string.menu_new_game), onNewGame)
            }
            Spacer(Modifier.height(12.dp))
            SecondaryMenuButton(stringResource(R.string.menu_campaign), onCampaign)
            Spacer(Modifier.height(12.dp))
            SecondaryMenuButton(stringResource(R.string.menu_map_editor), onMapEditor)
            Spacer(Modifier.height(12.dp))
            SecondaryMenuButton(stringResource(R.string.guide_menu_entry)) { guideOpen = true }
            Spacer(Modifier.height(12.dp))
            SecondaryMenuButton(stringResource(R.string.menu_settings), onSettings)
            Spacer(Modifier.height(12.dp))
            SecondaryMenuButton(stringResource(R.string.menu_about), onAbout)
        }

        if (guideOpen) {
            FieldGuide(onClose = { guideOpen = false })
        }
    }
}

@Composable
private fun PrimaryMenuButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            // Same chrome as the end-turn FAB: pastel fill, its darker hairline, one boardLift.
            .hudSurface(radius = MenuSurfaceRadius, fill = UiColors.faction(0), border = PastelHairline),
        shape = RoundedCornerShape(MenuSurfaceRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = UiColors.faction(0),
            contentColor = UiColors.onFaction,
        ),
        // hudSurface already carries the one boardLift shadow; Material must add none.
        elevation = null,
    ) { Text(label, color = UiColors.onFaction) }
}

@Composable
private fun SecondaryMenuButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .hudSurface(radius = MenuSurfaceRadius),
        shape = RoundedCornerShape(MenuSurfaceRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = UiColors.surface,
            contentColor = UiColors.ink,
        ),
        // hudSurface already carries the one boardLift shadow; Material must add none.
        elevation = null,
    ) { Text(label, color = UiColors.ink) }
}
