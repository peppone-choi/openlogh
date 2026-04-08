package com.openlogh.engine.tactical

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Integration tests for succession processing in the tick loop (SUCC-03/04/05).
 *
 * Tests vacancy countdown, successor selection, succession execution,
 * and subfleet dissolution on commander incapacitation.
 */
class SuccessionEngineTest {

    private lateinit var hierarchy: CommandHierarchy

    @BeforeEach
    fun setUp() {
        hierarchy = CommandHierarchy(
            fleetCommander = 100L,
            successionQueue = mutableListOf(200L, 300L, 400L),
        )
    }

    @Nested
    inner class StartVacancyTest {

        @Test
        fun `sets vacancyStartTick when active commander dies`() {
            SuccessionService.startVacancy(hierarchy, 100L, 50)
            assertEquals(50, hierarchy.vacancyStartTick)
        }

        @Test
        fun `does NOT set vacancy when non-commander officer dies`() {
            SuccessionService.startVacancy(hierarchy, 200L, 50)
            assertEquals(-1, hierarchy.vacancyStartTick)
        }

        @Test
        fun `sets vacancy for delegated active commander`() {
            hierarchy.activeCommander = 200L
            hierarchy.commandDelegated = true
            SuccessionService.startVacancy(hierarchy, 200L, 75)
            assertEquals(75, hierarchy.vacancyStartTick)
        }

        @Test
        fun `original fleet commander death does not trigger vacancy after delegation`() {
            hierarchy.activeCommander = 200L
            hierarchy.commandDelegated = true
            SuccessionService.startVacancy(hierarchy, 100L, 75)
            assertEquals(-1, hierarchy.vacancyStartTick)
        }
    }

    @Nested
    inner class IsVacancyExpiredTest {

        @Test
        fun `returns false when no vacancy is active`() {
            assertFalse(SuccessionService.isVacancyExpired(hierarchy, 100))
        }

        @Test
        fun `returns false before 30 ticks`() {
            hierarchy.vacancyStartTick = 10
            assertFalse(SuccessionService.isVacancyExpired(hierarchy, 39))
        }

        @Test
        fun `returns true at exactly 30 ticks`() {
            hierarchy.vacancyStartTick = 10
            assertTrue(SuccessionService.isVacancyExpired(hierarchy, 40))
        }

        @Test
        fun `returns true after 30 ticks`() {
            hierarchy.vacancyStartTick = 10
            assertTrue(SuccessionService.isVacancyExpired(hierarchy, 50))
        }
    }

    @Nested
    inner class FindNextSuccessorTest {

        @Test
        fun `returns designated successor if alive`() {
            hierarchy.designatedSuccessor = 300L
            val alive = setOf(100L, 200L, 300L, 400L)
            val result = SuccessionService.findNextSuccessor(hierarchy, alive)
            assertEquals(300L, result)
        }

        @Test
        fun `skips dead designated successor and falls back to queue`() {
            hierarchy.designatedSuccessor = 300L
            val alive = setOf(100L, 200L, 400L) // 300 is dead
            val result = SuccessionService.findNextSuccessor(hierarchy, alive)
            assertEquals(200L, result) // first in queue that's alive and not active commander
        }

        @Test
        fun `returns first alive in queue when no designated successor`() {
            val alive = setOf(100L, 200L, 300L, 400L)
            val result = SuccessionService.findNextSuccessor(hierarchy, alive)
            assertEquals(200L, result)
        }

        @Test
        fun `skips active commander in queue`() {
            // activeCommander is 100 (fleetCommander), queue has 200, 300, 400
            // If 200 is dead, next should be 300
            val alive = setOf(100L, 300L, 400L)
            val result = SuccessionService.findNextSuccessor(hierarchy, alive)
            assertEquals(300L, result)
        }

        @Test
        fun `returns null when all candidates are dead`() {
            val alive = setOf(100L) // only current commander alive, no candidates
            val result = SuccessionService.findNextSuccessor(hierarchy, alive)
            assertNull(result)
        }

        @Test
        fun `returns null when queue is empty and no designated successor`() {
            hierarchy.successionQueue.clear()
            val alive = setOf(100L, 200L)
            val result = SuccessionService.findNextSuccessor(hierarchy, alive)
            assertNull(result)
        }
    }

