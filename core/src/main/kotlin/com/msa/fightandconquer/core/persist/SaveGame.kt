package com.msa.fightandconquer.core.persist

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.model.GameState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A saved game: the state snapshot at the current player's turn start plus the actions
 * taken since. Restoring replays those actions through the reducer — which doubles as a
 * persistence correctness check (any drift fails loudly in tests).
 */
@Serializable
data class SaveGame(
    val version: Int = 1,
    val turnStartState: GameState,
    val actionsThisTurn: List<GameAction> = emptyList(),
)

object SaveCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true // forward-compatible loading
        encodeDefaults = false
    }

    fun encode(save: SaveGame): String = json.encodeToString(SaveGame.serializer(), save)

    fun decode(text: String): SaveGame = json.decodeFromString(SaveGame.serializer(), text)

    /** Rebuilds the live state by replaying the turn's actions over the snapshot. */
    fun restore(save: SaveGame): GameState {
        var state = save.turnStartState
        for (action in save.actionsThisTurn) {
            state = Reducer.reduce(state, action).state
        }
        return state
    }
}
