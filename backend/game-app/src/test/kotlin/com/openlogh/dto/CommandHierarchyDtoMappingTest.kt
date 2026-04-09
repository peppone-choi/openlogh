package com.openlogh.dto

import com.openlogh.engine.tactical.CommandHierarchy
import com.openlogh.engine.tactical.SubFleet
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Phase 14 Plan 01 Task 2 (D-21, D-37): Engine → DTO mapping test for
 * [CommandHierarchyDto.fromEngine].
 *
 * Frontend-critical: this test pins the field-rename contract
 * (`commanderId` → `commanderOfficerId`, `unitIds` → `memberFleetIds`) so that
 * 14-06 (frontend types) remains stable even if the engine refactors.
 */
class CommandHierarchyDtoMappingTest {

    @Test
    fun `fromEngine maps all scalar fields verbatim`() {
        val engine = CommandHierarchy(
            fleetCommander = 100L,
            successionQueue = mutableListOf(200L, 300L),
            commJammed = true,
            jammingTicksRemaining = 15,
            designatedSuccessor = 200L,
            vacancyStartTick = 42,
            activeCommander = 200L,
        )
        // Sub-commanders are stored keyed by commanderId in the engine model.
        engine.subCommanders[200L] = SubFleet(
            commanderId = 200L,
            commanderName = "Yang",
            unitIds = listOf(10L, 11L),
            commanderRank = 8,
        )
        engine.subCommanders[300L] = SubFleet(
            commanderId = 300L,
            commanderName = "Reinhardt",
            unitIds = listOf(12L),
            commanderRank = 7,
        )

        val dto = CommandHierarchyDto.fromEngine(engine)

        assertEquals(100L, dto.fleetCommander)
        assertEquals(listOf(200L, 300L), dto.successionQueue)
        assertEquals(200L, dto.designatedSuccessor)
        assertEquals(42, dto.vacancyStartTick)
        assertTrue(dto.commJammed)
        assertEquals(15, dto.jammingTicksRemaining)
        assertEquals(200L, dto.activeCommander)
    }

    @Test
    fun `fromEngine projects sub-fleets with renamed fields`() {
        val engine = CommandHierarchy(fleetCommander = 100L)
        engine.subCommanders[200L] = SubFleet(
            commanderId = 200L,
            commanderName = "Yang",
            unitIds = listOf(10L, 11L),
            commanderRank = 8,
        )
        engine.subCommanders[300L] = SubFleet(
            commanderId = 300L,
            commanderName = "Reinhardt",
            unitIds = listOf(12L),
            commanderRank = 7,
        )

        val dto = CommandHierarchyDto.fromEngine(engine)

        assertEquals(2, dto.subFleets.size)
        val yang = dto.subFleets.first { it.commanderOfficerId == 200L }
        assertEquals("Yang", yang.commanderName)
        assertEquals(listOf(10L, 11L), yang.memberFleetIds)
        assertEquals(8, yang.commanderRank)
        val reinhardt = dto.subFleets.first { it.commanderOfficerId == 300L }
        assertEquals("Reinhardt", reinhardt.commanderName)
        assertEquals(listOf(12L), reinhardt.memberFleetIds)
        assertEquals(7, reinhardt.commanderRank)
    }

    @Test
    fun `fromEngine uses fallback name when engine SubFleet commanderName is blank`() {
        val engine = CommandHierarchy(fleetCommander = 100L)
        engine.subCommanders[200L] = SubFleet(
            commanderId = 200L,
            commanderName = "",  // blank — fallback should fill it
            unitIds = listOf(10L),
            commanderRank = 8,
        )

        val dto = CommandHierarchyDto.fromEngine(engine) { id ->
            if (id == 200L) "Fallback Yang" else "Unknown"
        }

        assertEquals("Fallback Yang", dto.subFleets[0].commanderName)
    }

    @Test
    fun `fromEngine defaults preserve succession vacancy semantics`() {
        val engine = CommandHierarchy(
            fleetCommander = 100L,
            // vacancyStartTick defaults to -1 (no vacancy)
        )

        val dto = CommandHierarchyDto.fromEngine(engine)

        assertEquals(-1, dto.vacancyStartTick)
        assertFalse(dto.commJammed)
        assertEquals(0, dto.jammingTicksRemaining)
        assertNull(dto.activeCommander)
        assertNull(dto.designatedSuccessor)
        assertTrue(dto.subFleets.isEmpty())
        assertTrue(dto.successionQueue.isEmpty())
    }
}
