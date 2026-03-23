package com.openlogh.engine

import com.openlogh.engine.turn.cqrs.TurnCoordinator
import com.openlogh.entity.SessionState
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.GameEventService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class TurnDaemon(
    private val turnService: TurnService,
    private val turnCoordinator: TurnCoordinator,
    private val realtimeService: RealtimeService,
    @Value("\${game.commit-sha:local}") private val commitSha: String,
    @Value("\${game.cqrs-enabled:false}") private val cqrsEnabled: Boolean,
    private val sessionStateRepository: SessionStateRepository,
    private val gameEventService: GameEventService,
) {
    enum class DaemonState { RUNNING, PAUSED }

    companion object {
        private val log = LoggerFactory.getLogger(TurnDaemon::class.java)
    }

    private var state = DaemonState.RUNNING

    fun pause() {
        state = DaemonState.PAUSED
    }

    fun resume() {
        state = DaemonState.RUNNING
    }

    fun getStatus(): DaemonState = state

    @Scheduled(fixedRateString = "\${game.tick-rate:1000}")
    fun tick() {
        if (state == DaemonState.PAUSED) return

        val worlds = sessionStateRepository.findByCommitSha(commitSha)
        for (world in worlds) {
            if (shouldSkip(world)) continue

            val prevYear = world.currentYear
            val prevMonth = world.currentMonth

            try {
                when {
                    world.realtimeMode -> {
                        realtimeService.processCompletedCommands(world)
                        realtimeService.regenerateCommandPoints(world)
                    }
                    cqrsEnabled -> turnCoordinator.processWorld(world)
                    else -> turnService.processWorld(world)
                }
            } catch (e: Exception) {
                log.error("Error processing world {}", world.id, e)
            }

            if (world.currentYear != prevYear || world.currentMonth != prevMonth) {
                gameEventService.broadcastTurnAdvance(
                    world.id.toLong(),
                    world.currentYear.toInt(),
                    world.currentMonth.toInt(),
                )
            }
        }
    }

    private fun shouldSkip(world: SessionState): Boolean {
        if (world.config["locked"] == true) return true

        val opentime = world.config["opentime"] as? String
        if (opentime != null) {
            try {
                val openAt = OffsetDateTime.parse(opentime)
                if (OffsetDateTime.now().isBefore(openAt)) return true
            } catch (e: Exception) {
                log.warn("Invalid opentime format for world {}: {}", world.id, opentime)
            }
        }

        val gatewayActive = world.meta["gatewayActive"]
        if (gatewayActive == false) return true

        return false
    }
}
