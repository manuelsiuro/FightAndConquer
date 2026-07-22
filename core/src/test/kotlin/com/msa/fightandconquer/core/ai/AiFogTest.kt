package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates.custom
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Proves the AI's omniscience leak is closed: enemy assets beyond the AI's own
 * vision must not influence evaluation when fog is on — the score of a state with
 * a far-away enemy cluster must equal the score of the same state without it.
 */
class AiFogTest {

    private val fogRules = RuleConstants(fogOfWar = true)

    /** P0 owns 0..2 (capital 0); the enemy cluster 9..11 is >= distance 5 from every P0 vision source. */
    private fun withEnemyCluster(rules: RuleConstants) = strip(12, 0..2, 9..11, rules = rules)

    private fun withoutEnemyCluster(rules: RuleConstants) = custom(
        owners = (0..11).associate { hex(it) to if (it <= 2) 0 else null },
        capital0 = hex(0),
        capital1 = hex(11),
        rules = rules,
    )

    @Test
    fun `fog hides an out-of-vision enemy cluster from evaluation`() {
        for (difficulty in Difficulty.entries) {
            val withEnemy = Evaluator.score(withEnemyCluster(fogRules), PlayerId(0), difficulty)
            val without = Evaluator.score(withoutEnemyCluster(fogRules), PlayerId(0), difficulty)
            assertEquals("difficulty $difficulty must not see through fog", without, withEnemy, 0.0)
        }
    }

    @Test
    fun `without fog the same enemy cluster changes the score`() {
        val plain = RuleConstants()
        val withEnemy = Evaluator.score(withEnemyCluster(plain), PlayerId(0), Difficulty.NORMAL)
        val without = Evaluator.score(withoutEnemyCluster(plain), PlayerId(0), Difficulty.NORMAL)
        assertNotEquals(without, withEnemy, 0.0)
    }

    @Test
    fun `enemy units beyond vision do not add exposed-border pressure under fog`() {
        // An enemy knight parked far away must not scare HARD's border heuristic.
        val far = withEnemyCluster(fogRules).withUnit(owner = 1, tier = 4, at = hex(10))
        val bare = withoutEnemyCluster(fogRules)
        assertEquals(
            Evaluator.score(bare, PlayerId(0), Difficulty.HARD),
            Evaluator.score(far, PlayerId(0), Difficulty.HARD),
            0.0,
        )
    }
}
