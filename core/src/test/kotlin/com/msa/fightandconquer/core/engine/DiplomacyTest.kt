package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val P0 = PlayerId(0)
private val P1 = PlayerId(1)

private fun GameState.apply(action: GameAction): ReduceResult = Reducer.reduce(this, action)

private fun rejectionOf(result: ReduceResult): RejectionReason =
    result.events.filterIsInstance<GameEvent.ActionRejected>().single().reason

class PactLifecycleTest {

    @Test
    fun `propose accept live expire`() {
        val s = strip(9, 0..2, 6..8)
        val (proposed, proposeEvents) = s.apply(GameAction.ProposePact(P1, durationRounds = 2))
        assertTrue(proposeEvents.any { it is GameEvent.PactProposed && it.to == P1 })
        assertNotNull(proposed.diplomacy.proposalBetween(P0, P1))

        // P1's turn: accept.
        val (p1Turn, _) = proposed.apply(GameAction.EndTurn)
        val (accepted, acceptEvents) = p1Turn.apply(GameAction.RespondPact(P0, accept = true))
        val pact = accepted.diplomacy.pactBetween(P0, P1)
        assertNotNull(pact)
        assertEquals(accepted.turnNumber + 2, pact!!.expiresAtRound)
        assertTrue(acceptEvents.any { it is GameEvent.PactAccepted })
        assertNull(accepted.diplomacy.proposalBetween(P0, P1))

        // Attacks between partners are still legal actions but MoveGenerator avoids
        // them; here we just roll rounds forward until the pact lapses.
        var state = accepted
        var expired = false
        repeat(8) {
            val (next, events) = state.apply(GameAction.EndTurn)
            state = next
            if (events.any { it is GameEvent.PactExpired }) expired = true
        }
        assertTrue(expired)
        assertNull(state.diplomacy.pactBetween(P0, P1))
    }

    @Test
    fun `decline removes the proposal`() {
        val s = strip(9, 0..2, 6..8)
        val proposed = s.apply(GameAction.ProposePact(P1, 4)).state
        val p1Turn = proposed.apply(GameAction.EndTurn).state
        val (declined, events) = p1Turn.apply(GameAction.RespondPact(P0, accept = false))
        assertTrue(events.any { it is GameEvent.PactDeclined })
        assertNull(declined.diplomacy.proposalBetween(P0, P1))
        assertNull(declined.diplomacy.pactBetween(P0, P1))
    }

    @Test
    fun `unanswered proposals lapse after the ttl`() {
        var state = strip(9, 0..2, 6..8).apply(GameAction.ProposePact(P1, 4)).state
        var lapsed = false
        repeat(6) {
            val (next, events) = state.apply(GameAction.EndTurn)
            state = next
            if (events.any { it is GameEvent.PactProposalExpired }) lapsed = true
        }
        assertTrue(lapsed)
        assertNull(state.diplomacy.proposalBetween(P0, P1))
    }

    @Test
    fun `proposal rejections cover every guard`() {
        val s = strip(9, 0..2, 6..8)
        assertEquals(RejectionReason.INVALID_PLAYER, rejectionOf(s.apply(GameAction.ProposePact(P0, 4))))
        assertEquals(RejectionReason.INVALID_PACT_DURATION, rejectionOf(s.apply(GameAction.ProposePact(P1, 1))))
        assertEquals(RejectionReason.INVALID_PACT_DURATION, rejectionOf(s.apply(GameAction.ProposePact(P1, 99))))

        val pending = s.apply(GameAction.ProposePact(P1, 4)).state
        assertEquals(RejectionReason.PROPOSAL_PENDING, rejectionOf(pending.apply(GameAction.ProposePact(P1, 4))))

        // Once declined, the pair is on cooldown.
        val declined = pending.apply(GameAction.EndTurn).state
            .apply(GameAction.RespondPact(P0, accept = false)).state
            .apply(GameAction.EndTurn).state
        assertEquals(RejectionReason.PROPOSAL_COOLDOWN, rejectionOf(declined.apply(GameAction.ProposePact(P1, 4))))

        val disabled = strip(9, 0..2, 6..8, rules = RuleConstants(diplomacyEnabled = false))
        assertEquals(RejectionReason.DIPLOMACY_DISABLED, rejectionOf(disabled.apply(GameAction.ProposePact(P1, 4))))
        assertEquals(RejectionReason.NO_SUCH_PROPOSAL, rejectionOf(s.apply(GameAction.RespondPact(P1, true))))
    }
}

class TributeAndBreakTest {

