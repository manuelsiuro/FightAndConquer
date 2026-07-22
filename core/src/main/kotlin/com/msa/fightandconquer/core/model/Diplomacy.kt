package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

/**
 * A non-aggression pact between two players. Normalized: [a].value < [b].value.
 * Active while `turnNumber < expiresAtRound`. There is no explicit "break"
 * action — capturing a partner's hex breaks the pact automatically and costs
 * [RuleConstants.pactBreakPenaltyPercent] of the breaker's treasury, paid to
 * the victim.
 */
@Serializable
data class Pact(val a: PlayerId, val b: PlayerId, val expiresAtRound: Int)

/** A pending pact offer. Expires [RuleConstants.pactProposalTtlRounds] after it was made. */
@Serializable
data class PactProposal(
    val from: PlayerId,
    val to: PlayerId,
    val durationRounds: Int,
    val proposedAtRound: Int,
)

/** Bookkeeping entry: the round something last happened between two players. */
@Serializable
data class PairRound(val a: PlayerId, val b: PlayerId, val round: Int)

/**
 * All diplomacy state. Every list is kept canonically sorted on write
 * (byte-stable JSON — the bit-identical determinism tests pin this):
 * pacts/lastProposalRounds by (a, b) with a < b; proposals/lastTributeRounds
 * by (from/a, to/b) directed.
 *
 * [lastProposalRounds] is the anti-oscillation cooldown consumed by Legality
 * ([RuleConstants.pactProposalCooldownRounds]); [lastTributeRounds] is
 * advisory state for the AI's tribute pacing. [pactBreaks] counts lifetime
 * pact breaks per seat (index == PlayerId.value; an empty list means zeros) —
 * a reputation input for AI attitude.
 */
@Serializable
data class DiplomacyState(
    val pacts: List<Pact> = emptyList(),
    val proposals: List<PactProposal> = emptyList(),
    val lastProposalRounds: List<PairRound> = emptyList(),
    val lastTributeRounds: List<PairRound> = emptyList(),
    val pactBreaks: List<Int> = emptyList(),
) {
    fun pactBetween(p: PlayerId, q: PlayerId): Pact? {
        val (lo, hi) = if (p.value < q.value) p to q else q to p
        return pacts.firstOrNull { it.a == lo && it.b == hi }
    }

    fun proposalBetween(from: PlayerId, to: PlayerId): PactProposal? =
        proposals.firstOrNull { it.from == from && it.to == to }

    fun lastProposalRound(p: PlayerId, q: PlayerId): Int? {
        val (lo, hi) = if (p.value < q.value) p to q else q to p
        return lastProposalRounds.firstOrNull { it.a == lo && it.b == hi }?.round
    }

    fun lastTributeRound(from: PlayerId, to: PlayerId): Int? =
        lastTributeRounds.firstOrNull { it.a == from && it.b == to }?.round

    fun breaksOf(p: PlayerId): Int = pactBreaks.getOrElse(p.value) { 0 }
}
