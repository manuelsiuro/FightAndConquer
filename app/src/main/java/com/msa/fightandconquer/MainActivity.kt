package com.msa.fightandconquer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msa.fightandconquer.ui.GameViewModel
import com.msa.fightandconquer.ui.MenuScreen
import com.msa.fightandconquer.ui.PlaceholderScreen
import com.msa.fightandconquer.ui.Screen
import com.msa.fightandconquer.ui.SetupScreen
import com.msa.fightandconquer.ui.game.GameScreen
import com.msa.fightandconquer.ui.theme.FightAndConquerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FightAndConquerTheme {
                val screen by viewModel.screen.collectAsStateWithLifecycle()
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
                    Screen.Campaign -> PlaceholderScreen(
                        title = stringResource(R.string.menu_campaign),
                        onBack = viewModel::backToMenu,
                    )
                    Screen.MapEditor -> PlaceholderScreen(
                        title = stringResource(R.string.menu_map_editor),
                        onBack = viewModel::backToMenu,
                    )
                    Screen.Settings -> PlaceholderScreen(
                        title = stringResource(R.string.menu_settings),
                        onBack = viewModel::backToMenu,
                    )
                    Screen.About -> PlaceholderScreen(
                        title = stringResource(R.string.menu_about),
                        onBack = viewModel::backToMenu,
                    )
                    Screen.Game -> GameScreen(viewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.persistNow()
    }
}
