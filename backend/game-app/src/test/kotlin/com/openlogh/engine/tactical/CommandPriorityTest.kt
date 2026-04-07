package com.openlogh.engine.tactical

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for CommandPriority ordering (CMD-02).
 *
 * Priority order: online > rank > evaluation > merit > officerId (lower = older = wins)
 * Per decisions D-09, D-10, D-11.
 */
class CommandPriorityTest {

    @Test
    fun `online player beats offline player regardless of rank`() {
        val online = CommandPriority(isOnline = true, rank = 1, evaluation = 0, merit = 0, officerId = 200L)
        val offline = CommandPriority(isOnline = false, rank = 10, evaluation = 100, merit = 100, officerId = 100L)
        assertTrue(online > offline, "Online player should beat offline player even with lower rank")
    }

    @Test
    fun `higher rank wins among same online status`() {
        val highRank = CommandPriority(isOnline = true, rank = 8, evaluation = 0, merit = 0, officerId = 200L)
        val lowRank = CommandPriority(isOnline = true, rank = 5, evaluation = 100, merit = 100, officerId = 100L)
        assertTrue(highRank > lowRank, "Higher rank should win among same online status")
    }

    @Test
    fun `higher evaluation wins among same rank`() {
        val highEval = CommandPriority(isOnline = true, rank = 5, evaluation = 80, merit = 0, officerId = 200L)
        val lowEval = CommandPriority(isOnline = true, rank = 5, evaluation = 30, merit = 100, officerId = 100L)
        assertTrue(highEval > lowEval, "Higher evaluation should win among same rank")
    }

    @Test
    fun `higher merit wins among same evaluation`() {
        val highMerit = CommandPriority(isOnline = true, rank = 5, evaluation = 50, merit = 80, officerId = 200L)
        val lowMerit = CommandPriority(isOnline = true, rank = 5, evaluation = 50, merit = 20, officerId = 100L)
        assertTrue(highMerit > lowMerit, "Higher merit should win among same evaluation")
    }

    @Test
    fun `lower officerId wins as tiebreak`() {
        val older = CommandPriority(isOnline = true, rank = 5, evaluation = 50, merit = 50, officerId = 100L)
        val newer = CommandPriority(isOnline = true, rank = 5, evaluation = 50, merit = 50, officerId = 200L)
        assertTrue(older > newer, "Lower officerId (older officer) should win as tiebreak")
    }

    @Test
    fun `all NPCs offline sorted by rank`() {
        val priorities = listOf(
            CommandPriority(isOnline = false, rank = 3, evaluation = 50, merit = 50, officerId = 10L),
            CommandPriority(isOnline = false, rank = 7, evaluation = 30, merit = 20, officerId = 20L),
            CommandPriority(isOnline = false, rank = 5, evaluation = 60, merit = 80, officerId = 30L),
        ).sorted().reversed()  // sorted ascending, reverse for descending priority

        assertEquals(20L, priorities[0].officerId, "Rank 7 should be first")
        assertEquals(30L, priorities[1].officerId, "Rank 5 should be second")
        assertEquals(10L, priorities[2].officerId, "Rank 3 should be third")
    }

    @Test
    fun `all online sorted by rank then evaluation`() {
        val priorities = listOf(
            CommandPriority(isOnline = true, rank = 5, evaluation = 80, merit = 0, officerId = 10L),
            CommandPriority(isOnline = true, rank = 5, evaluation = 40, merit = 0, officerId = 20L),
            CommandPriority(isOnline = true, rank = 8, evaluation = 10, merit = 0, officerId = 30L),
        ).sorted().reversed()

        assertEquals(30L, priorities[0].officerId, "Rank 8 should be first")
        assertEquals(10L, priorities[1].officerId, "Rank 5 eval 80 should be second")
        assertEquals(20L, priorities[2].officerId, "Rank 5 eval 40 should be third")
    }

    @Test
    fun `mixed online and offline with various ranks`() {
        val priorities = listOf(
            CommandPriority(isOnline = false, rank = 10, evaluation = 100, merit = 100, officerId = 1L),  // offline high rank
            CommandPriority(isOnline = true, rank = 1, evaluation = 0, merit = 0, officerId = 2L),        // online low rank
            CommandPriority(isOnline = true, rank = 5, evaluation = 50, merit = 50, officerId = 3L),      // online mid rank
            CommandPriority(isOnline = false, rank = 7, evaluation = 80, merit = 80, officerId = 4L),     // offline mid rank
        ).sorted().reversed()

        // Online players first (sorted by rank), then offline (sorted by rank)
        assertEquals(3L, priorities[0].officerId, "Online rank 5 should be first")
        assertEquals(2L, priorities[1].officerId, "Online rank 1 should be second")
        assertEquals(1L, priorities[2].officerId, "Offline rank 10 should be third")
        assertEquals(4L, priorities[3].officerId, "Offline rank 7 should be fourth")
    }

    @Test
    fun `equal priorities are consistent with equals`() {
        val a = CommandPriority(isOnline = true, rank = 5, evaluation = 50, merit = 50, officerId = 100L)
        val b = CommandPriority(isOnline = true, rank = 5, evaluation = 50, merit = 50, officerId = 100L)
        assertEquals(0, a.compareTo(b))
        assertEquals(a, b)
    }
}
