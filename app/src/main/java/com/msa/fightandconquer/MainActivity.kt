package com.msa.fightandconquer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.msa.fightandconquer.ui.GameScreen
import com.msa.fightandconquer.ui.GameViewModel
import com.msa.fightandconquer.ui.MenuScreen
import com.msa.fightandconquer.ui.Screen
import com.msa.fightandconquer.ui.theme.FightAndConquerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FightAndConquerTheme {
                val screen by viewModel.screen.collectAsState()
                when (val s = screen) {
                    is Screen.Menu -> MenuScreen(
                        hasAutosave = s.hasAutosave,
                        generating = s.generating,
                        onNewGame = viewModel::newGame,
                        onContinue = viewModel::continueGame,
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
