package com.openlogh.engine

import com.openlogh.entity.SessionState
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.AlliancePoliticsService
import com.openlogh.service.EmpirePoliticsService
import com.openlogh.service.FezzanEndingService
import com.openlogh.service.FezzanService
import com.openlogh.service.GameEventService
import com.openlogh.engine.ai.ScenarioEventAIService
import com.openlogh.service.OfflinePlayerAIService
import com.openlogh.service.OperationLifecycleService
import com.openlogh.service.ShipyardProductionService
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
 *   7. Shipyard auto-production (every 3600 ticks = 1 game day)
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
    private val gin7EconomyService: Gin7EconomyService,
    private val shipyardProductionService: ShipyardProductionService,
    private val fleetSortieCostService: FleetSortieCostService,
    private val offlinePlayerAIService: OfflinePlayerAIService,
    private val scenarioEventAIService: ScenarioEventAIService,
    private val operationLifecycleService: OperationLifecycleService,
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

        // 5.5 Phase 12 (D-15): Process operation lifecycle (PENDING→ACTIVE + ACTIVE→COMPLETED).
        //     MUST run BEFORE battle trigger so activation state is visible to
        //     BattleTriggerService.buildInitialState's OperationPlan lookup on the
        //     same tick (eliminates the activation-vs-battle-trigger tick race).
        try {
            operationLifecycleService.processTick(world.id.toLong(), world.tickCount)
        } catch (e: Exception) {
            logger.warn("Operation lifecycle error for world {}: {}", world.id, e.message)
        }

        // 6. Process active tactical battles
        tacticalBattleService.processSessionBattles(world.id.toLong())

        // 7. 조병창 자동생산: 매 3600틱(1 게임일)마다
        if (world.tickCount % GameTimeConstants.SHIPYARD_INTERVAL_TICKS == 0L) {
            try {
                shipyardProductionService.runProduction(world.id.toLong())
            } catch (e: Exception) {
                logger.warn("Shipyard production error for world {}: {}", world.id, e.message)
            }
        }

        // 8. 함대 출격비용: 매 SORTIE_COST_INTERVAL_TICKS(1 게임일)마다 진영 자금 차감
        if (world.tickCount % FleetSortieCostService.SORTIE_COST_INTERVAL_TICKS == 0L) {
            try {
                fleetSortieCostService.processSortieCost(world.id.toLong())
            } catch (e: Exception) {
                logger.warn("Fleet sortie cost error for world {}: {}", world.id, e.message)
            }
        }

        // 9. Persist state
        sessionStateRepository.save(world)

        // 9. Broadcast tick state to clients every TICK_BROADCAST_INTERVAL ticks
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

            // Offline player AI: every 100 ticks (same interval as fezzanService)
            if (world.tickCount % 100 == 0L) {
                offlinePlayerAIService.processOfflinePlayers(world)
            }

            // Scenario event AI: civil war detection every 100 ticks
            if (world.tickCount % 100 == 0L) {
                scenarioEventAIService.processTick(world)
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
     * Run the monthly pipeline: gin7 경제 파이프라인 (세율/납입 + 행성자원 성장) + broadcast.
     *
     * Phase 23-10: activates the legacy-correct per-resource event schedule
     * directly on the TickEngine path (TickEngine drives the real-time/WebSocket
     * realtime-mode sessions; [InMemoryTurnProcessor] drives the turn-based mode
     * and already routes through EconomyService which now delegates to Gin7).
     *
     * Execution order within a month boundary:
     *   1. Gin7 monthly base (tax on month 1/4/7/10, approval, planet growth)
     *   2. Per-resource income event (month 1 → gold/funds, month 7 → rice/supplies)
     *      — rice branch is a no-op until a future phase adds rice production
     *   3. Per-resource semi-annual decay (month 1 → gold, month 7 → rice)
     *   4. War income (every month — casualty salvage on planet.dead > 0)
     *   5. Supply-state recompute (every month — BFS from capital, isolation decay)
     *   6. Disaster/boom roll (every month, probabilistic)
     *   7. Trade-rate randomization (every month, level-gated probability)
     *   8. Yearly statistics refresh (month 1 only — military_power recompute)
     *   9. Annual faction rank recalc (month 1 only — count(planet.level>=4))
     *
     * Every step is wrapped in try/catch so one subsystem failure does not
     * block the others. Logging identifies the failing step for triage.
     */
    private fun runMonthlyPipeline(world: SessionState) {
        logger.info(
            "Monthly pipeline triggered for world {} at year={} month={}",
            world.id, world.currentYear, world.currentMonth
        )

        val month = world.currentMonth.toInt()

        // 1. Gin7 monthly base
        try {
            gin7EconomyService.processMonthly(world)
        } catch (e: Exception) {
            logger.warn("Gin7.processMonthly error for world {}: {}", world.id, e.message)
        }

        // 2. Per-resource income event — legacy month 1/7 schedule
        if (month == 1) {
            try {
                gin7EconomyService.processIncome(world, "gold")
            } catch (e: Exception) {
                logger.warn("Gin7.processIncome(gold) error for world {}: {}", world.id, e.message)
            }
        } else if (month == 7) {
            try {
                gin7EconomyService.processIncome(world, "rice")
            } catch (e: Exception) {
                logger.warn("Gin7.processIncome(rice) error for world {}: {}", world.id, e.message)
            }
        }

        // 3. Per-resource semi-annual decay — legacy month 1/7 schedule
        if (month == 1) {
            try {
                gin7EconomyService.processSemiAnnual(world, "gold")
            } catch (e: Exception) {
                logger.warn("Gin7.processSemiAnnual(gold) error for world {}: {}", world.id, e.message)
            }
        } else if (month == 7) {
            try {
                gin7EconomyService.processSemiAnnual(world, "rice")
            } catch (e: Exception) {
                logger.warn("Gin7.processSemiAnnual(rice) error for world {}: {}", world.id, e.message)
            }
        }

        // 4. War income — casualty salvage (every month)
        try {
            gin7EconomyService.processWarIncome(world)
        } catch (e: Exception) {
            logger.warn("Gin7.processWarIncome error for world {}: {}", world.id, e.message)
        }

        // 5. Supply-state recompute (every month)
        try {
            gin7EconomyService.updatePlanetSupplyState(world)
        } catch (e: Exception) {
            logger.warn("Gin7.updatePlanetSupplyState error for world {}: {}", world.id, e.message)
        }

        // 6. Disaster/boom roll (every month)
        try {
            gin7EconomyService.processDisasterOrBoom(world)
        } catch (e: Exception) {
            logger.warn("Gin7.processDisasterOrBoom error for world {}: {}", world.id, e.message)
        }

        // 7. Trade-rate randomization (every month)
        try {
            gin7EconomyService.randomizePlanetTradeRate(world)
        } catch (e: Exception) {
            logger.warn("Gin7.randomizePlanetTradeRate error for world {}: {}", world.id, e.message)
        }

        // 8. Yearly statistics + 9. Faction rank — annual (month 1)
        if (month == 1) {
            try {
                gin7EconomyService.processYearlyStatistics(world)
            } catch (e: Exception) {
                logger.warn("Gin7.processYearlyStatistics error for world {}: {}", world.id, e.message)
            }
            try {
                gin7EconomyService.updateFactionRank(world)
            } catch (e: Exception) {
                logger.warn("Gin7.updateFactionRank error for world {}: {}", world.id, e.message)
            }
        }

        gameEventService.broadcastTurnAdvance(
            world.id.toLong(),
            world.currentYear.toInt(),
            world.currentMonth.toInt(),
        )
    }
}
