package com.openlogh.service

import com.openlogh.engine.tactical.BattleTriggerService
import com.openlogh.engine.tactical.TacticalBattleState
import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.entity.OperationPlan
import com.openlogh.model.OperationStatus
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.TacticalBattleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 12 Plan 12-03 Task 2 RED/GREEN: TacticalBattleService must expose a
 * direct-call sync channel (D-08) that propagates OperationPlan CRUD events
 * into every currently-active TacticalBattleState without a DB round-trip
 * from the tick loop.
 *
 * Uses plain org.mockito.Mockito for dependency mocks (mockito-kotlin is NOT
 * on the classpath — see build.gradle.kts:85 exclusion note) and reflection
 * to seed the private `activeBattles` field.
 *
 * VERIFIED TacticalBattleService constructor (TacticalBattleService.kt:27-35)
 * — EXACTLY 7 params: tacticalBattleRepository, fleetRepository,
 *   officerRepository, battleTriggerService, gameEventService,
 *   messagingTemplate, shipStatRegistry. `engine` and `planetConquestService`
 *   are CREATED inside the class, not injected.
 */
class TacticalBattleServiceSyncTest {

    private fun buildServiceWithSeededBattle(state: TacticalBattleState): TacticalBattleService {
        val service = TacticalBattleService(
            tacticalBattleRepository = mock(TacticalBattleRepository::class.java),
            fleetRepository = mock(FleetRepository::class.java),
            officerRepository = mock(OfficerRepository::class.java),
            battleTriggerService = mock(BattleTriggerService::class.java),
            gameEventService = mock(GameEventService::class.java),
            messagingTemplate = mock(SimpMessagingTemplate::class.java),
            shipStatRegistry = mock(ShipStatRegistry::class.java),
        )
        val field = TacticalBattleService::class.java.getDeclaredField("activeBattles")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(service) as ConcurrentHashMap<Long, TacticalBattleState>
        map[state.battleId] = state
        return service
    }

    private fun emptyState(battleId: Long): TacticalBattleState = TacticalBattleState(
        battleId = battleId,
        starSystemId = 100L,
        units = mutableListOf(),
    )

    @Test
    fun `sync_adds_entries_for_PENDING_or_ACTIVE_operation`() {
        val state = emptyState(battleId = 1L)
        val service = buildServiceWithSeededBattle(state)
        val plan = OperationPlan(
            sessionId = 1L,
            factionId = 1L,
            name = "op",
            objective = MissionObjective.CONQUEST,
            targetStarSystemId = 5L,
            status = OperationStatus.ACTIVE,
            participantFleetIds = mutableListOf(42L, 43L),
            scale = 1,
            issuedByOfficerId = 1L,
            issuedAtTick = 0L,
        )

        service.syncOperationToActiveBattles(plan)

        assertEquals(MissionObjective.CONQUEST, state.missionObjectiveByFleetId[42L])
        assertEquals(MissionObjective.CONQUEST, state.missionObjectiveByFleetId[43L])
        assertTrue(state.operationParticipantFleetIds.contains(42L))
        assertTrue(state.operationParticipantFleetIds.contains(43L))
    }

    @Test
    fun `sync_removes_entries_for_CANCELLED_operation`() {
        val state = emptyState(battleId = 2L)
        state.missionObjectiveByFleetId[42L] = MissionObjective.CONQUEST
        state.operationParticipantFleetIds.add(42L)
        val service = buildServiceWithSeededBattle(state)

        val plan = OperationPlan(
            sessionId = 1L,
            factionId = 1L,
            name = "op",
            objective = MissionObjective.CONQUEST,
            targetStarSystemId = 5L,
            status = OperationStatus.CANCELLED,
            participantFleetIds = mutableListOf(42L),
            scale = 1,
            issuedByOfficerId = 1L,
            issuedAtTick = 0L,
        )

        service.syncOperationToActiveBattles(plan)

        assertFalse(state.missionObjectiveByFleetId.containsKey(42L))
        assertFalse(state.operationParticipantFleetIds.contains(42L))
    }
}