    @Nested
    inner class ExecuteSuccessionTest {

        @Test
        fun `sets activeCommander and resets vacancy state`() {
            hierarchy.vacancyStartTick = 10
            hierarchy.designatedSuccessor = 200L
            hierarchy.injuryCapabilityModifier = 0.5
            hierarchy.commandDelegated = true

            val alive = setOf(100L, 200L, 300L)
            val result = SuccessionService.executeSuccession(hierarchy, alive)

            assertEquals(200L, result)
            assertEquals(200L, hierarchy.activeCommander)
            assertEquals(-1, hierarchy.vacancyStartTick)
            assertNull(hierarchy.designatedSuccessor)
            assertFalse(hierarchy.commandDelegated)
            assertEquals(1.0, hierarchy.injuryCapabilityModifier)
        }

        @Test
        fun `returns null when no successor available`() {
            hierarchy.vacancyStartTick = 10
            val alive = setOf(100L) // only commander alive
            val result = SuccessionService.executeSuccession(hierarchy, alive)
            assertNull(result)
        }

        @Test
        fun `falls back to rank-ordered queue when designated is dead`() {
            hierarchy.vacancyStartTick = 10
            hierarchy.designatedSuccessor = 300L
            val alive = setOf(100L, 200L, 400L) // 300 is dead
            val result = SuccessionService.executeSuccession(hierarchy, alive)
            assertEquals(200L, result)
        }
    }

    @Nested
    inner class ReturnUnitsToDirectCommandTest {

        @Test
        fun `removes subfleet entry and clears unit subFleetCommanderId`() {
            val units = listOf(
                createUnit(1L, 10L, BattleSide.ATTACKER, subFleetCmdId = 200L),
                createUnit(2L, 20L, BattleSide.ATTACKER, subFleetCmdId = 200L),
                createUnit(3L, 30L, BattleSide.ATTACKER, subFleetCmdId = null),
            )
            hierarchy.subCommanders[200L] = SubFleet(
                commanderId = 200L,
                commanderName = "Mittermeyer",
                unitIds = listOf(1L, 2L),
                commanderRank = 8,
            )

            val returned = CommandHierarchyService.returnUnitsToDirectCommand(hierarchy, 200L, units)

            assertEquals(listOf(1L, 2L), returned)
            assertFalse(hierarchy.subCommanders.containsKey(200L))
            assertNull(units[0].subFleetCommanderId)
            assertNull(units[1].subFleetCommanderId)
            assertNull(units[2].subFleetCommanderId) // unchanged
        }

        @Test
        fun `returns empty list for non-existent commander`() {
            val units = listOf(
                createUnit(1L, 10L, BattleSide.ATTACKER),
            )
            val returned = CommandHierarchyService.returnUnitsToDirectCommand(hierarchy, 999L, units)
            assertTrue(returned.isEmpty())
        }

        @Test
        fun `does not affect units in other subfleets`() {
            val units = listOf(
                createUnit(1L, 10L, BattleSide.ATTACKER, subFleetCmdId = 200L),
                createUnit(2L, 20L, BattleSide.ATTACKER, subFleetCmdId = 300L),
            )
            hierarchy.subCommanders[200L] = SubFleet(200L, "Mittermeyer", listOf(1L), 8)
            hierarchy.subCommanders[300L] = SubFleet(300L, "Reuenthal", listOf(2L), 8)

            CommandHierarchyService.returnUnitsToDirectCommand(hierarchy, 200L, units)

            assertNull(units[0].subFleetCommanderId)
            assertEquals(300L, units[1].subFleetCommanderId) // unchanged
            assertTrue(hierarchy.subCommanders.containsKey(300L))
        }
    }

    // ── Helper ──

    private fun createUnit(
        fleetId: Long,
        officerId: Long,
        side: BattleSide,
        subFleetCmdId: Long? = null,
    ): TacticalUnit = TacticalUnit(
        fleetId = fleetId,
        officerId = officerId,
        officerName = "Officer$officerId",
        factionId = 1L,
        side = side,
        subFleetCommanderId = subFleetCmdId,
    )
}
