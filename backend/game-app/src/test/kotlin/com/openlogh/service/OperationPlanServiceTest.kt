package com.openlogh.service

import com.openlogh.OpenloghApplication
import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.entity.Fleet
import com.openlogh.model.OperationStatus
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OperationPlanRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * Phase 12 Plan 12-02 Task 2 RED/GREEN: transactional boundary for
 * OperationPlan CRUD (D-01/D-04/D-19). CommandExecutor is NOT @Transactional,
 * so the 1-fleet-1-operation invariant must live inside this service under a
 * single @Transactional scope.
 *
 * Uses explicit classes = [OpenloghApplication::class] to avoid duplicate
 * @SpringBootConfiguration discovery with OpenloghApplicationTests$TestConfig
 * (pattern established in Plan 12-01).
 */
@SpringBootTest(
    classes = [OpenloghApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("test")
@Transactional
class OperationPlanServiceTest {

    @Autowired
    private lateinit var operationPlanService: OperationPlanService

    @Autowired
    private lateinit var operationPlanRepository: OperationPlanRepository

    @Autowired
    private lateinit var fleetRepository: FleetRepository

    private fun newFleet(factionId: Long, sessionId: Long): Fleet =
        fleetRepository.saveAndFlush(
            Fleet(planetId = 1L).also {
                it.sessionId = sessionId
                it.factionId = factionId
                it.leaderOfficerId = 1L
                it.name = "F-${System.nanoTime()}"
            }
        )

    @Test
    fun `assignOperation persists operation row`() {
        val fleet = newFleet(factionId = 1L, sessionId = 1L)
        val result = operationPlanService.assignOperation(
            sessionId = 1L,
            factionId = 1L,
            name = "이제르론 공략",
            objective = MissionObjective.CONQUEST,
            targetStarSystemId = 42L,
            participantFleetIds = listOf(fleet.id),
            scale = 3,
            issuedByOfficerId = 99L,
        )
        assertEquals(MissionObjective.CONQUEST, result.objective)
        assertEquals(OperationStatus.PENDING, result.status)
        assertTrue(result.participantFleetIds.contains(fleet.id))

        val persisted = operationPlanRepository.findBySessionIdAndStatus(1L, OperationStatus.PENDING)
        assertEquals(1, persisted.size)
        assertEquals(42L, persisted[0].targetStarSystemId)
    }

    @Test
    fun `assignOperation enforces 1-fleet-1-operation atomically`() {
        val fleet = newFleet(factionId = 1L, sessionId = 1L)

        // Op A — assigned first
        val opA = operationPlanService.assignOperation(
            sessionId = 1L, factionId = 1L, name = "Op A",
            objective = MissionObjective.DEFENSE, targetStarSystemId = 5L,
            participantFleetIds = listOf(fleet.id), scale = 1, issuedByOfficerId = 1L,
        )

        // Op B — same fleet, different objective. D-04: Op A must release it.
        val opB = operationPlanService.assignOperation(
            sessionId = 1L, factionId = 1L, name = "Op B",
            objective = MissionObjective.CONQUEST, targetStarSystemId = 6L,
            participantFleetIds = listOf(fleet.id), scale = 1, issuedByOfficerId = 1L,
        )

        val refreshedA = operationPlanRepository.findById(opA.id!!).orElseThrow()
        val refreshedB = operationPlanRepository.findById(opB.id!!).orElseThrow()
        assertFalse(
            refreshedA.participantFleetIds.contains(fleet.id),
            "Fleet must be removed from Op A when assigned to Op B (D-04)",
        )
        assertTrue(refreshedB.participantFleetIds.contains(fleet.id))
    }

    @Test
    fun `assignOperation rejects empty participant list`() {
        assertThrows(IllegalArgumentException::class.java) {
            operationPlanService.assignOperation(
                sessionId = 1L, factionId = 1L, name = "Empty",
                objective = MissionObjective.SWEEP, targetStarSystemId = 5L,
                participantFleetIds = emptyList(), scale = 1, issuedByOfficerId = 1L,
            )
        }
    }

    @Test
    fun `assignOperation rejects foreign faction fleet`() {
        val foreignFleet = newFleet(factionId = 99L, sessionId = 1L)
        assertThrows(IllegalArgumentException::class.java) {
            operationPlanService.assignOperation(
                sessionId = 1L, factionId = 1L, name = "Foreign",
                objective = MissionObjective.CONQUEST, targetStarSystemId = 5L,
                participantFleetIds = listOf(foreignFleet.id), scale = 1, issuedByOfficerId = 1L,
            )
        }
    }

    @Test
    fun `cancelOperation flips status to CANCELLED`() {
        val fleet = newFleet(factionId = 1L, sessionId = 1L)
        val op = operationPlanService.assignOperation(
            sessionId = 1L, factionId = 1L, name = "To Cancel",
            objective = MissionObjective.DEFENSE, targetStarSystemId = 5L,
            participantFleetIds = listOf(fleet.id), scale = 1, issuedByOfficerId = 1L,
        )

        operationPlanService.cancelOperation(factionId = 1L, operationId = op.id!!)

        val refreshed = operationPlanRepository.findById(op.id!!).orElseThrow()
        assertEquals(OperationStatus.CANCELLED, refreshed.status)
    }

    @Test
    fun `cancelOperation rejects different faction`() {
        val fleet = newFleet(factionId = 1L, sessionId = 1L)
        val op = operationPlanService.assignOperation(
            sessionId = 1L, factionId = 1L, name = "Mine",
            objective = MissionObjective.DEFENSE, targetStarSystemId = 5L,
            participantFleetIds = listOf(fleet.id), scale = 1, issuedByOfficerId = 1L,
        )
        assertThrows(IllegalStateException::class.java) {
            operationPlanService.cancelOperation(factionId = 99L, operationId = op.id!!)
        }
    }
}
