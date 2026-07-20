package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitId
import com.msa.fightandconquer.core.persist.SaveGame
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PurchaseOption {
    val cost: Int

    data class Unit(val tier: Int, override val cost: Int) : PurchaseOption
    data class Structure(val type: BuildingType, override val cost: Int) : PurchaseOption
}

data class IncomeSummary(val income: Int, val upkeep: Int, val treasury: Int) {
    val net: Int get() = income - upkeep
    val projected: Int get() = treasury + net
}

/**
 * The single mutation gateway the app layer talks to. Thread-confined by design:
 * call [submit]/[undo] from one thread (the ViewModel's main dispatcher).
 *
 * [events] is buffered (drop-oldest) — the renderer treats events as animation hints
 * and can always fall back to diffing [state].
 */
class GameEngine private constructor(
    initial: GameState,
    private var turnStartState: GameState,
    private val actionsThisTurn: MutableList<GameAction>,
) {
    constructor(initial: GameState) : this(initial, initial, mutableListOf())

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(
        replay = 0,
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private val undoStack = ArrayDeque<GameState>()

    fun submit(action: GameAction): LegalityResult {
        val current = _state.value
        val legality = Legality.check(current, action)
        if (legality is LegalityResult.Rejected) return legality

        val result = Reducer.reduce(current, action)
        if (action is GameAction.EndTurn || action is GameAction.Surrender) {
            undoStack.clear()
            actionsThisTurn.clear()
            turnStartState = result.state
        } else {
            undoStack.addLast(current)
            actionsThisTurn.add(action)
        }
        _state.value = result.state
        result.events.forEach { _events.tryEmit(it) }
        return LegalityResult.Ok
    }

    /** Undo the current seat's last action (never across an EndTurn). */
    fun undo(): Boolean {
        val previous = undoStack.removeLastOrNull() ?: return false
        actionsThisTurn.removeLastOrNull()
        _state.value = previous
        return true
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    // ----- pure queries -----

    fun reachableFor(unit: UnitId): ReachResult = Rules.reachable(_state.value, unit)

    fun incomeSummary(player: PlayerId): IncomeSummary {
        val s = _state.value
        return IncomeSummary(
            income = Rules.incomeOf(s, player),
            upkeep = Rules.upkeepOf(s, player),
            treasury = s.player(player).treasury,
        )
    }

    /** Affordable, legally placeable purchases at [hex] for the current player. */
    fun buyableAt(hex: Hex): List<PurchaseOption> {
        val s = _state.value
        val options = ArrayList<PurchaseOption>()
        for (tier in 1..s.config.rules.maxTier) {
            if (Legality.check(s, GameAction.BuyUnit(tier, hex)) is LegalityResult.Ok) {
                options.add(PurchaseOption.Unit(tier, s.config.rules.unitCost[tier - 1]))
            }
        }
        for (type in BuildingType.entries) {
            if (Legality.check(s, GameAction.BuyBuilding(type, hex)) is LegalityResult.Ok) {
                options.add(PurchaseOption.Structure(type, Rules.buildingCost(s, s.currentPlayer, type)))
            }
        }
        return options
    }

    // ----- persistence -----

    fun toSave(): SaveGame = SaveGame(
        turnStartState = turnStartState,
        actionsThisTurn = actionsThisTurn.toList(),
    )

    companion object {
        fun fromSave(save: SaveGame): GameEngine {
            val engine = GameEngine(
                initial = save.turnStartState,
                turnStartState = save.turnStartState,
                actionsThisTurn = mutableListOf(),
            )
            // Replay through submit so the undo stack is rebuilt too.
            for (action in save.actionsThisTurn) {
                check(engine.submit(action) is LegalityResult.Ok) { "corrupt save: $action rejected" }
            }
            return engine
        }
    }
}
