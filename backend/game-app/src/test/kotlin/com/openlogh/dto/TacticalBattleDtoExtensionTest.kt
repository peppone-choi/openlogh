package com.openlogh.dto

import com.openlogh.model.EnergyAllocation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Phase 14 Plan 01 Task 1 (D-21, D-22, D-24): contract test for the extended
 * TacticalBattleDto / TacticalUnitDto / BattleTickBroadcast shape used by the
 * frontend (FE-01/03/04/05 gating + CRC + fog of war + NPC markers).
 *
 * This test is the authoritative field list — 14-06 (frontend types) mirrors
 * whatever compiles here.
 */
class TacticalBattleDtoExtensionTest {

    @Test
    fun `TacticalUnitDto defaults for new Phase 14 fields`() {
        val dto = TacticalUnitDto(
            fleetId = 1L,
            officerId = 100L,
            officerName = "Test",
            factionId = 1L,
            side = "ATTACKER",
            posX = 0.0,
            posY = 0.0,
            hp = 100,
            maxHp = 100,
            ships = 300,
            maxShips = 300,
            training = 50,
            morale = 50,
            energy = EnergyAllocation.toMap(EnergyAllocation.BALANCED),
            formation = "MIXED",
            commandRange = 0.0,
            isAlive = true,
            isRetreating = false,
            retreatProgress = 0.0,
            unitType = "FLEET",
        )

        // New Phase 14 defaults (D-22 / D-24 / D-37)
        assertEquals(0.0, dto.sensorRange, "sensorRange default")
        assertNull(dto.subFleetCommanderId, "subFleetCommanderId default")
        assertNull(dto.successionState, "successionState default")
        assertNull(dto.successionTicksRemaining, "successionTicksRemaining default")
        assertTrue(dto.isOnline, "isOnline default")
        assertFalse(dto.isNpc, "isNpc default")
        assertNull(dto.missionObjective, "missionObjective default")
        assertEquals(0.0, dto.maxCommandRange, "maxCommandRange default")
    }

    @Test
    fun `TacticalBattleDto hierarchy fields default to null`() {
        val dto = TacticalBattleDto(
            id = 1L,
            sessionId = 1L,
            starSystemId = 1L,
            attackerFactionId = 1L,
            defenderFactionId = 2L,
            phase = "PREPARING",
            startedAt = "2026-04-09T00:00:00",
            tickCount = 0,
            attackerFleetIds = emptyList(),
            defenderFleetIds = emptyList(),
            units = emptyList(),
        )

        assertNull(dto.attackerHierarchy)
        assertNull(dto.defenderHierarchy)
    }

    @Test
    fun `BattleTickBroadcast hierarchy fields default to null`() {
        val broadcast = BattleTickBroadcast(
            battleId = 1L,
            tickCount = 0,
            phase = "ACTIVE",
            units = emptyList(),
            events = emptyList(),
        )

        assertNull(broadcast.attackerHierarchy)
        assertNull(broadcast.defenderHierarchy)
    }

    @Test
    fun `CommandHierarchyDto constructible with minimal args`() {
        val dto = CommandHierarchyDto(
            fleetCommander = 100L,
            subFleets = emptyList(),
            successionQueue = emptyList(),
        )

        assertEquals(100L, dto.fleetCommander)
        assertNull(dto.designatedSuccessor)
        assertEquals(-1, dto.vacancyStartTick)
        assertFalse(dto.commJammed)
        assertEquals(0, dto.jammingTicksRemaining)
        assertNull(dto.activeCommander)
    }

    @Test
    fun `SubFleetDto exposes commander name and member fleet ids`() {
        val dto = SubFleetDto(
            commanderOfficerId = 200L,
            commanderName = "Yang Wen-li",
            memberFleetIds = listOf(10L, 11L),
            commanderRank = 8,
        )

        assertEquals("Yang Wen-li", dto.commanderName)
        assertEquals(listOf(10L, 11L), dto.memberFleetIds)
        assertEquals(8, dto.commanderRank)
    }
}
