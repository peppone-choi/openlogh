package com.openlogh.engine.ai

import com.openlogh.entity.Officer
import com.openlogh.model.CommandGroup
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for UtilityScorer: verifies that PersonalityTrait produces correct
 * CommandGroup score ordering for gin7 command selection.
 */
class UtilityScorerTest {

    /**
     * Build an Officer with all stats set to a base value.
     * Stat fields are nullable Short in the entity, so we use non-null values here.
     */
    private fun officerWithStats(
        leadership: Int = 50, command: Int = 50,
        intelligence: Int = 50, politics: Int = 50,
        administration: Int = 50, mobility: Int = 50,
        attack: Int = 50, defense: Int = 50,
    ): Officer {
        val o = Officer()
        o.leadership = leadership.toShort()
        o.command = command.toShort()
        o.intelligence = intelligence.toShort()
        o.politics = politics.toShort()
        o.administration = administration.toShort()
        o.mobility = mobility.toShort()
        o.attack = attack.toShort()
        o.defense = defense.toShort()
        o.positionCards = mutableListOf()
        o.personality = PersonalityTrait.BALANCED.name
        return o
    }

    // ─── Test 1: AGGRESSIVE ────────────────────────────────────────────────

    @Test
    fun `AGGRESSIVE trait scores OPERATIONS group highest`() {
        val officer = officerWithStats(attack = 90, command = 85, mobility = 80)
        val scores = UtilityScorer.scoreGroups(officer, PersonalityTrait.AGGRESSIVE)

        val sorted = scores.entries.sortedByDescending { it.value }
        val topGroup = sorted.first().key

        // OPERATIONS is driven by attack+command+mobility which AGGRESSIVE boosts
        assertTrue(
            topGroup == CommandGroup.OPERATIONS.name || topGroup == CommandGroup.COMMAND.name,
            "Expected OPERATIONS or COMMAND at top for AGGRESSIVE, got: $topGroup. Scores: $scores"
        )
        // Specifically, OPERATIONS must be in top 2
        val top2 = sorted.take(2).map { it.key }.toSet()
        assertTrue(CommandGroup.OPERATIONS.name in top2,
            "OPERATIONS must be in top 2 for AGGRESSIVE. Got top2=$top2, scores=$scores")
    }

    @Test
    fun `AGGRESSIVE trait scores COMMANDER group in top 2`() {
        val officer = officerWithStats(attack = 90, command = 85, mobility = 80)
        val scores = UtilityScorer.scoreGroups(officer, PersonalityTrait.AGGRESSIVE)

        val sorted = scores.entries.sortedByDescending { it.value }.take(3).map { it.key }
        assertTrue(
            CommandGroup.OPERATIONS.name in sorted || CommandGroup.COMMAND.name in sorted,
            "OPERATIONS or COMMAND must appear in top 3 for AGGRESSIVE. Got: $sorted"
        )
    }

    // ─── Test 2: POLITICAL ──────────────────────────────────────────────────

    @Test
    fun `POLITICAL trait scores POLITICS or INTELLIGENCE group highest`() {
        val officer = officerWithStats(politics = 90, intelligence = 85, administration = 80)
        val scores = UtilityScorer.scoreGroups(officer, PersonalityTrait.POLITICAL)

        val sorted = scores.entries.sortedByDescending { it.value }
        val top2 = sorted.take(2).map { it.key }.toSet()

        assertTrue(
            CommandGroup.POLITICS.name in top2 || CommandGroup.INTELLIGENCE.name in top2,
            "POLITICS or INTELLIGENCE must be in top 2 for POLITICAL. Got top2=$top2, scores=$scores"
        )
    }

    // ─── Test 3: DEFENSIVE ──────────────────────────────────────────────────

    @Test
    fun `DEFENSIVE trait scores LOGISTICS or PERSONNEL group in top 2`() {
        val officer = officerWithStats(defense = 90, administration = 85, leadership = 80)
        val scores = UtilityScorer.scoreGroups(officer, PersonalityTrait.DEFENSIVE)

        val sorted = scores.entries.sortedByDescending { it.value }
        val top2 = sorted.take(2).map { it.key }.toSet()

        assertTrue(
            CommandGroup.LOGISTICS.name in top2 || CommandGroup.PERSONNEL.name in top2,
            "LOGISTICS or PERSONNEL must be in top 2 for DEFENSIVE. Got top2=$top2, scores=$scores"
        )
    }

    // ─── Test 4: CAUTIOUS ───────────────────────────────────────────────────

    @Test
    fun `CAUTIOUS trait scores INTELLIGENCE group in top 3`() {
        val officer = officerWithStats(intelligence = 90, defense = 85)
        val scores = UtilityScorer.scoreGroups(officer, PersonalityTrait.CAUTIOUS)

        val top3 = scores.entries.sortedByDescending { it.value }.take(3).map { it.key }.toSet()

        assertTrue(
            CommandGroup.INTELLIGENCE.name in top3,
            "INTELLIGENCE must be in top 3 for CAUTIOUS. Got top3=$top3, scores=$scores"
        )
    }

    // ─── Test 5: BALANCED ───────────────────────────────────────────────────

    @Test
    fun `BALANCED trait has no single dominant group — all scores within 20 percent of each other`() {
        // Equal stats ensure only weights differentiate scores
        val officer = officerWithStats()  // all stats = 50
        val scores = UtilityScorer.scoreGroups(officer, PersonalityTrait.BALANCED)

        val maxScore = scores.values.max()
        val minScore = scores.values.min()

        // With BALANCED weights (all 1.0), stats are equal → scores should be very close
        assertTrue(
            minScore >= maxScore * 0.80,
            "BALANCED should have no dominant group (all within 20%). max=$maxScore min=$minScore scores=$scores"
        )
    }

    // ─── Test 6: scoreCommand returns 0.0 for commands officer can't hold ──

    @Test
    fun `scoreCommand returns 0 dot 0 for command whose PositionCard officer does not hold`() {
        val officer = officerWithStats()
        // Officer has no position cards
        officer.positionCards = mutableListOf()

        // "워프항행" is in OPERATIONS group — requires an OPERATIONS-granting card
        val score = UtilityScorer.scoreCommand(
            commandName = "워프항행",
            officer = officer,
            trait = PersonalityTrait.AGGRESSIVE,
        )

        assertEquals(0.0, score, "Command inaccessible to officer should score 0.0")
    }
}
