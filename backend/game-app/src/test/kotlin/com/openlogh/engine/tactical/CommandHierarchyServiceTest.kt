package com.openlogh.engine.tactical

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for CommandHierarchyService (CMD-01: sub-fleet assignment).
 *
 * Pure object, no Spring context needed.
 */
class CommandHierarchyServiceTest {

    private lateinit var hierarchy: CommandHierarchy
    private lateinit var units: MutableList<TacticalUnit>
    private val fleetCommanderId = 100L
    private val subCommanderId = 200L
    private val crewOfficerIds = setOf(100L, 200L, 300L)

    @BeforeEach
    fun setup() {
        hierarchy = CommandHierarchy(
            fleetCommander = fleetCommanderId,
            successionQueue = mutableListOf(100L, 200L, 300L),
        )
        units = (1L..10L).map { id ->
            TacticalUnit(
                fleetId = id,
                officerId = id * 100,
                officerName = "Officer $id",
                factionId = 1L,
                side = BattleSide.ATTACKER,
            )
        }.toMutableList()
    }

    // ── assignSubFleet: valid assignment ──

    @Test
    fun `assignSubFleet_validAssignment_success`() {
        val unitIds = listOf(1L, 2L, 3L)
        val error = CommandHierarchyService.validateSubFleetAssignment(
            hierarchy, fleetCommanderId, subCommanderId, unitIds, crewOfficerIds,
        )
        assertNull(error, "Valid assignment should return null error")

        CommandHierarchyService.assignSubFleet(
            hierarchy, subCommanderId, "Vice Commander", 7, unitIds, units,
        )

        assertTrue(hierarchy.subCommanders.containsKey(subCommanderId))
        assertEquals(unitIds, hierarchy.subCommanders[subCommanderId]!!.unitIds)
        // Verify TacticalUnit.subFleetCommanderId is set
        for (id in unitIds) {
            val unit = units.find { it.fleetId == id }!!
            assertEquals(subCommanderId, unit.subFleetCommanderId)
        }
    }

    // ── assignSubFleet: rejection cases ──

    @Test
    fun `assignSubFleet_nonCommanderRejects`() {
        val error = CommandHierarchyService.validateSubFleetAssignment(
            hierarchy, 999L, subCommanderId, listOf(1L), crewOfficerIds,
        )
        assertNotNull(error, "Non-fleet-commander should be rejected")
        assertTrue(error!!.contains("commander"), "Error should mention commander")
    }

    @Test
    fun `assignSubFleet_nonCrewOfficerRejects`() {
        val error = CommandHierarchyService.validateSubFleetAssignment(
            hierarchy, fleetCommanderId, 999L, listOf(1L), crewOfficerIds,
        )
        assertNotNull(error, "Non-crew officer should be rejected")
        assertTrue(error!!.contains("crew"), "Error should mention crew")
    }

    @Test
    fun `assignSubFleet_emptyUnitsRejects`() {
        val error = CommandHierarchyService.validateSubFleetAssignment(
            hierarchy, fleetCommanderId, subCommanderId, emptyList(), crewOfficerIds,
        )
        assertNotNull(error, "Empty unit list should be rejected")
    }

    @Test
    fun `assignSubFleet_exceedsMaxUnitsRejects`() {
        // First assign 58 units to sub-commander A
        val bigUnitIds = (1L..58L).toList()
        val bigUnits = (1L..65L).map { id ->
            TacticalUnit(
                fleetId = id, officerId = id * 100, officerName = "Officer $id",
                factionId = 1L, side = BattleSide.ATTACKER,
            )
        }.toMutableList()
        val bigHierarchy = CommandHierarchy(
            fleetCommander = fleetCommanderId,
        )
        CommandHierarchyService.assignSubFleet(
            bigHierarchy, 300L, "Staff A", 5, bigUnitIds, bigUnits,
        )

        // Now try to assign 3 more to sub-commander B (total would be 61, > 60)
        val error = CommandHierarchyService.validateSubFleetAssignment(
            bigHierarchy, fleetCommanderId, subCommanderId, listOf(59L, 60L, 61L),
            setOf(100L, 200L, 300L),
        )
        assertNotNull(error, "Exceeding 60 total units should be rejected")
        assertTrue(error!!.contains("60"), "Error should mention 60 unit limit")
    }

    // ── assignSubFleet: reassignment from old sub-fleet ──

    @Test
    fun `assignSubFleet_reassignsFromOldSubFleet`() {
        // First assign units 1,2,3 to sub-commander 200
        CommandHierarchyService.assignSubFleet(
            hierarchy, subCommanderId, "Vice Commander", 7, listOf(1L, 2L, 3L), units,
        )
        // Now assign units 2,3 to sub-commander 300 (should remove from 200's sub-fleet)
        CommandHierarchyService.assignSubFleet(
            hierarchy, 300L, "Staff Officer", 5, listOf(2L, 3L), units,
        )

        assertEquals(listOf(1L), hierarchy.subCommanders[subCommanderId]!!.unitIds)
        assertEquals(listOf(2L, 3L), hierarchy.subCommanders[300L]!!.unitIds)
        // Verify unit.subFleetCommanderId updated
        assertEquals(subCommanderId, units.find { it.fleetId == 1L }!!.subFleetCommanderId)
        assertEquals(300L, units.find { it.fleetId == 2L }!!.subFleetCommanderId)
        assertEquals(300L, units.find { it.fleetId == 3L }!!.subFleetCommanderId)
    }

    // ── resolveCommanderForUnit ──

    @Test
    fun `resolveCommanderForUnit_directUnit_returnsFleetCommander`() {
        val unit = units[0]  // not assigned to any sub-fleet
        val commander = CommandHierarchyService.resolveCommanderForUnit(unit, hierarchy)
        assertEquals(fleetCommanderId, commander)
    }

    @Test
    fun `resolveCommanderForUnit_assignedUnit_returnsSubFleetCommander`() {
        CommandHierarchyService.assignSubFleet(
            hierarchy, subCommanderId, "Vice Commander", 7, listOf(1L), units,
        )
        val unit = units.find { it.fleetId == 1L }!!
        val commander = CommandHierarchyService.resolveCommanderForUnit(unit, hierarchy)
        assertEquals(subCommanderId, commander)
    }

    // ── buildPriorityList ──

    @Test
    fun `buildPriorityList_sortsCorrectly`() {
        val officerData = listOf(
            OfficerPriorityData(officerId = 1L, rank = 3, evaluation = 50, merit = 50),
            OfficerPriorityData(officerId = 2L, rank = 7, evaluation = 30, merit = 20),
            OfficerPriorityData(officerId = 3L, rank = 5, evaluation = 60, merit = 80),
        )
        val onlineIds = setOf(3L)  // only officer 3 is online

        val result = CommandHierarchyService.buildPriorityList(officerData, onlineIds)

        // Officer 3 is online, so first regardless of rank
        assertEquals(3L, result[0].officerId, "Online officer should be first")
        // Among offline: rank 7 > rank 3
        assertEquals(2L, result[1].officerId, "Offline rank 7 should be second")
        assertEquals(1L, result[2].officerId, "Offline rank 3 should be third")
    }
}
