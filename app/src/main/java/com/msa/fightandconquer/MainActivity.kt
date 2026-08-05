package com.msa.fightandconquer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msa.fightandconquer.ui.AboutScreen
import com.msa.fightandconquer.ui.GameViewModel
import com.msa.fightandconquer.ui.MenuScreen
import com.msa.fightandconquer.ui.PlaceholderScreen
import com.msa.fightandconquer.ui.Screen
import com.msa.fightandconquer.ui.SetupScreen
import com.msa.fightandconquer.ui.campaign.BriefingScreen
import com.msa.fightandconquer.ui.campaign.CampaignScreen
import com.msa.fightandconquer.ui.game.GameScreen
import com.msa.fightandconquer.ui.theme.FightAndConquerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // Must come after enableEdgeToEdge: auto() re-enables the system's
        // 3-button-nav contrast scrim, which reads as a black bottom bar.
        window.isNavigationBarContrastEnforced = false
        setContent {
            FightAndConquerTheme {
                val screen by viewModel.screen.collectAsStateWithLifecycle()
                ImmersiveDuringGame(screen)
                when (val s = screen) {
                    is Screen.Menu -> MenuScreen(
                        hasAutosave = s.hasAutosave,
                        onContinue = viewModel::continueGame,
                        onNewGame = viewModel::openSetup,
                        onCampaign = viewModel::openCampaign,
                        onMapEditor = viewModel::openMapEditor,
                        onSettings = viewModel::openSettings,
                        onAbout = viewModel::openAbout,
                    )
                    is Screen.Setup -> SetupScreen(
                        generating = s.generating,
                        onStart = viewModel::newGame,
                        onBack = viewModel::backToMenu,
                    )
                    Screen.Campaign -> CampaignScreen(
                        campaigns = viewModel.campaigns.campaigns(),
                        progress = viewModel.campaignProgress,
                        onLevel = viewModel::openBriefing,
                        onBack = viewModel::backToMenu,
                    )
                    is Screen.Briefing -> {
                        val level = viewModel.campaigns.level(s.campaignId, s.levelId)
                        if (level == null) {
                            // Only reachable if a saved screen names a level this build no
                            // longer ships. Navigation is a side effect, so it happens in
                            // an effect rather than during composition.
                            LaunchedEffect(s) { viewModel.openCampaign() }
                        } else {
                            BriefingScreen(
                                level = level,
                                alreadyCleared = viewModel.campaignProgress.isComplete(level.id),
                                onStart = { viewModel.startLevel(s.campaignId, s.levelId) },
                                onBack = viewModel::openCampaign,
                            )
                        }
                    }
                    Screen.MapEditor -> PlaceholderScreen(
                        title = stringResource(R.string.menu_map_editor),
                        onBack = viewModel::backToMenu,
                    )
                    Screen.Settings -> PlaceholderScreen(
                        title = stringResource(R.string.menu_settings),
                        onBack = viewModel::backToMenu,
                    )
                    Screen.About -> AboutScreen(onBack = viewModel::backToMenu)
                    Screen.Game -> GameScreen(viewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.persistNow()
    }

    /**
     * Hides the system bars while playing (edge swipe reveals them transiently)
     * and restores them on every other screen. Conditional composition means
     * onDispose runs on any exit from the game, whatever the path.
     */
    @Composable
    private fun ImmersiveDuringGame(screen: Screen) {
        if (screen !is Screen.Game) return
        val view = LocalView.current
        DisposableEffect(Unit) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
