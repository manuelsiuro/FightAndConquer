package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameUnit
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitId
import kotlinx.serialization.Serializable

@Serializable
enum class DeathCause { KILLED, STARVED, BANKRUPTCY }

/**
 * Facts emitted by the reducer, in order. The renderer consumes them as animation
 * hints; headless tests assert on them. State remains the source of truth.
 */
@Serializable
sealed interface GameEvent {
    @Serializable data class ActionRejected(
        val action: GameAction,
        val reason: RejectionReason,
        val amount: Int? = null,
    ) : GameEvent
    @Serializable data class UnitSpawned(val unit: GameUnit) : GameEvent
    @Serializable data class UnitMoved(val unit: UnitId, val from: Hex, val to: Hex) : GameEvent
    @Serializable data class HexCaptured(val hex: Hex, val newOwner: PlayerId, val oldOwner: PlayerId?) : GameEvent
    @Serializable data class UnitDied(val unit: UnitId, val hex: Hex, val cause: DeathCause) : GameEvent
    @Serializable data class UnitsMerged(val into: GameUnit, val consumed: UnitId) : GameEvent
    @Serializable data class BuildingBuilt(val hex: Hex, val building: Building) : GameEvent
    @Serializable data class BuildingDestroyed(val hex: Hex, val building: Building) : GameEvent
    @Serializable data class TreeGrown(val hex: Hex) : GameEvent
    @Serializable data class TreeSpread(val from: Hex, val to: Hex) : GameEvent
    @Serializable data class TreeCleared(val hex: Hex, val bonus: Int) : GameEvent
    @Serializable data class GravestoneTrampled(val hex: Hex) : GameEvent
    @Serializable data class TurnStarted(val player: PlayerId, val income: Int, val upkeep: Int) : GameEvent
    @Serializable data class Bankruptcy(val player: PlayerId) : GameEvent
    @Serializable data class CapitalMoved(val player: PlayerId, val from: Hex, val to: Hex, val loot: Int) : GameEvent
    @Serializable data class PlayerEliminated(val player: PlayerId) : GameEvent
    @Serializable data class GameOver(val winner: PlayerId) : GameEvent

    // --- Diplomacy (HUD-level facts; the board renders nothing for these) ---
    @Serializable data class PactProposed(val from: PlayerId, val to: PlayerId, val durationRounds: Int) : GameEvent
    @Serializable data class PactAccepted(val a: PlayerId, val b: PlayerId, val expiresAtRound: Int) : GameEvent
    @Serializable data class PactDeclined(val from: PlayerId, val to: PlayerId) : GameEvent
    @Serializable data class PactExpired(val a: PlayerId, val b: PlayerId) : GameEvent
    @Serializable data class PactProposalExpired(val from: PlayerId, val to: PlayerId) : GameEvent
    @Serializable data class PactBroken(val breaker: PlayerId, val victim: PlayerId, val penalty: Int) : GameEvent
    @Serializable data class TributeSent(val from: PlayerId, val to: PlayerId, val amount: Int) : GameEvent
}
