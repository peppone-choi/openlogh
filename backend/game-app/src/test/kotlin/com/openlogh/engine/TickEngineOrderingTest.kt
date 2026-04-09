package com.openlogh.engine

import com.openlogh.engine.ai.ScenarioEventAIService
import com.openlogh.entity.SessionState
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.AlliancePoliticsService
import com.openlogh.service.EmpirePoliticsService
import com.openlogh.service.FezzanEndingService
import com.openlogh.service.FezzanService
import com.openlogh.service.GameEventService
import com.openlogh.service.OfflinePlayerAIService
import com.openlogh.service.OperationLifecycleService
import com.openlogh.service.ShipyardProductionService
import com.openlogh.service.TacticalBattleService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock

/**
 * Phase 12 Plan 12-04 Task 1 RED/GREEN: TickEngine must invoke
 * OperationLifecycleService.processTick BEFORE TacticalBattleService.processSessionBattles
 * (Step 5.5 ordering — D-15 / Blocker 2 from 12-VALIDATION).
 *
 * Uses plain org.mockito.Mockito (mockito-kotlin is NOT on the classpath).
 */
class TickEngineOrderingTest {

    @Test
    fun `operation lifecycle runs before tactical battle processing`() {
        val operationLifecycleService = mock(OperationLifecycleService::class.java)
        val tacticalBattleService = mock(TacticalBattleService::class.java)

        // VERIFIED constructor param list from TickEngine.kt:30-45 (14 pre-existing
        // params + operationLifecycleService added at the END for backward compat).
        val engine = TickEngine(
            realtimeService = mock(RealtimeService::class.java),
            sessionStateRepository = mock(SessionStateRepository::class.java),
            gameEventService = mock(GameEventService::class.java),
            empirePoliticsService = mock(EmpirePoliticsService::class.java),
            alliancePoliticsService = mock(AlliancePoliticsService::class.java),
            fezzanService = mock(FezzanService::class.java),
            fezzanAiService = mock(FezzanAiService::class.java),
            fezzanEndingService = mock(FezzanEndingService::class.java),
            tacticalBattleService = tacticalBattleService,
            gin7EconomyService = mock(Gin7EconomyService::class.java),
            shipyardProductionService = mock(ShipyardProductionService::class.java),
            fleetSortieCostService = mock(FleetSortieCostService::class.java),
            offlinePlayerAIService = mock(OfflinePlayerAIService::class.java),
            scenarioEventAIService = mock(ScenarioEventAIService::class.java),
            operationLifecycleService = operationLifecycleService,
        )

        // VERIFIED SessionState shape — id is Short, tickCount is Long.
        val world = SessionState().apply {
            id = 1
            tickCount = 10L
        }
        engine.processTick(world)

        // Step 5.5 (operationLifecycleService) MUST run BEFORE Step 6 (tacticalBattleService).
        val order = inOrder(operationLifecycleService, tacticalBattleService)
        order.verify(operationLifecycleService).processTick(1L, 11L) // tickCount++ happens first
        order.verify(tacticalBattleService).processSessionBattles(1L)
    }
}
