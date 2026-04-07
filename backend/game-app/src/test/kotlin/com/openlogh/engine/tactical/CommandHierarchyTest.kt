package com.openlogh.engine.tactical

import com.openlogh.model.UnitStance
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for ENGINE-03: CommandHierarchy initialization.
 *
 * Tests verify the behavioral contract for buildCommandHierarchy():
 * given a leader officer ID, list of units, and officer rank map,
 * produce a correctly initialized CommandHierarchy.
 */
class CommandHierarchyTest {

    // Test data: 3 officers with different ranks
    private val leaderOfficerId = 100L
    private val officerRanks = mapOf(
        100L to 10,  // Reichsmarschall (rank 10) -- fleet commander
        200L to 7,   // Vice Admiral (rank 7)
        300L to 5,   // Commodore (rank 5)
    )

    private fun makeUnit(officerId: Long, side: BattleSide) = TacticalUnit(
        fleetId = officerId,
        officerId = officerId,
        officerName = "Officer $officerId",
        factionId = 1L,
        side = side,
        posX = 100.0,
        posY = 100.0,
        hp = 1000,
        maxHp = 1000,
        ships = 300,
        maxShips = 300,
        training = 80,
        morale = 80,
        leadership = 50,
        command = 50,
        intelligence = 50,
        mobility = 50,
        attack = 50,
        defense = 50,
    )

    private val units = listOf(
        makeUnit(100L, BattleSide.ATTACKER),
        makeUnit(200L, BattleSide.ATTACKER),
        makeUnit(300L, BattleSide.ATTACKER),
    )

    // Use BattleTriggerService.buildCommandHierarchy directly (internal visibility)
    private fun buildHierarchy() = BattleTriggerService.buildCommandHierarchyStatic(
        leaderOfficerId = leaderOfficerId,
        units = units,
        officerRanks = officerRanks,
    )

    /**
     * buildCommandHierarchy should create a hierarchy with the correct fleetCommander.
     */
    @Test
    fun `buildCommandHierarchy creates hierarchy with correct fleetCommander`() {
        val hierarchy = buildHierarchy()
        assertEquals(leaderOfficerId, hierarchy.fleetCommander)
    }

    /**
     * successionQueue should be ordered by rank descending (highest rank first).
     */
    @Test
    fun `successionQueue is ordered by rank descending`() {
        val hierarchy = buildHierarchy()
        assertEquals(listOf(100L, 200L, 300L), hierarchy.successionQueue)
    }

    /**
     * crcRadius should have an entry for the fleet commander.
     */
    @Test
    fun `crcRadius is initialized for fleet commander`() {
        val hierarchy = buildHierarchy()
        assertTrue(hierarchy.crcRadius.containsKey(leaderOfficerId))
        assertTrue(hierarchy.crcRadius[leaderOfficerId]!! >= 0.0)
    }

    /**
     * commJammed should default to false on initialization.
     */
    @Test
    fun `commJammed defaults to false`() {
        val hierarchy = buildHierarchy()
        assertFalse(hierarchy.commJammed)
    }
}
