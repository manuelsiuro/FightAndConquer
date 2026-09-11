package com.msa.fightandconquer.render.scene

import com.msa.fightandconquer.core.engine.GameEvent
import com.msa.fightandconquer.core.model.GameState
import kotlinx.coroutines.flow.StateFlow

/**
 * The board as the ViewModel sees it: a sink for authoritative states with the events that
 * produced them, and one signal saying whether everything fed so far has been shown.
 * Main-thread only ([apply]); [playbackIdle] is a StateFlow and may be awaited from any thread.
 */
interface BoardPlayback {
    /** Queue [events] as beats and remember [state] as the truth to snap to once they played. */
    fun apply(state: GameState, events: List<GameEvent>)

    /**
     * False from the moment [apply] is called; true again once every queued beat has played
     * and the scene reconciled to the last fed state (also after a skip, and forever after
     * the scene is destroyed, so no waiter is ever stranded).
     */
    val playbackIdle: StateFlow<Boolean>
}
