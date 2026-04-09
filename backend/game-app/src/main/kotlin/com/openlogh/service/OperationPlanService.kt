package com.openlogh.service

import com.openlogh.dto.OperationEventDto
import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.entity.OperationPlan
import com.openlogh.model.OperationStatus
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OperationPlanRepository
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * Phase 12 D-01/D-04/D-19: Transactional boundary for OperationPlan CRUD.
 *
 * CommandExecutor has NO @Transactional annotation (verified in
 * CommandExecutor.kt constructor site), so the 1-fleet-1-operation invariant
 * (D-04) must live in a dedicated @Transactional service. Every concurrent
 * OperationPlanCommand invocation funnels through [assignOperation] which
 * atomically:
 *   (a) loads every PENDING/ACTIVE operation for the faction,
 *   (b) removes the new operation's fleet IDs from every prior op's
 *       participantFleetIds,
 *   (c) saves the updated prior operations,
 *   (d) constructs and saves the new OperationPlan.
 *
 * All four steps share one JTA transaction — a failure in (c) or (d) rolls
 * back (a)+(b) automatically. No dual-membership orphans can result.
 */
@Service
class OperationPlanService(
    private val operationPlanRepository: OperationPlanRepository,
    private val fleetRepository: FleetRepository,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    private val logger = LoggerFactory.getLogger(OperationPlanService::class.java)

    /**
     * Assign a new OperationPlan to the given faction, releasing any fleets
     * previously attached to PENDING/ACTIVE operations of the same faction.
     *
     * @throws IllegalArgumentException if [participantFleetIds] is empty, a
     *   requested fleet does not exist, or any fleet belongs to a different
     *   faction.
     */
    @Transactional
    fun assignOperation(
        sessionId: Long,
        factionId: Long,
        name: String,
        objective: MissionObjective,
        targetStarSystemId: Long,
        participantFleetIds: List<Long>,
        scale: Int,
        issuedByOfficerId: Long,
    ): OperationPlan {
        require(participantFleetIds.isNotEmpty()) {
            "참가 부대를 1개 이상 지정해야 합니다"
        }

        val fleets = fleetRepository.findAllById(participantFleetIds)
        require(fleets.size == participantFleetIds.size) {
            "일부 부대를 찾을 수 없습니다"
        }
        fleets.firstOrNull { it.factionId != factionId }?.let {
            throw IllegalArgumentException("부대 ${it.id}가 같은 진영이 아닙니다")
        }

        // D-04: atomic 1-fleet-1-operation. Read all active/pending ops
        // for this faction, remove any prior membership of these fleets,
        // save updates, THEN persist the new operation — all in one tx.
        val priorOps = operationPlanRepository.findBySessionIdAndFactionIdAndStatusIn(
            sessionId,
            factionId,
            listOf(OperationStatus.PENDING, OperationStatus.ACTIVE),
        )
        val participantSet = participantFleetIds.toSet()
        val now = OffsetDateTime.now()
        val mutatedPriors = mutableListOf<OperationPlan>()
        for (prior in priorOps) {
            val before = prior.participantFleetIds.size
            prior.participantFleetIds.removeAll { it in participantSet }
            if (prior.participantFleetIds.size != before) {
                prior.updatedAt = now
                mutatedPriors += prior
            }
        }
        if (mutatedPriors.isNotEmpty()) {
            operationPlanRepository.saveAll(mutatedPriors)
        }

        val plan = OperationPlan(
            sessionId = sessionId,
            factionId = factionId,
            name = name,
            objective = objective,
            targetStarSystemId = targetStarSystemId,
            status = OperationStatus.PENDING,
            participantFleetIds = participantFleetIds.toMutableList(),
            scale = scale.toShort(),
            issuedByOfficerId = issuedByOfficerId,
            // Phase 12 limitation: no tick accessor available at this layer.
            // issuedAtTick is always 0L. OPS-03 semantics do not depend on
            // this value — see 12-02-PLAN.md must_haves.
            issuedAtTick = 0L,
            createdAt = now,
            updatedAt = now,
        )
        val saved = operationPlanRepository.save(plan)
        logger.info(
            "Operation {} assigned (session={}, faction={}, objective={})",
            saved.id, sessionId, factionId, objective,
        )

        // Phase 14 D-31: broadcast PLANNED event so galaxy map updates without polling.
        val event = OperationEventDto.fromPlan(saved, "OPERATION_PLANNED")
        messagingTemplate.convertAndSend("/topic/world/${saved.sessionId}/operations", event)

        return saved
    }

    /**
     * Cancel an existing OperationPlan. Idempotent-safe: repeated cancels on
     * an already-CANCELLED op are no-ops. COMPLETED ops cannot be cancelled.
     *
     * @throws IllegalStateException if the operation belongs to a different
     *   faction or is already COMPLETED.
     * @throws NoSuchElementException if the operation does not exist.
     */
    @Transactional
    fun cancelOperation(factionId: Long, operationId: Long): OperationPlan {
        val operation = operationPlanRepository.findById(operationId).orElseThrow {
            NoSuchElementException("작전을 찾을 수 없습니다: $operationId")
        }
        check(operation.factionId == factionId) {
            "다른 진영의 작전은 철회할 수 없습니다"
        }
        check(operation.status != OperationStatus.COMPLETED) {
            "이미 완료된 작전입니다"
        }
        if (operation.status == OperationStatus.CANCELLED) {
            return operation
        }
        operation.status = OperationStatus.CANCELLED
        operation.updatedAt = OffsetDateTime.now()
        val saved = operationPlanRepository.save(operation)
        logger.info("Operation {} cancelled (faction={})", saved.id, factionId)

        // Phase 14 D-31: broadcast CANCELLED event so galaxy map removes the overlay.
        val event = OperationEventDto.fromPlan(saved, "OPERATION_CANCELLED")
        messagingTemplate.convertAndSend("/topic/world/${saved.sessionId}/operations", event)

        return saved
    }
}
