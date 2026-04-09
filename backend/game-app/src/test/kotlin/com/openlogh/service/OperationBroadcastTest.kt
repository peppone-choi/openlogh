package com.openlogh.service

import com.openlogh.dto.OperationEventDto
import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.entity.Fleet
import com.openlogh.entity.OperationPlan
import com.openlogh.entity.StarSystem
import com.openlogh.model.OperationStatus
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OperationPlanRepository
import com.openlogh.repository.StarSystemRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.util.Optional

/**
 * Phase 14 Plan 14-04 D-31: Operations WebSocket sync channel.
 *
 * Contract test — verifies that every OperationPlan status transition fires a
 * broadcast on /topic/world/{sessionId}/operations with the matching
 * OperationEventDto payload type.
 *
 * Uses plain org.mockito.Mockito (mockito-kotlin is NOT on the classpath per
 * Phase 12 D-17 / build.gradle.kts:85 exclusion note).
 */
class OperationBroadcastTest {

    private val opRepo: OperationPlanRepository = mock(OperationPlanRepository::class.java)
    private val fleetRepo: FleetRepository = mock(FleetRepository::class.java)
    private val starSystemRepo: StarSystemRepository = mock(StarSystemRepository::class.java)
    private val tacticalBattleService: TacticalBattleService = mock(TacticalBattleService::class.java)
    private val messagingTemplate: SimpMessagingTemplate = mock(SimpMessagingTemplate::class.java)

    private fun planService() = OperationPlanService(opRepo, fleetRepo, messagingTemplate)
    private fun lifecycleService() = OperationLifecycleService(
        opRepo, fleetRepo, starSystemRepo, tacticalBattleService, messagingTemplate,
    )

    private fun fleet(id: Long, planetId: Long?, factionId: Long = 1L): Fleet =
        Fleet().also {
            it.id = id
            it.sessionId = 1L
            it.factionId = factionId
            it.leaderOfficerId = id
            it.name = "F$id"
            it.planetId = planetId
        }

    private fun savedPlan(
        id: Long = 10L,
        status: OperationStatus = OperationStatus.PENDING,
        objective: MissionObjective = MissionObjective.CONQUEST,
        participants: MutableList<Long> = mutableListOf(42L),
        stabilityCounter: Int = 0,
    ) = OperationPlan(
        id = id,
        sessionId = 1L,
        factionId = 1L,
        name = "op$id",
        objective = objective,
        targetStarSystemId = 5L,
        status = status,
        participantFleetIds = participants,
        scale = 1,
        issuedByOfficerId = 1L,
        issuedAtTick = 0L,
        stabilityTickCounter = stabilityCounter,
    )

    private fun captureBroadcast(): Pair<String, OperationEventDto> {
        val topicCaptor = ArgumentCaptor.forClass(String::class.java)
        val payloadCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
            topicCaptor.capture(), payloadCaptor.capture()
        )
        val payload = payloadCaptor.value as OperationEventDto
        return topicCaptor.value to payload
    }

    @Test
    fun `assignOperation broadcasts OPERATION_PLANNED`() {
        `when`(opRepo.findBySessionIdAndFactionIdAndStatusIn(
            1L, 1L,
            listOf(OperationStatus.PENDING, OperationStatus.ACTIVE),
        )).thenReturn(emptyList())
        `when`(fleetRepo.findAllById(listOf(42L))).thenReturn(listOf(fleet(42L, 99L)))
        `when`(opRepo.save(org.mockito.ArgumentMatchers.any(OperationPlan::class.java)))
            .thenAnswer { invocation ->
                val plan = invocation.getArgument<OperationPlan>(0)
                plan.id = 10L
                plan
            }

        planService().assignOperation(
            sessionId = 1L,
            factionId = 1L,
            name = "test-op",
            objective = MissionObjective.CONQUEST,
            targetStarSystemId = 5L,
            participantFleetIds = listOf(42L),
            scale = 1,
            issuedByOfficerId = 7L,
        )

        val (topic, event) = captureBroadcast()
        assertTrue(
            topic.matches(Regex("/topic/world/\\d+/operations")),
            "topic should match /topic/world/{sessionId}/operations, got: $topic",
        )
        assertEquals("/topic/world/1/operations", topic)
        assertEquals("OPERATION_PLANNED", event.type)
        assertEquals("PENDING", event.status)
        assertEquals(10L, event.operationId)
        assertEquals(1L, event.sessionId)
        assertEquals(1L, event.factionId)
        assertEquals("CONQUEST", event.objective)
        assertEquals(5L, event.targetStarSystemId)
        assertEquals(listOf(42L), event.participantFleetIds)
    }

    @Test
    fun `cancelOperation broadcasts OPERATION_CANCELLED`() {
        val existing = savedPlan(id = 10L, status = OperationStatus.PENDING)
        `when`(opRepo.findById(10L)).thenReturn(Optional.of(existing))
        `when`(opRepo.save(existing)).thenReturn(existing)

        planService().cancelOperation(factionId = 1L, operationId = 10L)

        val (topic, event) = captureBroadcast()
        assertEquals("/topic/world/1/operations", topic)
        assertEquals("OPERATION_CANCELLED", event.type)
        assertEquals("CANCELLED", event.status)
        assertEquals(10L, event.operationId)
    }

    @Test
    fun `activatePending broadcasts OPERATION_STARTED`() {
        val op = savedPlan(id = 11L, status = OperationStatus.PENDING)
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.PENDING)).thenReturn(listOf(op))
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.ACTIVE)).thenReturn(emptyList())
        `when`(fleetRepo.findAllById(listOf(42L))).thenReturn(listOf(fleet(42L, 5L)))
        `when`(opRepo.save(op)).thenReturn(op)

        lifecycleService().processTick(sessionId = 1L, tickCount = 100L)

        val (topic, event) = captureBroadcast()
        assertEquals("/topic/world/1/operations", topic)
        assertEquals("OPERATION_STARTED", event.type)
        assertEquals("ACTIVE", event.status)
        assertEquals(11L, event.operationId)
        assertEquals(OperationStatus.ACTIVE, op.status)
    }

    @Test
    fun `evaluateCompletion broadcasts OPERATION_COMPLETED on conquest`() {
        val op = savedPlan(
            id = 12L,
            status = OperationStatus.ACTIVE,
            objective = MissionObjective.CONQUEST,
            participants = mutableListOf(),
        )
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.PENDING)).thenReturn(emptyList())
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.ACTIVE)).thenReturn(listOf(op))
        `when`(starSystemRepo.findById(5L)).thenReturn(
            Optional.of(StarSystem().apply { factionId = 1L })
        )
        `when`(opRepo.save(op)).thenReturn(op)

        lifecycleService().processTick(sessionId = 1L, tickCount = 100L)

        val (topic, event) = captureBroadcast()
        assertEquals("/topic/world/1/operations", topic)
        assertEquals("OPERATION_COMPLETED", event.type)
        assertEquals("COMPLETED", event.status)
        assertEquals(12L, event.operationId)
        assertEquals(OperationStatus.COMPLETED, op.status)
    }
}
