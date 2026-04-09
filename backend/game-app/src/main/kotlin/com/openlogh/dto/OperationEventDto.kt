package com.openlogh.dto

import com.openlogh.entity.OperationPlan

/**
 * Phase 14 Plan 14-04 D-31: WebSocket broadcast payload for operation
 * status transitions.
 *
 * Topic: /topic/world/{sessionId}/operations
 * Consumed by frontend galaxyStore (see Phase 14 plan 14-16) — enables the
 * operations overlay to render without polling /api/operations every second.
 *
 * Event types:
 *  - OPERATION_PLANNED   : PENDING (just created by assignOperation)
 *  - OPERATION_STARTED   : PENDING → ACTIVE
 *  - OPERATION_COMPLETED : ACTIVE → COMPLETED
 *  - OPERATION_CANCELLED : * → CANCELLED
 */
data class OperationEventDto(
    val type: String,
    val operationId: Long,
    val sessionId: Long,
    val factionId: Long,
    val objective: String,
    val targetStarSystemId: Long,
    val participantFleetIds: List<Long>,
    val status: String,
    val timestamp: Long = System.currentTimeMillis(),
) {
    companion object {
        fun fromPlan(plan: OperationPlan, type: String): OperationEventDto =
            OperationEventDto(
                type = type,
                operationId = plan.id
                    ?: throw IllegalStateException("OperationPlan must be persisted (id == null) before broadcasting"),
                sessionId = plan.sessionId,
                factionId = plan.factionId,
                objective = plan.objective.name,
                targetStarSystemId = plan.targetStarSystemId,
                participantFleetIds = plan.participantFleetIds.toList(),
                status = plan.status.name,
            )
    }
}
