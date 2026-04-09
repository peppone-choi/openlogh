package com.openlogh.repository

import com.openlogh.OpenloghApplication
import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.entity.OperationPlan
import com.openlogh.model.OperationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(
    classes = [OpenloghApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("test")
@Transactional
class OperationPlanRepositoryTest {

    @Autowired
    private lateinit var operationPlanRepository: OperationPlanRepository

    @Test
    fun `jsonb round trip preserves long fleet ids including values above Int MAX`() {
        val plan = OperationPlan(
            sessionId = 1L,
            factionId = 2L,
            name = "테스트 작전",
            objective = MissionObjective.CONQUEST,
            targetStarSystemId = 42L,
            status = OperationStatus.PENDING,
            participantFleetIds = mutableListOf(1L, 10_000_000_000L),
            scale = 3,
            issuedByOfficerId = 99L,
            issuedAtTick = 1000L,
        )
        val saved = operationPlanRepository.saveAndFlush(plan)
        val found = operationPlanRepository.findById(saved.id!!).orElseThrow()
        assertEquals(2, found.participantFleetIds.size)
        assertTrue(found.participantFleetIds.contains(1L))
        assertTrue(found.participantFleetIds.contains(10_000_000_000L))
    }

    @Test
    fun `findBySessionIdAndStatus returns matching operations`() {
        val plan1 = OperationPlan(
            sessionId = 100L, factionId = 1L, name = "op1",
            objective = MissionObjective.DEFENSE, targetStarSystemId = 5L,
            status = OperationStatus.PENDING,
            participantFleetIds = mutableListOf(),
            scale = 1, issuedByOfficerId = 1L, issuedAtTick = 0L,
        )
        val plan2 = OperationPlan(
            sessionId = 100L, factionId = 1L, name = "op2",
            objective = MissionObjective.SWEEP, targetStarSystemId = 6L,
            status = OperationStatus.ACTIVE,
            participantFleetIds = mutableListOf(),
            scale = 1, issuedByOfficerId = 1L, issuedAtTick = 0L,
        )
        operationPlanRepository.saveAll(listOf(plan1, plan2))
        operationPlanRepository.flush()

        val pending = operationPlanRepository.findBySessionIdAndStatus(100L, OperationStatus.PENDING)
        assertEquals(1, pending.size)
        assertEquals("op1", pending[0].name)
    }
}
