package com.openlogh.engine.tactical

import com.openlogh.model.InjuryEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SuccessionServiceTest {

    private lateinit var hierarchy: CommandHierarchy

    @BeforeEach
    fun setUp() {
        hierarchy = CommandHierarchy(
            fleetCommander = 100L,
            successionQueue = mutableListOf(200L, 300L, 400L),
        )
    }

    @Nested
    inner class DesignateSuccessorTest {

        @Test
        fun `valid designation sets designatedSuccessor field`() {
            val error = SuccessionService.designateSuccessor(hierarchy, 100L, 200L)
            assertNull(error)
            assertEquals(200L, hierarchy.designatedSuccessor)
        }

        @Test
        fun `successor not in queue returns error`() {
            val error = SuccessionService.designateSuccessor(hierarchy, 100L, 999L)
            assertNotNull(error)
            assertTrue(error!!.contains("succession queue"))
            assertNull(hierarchy.designatedSuccessor)
        }

        @Test
        fun `non-commander cannot designate`() {
            val error = SuccessionService.designateSuccessor(hierarchy, 200L, 300L)
            assertNotNull(error)
            assertTrue(error!!.contains("active commander"))
            assertNull(hierarchy.designatedSuccessor)
        }

        @Test
        fun `activeCommander can designate after delegation`() {
            // Simulate delegation: activeCommander is 200
            hierarchy.designatedSuccessor = 200L
            hierarchy.activeCommander = 200L
            hierarchy.commandDelegated = true

            // 200 (now active) designates 300
            val error = SuccessionService.designateSuccessor(hierarchy, 200L, 300L)
            assertNull(error)
            assertEquals(300L, hierarchy.designatedSuccessor)
        }
    }

    @Nested
    inner class ApplyInjuryCapabilityReductionTest {

        @Test
        fun `severity at threshold halves capability`() {
            val injury = InjuryEvent(
                officerId = 100L,
                officerName = "Reinhard",
                severity = 40,
                returnPlanetId = 1L,
            )
            val reduced = SuccessionService.applyInjuryCapabilityReduction(hierarchy, injury)
            assertTrue(reduced)
            assertEquals(0.5, hierarchy.injuryCapabilityModifier)
        }

        @Test
        fun `severity above threshold halves capability`() {
            val injury = InjuryEvent(
                officerId = 100L,
                officerName = "Reinhard",
                severity = 60,
                returnPlanetId = 1L,
            )
            val reduced = SuccessionService.applyInjuryCapabilityReduction(hierarchy, injury)
            assertTrue(reduced)
            assertEquals(0.5, hierarchy.injuryCapabilityModifier)
        }

        @Test
        fun `severity below threshold leaves capability unchanged`() {
            val injury = InjuryEvent(
                officerId = 100L,
                officerName = "Reinhard",
                severity = 20,
                returnPlanetId = 1L,
            )
            val reduced = SuccessionService.applyInjuryCapabilityReduction(hierarchy, injury)
            assertFalse(reduced)
            assertEquals(1.0, hierarchy.injuryCapabilityModifier)
        }

        @Test
        fun `injury to non-commander has no effect`() {
            val injury = InjuryEvent(
                officerId = 200L,
                officerName = "Mittermeyer",
                severity = 60,
                returnPlanetId = 1L,
            )
            val reduced = SuccessionService.applyInjuryCapabilityReduction(hierarchy, injury)
            assertFalse(reduced)
            assertEquals(1.0, hierarchy.injuryCapabilityModifier)
        }
    }

    @Nested
    inner class DelegateCommandTest {

        @Test
        fun `delegation sets activeCommander to designatedSuccessor`() {
            hierarchy.designatedSuccessor = 200L
            val error = SuccessionService.delegateCommand(hierarchy, 100L)
            assertNull(error)
            assertEquals(200L, hierarchy.activeCommander)
            assertTrue(hierarchy.commandDelegated)
        }

        @Test
        fun `delegation without designated successor returns error`() {
            val error = SuccessionService.delegateCommand(hierarchy, 100L)
            assertNotNull(error)
            assertTrue(error!!.contains("No successor designated"))
            assertNull(hierarchy.activeCommander)
            assertFalse(hierarchy.commandDelegated)
        }

        @Test
        fun `non-commander cannot delegate`() {
            hierarchy.designatedSuccessor = 200L
            val error = SuccessionService.delegateCommand(hierarchy, 300L)
            assertNotNull(error)
            assertTrue(error!!.contains("active commander"))
            assertNull(hierarchy.activeCommander)
        }
    }

    @Nested
    inner class GetActiveCommanderTest {

        @Test
        fun `returns fleetCommander when no delegation`() {
            assertEquals(100L, SuccessionService.getActiveCommander(hierarchy))
        }

        @Test
        fun `returns activeCommander after delegation`() {
            hierarchy.activeCommander = 200L
            assertEquals(200L, SuccessionService.getActiveCommander(hierarchy))
        }
    }
}
