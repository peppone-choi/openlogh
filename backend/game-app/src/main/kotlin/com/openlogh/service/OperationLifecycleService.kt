package com.openlogh.service

import com.openlogh.dto.OperationEventDto
import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.entity.OperationPlan
import com.openlogh.model.OperationStatus
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OperationPlanRepository
import com.openlogh.repository.StarSystemRepository
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * Phase 12 D-15/D-16/D-17/D-18: OperationPlan lifecycle.
 *
 * Called from TickEngine.processTick step 5.5, BEFORE
 * TacticalBattleService.processSessionBattles so the activation state is
 * visible to BattleTriggerService.buildInitialState's OperationPlan lookup
 * on the same tick.
 *
 * Responsibilities:
 *  - activatePending(): PENDING → ACTIVE when any participant fleet has
 *    reached targetStarSystemId (fleet.planetId == operation.targetStarSystemId).
 *    One arriving fleet is sufficient (D-17).
 *  - evaluateCompletion(): ACTIVE → COMPLETED per objective type:
 *      CONQUEST: StarSystem.factionId == operation.factionId
 *      DEFENSE:  stability_tick_counter reaches DEFENSE_STABILITY_TICKS
 *                (counter resets when enemies present at target)
 *      SWEEP:    enemy fleet count at target drops to 0
 *
 * Sync channel: successful PENDING→ACTIVE and ACTIVE→COMPLETED transitions
 * are propagated to all in-flight TacticalBattleStates via
 * TacticalBattleService.syncOperationToActiveBattles (D-08). Sync failures
 * are logged but do not roll back the status change — the next tick will
 * re-attempt propagation via the read-through cache in Step 0.6.
 */
@Service
class OperationLifecycleService(
    private val operationPlanRepository: OperationPlanRepository,
    private val fleetRepository: FleetRepository,
    private val starSystemRepository: StarSystemRepository,
    private val tacticalBattleService: TacticalBattleService,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    private val logger = LoggerFactory.getLogger(OperationLifecycleService::class.java)

    companion object {
        /** D-18: DEFENSE stability window — 60 ticks of no enemy presence → COMPLETED. */
        const val DEFENSE_STABILITY_TICKS: Int = 60
    }

    /**
     * Single tick entry point. Runs activation first, then completion evaluation.
     * Both phases are wrapped in one @Transactional scope so repository mutations
     * roll back together on failure.
     */
    @Transactional
    fun processTick(sessionId: Long, tickCount: Long) {
        activatePending(sessionId, tickCount)
        evaluateCompletion(sessionId, tickCount)
    }

    /**
     * D-17: flip PENDING operations whose any participant fleet has reached
     * targetStarSystemId. One arriving fleet is sufficient (activation keys on
     * fleetId presence, not on faction — ghost fleets still activate).
     */
    internal fun activatePending(sessionId: Long, tickCount: Long) {
        val pending = operationPlanRepository
            .findBySessionIdAndStatus(sessionId, OperationStatus.PENDING)
        if (pending.isEmpty()) return

        for (operation in pending) {
            if (operation.participantFleetIds.isEmpty()) continue
            val fleets = fleetRepository.findAllById(operation.participantFleetIds)
            val reached = fleets.any { it.planetId == operation.targetStarSystemId }
            if (reached) {
                operation.status = OperationStatus.ACTIVE
                operation.updatedAt = OffsetDateTime.now()
                operationPlanRepository.save(operation)
                runCatching { tacticalBattleService.syncOperationToActiveBattles(operation) }
                    .onFailure { logger.warn("sync failure for op ${operation.id}: ${it.message}") }
                logger.info(
                    "Operation {} ACTIVE (session={}, tick={})",
                    operation.id, sessionId, tickCount,
                )

                // Phase 14 D-31: broadcast STARTED event so galaxy map promotes the overlay.
                val event = OperationEventDto.fromPlan(operation, "OPERATION_STARTED")
                messagingTemplate.convertAndSend("/topic/world/${operation.sessionId}/operations", event)
            }
        }
    }

    /**
     * D-16/D-18: evaluate completion for each ACTIVE operation per objective type.
     * DEFENSE operations additionally persist stability counter updates even when
     * the threshold has not yet been reached (counter mutation is a side-effect
     * that must survive across ticks).
     */
    internal fun evaluateCompletion(sessionId: Long, tickCount: Long) {
        val active = operationPlanRepository
            .findBySessionIdAndStatus(sessionId, OperationStatus.ACTIVE)
        if (active.isEmpty()) return

        for (operation in active) {
            val completed = when (operation.objective) {
                MissionObjective.CONQUEST -> evaluateConquest(operation)
                MissionObjective.DEFENSE -> evaluateDefense(operation)
                MissionObjective.SWEEP -> evaluateSweep(operation)
            }
            if (completed) {
                operation.status = OperationStatus.COMPLETED
                operation.updatedAt = OffsetDateTime.now()
                operationPlanRepository.save(operation)
                runCatching { tacticalBattleService.syncOperationToActiveBattles(operation) }
                    .onFailure { logger.warn("sync failure for op ${operation.id}: ${it.message}") }
                logger.info(
                    "Operation {} COMPLETED (session={}, tick={}, objective={})",
                    operation.id, sessionId, tickCount, operation.objective,
                )

                // Phase 14 D-31: broadcast COMPLETED event so galaxy map clears the overlay.
                val event = OperationEventDto.fromPlan(operation, "OPERATION_COMPLETED")
                messagingTemplate.convertAndSend("/topic/world/${operation.sessionId}/operations", event)
            } else if (operation.objective == MissionObjective.DEFENSE) {
                // DEFENSE stability counter mutation must persist across ticks.
                operation.updatedAt = OffsetDateTime.now()
                operationPlanRepository.save(operation)
            }
        }
    }

    private fun evaluateConquest(operation: OperationPlan): Boolean {
        val star = starSystemRepository.findById(operation.targetStarSystemId).orElse(null)
            ?: return false
        return star.factionId == operation.factionId
    }

    private fun evaluateDefense(operation: OperationPlan): Boolean {
        val fleets = fleetRepository.findByPlanetId(operation.targetStarSystemId)
        val enemyCount = fleets.count { it.factionId != operation.factionId }
        return if (enemyCount == 0) {
            operation.stabilityTickCounter += 1
            operation.stabilityTickCounter >= DEFENSE_STABILITY_TICKS
        } else {
            operation.stabilityTickCounter = 0
            false
        }
    }

    private fun evaluateSweep(operation: OperationPlan): Boolean {
        val fleets = fleetRepository.findByPlanetId(operation.targetStarSystemId)
        val enemyCount = fleets.count { it.factionId != operation.factionId }
        return enemyCount == 0
    }
}
