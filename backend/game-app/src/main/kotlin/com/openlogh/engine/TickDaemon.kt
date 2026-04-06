package com.openlogh.engine

import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.GameEventService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

/**
 * Tick daemon: periodically iterates worlds and processes real-time ticks.
 * The legacy turn-based path has been removed — all worlds now use the TickEngine.
 */
@Component
class TickDaemon(
    private val tickEngine: TickEngine,
    @Value("\${game.commit-sha:local}") private val processCommitSha: String,
    private val sessionStateRepository: SessionStateRepository,
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

    private val logger = LoggerFactory.getLogger(TickDaemon::class.java)

    private fun transitionTo(newState: DaemonState, reason: String, reqId: String? = null) {
        val prev = state
        state = newState
        stateReason = reason
        if (reqId != null) requestId = reqId
        logger.debug("Daemon state: {} -> {} (reason={}, requestId={})", prev, newState, reason, requestId)
    }

    @Scheduled(fixedRateString = "\${app.turn.tick-ms:1000}")
    fun tick() {
        if (state != DaemonState.IDLE) return
        val reqId = java.util.UUID.randomUUID().toString().take(8)
        transitionTo(DaemonState.RUNNING, "tick_start", reqId)
        try {
            val worlds = sessionStateRepository
                .findByCommitSha(processCommitSha)
                .filter { shouldProcessWorld(it.meta["gatewayActive"]) }

            for (world in worlds) {
                if (isPreOpen(world)) continue
                if (isWorldLocked(world)) continue
                try {
                    tickEngine.processTick(world)
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

    private fun isPreOpen(world: com.openlogh.entity.SessionState): Boolean {
        val now = OffsetDateTime.now()
        val startTime = (world.config["startTime"] as? String)?.let {
            try { OffsetDateTime.parse(it) } catch (e: Exception) { logger.warn("Failed to parse startTime '{}': {}", it, e.message); null }
        }
        if (startTime != null && now.isBefore(startTime)) return true
        val opentime = (world.config["opentime"] as? String) ?: return false
        return try {
            now.isBefore(OffsetDateTime.parse(opentime))
        } catch (e: Exception) {
            logger.warn("Failed to parse opentime '{}': {}", opentime, e.message)
            false
        }
    }

    private fun isWorldLocked(world: com.openlogh.entity.SessionState): Boolean {
        val locked = world.config["locked"] ?: world.meta["locked"] ?: world.meta["isLocked"]
        return when (locked) {
            is Boolean -> locked
            is Number -> locked.toInt() != 0
            is String -> locked.equals("true", ignoreCase = true) || locked == "1"
            else -> false
        }
    }
}