    @Test
    fun `tribute transfers gold and records the round`() {
        val s = strip(9, 0..2, 6..8)
        val (next, events) = s.apply(GameAction.SendTribute(P1, 25))
        assertEquals(75, next.player(P0).treasury)
        assertEquals(125, next.player(P1).treasury)
        assertTrue(events.any { it is GameEvent.TributeSent && it.amount == 25 })
        assertEquals(next.turnNumber, next.diplomacy.lastTributeRound(P0, P1))

        assertEquals(RejectionReason.INVALID_TRIBUTE_AMOUNT, rejectionOf(s.apply(GameAction.SendTribute(P1, 0))))
        assertEquals(RejectionReason.CANNOT_AFFORD, rejectionOf(s.apply(GameAction.SendTribute(P1, 1000))))
    }

    @Test
    fun `capturing a partner hex breaks the pact with a penalty`() {
        // Active pact P0/P1, then P0 captures a P1 hex with a unit.
        val pacted = strip(9, 0..5, 6..8)
            .apply(GameAction.ProposePact(P1, 6)).state
            .apply(GameAction.EndTurn).state
            .apply(GameAction.RespondPact(P0, accept = true)).state
            .apply(GameAction.EndTurn).state
        assertNotNull(pacted.diplomacy.pactBetween(P0, P1))

        val armed = pacted.withUnit(owner = 0, tier = 2, at = hex(5))
        val attacker = armed.unitIdAt(hex(5))
        val treasuryBefore = armed.player(P0).treasury
        val penalty = treasuryBefore * armed.config.rules.pactBreakPenaltyPercent / 100
        val (next, events) = armed.apply(GameAction.MoveUnit(attacker, hex(6)))

        assertTrue(events.any { it is GameEvent.PactBroken && it.breaker == P0 && it.penalty == penalty })
        assertTrue(events.any { it is GameEvent.HexCaptured && it.newOwner == P0 })
        assertNull(next.diplomacy.pactBetween(P0, P1))
        assertEquals(1, next.diplomacy.breaksOf(P0))
        assertEquals(0, next.diplomacy.breaksOf(P1))
        assertEquals(treasuryBefore - penalty, next.player(P0).treasury)
        assertInvariants(next)
    }

    @Test
    fun `buy capture on a partner hex also breaks the pact`() {
        val pacted = strip(9, 0..5, 6..8)
            .apply(GameAction.ProposePact(P1, 6)).state
            .apply(GameAction.EndTurn).state
            .apply(GameAction.RespondPact(P0, accept = true)).state
            .apply(GameAction.EndTurn).state
        val (next, events) = pacted.apply(GameAction.BuyUnit(1, hex(6)))
        assertTrue(events.any { it is GameEvent.PactBroken })
        assertNull(next.diplomacy.pactBetween(P0, P1))
    }

    @Test
    fun `elimination prunes pacts and proposals`() {
        // P1 holds a single hex; P0 captures it -> P1 eliminated, diplomacy pruned.
        val owners = HashMap<com.msa.fightandconquer.core.hex.Hex, Int?>()
        for (q in 0..4) owners[hex(q)] = 0
        owners[hex(5)] = 1
        val s = com.msa.fightandconquer.core.TestStates.custom(owners, capital0 = hex(0), capital1 = hex(5))
            .withUnit(owner = 0, tier = 3, at = hex(4))
            .apply(GameAction.ProposePact(P1, 6)).state
        assertNotNull(s.diplomacy.proposalBetween(P0, P1))
        val attacker = s.unitIdAt(hex(4))
        val (next, _) = s.apply(GameAction.MoveUnit(attacker, hex(5)))
        assertTrue(next.player(P1).eliminated)
        assertTrue(next.diplomacy.proposals.isEmpty())
        assertTrue(next.diplomacy.pacts.isEmpty())
    }

    @Test
    fun `mid-turn diplomacy actions replay bit-identically from a save`() {
        val engine = GameEngine(strip(9, 0..2, 6..8).withTreasury(0, 100))
        check(engine.submit(GameAction.SendTribute(P1, 10)) is LegalityResult.Ok)
        check(engine.submit(GameAction.ProposePact(P1, 4)) is LegalityResult.Ok)
        val save = engine.toSave()
        val restored = GameEngine.fromSave(com.msa.fightandconquer.core.persist.SaveCodec.decode(
            com.msa.fightandconquer.core.persist.SaveCodec.encode(save),
        ))
        val json = com.msa.fightandconquer.core.persist.SaveCodec.json
        assertEquals(
            json.encodeToString(GameState.serializer(), engine.state.value),
            json.encodeToString(GameState.serializer(), restored.state.value),
        )
    }
}
