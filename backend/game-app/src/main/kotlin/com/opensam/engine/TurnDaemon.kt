package com.opensam.engine

import com.opensam.repository.WorldStateRepository
import com.opensam.engine.turn.cqrs.TurnCoordinator
import com.opensam.service.GameEventService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 턴 데몬: 주기적으로 월드를 순회하며 턴을 처리한다.
 * - 일반 모드: TurnService (전체 파이프라인 — 커맨드 실행, 경제, 외교, 이벤트, AI, 유지보수)
 * - 실시간 모드: RealtimeService (커맨드 포인트 기반)
 */
@Component
class TurnDaemon(
    private val turnService: TurnService,
    private val turnCoordinator: TurnCoordinator,
    private val realtimeService: RealtimeService,
    @Value("\${game.commit-sha:local}") private val processCommitSha: String,
    @Value("\${opensam.cqrs.enabled:false}") private val cqrsEnabled: Boolean,
    private val worldStateRepository: WorldStateRepository,
    private val gameEventService: GameEventService,
) {
    enum class DaemonState { IDLE, RUNNING, FLUSHING, PAUSED, STOPPING }

    @Volatile
    private var state = DaemonState.IDLE

    /** Reason for last state transition (e.g. "manual_pause", "tick_start", "error"). */
    @Volatile
    private var stateReason: String? = null

    /** Unique request ID for the current/last operation. */
    @Volatile
    private var requestId: String? = null

    private val logger = LoggerFactory.getLogger(TurnDaemon::class.java)

    private fun transitionTo(newState: DaemonState, reason: String, reqId: String? = null) {
        val prev = state
        state = newState
        stateReason = reason
        if (reqId != null) requestId = reqId
        logger.debug("Daemon state: {} -> {} (reason={}, requestId={})", prev, newState, reason, requestId)
    }

    @Scheduled(fixedDelayString = "\${app.turn.interval-ms:5000}")
    fun tick() {
        if (state != DaemonState.IDLE) return
        val reqId = java.util.UUID.randomUUID().toString().take(8)
        transitionTo(DaemonState.RUNNING, "tick_start", reqId)
        try {
            val worlds = worldStateRepository
                .findByCommitSha(processCommitSha)
                .filter { shouldProcessWorld(it.meta["gatewayActive"]) }

            for (world in worlds) {
                if (isWorldLocked(world)) continue
                try {
                    if (world.realtimeMode) {
                        realtimeService.processCompletedCommands(world)
                        realtimeService.regenerateCommandPoints(world)
                    } else {
                        val prevYear = world.currentYear.toInt()
                        val prevMonth = world.currentMonth.toInt()
                        if (cqrsEnabled) {
                            turnCoordinator.processWorld(world)
                        } else {
                            turnService.processWorld(world)
                            // Legacy path doesn't broadcast — notify frontend of turn advance
                            if (world.currentYear.toInt() != prevYear || world.currentMonth.toInt() != prevMonth) {
                                gameEventService.broadcastTurnAdvance(
                                    world.id.toLong(),
                                    world.currentYear.toInt(),
                                    world.currentMonth.toInt(),
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error processing world ${world.id}: ${e.message}", e)
                }
            }
        } finally {
            transitionTo(DaemonState.IDLE, "tick_complete")
        }
    }

    fun pause(reason: String = "manual_pause") { transitionTo(DaemonState.PAUSED, reason) }
    fun resume(reason: String = "manual_resume") { transitionTo(DaemonState.IDLE, reason) }
    fun getStatus() = state
    fun getStatusDetail() = mapOf("state" to state, "reason" to stateReason, "requestId" to requestId)
    fun manualRun() {
        if (state == DaemonState.IDLE) tick()
    }

    private fun shouldProcessWorld(value: Any?): Boolean {
        return when (value) {
            null -> true
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> false
        }
    }

    private fun isWorldLocked(world: com.opensam.entity.WorldState): Boolean {
        val locked = world.config["locked"] ?: world.meta["locked"] ?: world.meta["isLocked"]
        return when (locked) {
            is Boolean -> locked
            is Number -> locked.toInt() != 0
            is String -> locked.equals("true", ignoreCase = true) || locked == "1"
            else -> false
        }
    }
}
