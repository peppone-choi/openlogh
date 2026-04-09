package com.openlogh.service

import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.entity.Fleet
import com.openlogh.entity.OperationPlan
import com.openlogh.entity.StarSystem
import com.openlogh.model.OperationStatus
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OperationPlanRepository
import com.openlogh.repository.StarSystemRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

/**
 * Phase 12 Plan 12-04 Task 1 RED/GREEN: OperationLifecycleService
 * covers D-15 activation (PENDING→ACTIVE on arrival), D-16/D-17/D-18
 * completion evaluation (CONQUEST / DEFENSE / SWEEP).
 *
 * Uses plain org.mockito.Mockito (mockito-kotlin is NOT on the classpath —
 * see build.gradle.kts:85 exclusion note).
 */
class OperationLifecycleServiceTest {

    private val opRepo: OperationPlanRepository = mock(OperationPlanRepository::class.java)
    private val fleetRepo: FleetRepository = mock(FleetRepository::class.java)
    private val starSystemRepo: StarSystemRepository = mock(StarSystemRepository::class.java)
    private val tacticalBattleService: TacticalBattleService = mock(TacticalBattleService::class.java)

    private fun svc() = OperationLifecycleService(opRepo, fleetRepo, starSystemRepo, tacticalBattleService)

    private fun pendingOp(id: Long, fleetIds: List<Long>, target: Long) = OperationPlan(
        id = id,
        sessionId = 1L,
        factionId = 1L,
        name = "op$id",
        objective = MissionObjective.CONQUEST,
        targetStarSystemId = target,
        status = OperationStatus.PENDING,
        participantFleetIds = fleetIds.toMutableList(),
        scale = 1,
        issuedByOfficerId = 1L,
        issuedAtTick = 0L,
    )

    // Fleet constructor from Fleet.kt:11-49 — all fields have defaults, id is non-nullable Long.
    private fun fleet(id: Long, planetId: Long, factionId: Long = 1L): Fleet =
        Fleet().also {
            it.id = id
            it.sessionId = 1L
            it.factionId = factionId
            it.leaderOfficerId = id
            it.name = "F$id"
            it.planetId = planetId
        }

    @Test
    fun `activates on first arrival`() {
        val op = pendingOp(id = 10L, fleetIds = listOf(42L), target = 5L)
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.PENDING)).thenReturn(listOf(op))
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.ACTIVE)).thenReturn(emptyList())
        `when`(fleetRepo.findAllById(listOf(42L))).thenReturn(listOf(fleet(id = 42L, planetId = 5L)))

        svc().processTick(sessionId = 1L, tickCount = 100L)

        assertEquals(OperationStatus.ACTIVE, op.status)
        verify(opRepo).save(op)
    }

    @Test
    fun `remains pending if none arrived`() {
        val op = pendingOp(id = 10L, fleetIds = listOf(42L), target = 5L)
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.PENDING)).thenReturn(listOf(op))
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.ACTIVE)).thenReturn(emptyList())
        `when`(fleetRepo.findAllById(listOf(42L))).thenReturn(listOf(fleet(id = 42L, planetId = 99L)))

        svc().processTick(sessionId = 1L, tickCount = 100L)

        assertEquals(OperationStatus.PENDING, op.status)
    }

    @Test
    fun `conquest completion when target faction matches`() {
        val op = OperationPlan(
            id = 1L,
            sessionId = 1L,
            factionId = 1L,
            name = "op",
            objective = MissionObjective.CONQUEST,
            targetStarSystemId = 5L,
            status = OperationStatus.ACTIVE,
            participantFleetIds = mutableListOf(),
            scale = 1,
            issuedByOfficerId = 1L,
            issuedAtTick = 0L,
        )
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.PENDING)).thenReturn(emptyList())
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.ACTIVE)).thenReturn(listOf(op))
        `when`(starSystemRepo.findById(5L)).thenReturn(
            Optional.of(StarSystem().apply { factionId = 1L })
        )

        svc().processTick(sessionId = 1L, tickCount = 100L)

        assertEquals(OperationStatus.COMPLETED, op.status)
    }

    @Test
    fun `defense stability window requires 60 consecutive clean ticks`() {
        val op = OperationPlan(
            id = 1L,
            sessionId = 1L,
            factionId = 1L,
            name = "op",
            objective = MissionObjective.DEFENSE,
            targetStarSystemId = 5L,
            status = OperationStatus.ACTIVE,
            participantFleetIds = mutableListOf(),
            scale = 1,
            issuedByOfficerId = 1L,
            issuedAtTick = 0L,
            stabilityTickCounter = 59,
        )
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.PENDING)).thenReturn(emptyList())
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.ACTIVE)).thenReturn(listOf(op))
        `when`(fleetRepo.findByPlanetId(5L)).thenReturn(emptyList()) // no enemies

        svc().processTick(sessionId = 1L, tickCount = 100L)

        // After one more clean tick (59 → 60), threshold reached
        assertEquals(OperationStatus.COMPLETED, op.status)
    }

    @Test
    fun `defense counter resets on enemy presence`() {
        val op = OperationPlan(
            id = 1L,
            sessionId = 1L,
            factionId = 1L,
            name = "op",
            objective = MissionObjective.DEFENSE,
            targetStarSystemId = 5L,
            status = OperationStatus.ACTIVE,
            participantFleetIds = mutableListOf(),
            scale = 1,
            issuedByOfficerId = 1L,
            issuedAtTick = 0L,
            stabilityTickCounter = 30,
        )
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.PENDING)).thenReturn(emptyList())
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.ACTIVE)).thenReturn(listOf(op))
        `when`(fleetRepo.findByPlanetId(5L)).thenReturn(
            listOf(fleet(id = 77L, planetId = 5L, factionId = 2L)) // enemy
        )

        svc().processTick(sessionId = 1L, tickCount = 100L)

        assertEquals(0, op.stabilityTickCounter)
        assertEquals(OperationStatus.ACTIVE, op.status)
    }

    @Test
    fun `sweep completion when enemies at target drop to zero`() {
        val op = OperationPlan(
            id = 1L,
            sessionId = 1L,
            factionId = 1L,
            name = "op",
            objective = MissionObjective.SWEEP,
            targetStarSystemId = 5L,
            status = OperationStatus.ACTIVE,
            participantFleetIds = mutableListOf(),
            scale = 1,
            issuedByOfficerId = 1L,
            issuedAtTick = 0L,
        )
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.PENDING)).thenReturn(emptyList())
        `when`(opRepo.findBySessionIdAndStatus(1L, OperationStatus.ACTIVE)).thenReturn(listOf(op))
        `when`(fleetRepo.findByPlanetId(5L)).thenReturn(emptyList()) // no fleets at all

        svc().processTick(sessionId = 1L, tickCount = 100L)

        assertEquals(OperationStatus.COMPLETED, op.status)
    }
}
