package com.openlogh.engine.tactical

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Communication jamming tests (D-12, D-13, D-14).
 *
 * D-12: Jamming triggered by enemy special ability/equipment
 * D-13: Only fleet commander's fleet-wide orders are blocked; sub-fleet commanders unaffected
 * D-14: Auto-clear after ticks expire or jammer destroyed/retreated
 */
class CommunicationJammingTest {

    private fun makeHierarchy(fleetCommander: Long = 1L): CommandHierarchy {
        return CommandHierarchy(
            fleetCommander = fleetCommander,
            subCommanders = mutableMapOf(
                2L to SubFleet(commanderId = 2L, commanderName = "Sub-Cmdr", unitIds = listOf(20L, 21L), commanderRank = 5),
            ),
            successionQueue = mutableListOf(2L, 3L),
            crcRadius = mutableMapOf(1L to 100.0, 2L to 80.0),
        )
    }

    private fun makeUnit(
        fleetId: Long = 1L,
        officerId: Long = 1L,
        side: BattleSide = BattleSide.ATTACKER,
        isAlive: Boolean = true,
        isRetreating: Boolean = false,
    ): TacticalUnit {
        return TacticalUnit(
            fleetId = fleetId,
            officerId = officerId,
            officerName = "Officer-$officerId",
            factionId = 1L,
            side = side,
            isAlive = isAlive,
            isRetreating = isRetreating,
        )
    }

    private fun makeCommand(officerId: Long, battleId: Long = 1L): TacticalCommand {
        return TacticalCommand.SetEnergy(
            battleId = battleId,
            officerId = officerId,
            allocation = com.openlogh.model.EnergyAllocation.BALANCED,
        )
    }

    // ── applyJamming ──

    @Test
    fun `applyJamming sets fields correctly`() {
        val hierarchy = makeHierarchy()
        assertFalse(hierarchy.commJammed)
        assertEquals(0, hierarchy.jammingTicksRemaining)
        assertNull(hierarchy.jammingSourceOfficerId)

        CommunicationJamming.applyJamming(hierarchy, sourceOfficerId = 99L)

        assertTrue(hierarchy.commJammed)
        assertEquals(60, hierarchy.jammingTicksRemaining)
        assertEquals(99L, hierarchy.jammingSourceOfficerId)
    }

    @Test
    fun `applyJamming with custom duration`() {
        val hierarchy = makeHierarchy()
        CommunicationJamming.applyJamming(hierarchy, sourceOfficerId = 99L, durationTicks = 30)

        assertTrue(hierarchy.commJammed)
        assertEquals(30, hierarchy.jammingTicksRemaining)
    }

    // ── isFleetWideCommandBlocked ──

    @Test
    fun `isBlocked - jammed fleet commander - returns true`() {
        val hierarchy = makeHierarchy(fleetCommander = 1L)
        CommunicationJamming.applyJamming(hierarchy, sourceOfficerId = 99L)

        val cmd = makeCommand(officerId = 1L)  // fleet commander
        val targetUnit = makeUnit(fleetId = 10L, officerId = 10L)  // different officer's unit

        assertTrue(CommunicationJamming.isFleetWideCommandBlocked(cmd, targetUnit, hierarchy))
    }

    @Test
    fun `isBlocked - jammed sub-fleet commander - returns false (D-13)`() {
        val hierarchy = makeHierarchy(fleetCommander = 1L)
        CommunicationJamming.applyJamming(hierarchy, sourceOfficerId = 99L)

        val cmd = makeCommand(officerId = 2L)  // sub-fleet commander
        val targetUnit = makeUnit(fleetId = 20L, officerId = 20L)

        assertFalse(CommunicationJamming.isFleetWideCommandBlocked(cmd, targetUnit, hierarchy))
    }

    @Test
    fun `isBlocked - self command - returns false`() {
        val hierarchy = makeHierarchy(fleetCommander = 1L)
        CommunicationJamming.applyJamming(hierarchy, sourceOfficerId = 99L)

        val cmd = makeCommand(officerId = 1L)  // fleet commander
        val targetUnit = makeUnit(fleetId = 10L, officerId = 1L)  // same officer's unit (self-command)

        assertFalse(CommunicationJamming.isFleetWideCommandBlocked(cmd, targetUnit, hierarchy))
    }

    @Test
    fun `isBlocked - not jammed - returns false`() {
        val hierarchy = makeHierarchy(fleetCommander = 1L)
        // Not jammed

        val cmd = makeCommand(officerId = 1L)
        val targetUnit = makeUnit(fleetId = 10L, officerId = 10L)

        assertFalse(CommunicationJamming.isFleetWideCommandBlocked(cmd, targetUnit, hierarchy))
    }

    // ── tickJamming ──

