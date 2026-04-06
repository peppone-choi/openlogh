package com.openlogh.engine

import com.openlogh.entity.SessionState
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.AlliancePoliticsService
import com.openlogh.service.EmpirePoliticsService
import com.openlogh.service.FezzanEndingService
import com.openlogh.service.FezzanService
import com.openlogh.service.GameEventService
import com.openlogh.service.TacticalBattleService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Real-time tick engine: 1 tick = 1 real second = 24 game-seconds (24x speed).
 *
 * Responsibilities per tick:
 *   1. Increment tickCount and advance gameTimeSec
 *   2. Process completed commands (every tick)
 *   3. Regenerate command points (every 300 ticks = 5 real minutes)
 *   4. Trigger monthly pipeline on month boundary crossing (every 108,000 ticks = 30 real hours)
 *   5. Persist updated world state
 *   6. Process active tactical battles (every tick)
 */
@Service
class TickEngine(
    private val realtimeService: RealtimeService,
    private val sessionStateRepository: SessionStateRepository,
    private val gameEventService: GameEventService,
    private val empirePoliticsService: EmpirePoliticsService,
    private val alliancePoliticsService: AlliancePoliticsService,
    private val fezzanService: FezzanService,
    private val fezzanAiService: FezzanAiService,
    private val fezzanEndingService: FezzanEndingService,
    private val tacticalBattleService: TacticalBattleService,
) {
    private val logger = LoggerFactory.getLogger(TickEngine::class.java)

    /**
     * Process a single tick for the given world.
     * Called once per second by TickDaemon for realtime-mode sessions.
     */
    fun processTick(world: SessionState) {
        // 1. Advance game clock
        world.tickCount++
        world.gameTimeSec += GameTimeConstants.GAME_SECONDS_PER_TICK

        // 2. Process completed commands every tick
        realtimeService.processCompletedCommands(world)

        // 3. CP regeneration every 300 ticks (5 real minutes)
        if (world.tickCount % GameTimeConstants.CP_REGEN_INTERVAL_TICKS == 0L) {
            realtimeService.regenerateCommandPoints(world)
        }

        // 4. Month boundary crossing
        if (world.gameTimeSec >= GameTimeConstants.GAME_SECONDS_PER_MONTH) {
            world.gameTimeSec -= GameTimeConstants.GAME_SECONDS_PER_MONTH
            advanceMonth(world)
            runMonthlyPipeline(world)
        }

        // 5. Political processing
        processPolitics(world)

        // 6. Process active tactical battles
        tacticalBattleService.processSessionBattles(world.id.toLong())

        // 7. Persist state
        sessionStateRepository.save(world)

        // 8. Broadcast tick state to clients every TICK_BROADCAST_INTERVAL ticks
        if (world.tickCount % GameTimeConstants.TICK_BROADCAST_INTERVAL == 0L) {
            gameEventService.broadcastTickState(world)
        }
    }

    /**
     * Process faction politics: coups, elections, loans, Fezzan AI.
     * Called every tick but most subsystems run on intervals.
     */
    private fun processPolitics(world: SessionState) {
        val sessionId = world.id.toLong()
        try {
            // Empire: check active coups every 10 ticks
            if (world.tickCount % 10 == 0L) {
                empirePoliticsService.processCoupTick(sessionId)
            }

            // Alliance: check election deadlines every 10 ticks
            if (world.tickCount % 10 == 0L) {
                alliancePoliticsService.processElectionTick(sessionId)
            }

            // Fezzan: process loan interest/defaults every 100 ticks
            if (world.tickCount % 100 == 0L) {
                fezzanService.processLoanTick(sessionId)
            }

            // Fezzan AI: evaluate and act
            fezzanAiService.processFezzanTick(sessionId, world.tickCount)

            // Fezzan ending check every 100 ticks
            if (world.tickCount % 100 == 0L) {
                fezzanEndingService.checkAndTrigger(sessionId)
            }
        } catch (e: Exception) {
            logger.warn("Political processing error for session {}: {}", sessionId, e.message)
        }
    }

    /**
     * Advance the game month by 1, rolling over to the next year if necessary.
     */
    internal fun advanceMonth(world: SessionState) {
        val newMonth = world.currentMonth.toInt() + 1
        if (newMonth > 12) {
            world.currentMonth = 1
            world.currentYear = (world.currentYear.toInt() + 1).toShort()
        } else {
            world.currentMonth = newMonth.toShort()
        }
    }

    /**
     * Run the monthly pipeline (economy, diplomacy, maintenance, etc.).
     *
     * TODO: Wire to actual TurnPipeline steps when turn-based command execution
     *       is fully decoupled from monthly processing. Currently logs and broadcasts
     *       the month advance event.
     */
    private fun runMonthlyPipeline(world: SessionState) {
        logger.info(
            "Monthly pipeline triggered for world {} at year={} month={}",
            world.id, world.currentYear, world.currentMonth
        )
        gameEventService.broadcastTurnAdvance(
            world.id.toLong(),
            world.currentYear.toInt(),
            world.currentMonth.toInt(),
        )
    }
}