    @Test
    fun `tickJamming decrements and clears at zero`() {
        val hierarchy = makeHierarchy()
        CommunicationJamming.applyJamming(hierarchy, sourceOfficerId = 99L, durationTicks = 3)

        // Tick 1: 3 -> 2
        CommunicationJamming.tickJamming(hierarchy)
        assertTrue(hierarchy.commJammed)
        assertEquals(2, hierarchy.jammingTicksRemaining)

        // Tick 2: 2 -> 1
        CommunicationJamming.tickJamming(hierarchy)
        assertTrue(hierarchy.commJammed)
        assertEquals(1, hierarchy.jammingTicksRemaining)

        // Tick 3: 1 -> 0, clears
        CommunicationJamming.tickJamming(hierarchy)
        assertFalse(hierarchy.commJammed)
        assertEquals(0, hierarchy.jammingTicksRemaining)
        assertNull(hierarchy.jammingSourceOfficerId)
    }

    @Test
    fun `tickJamming does nothing when not jammed`() {
        val hierarchy = makeHierarchy()
        // Not jammed
        CommunicationJamming.tickJamming(hierarchy)

        assertFalse(hierarchy.commJammed)
        assertEquals(0, hierarchy.jammingTicksRemaining)
    }

    // ── clearJammingIfSourceGone ──

    @Test
    fun `clearIfSourceGone - source destroyed - clears immediately (Pitfall 4)`() {
        val hierarchy = makeHierarchy()
        CommunicationJamming.applyJamming(hierarchy, sourceOfficerId = 99L)

        val allUnits = listOf(
            makeUnit(fleetId = 99L, officerId = 99L, side = BattleSide.DEFENDER, isAlive = false),
        )

        CommunicationJamming.clearJammingIfSourceGone(hierarchy, allUnits)

        assertFalse(hierarchy.commJammed)
        assertEquals(0, hierarchy.jammingTicksRemaining)
        assertNull(hierarchy.jammingSourceOfficerId)
    }

    @Test
    fun `clearIfSourceGone - source retreated - clears immediately`() {
        val hierarchy = makeHierarchy()
        CommunicationJamming.applyJamming(hierarchy, sourceOfficerId = 99L)

        val allUnits = listOf(
            makeUnit(fleetId = 99L, officerId = 99L, side = BattleSide.DEFENDER, isAlive = true, isRetreating = true),
        )

        CommunicationJamming.clearJammingIfSourceGone(hierarchy, allUnits)

        assertFalse(hierarchy.commJammed)
        assertEquals(0, hierarchy.jammingTicksRemaining)
        assertNull(hierarchy.jammingSourceOfficerId)
    }

    @Test
    fun `clearIfSourceGone - source alive and present - no change`() {
        val hierarchy = makeHierarchy()
        CommunicationJamming.applyJamming(hierarchy, sourceOfficerId = 99L)

        val allUnits = listOf(
            makeUnit(fleetId = 99L, officerId = 99L, side = BattleSide.DEFENDER, isAlive = true, isRetreating = false),
        )

        CommunicationJamming.clearJammingIfSourceGone(hierarchy, allUnits)

        assertTrue(hierarchy.commJammed)
        assertEquals(60, hierarchy.jammingTicksRemaining)
        assertEquals(99L, hierarchy.jammingSourceOfficerId)
    }

    @Test
    fun `clearIfSourceGone - source not in unit list - clears`() {
        val hierarchy = makeHierarchy()
        CommunicationJamming.applyJamming(hierarchy, sourceOfficerId = 99L)

        val allUnits = listOf(
            makeUnit(fleetId = 1L, officerId = 1L),
        )

        CommunicationJamming.clearJammingIfSourceGone(hierarchy, allUnits)

        assertFalse(hierarchy.commJammed)
    }

    // ── Full tick cycle ──

    @Test
    fun `triggerJamming - full tick cycle - expires after duration`() {
        val hierarchy = makeHierarchy()
        CommunicationJamming.applyJamming(hierarchy, sourceOfficerId = 99L, durationTicks = 5)

        val sourceUnit = makeUnit(fleetId = 99L, officerId = 99L, side = BattleSide.DEFENDER)
        val allUnits = listOf(sourceUnit)

        for (tick in 1..4) {
            CommunicationJamming.tickJamming(hierarchy)
            CommunicationJamming.clearJammingIfSourceGone(hierarchy, allUnits)
            assertTrue(hierarchy.commJammed, "Should still be jammed at tick $tick")
        }

        // Tick 5: expires
        CommunicationJamming.tickJamming(hierarchy)
        CommunicationJamming.clearJammingIfSourceGone(hierarchy, allUnits)
        assertFalse(hierarchy.commJammed, "Should be cleared after 5 ticks")
    }
}
