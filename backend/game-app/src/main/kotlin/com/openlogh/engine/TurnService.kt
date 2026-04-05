package com.openlogh.engine

import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandRegistry
import com.openlogh.engine.ai.OfficerAI
import com.openlogh.engine.ai.FactionAI
import com.openlogh.engine.modifier.ItemModifiers
import com.openlogh.engine.modifier.ModifierService
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.engine.war.BattleService
import com.openlogh.engine.war.FieldBattleTrigger
import com.openlogh.engine.trigger.TriggerCaller
import com.openlogh.engine.trigger.TriggerEnv
import com.openlogh.engine.trigger.buildPreTurnTriggers
import com.openlogh.entity.Planet
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.model.CrewType
import com.openlogh.repository.*
import com.openlogh.service.AuctionService
import com.openlogh.service.CommandLogDispatcher
import com.openlogh.service.InheritanceService
import com.openlogh.service.FactionService
import com.openlogh.service.ScenarioService
import com.openlogh.service.TournamentService
import com.openlogh.service.WorldService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToLong

/**
 * 턴 서비스: 월드의 전체 턴 파이프라인을 실행한다.
 * 1. 장수 커맨드 실행 (AI 포함)
 * 2. 보급 상태 갱신
 * 3. 이벤트 (PRE_MONTH, MONTH)
 * 4. 월 진행
 * 5. 경제 파이프라인 (수입, 반기, 재해, 교역)
 * 6. 외교 턴 처리
 * 7. 장수 유지보수 (나이, 경험, 헌신, 부상, 은퇴)
 * 8. NPC 스폰, 통일 체크
 */
@Service
class TurnService @Autowired constructor(
    private val sessionStateRepository: SessionStateRepository,
    private val officerRepository: OfficerRepository,
    private val officerTurnRepository: OfficerTurnRepository,
    private val factionTurnRepository: FactionTurnRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val commandExecutor: CommandExecutor,
    private val commandRegistry: CommandRegistry,
    private val scenarioService: ScenarioService,
    private val economyService: EconomyService,
    private val eventService: EventService,
    private val diplomacyService: DiplomacyService,
    private val officerMaintenanceService: OfficerMaintenanceService,
    private val specialAssignmentService: SpecialAssignmentService,
    private val npcSpawnService: NpcSpawnService,
    private val unificationService: UnificationService,
    private val inheritanceService: InheritanceService,
    private val yearbookService: YearbookService,
    private val auctionService: AuctionService,
    private val tournamentService: TournamentService,
    private val trafficSnapshotRepository: com.openlogh.repository.TrafficSnapshotRepository,
    private val worldPortFactory: JpaWorldPortFactory,
    private val officerAI: OfficerAI,
    private val factionAI: FactionAI,
    private val modifierService: ModifierService,
    private val worldService: WorldService,
    private val factionService: FactionService,
    private val battleService: BattleService,
    private val uniqueLotteryService: UniqueLotteryService,
    private val commandLogDispatcher: CommandLogDispatcher,
    private val gameConstService: com.openlogh.service.GameConstService,
    private val officerAccessLogRepository: OfficerAccessLogRepository,
    private val turnPipeline: com.openlogh.engine.turn.TurnPipeline,
    private val fieldBattleTrigger: FieldBattleTrigger,
    private val gameEventService: com.openlogh.service.GameEventService? = null,
) {
    /** Test-only constructor: omits turnPipeline, uses an empty no-op pipeline. */
    constructor(
        sessionStateRepository: SessionStateRepository,
        officerRepository: OfficerRepository,
        officerTurnRepository: OfficerTurnRepository,
        factionTurnRepository: FactionTurnRepository,
        planetRepository: PlanetRepository,
        factionRepository: FactionRepository,
        commandExecutor: CommandExecutor,
        commandRegistry: CommandRegistry,
        scenarioService: ScenarioService,
        economyService: EconomyService,
        eventService: EventService,
        diplomacyService: DiplomacyService,
        officerMaintenanceService: OfficerMaintenanceService,
        specialAssignmentService: SpecialAssignmentService,
        npcSpawnService: NpcSpawnService,
        unificationService: UnificationService,
        inheritanceService: InheritanceService,
        yearbookService: YearbookService,
        auctionService: AuctionService,
        tournamentService: TournamentService,
        trafficSnapshotRepository: com.openlogh.repository.TrafficSnapshotRepository,
        officerAI: OfficerAI,
        factionAI: FactionAI,
        modifierService: ModifierService,
        worldService: WorldService,
        factionService: FactionService,
        battleService: BattleService,
        uniqueLotteryService: UniqueLotteryService,
        commandLogDispatcher: CommandLogDispatcher,
        gameConstService: com.openlogh.service.GameConstService,
        officerAccessLogRepository: OfficerAccessLogRepository,
        fieldBattleTrigger: FieldBattleTrigger,
    ) : this(
        sessionStateRepository = sessionStateRepository,
        officerRepository = officerRepository,
        officerTurnRepository = officerTurnRepository,
        factionTurnRepository = factionTurnRepository,
        planetRepository = planetRepository,
        factionRepository = factionRepository,
        commandExecutor = commandExecutor,
        commandRegistry = commandRegistry,
        scenarioService = scenarioService,
        economyService = economyService,
        eventService = eventService,
        diplomacyService = diplomacyService,
        officerMaintenanceService = officerMaintenanceService,
        specialAssignmentService = specialAssignmentService,
        npcSpawnService = npcSpawnService,
        unificationService = unificationService,
        inheritanceService = inheritanceService,
        yearbookService = yearbookService,
        auctionService = auctionService,
        tournamentService = tournamentService,
        trafficSnapshotRepository = trafficSnapshotRepository,
        officerAI = officerAI,
        factionAI = factionAI,
        modifierService = modifierService,
        worldService = worldService,
        factionService = factionService,
        battleService = battleService,
        uniqueLotteryService = uniqueLotteryService,
        commandLogDispatcher = commandLogDispatcher,
        gameConstService = gameConstService,
        officerAccessLogRepository = officerAccessLogRepository,
        turnPipeline = com.openlogh.engine.turn.TurnPipeline(emptyList()),
        fieldBattleTrigger = fieldBattleTrigger,
    )

    constructor(
        sessionStateRepository: SessionStateRepository,
        officerRepository: OfficerRepository,
        officerTurnRepository: OfficerTurnRepository,
        factionTurnRepository: FactionTurnRepository,
        planetRepository: PlanetRepository,
        factionRepository: FactionRepository,
        commandExecutor: CommandExecutor,
        commandRegistry: CommandRegistry,
        scenarioService: ScenarioService,
        economyService: EconomyService,
        eventService: EventService,
        diplomacyService: DiplomacyService,
        officerMaintenanceService: OfficerMaintenanceService,
        specialAssignmentService: SpecialAssignmentService,
        npcSpawnService: NpcSpawnService,
        unificationService: UnificationService,
        inheritanceService: InheritanceService,
        yearbookService: YearbookService,
        auctionService: AuctionService,
        tournamentService: TournamentService,
        trafficSnapshotRepository: com.openlogh.repository.TrafficSnapshotRepository,
        officerAI: OfficerAI,
        factionAI: FactionAI,
        modifierService: ModifierService,
        worldService: WorldService,
        factionService: FactionService,
        battleService: BattleService,
        uniqueLotteryService: UniqueLotteryService,
        commandLogDispatcher: CommandLogDispatcher,
        gameConstService: com.openlogh.service.GameConstService,
        officerAccessLogRepository: OfficerAccessLogRepository,
        turnPipeline: com.openlogh.engine.turn.TurnPipeline,
        fieldBattleTrigger: FieldBattleTrigger,
    ) : this(
        sessionStateRepository = sessionStateRepository,
        officerRepository = officerRepository,
        officerTurnRepository = officerTurnRepository,
        factionTurnRepository = factionTurnRepository,
        planetRepository = planetRepository,
        factionRepository = factionRepository,
        commandExecutor = commandExecutor,
        commandRegistry = commandRegistry,
        scenarioService = scenarioService,
        economyService = economyService,
        eventService = eventService,
        diplomacyService = diplomacyService,
        officerMaintenanceService = officerMaintenanceService,
        specialAssignmentService = specialAssignmentService,
        npcSpawnService = npcSpawnService,
        unificationService = unificationService,
        inheritanceService = inheritanceService,
        yearbookService = yearbookService,
        auctionService = auctionService,
        tournamentService = tournamentService,
        trafficSnapshotRepository = trafficSnapshotRepository,
        worldPortFactory = JpaWorldPortFactory(
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
        ),
        officerAI = officerAI,
        factionAI = factionAI,
        modifierService = modifierService,
        worldService = worldService,
        factionService = factionService,
        battleService = battleService,
        uniqueLotteryService = uniqueLotteryService,
        commandLogDispatcher = commandLogDispatcher,
        gameConstService = gameConstService,
        officerAccessLogRepository = officerAccessLogRepository,
        turnPipeline = turnPipeline,
        fieldBattleTrigger = fieldBattleTrigger,
    )

    private val logger = LoggerFactory.getLogger(TurnService::class.java)
    private val jsonMapper = jacksonObjectMapper()

    private companion object {
        const val MAX_TICK_DURATION_MS = 30_000L
        val SABOTAGE_ACTION_CODES = setOf("화계", "선동", "파괴", "탈취", "첩보")
    }

    @Transactional
    fun processWorld(world: SessionState) {
        val world = sessionStateRepository.findById(world.id)
            .orElse(world)
        worldPortFactory.beginScope()
        try {
            val processStartMs = System.currentTimeMillis()
            val now = OffsetDateTime.now()
            val tickDuration = Duration.ofSeconds(world.tickSeconds.toLong())
            var nextTurnAt = world.updatedAt.plus(tickDuration)
            val worldId = world.id.toLong()
            val tickDeadline = System.currentTimeMillis() + MAX_TICK_DURATION_MS

            // 가오픈→정식 오픈 전환 시: 전체 장수 killTurn을 global 값으로 리셋
            // 가오픈 중 생성된 장수의 killTurn이 너무 낮아 정식 오픈 직후 삭턴 사망하는 것을 방지
            if (world.meta["openKillTurnReset"] != true) {
                val globalKillTurn = resolveGlobalKillTurn(world, null)
                val allGenerals = officerRepository.findBySessionId(worldId)
                var resetCount = 0
                for (general in allGenerals) {
                    if (general.npcState.toInt() == 5) continue
                    val currentKt = general.killTurn?.toInt() ?: continue
                    if (currentKt < globalKillTurn) {
                        general.killTurn = globalKillTurn.coerceIn(-32768, 32767).toShort()
                        resetCount++
                    }
                }
                if (resetCount > 0) {
                    officerRepository.saveAll(allGenerals)
                    logger.info("[Turn] 가오픈→오픈 전환: {}명 장수 killTurn을 {}로 리셋", resetCount, globalKillTurn)
                }
                world.meta["openKillTurnReset"] = true
            }

            // M-online: 월간 루프 진입 전 per-tick 온라인/오버헤드 갱신
            // Legacy: daemon.ts per-tick updateOnline() + CheckOverhead() before monthly loop
            try {
                updateOnline(world)
            } catch (e: Exception) {
                logger.warn("updateOnline failed: ${e.message}")
            }
            try {
                checkOverhead(world)
            } catch (e: Exception) {
                logger.warn("checkOverhead failed: ${e.message}")
            }

            var turnsProcessed = 0
            while (!now.isBefore(nextTurnAt) && System.currentTimeMillis() < tickDeadline) {
                turnsProcessed++
                val iterationStartMs = System.currentTimeMillis()
                // 진행 전 이전 월 기록 (연감 스냅샷용)
                val previousYear = world.currentYear.toInt()
                val previousMonth = world.currentMonth.toInt()

                try {
                    val commandStartMs = System.currentTimeMillis()
                    executeGeneralCommandsUntil(world, nextTurnAt)
                    val commandElapsedMs = System.currentTimeMillis() - commandStartMs
                    logger.info(
                        "[Turn] Turn {}/{}: executeGeneralCommands took {}ms",
                        world.currentYear,
                        world.currentMonth,
                        commandElapsedMs,
                    )
                } catch (e: Exception) {
                    logger.error("executeGeneralCommandsUntil failed for world {}: {}", worldId, e.message, e)
                }

                // C3: recalculateCitySupply — legacy ordering: after commands, before PreMonth
                try {
                    recalculateCitySupply(world)
                } catch (e: Exception) {
                    logger.warn("recalculateCitySupply failed: ${e.message}")
                }

                // C2: Step 200 — PRE_MONTH events directly before advanceMonth (legacy: PreMonth before turnDate)
                // Pipeline step 200 (PreMonthEventStep) has shouldSkip=true to avoid double-execution.
                try {
                    eventService.dispatchEvents(world, "PRE_MONTH")
                } catch (e: Exception) {
                    logger.warn("PreMonthEvent failed: ${e.message}")
                }

                // C2: Step 300 — economy pre-update directly before advanceMonth (legacy: preUpdateMonthly before turnDate)
                // Pipeline step 300 (EconomyPreUpdateStep) has shouldSkip=true to avoid double-execution.
                try {
                    economyService.preUpdateMonthly(world)
                } catch (e: Exception) {
                    logger.warn("EconomyPreUpdate failed: ${e.message}")
                }

                // H6: resetStrategicCommandLimits — after PRE_MONTH events + preUpdateMonthly, before advanceMonth
                // Legacy: runs inside postUpdateMonthly() after PreMonth events, before turnDate advances
                try {
                    resetStrategicCommandLimits(world)
                } catch (e: Exception) {
                    logger.warn("resetStrategicCommandLimits failed: ${e.message}")
                }

                // C2: Step 400 — Advance month after pre-update steps (legacy: turnDate advances after preUpdateMonthly)
                advanceMonth(world)

                // Step 500–1700: Pipeline handles all post-advanceMonth steps.
                // Steps 200/300 skipped in pipeline (shouldSkip=true); step 1400 is a no-op marker.
                val turnContext = com.openlogh.engine.turn.TurnContext(
                    world = world,
                    worldId = worldId,
                    year = world.currentYear.toInt(),
                    month = world.currentMonth.toInt(),
                    previousYear = previousYear,
                    previousMonth = previousMonth,
                    nextTurnAt = nextTurnAt,
                )
                turnPipeline.execute(turnContext)

                // H7: postUpdateMonthly — legacy order: checkWander -> updateGeneralNumber -> triggerTournament -> registerAuction
                // (func_gamerule.php:423-441)
                try {
                    checkWander(world)
                } catch (e: Exception) {
                    logger.warn("checkWander failed: ${e.message}")
                }
                try {
                    updateGeneralNumber(world)
                } catch (e: Exception) {
                    logger.warn("updateGeneralNumber failed: ${e.message}")
                }
                try {
                    triggerTournament(world)
                } catch (e: Exception) {
                    logger.warn("triggerTournament failed: ${e.message}")
                }
                try {
                    registerAuction(world)
                } catch (e: Exception) {
                    logger.warn("registerAuction failed: ${e.message}")
                }

                world.updatedAt = nextTurnAt
                nextTurnAt = nextTurnAt.plus(tickDuration)
                val iterationElapsedMs = System.currentTimeMillis() - iterationStartMs
                logger.info(
                    "[Turn] Turn iteration completed in {}ms for world {} (to {}/{})",
                    iterationElapsedMs,
                    worldId,
                    world.currentYear,
                    world.currentMonth,
                )
            }

            // 토너먼트 처리: 자동 진행 라운드 (legacy processTournament 패러티)
            try {
                tournamentService.processTournamentTurn(worldId)
            } catch (e: Exception) {
                logger.warn("TournamentService.processTournamentTurn failed: ${e.message}")
            }

            // 경매 처리: 만료된 경매 정리 (legacy processAuction 패러티)
            try {
                auctionService.processExpiredAuctions()
            } catch (e: Exception) {
                logger.warn("AuctionService.processExpiredAuctions failed: ${e.message}")
            }

            sessionStateRepository.save(world)
            val totalElapsedMs = System.currentTimeMillis() - processStartMs
            logger.info(
                "[Turn] processWorld completed: {} turns in {}ms for world {}",
                turnsProcessed,
                totalElapsedMs,
                worldId,
            )
        } finally {
            worldPortFactory.endScope()
        }
    }

    private fun executeGeneralCommandsUntil(world: SessionState, targetTime: OffsetDateTime) {
        val worldId = world.id.toLong()
        val ports = worldPortFactory.create(worldId)
        val generals = ports.allOfficers().map { it.toEntity() }
            .sortedWith(compareBy<com.openlogh.entity.Officer> { it.turnTime }.thenBy { it.id })
        val cityCache = ports.allPlanets().associate { it.id to it.toEntity() }
        val nationCache = ports.allFactions().associate { it.id to it.toEntity() }.toMutableMap()
        val env = buildCommandEnv(world)

        logger.info("[Turn] executeGeneralCommands: {} generals for world {}", generals.size, worldId)

        for (general in generals) {
            if (general.turnTime >= targetTime) {
                break
            }
            // Skip dead generals (killGeneral sets npcState=5, nationId=0)
            // Troop leaders also have npcState=5 but retain nationId>0
            if (general.npcState.toInt() == 5 && general.factionId == 0L) {
                continue
            }
            if (general.npcState == SovereignConstants.NPC_STATE_EMPEROR) {
                general.turnTime = calculateNextGeneralTurnTime(general, world.tickSeconds)
                general.updatedAt = OffsetDateTime.now()
                ports.putOfficer(general.toSnapshot())
                continue
            }
            try {
                val city = cityCache[general.planetId]
                val nation = if (general.factionId != 0L) {
                    nationCache[general.factionId]
                } else null
                val cityMates = generals.filter { it.planetId == general.planetId && it.id != general.id }

                firePreTurnTriggers(world, general, nation, cityMates)
                applyPerTurnCrewConsumption(general, city)

                if (general.blockState >= 2) {
                        if (general.killTurn != null) {
                            val kt = general.killTurn!! - 1
                            if (kt <= 0) {
                                val previousNationId = general.factionId
                                officerMaintenanceService.killGeneral(general, world, generals)
                                generals.forEach { ports.putOfficer(it.toSnapshot()) }
                                if (previousNationId > 0L) {
                                    factionRepository.findById(previousNationId).orElse(null)?.let {
                                        nationCache[previousNationId] = it
                                        ports.putFaction(it.toSnapshot())
                                    }
                                }
                                continue
                            } else {
                                general.killTurn = kt.coerceIn(-32768, 32767).toShort()
                        }
                    }
                    general.turnTime = calculateNextGeneralTurnTime(general, world.tickSeconds)
                    general.updatedAt = OffsetDateTime.now()
                    ports.putOfficer(general.toSnapshot())
                    continue
                }

                // Nation command for high-ranking officers
                var didConsumeNationTurn = false
                if (general.officerLevel >= 5 && nation != null) {
                    val nationTurns = factionTurnRepository
                        .findByNationIdAndOfficerLevelOrderByTurnIdx(general.factionId, general.officerLevel)
                    var nationActionCode: String? = null
                    var nationArg: Map<String, Any>? = null
                    var consumedNationTurn: com.openlogh.entity.NationTurn? = null

                    val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
                    if (nationTurns.isNotEmpty()) {
                        val nt = nationTurns.first()
                        nationActionCode = nt.actionCode
                        nationArg = nt.arg
                        consumedNationTurn = nt
                    } else if (general.npcState >= 2) {
                        val aiAction = officerAI.chooseNationTurn(general, world)
                        if (aiAction != "휴식") {
                            nationActionCode = aiAction
                            if (aiAction == "선전포고") {
                                nationArg = readStringAnyMap(nation.meta.remove("aiWarTarget"))
                            }
                        }
                    }

                    if (nationActionCode != null && commandRegistry.hasNationCommand(nationActionCode)) {
                        try {
                            val nationCmdResult = runBlocking {
                                commandExecutor.executeFactionCommand(
                                    nationActionCode,
                                    general,
                                    env,
                                    nationArg,
                                    city,
                                    nation,
                                    DeterministicRng.create(
                                        hiddenSeed,
                                        "nationCommand",
                                        general.id,
                                        world.currentYear,
                                        world.currentMonth,
                                        nationActionCode,
                                    )
                                )
                            }
                            if (nationCmdResult.logs.isNotEmpty()) {
                                try {
                                    commandLogDispatcher.dispatchLogs(
                                        worldId = worldId,
                                        generalId = general.id,
                                        nationId = if (general.factionId != 0L) general.factionId else null,
                                        year = env.year,
                                        month = env.month,
                                        logs = nationCmdResult.logs,
                                    )
                                } catch (e: Exception) { logger.warn("Failed to push nationCmd realtime result: {}", e.message) }
                            }
                        } catch (e: Exception) {
                            logger.warn("Nation command $nationActionCode failed for general ${general.id}: ${e.message}")
                        }
                    }

                    if (consumedNationTurn != null) {
                        factionTurnRepository.delete(consumedNationTurn)
                        factionTurnRepository.shiftTurnsDown(general.factionId, general.officerLevel, consumedNationTurn.turnIdx)
                        if (general.npcState < 2) didConsumeNationTurn = true
                        gameEventService?.fireCommand(
                            worldId = worldId,
                            year = world.currentYear,
                            month = world.currentMonth,
                            generalId = general.id,
                            commandEventType = "consumed",
                            detail = mapOf("actionCode" to (nationActionCode ?: ""), "nationId" to general.factionId),
                        )
                    }
                }

                var hasReservedTurn = didConsumeNationTurn

                // General command
                val actionCode: String
                val arg: Map<String, Any>?
                val executedTurn: com.openlogh.entity.GeneralTurn?

                // autorun_limit: 플레이어 장수가 일정 기간 미접속 시 AI가 대신 행동
                // legacy TurnExecutionHelper.php lines 289-296
                val useAutorun = general.npcState < 2 && run {
                    val currentYearMonth = world.currentYear.toInt() * 100 + world.currentMonth.toInt()
                    val autorunLimit = (general.meta["autorun_limit"] as? Number)?.toInt()
                        ?: ((world.currentYear.toInt() - 2) * 100 + world.currentMonth.toInt())
                    currentYearMonth < autorunLimit
                }

                if (general.npcState >= 2 || useAutorun) {
                    // NPC generals 또는 autorun 대상: AI가 행동 결정
                    actionCode = officerAI.decideAndExecute(general, world)
                    val aiArg = readStringAnyMap(general.meta.remove("aiArg"))
                    arg = if (actionCode == "선전포고" && aiArg == null) {
                        val warTargetId = (general.meta.remove("warTarget") as? Number)?.toLong()
                        if (warTargetId != null) mapOf("destNationId" to warTargetId) else null
                    } else {
                        aiArg
                    }
                    logger.info("[Turn] NPC {} ({}) AI decided: {}, arg={}", general.id, general.name, actionCode, arg)
                    executedTurn = null
                    // Consume any queued turns
                    val queuedTurns = officerTurnRepository.findByOfficerIdOrderByTurnIdx(general.id)
                    if (queuedTurns.isNotEmpty()) {
                        officerTurnRepository.deleteAll(queuedTurns)
                    }
                } else {
                    val generalTurns = officerTurnRepository.findByOfficerIdOrderByTurnIdx(general.id)
                    if (generalTurns.isNotEmpty()) {
                        val gt = generalTurns.first()
                        actionCode = gt.actionCode
                        arg = gt.arg
                        executedTurn = gt
                        if (actionCode != "휴식") hasReservedTurn = true
                    } else {
                        actionCode = "휴식"
                        arg = null
                        executedTurn = null
                    }
                }

                val fromCityId = general.planetId

                val generalHiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
                val rng = DeterministicRng.create(
                    generalHiddenSeed, "generalCommand", general.id, world.currentYear, world.currentMonth, actionCode
                )
                val cmdResult = if (commandRegistry.hasNationCommand(actionCode) && general.officerLevel >= 5 && nation != null) {
                    runBlocking {
                        commandExecutor.executeFactionCommand(actionCode, general, env, arg, city, nation, rng)
                    }
                } else {
                    runBlocking {
                        commandExecutor.executeOfficerCommand(actionCode, general, env, arg, city, nation, rng)
                    }
                }

                if (cmdResult.logs.isNotEmpty()) {
                    try {
                        commandLogDispatcher.dispatchLogs(
                            worldId = worldId,
                            generalId = general.id,
                            nationId = if (general.factionId != 0L) general.factionId else null,
                            year = env.year,
                            month = env.month,
                            logs = cmdResult.logs,
                        )
                    } catch (e: Exception) {
                        logger.warn("[Turn] Log dispatch failed for general {}: {}", general.id, e.message)
                    }
                }

                if (cmdResult.success && cmdResult.message != null) {
                    try {
                        val msgJson = jsonMapper.readTree(cmdResult.message!!)
                        if (msgJson.path("battleTriggered").asBoolean(false)) {
                            val targetCityId = when {
                                msgJson.path("targetCityId").isNumber -> msgJson.path("targetCityId").asLong()
                                msgJson.path("targetCityId").isTextual -> msgJson.path("targetCityId").asText().toLongOrNull()
                                else -> null
                            }
                            val targetCity = if (targetCityId != null) {
                                ports.planet(targetCityId)?.toEntity()
                            } else {
                                null
                            }
                            if (targetCity != null && targetCity.factionId != general.factionId) {
                                logger.info("[Turn] Battle triggered: {} ({}) attacks city {} (nation {})",
                                    general.id, general.name, targetCity.name, targetCity.factionId)
                                val battleResult = battleService.executeBattle(general, targetCity, world)
                                // Sync battle damage back to ports cache so subsequent
                                // attackers this turn see the reduced def/wall
                                ports.putPlanet(targetCity.toSnapshot())
                                ports.putOfficer(general.toSnapshot())
                                if (battleResult.cityOccupied) {
                                    general.planetId = targetCity.id
                                    ports.putOfficer(general.toSnapshot())
                                    logger.info("[Turn] City {} conquered by {} ({}) — general moved to conquered city",
                                        targetCity.name, general.id, general.name)
                                } else {
                                    logger.info("[Turn] Battle at {} — not conquered, {} ({}) stays at city {}",
                                        targetCity.name, general.id, general.name, general.planetId)
                                }
                            } else {
                                logger.warn("[Turn] battleTriggered but targetCity={} is null or same nation", targetCityId)
                            }
                        }

                        if (msgJson.path("tryUniqueLottery").asBoolean(false)) {
                            tryUniqueLottery(world, ports, general, actionCode)
                        }
                    } catch (e: Exception) {
                        logger.warn("[Turn] Failed to process command events: {}", e.message)
                    }
                }

                if (cmdResult.success) {
                    try {
                        fieldBattleTrigger.checkAndTrigger(general, actionCode, fromCityId, generals, world)
                    } catch (e: Exception) {
                        logger.warn("[Turn] FieldBattle trigger failed for general {}: {}", general.id, e.message)
                    }
                }

                if (executedTurn != null) {
                    officerTurnRepository.delete(executedTurn)
                    officerTurnRepository.shiftTurnsDown(general.id, executedTurn.turnIdx)
                    gameEventService?.fireCommand(
                        worldId = worldId,
                        year = world.currentYear,
                        month = world.currentMonth,
                        generalId = general.id,
                        commandEventType = "consumed",
                        detail = mapOf("actionCode" to actionCode),
                    )
                }

                // Track active actions for inheritance (core2026 parity)
                if (general.npcState.toInt() == 0 && actionCode != "휴식") {
                    inheritanceService.accruePoints(general, "active_action", 1)
                }

                if (cmdResult.success && general.npcState.toInt() == 0 && actionCode in SABOTAGE_ACTION_CODES) {
                    inheritanceService.accruePoints(general, "sabotage", 1)
                }

                // autorun_limit 갱신: 플레이어 장수가 예약된 턴을 실행했을 때
                // legacy TurnExecutionHelper.php lines 356-361
                val autorunUser = world.config["autorun_user"] as? Map<*, *>
                val limitMinutes = (autorunUser?.get("limit_minutes") as? Number)?.toInt()
                if (limitMinutes != null && limitMinutes > 0 && general.npcState < 2 && hasReservedTurn) {
                    val turnterm = world.tickSeconds / 60 // tick초 → 분 단위
                    val pushForward = if (turnterm > 0) limitMinutes / turnterm else 0
                    val currentYearMonth = world.currentYear.toInt() * 100 + world.currentMonth.toInt()
                    general.meta["autorun_limit"] = currentYearMonth + pushForward
                }

                // KillTurn handling — legacy TurnExecutionHelper.php lines 153-165
                if (general.killTurn != null) {
                    val configuredKillTurn = resolveGlobalKillTurn(world, env)
                    val isPlayer = general.npcState < 2 // 0=user, 1=빙의
                    val shouldReset = isPlayer
                        && general.killTurn!! <= configuredKillTurn
                        && !useAutorun
                        && actionCode != "휴식"

                    if (shouldReset) {
                        general.killTurn = configuredKillTurn.coerceIn(-32768, 32767).toShort()
                    } else {
                        val kt = general.killTurn!! - 1
                        if (kt <= 0) {
                            val previousNationId = general.factionId
                            officerMaintenanceService.killGeneral(general, world, generals)
                            generals.forEach { ports.putOfficer(it.toSnapshot()) }
                            if (previousNationId > 0L) {
                                factionRepository.findById(previousNationId).orElse(null)?.let {
                                    nationCache[previousNationId] = it
                                    ports.putFaction(it.toSnapshot())
                                }
                            }
                            continue
                        } else {
                            general.killTurn = kt.coerceIn(-32768, 32767).toShort()
                        }
                    }
                }

                general.turnTime = calculateNextGeneralTurnTime(general, world.tickSeconds)
                general.updatedAt = OffsetDateTime.now()
                ports.putOfficer(general.toSnapshot())
                if (nation != null) {
                    ports.putFaction(nation.toSnapshot())
                }
            } catch (e: Exception) {
                logger.error("Error processing general ${general.id}: ${e.message}", e)
            }
        }
    }

    private fun buildCommandEnv(world: SessionState): CommandEnv {
        val startYear = try {
            scenarioService.getScenario(world.scenarioCode).startYear
        } catch (e: Exception) {
            logger.warn("Failed to resolve startYear for scenario {}: {}", world.scenarioCode, e.message)
            world.currentYear.toInt()
        }

        val mapCode = (world.config["mapCode"] as? String) ?: "che"
        val killturn = resolveGlobalKillTurn(world, null)
        val gameStor = mutableMapOf<String, Any>("mapName" to mapCode)

        return CommandEnv(
            year = world.currentYear.toInt(),
            month = world.currentMonth.toInt(),
            startYear = startYear,
            worldId = world.id.toLong(),
            realtimeMode = world.realtimeMode,
            gameStor = gameStor,
            trainDelta = gameConstService.getDouble("trainDelta"),
            atmosDelta = gameConstService.getDouble("atmosDelta"),
            maxTrainByCommand = gameConstService.getInt("maxTrainByCommand"),
            maxAtmosByCommand = gameConstService.getInt("maxAtmosByCommand"),
            atmosSideEffectByTraining = gameConstService.getDouble("atmosSideEffectByTraining"),
            trainSideEffectByAtmosTurn = gameConstService.getDouble("trainSideEffectByAtmosTurn"),
            killturn = killturn.coerceIn(-32768, 32767).toShort(),
        )
    }

    private fun accrueYearlyInheritancePoints(generals: List<com.openlogh.entity.Officer>) {
        for (general in generals) {
            if (general.npcState.toInt() >= 2) {
                continue
            }

            val currentBelongYear = general.belong.toInt().coerceAtLeast(0)
            val storedMaxBelongYear = readInt(general.meta["inherit_max_belong_year"]) ?: 0
            if (currentBelongYear > storedMaxBelongYear) {
                inheritanceService.accruePoints(general, "max_belong", currentBelongYear - storedMaxBelongYear)
                general.meta["inherit_max_belong_year"] = currentBelongYear
            }

            val dexSum = general.dex1 + general.dex2 + general.dex3 + general.dex4 + general.dex5
            val currentDexPoint = (dexSum * 0.001).toInt()
            val storedDexPoint = readInt(general.meta["inherit_dex_point_total"]) ?: 0
            if (currentDexPoint > storedDexPoint) {
                inheritanceService.accruePoints(general, "dex", currentDexPoint - storedDexPoint)
                general.meta["inherit_dex_point_total"] = currentDexPoint
            }
        }
    }

    /**
     * Resolve the global killturn threshold.
     * Legacy: killturn = 4800 / turnterm (e.g. 80 for 60-min turns).
     * If npcmode==1 (빙의 모드), killturn is divided by 3.
     */
    private fun resolveGlobalKillTurn(world: SessionState, env: CommandEnv?): Int {
        return (world.config["killturn"] as? Number)?.toInt()
            ?: (world.config["killTurn"] as? Number)?.toInt()
            ?: env?.killturn?.toInt()
            ?: calcDefaultKillTurn(world)
    }

    private fun calcDefaultKillTurn(world: SessionState): Int {
        val turnterm = (world.tickSeconds / 60).coerceAtLeast(1)
        val base = 4800 / turnterm
        val npcmode = (world.config["npcmode"] as? Number)?.toInt() ?: 0
        return if (npcmode == 1) base / 3 else base
    }

    private fun calculateNextGeneralTurnTime(general: com.openlogh.entity.Officer, tickSeconds: Int): OffsetDateTime {
        val defaultNext = general.turnTime.plusSeconds(tickSeconds.toLong())
        val nextTurnTimeBase = readDouble(general.meta["nextTurnTimeBase"])
        if (nextTurnTimeBase == null) {
            return defaultNext
        }

        general.meta.remove("nextTurnTimeBase")
        val turnBoundary = cutTurn(defaultNext, tickSeconds)
        return turnBoundary.plusNanos((nextTurnTimeBase * 1_000_000_000L).roundToLong())
    }

    private fun tryUniqueLottery(
        world: SessionState,
        ports: com.openlogh.engine.turn.cqrs.persist.WorldPorts,
        general: com.openlogh.entity.Officer,
        actionCode: String,
    ) {
        if (general.npcState >= 2) {
            return
        }

        val itemRegistry = ItemModifiers.getAllMeta()
        val config = uniqueLotteryService.resolveUniqueConfig(world.config, itemRegistry)
        if (config.allItems.isEmpty()) {
            return
        }

        val acquireType = when (actionCode) {
            "임관" -> UniqueLotteryService.UniqueAcquireType.RANDOM_RECRUIT
            "건국", "CR건국", "무작위건국" -> UniqueLotteryService.UniqueAcquireType.FOUNDING
            else -> UniqueLotteryService.UniqueAcquireType.ITEM
        }

        val startYear = try {
            scenarioService.getScenario(world.scenarioCode).startYear
        } catch (e: Exception) {
            logger.warn("Failed to resolve startYear for scenario {}: {}", world.scenarioCode, e.message)
            world.currentYear.toInt()
        }
        val scenarioId = world.scenarioCode.toIntOrNull() ?: 0
        val generalItems = generalItemSlotsOf(general)
        val allGeneralSlots = ports.allOfficers().map { generalItemSlotsOf(it.toEntity()) }
        val occupiedUniqueCounts = uniqueLotteryService.countOccupiedUniqueItems(allGeneralSlots, itemRegistry, config)
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: world.id.toString()
        val seed = uniqueLotteryService.buildGenericUniqueSeed(
            hiddenSeed = hiddenSeed,
            year = world.currentYear.toInt(),
            month = world.currentMonth.toInt(),
            generalId = general.id,
            reason = actionCode,
        )
        val input = UniqueLotteryService.UniqueLotteryInput(
            rng = uniqueLotteryService.createDeterministicRng(seed),
            config = config,
            itemRegistry = itemRegistry,
            generalItems = generalItems,
            occupiedUniqueCounts = occupiedUniqueCounts,
            scenarioId = scenarioId,
            userCount = ports.allOfficers().count { it.npcState.toInt() < 2 },
            currentYear = world.currentYear.toInt(),
            currentMonth = world.currentMonth.toInt(),
            startYear = startYear,
            initYear = startYear,
            initMonth = 1,
            acquireType = acquireType,
            inheritRandomUnique = general.meta["inheritRandomUnique"] != null,
        )

        val reservedRandomUnique = general.meta["inheritRandomUnique"] != null
        val availableUniqueItems = uniqueLotteryService.collectAvailableUniqueItems(input)
        val openSlots = uniqueLotteryService.countOpenUniqueSlots(input)
        if (reservedRandomUnique && (openSlots <= 0 || availableUniqueItems.isEmpty())) {
            general.meta.remove("inheritRandomUnique")
            inheritanceService.refundRandomUniquePurchase(general)
            return
        }

        val itemKey = uniqueLotteryService.rollUniqueLottery(input) ?: return
        val slot = uniqueLotteryService.slotForItem(itemKey, itemRegistry) ?: return
        if (!uniqueLotteryService.applyUniqueItemGain(general, itemKey, slot)) {
            return
        }

        if (reservedRandomUnique && uniqueLotteryService.isInheritRandomUniqueAvailable(input)) {
            general.meta.remove("inheritRandomUnique")
        }
    }

    private fun generalItemSlotsOf(general: com.openlogh.entity.Officer): UniqueLotteryService.GeneralItemSlots {
        return UniqueLotteryService.GeneralItemSlots(
            horse = normalizeItemCode(general.engineCode),
            weapon = normalizeItemCode(general.flagshipCode),
            book = normalizeItemCode(general.equipCode),
            item = normalizeItemCode(general.accessoryCode),
        )
    }

    private fun normalizeItemCode(code: String?): String? {
        return code?.takeUnless { it == "None" }?.takeUnless { it.isBlank() }
    }

    private fun cutTurn(time: OffsetDateTime, tickSeconds: Int): OffsetDateTime {
        val tick = tickSeconds.toLong().coerceAtLeast(1L)
        val epoch = time.toEpochSecond()
        val floored = epoch - floorMod(epoch, tick)
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(floored), time.offset)
    }

    private fun floorMod(value: Long, mod: Long): Long {
        val raw = value % mod
        return if (raw >= 0) raw else raw + mod
    }

    private fun readDouble(raw: Any?): Double? {
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
    }

    private fun readInt(raw: Any?): Int? {
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    private fun advanceMonth(world: SessionState) {
        val startYear = (world.config["startyear"] as? Number)?.toInt() ?: world.currentYear.toInt()
        val elapsedTurns = (world.currentYear.toInt() - startYear) * 12 + (world.currentMonth.toInt() - 1) + 1
        val totalMonths = startYear.toLong() * 12 + elapsedTurns
        world.currentYear = (totalMonths / 12).coerceIn(0, 32767).toShort()
        world.currentMonth = (1 + totalMonths % 12).coerceIn(1, 12).toShort()
    }

    /**
     * Recalculate city supply state (traffic/supply routes) per turn.
     * Delegates to EconomyService which already has BFS-based supply logic.
     * Renamed from updateTraffic to clarify the actual operation.
     */
    private fun recalculateCitySupply(world: SessionState) {
        economyService.updateCitySupplyState(world)
    }

    /**
     * M-online: Update online status bookkeeping per tick.
     * Legacy: func.php:1205-1248 updateOnline() — updates online general count in world state.
     */
    private fun updateOnline(world: SessionState) {
        val worldId = world.id.toLong()
        val accessLogs = officerAccessLogRepository.findBySessionId(worldId)
        // Filter to recent access (accessed since last tick update)
        val recentLogs = accessLogs.filter { it.accessedAt >= world.updatedAt }
        val onlineCount = recentLogs.size

        // Build nation name map
        val nations = factionRepository.findBySessionId(worldId)
        val nationNames = mutableMapOf<Long, String>(0L to "재야")
        nations.forEach { nationNames[it.id] = it.name }

        // Map generalId -> nationId via generals
        val generals = officerRepository.findBySessionId(worldId)
        val generalNationMap = generals.associate { it.id to it.factionId }

        // Group online logs by nation, sort by count descending
        val onlineByNation = recentLogs
            .groupBy { generalNationMap[it.generalId] ?: 0L }
            .entries.sortedByDescending { it.value.size }

        val onlineNationStr = onlineByNation.map { (nationId, _) ->
            "【${nationNames[nationId] ?: "unknown"}】"
        }.joinToString(", ")

        world.meta["online_user_cnt"] = onlineCount
        world.meta["online_nation"] = onlineNationStr
    }

    /**
     * M-online: Check overhead (resource/process overhead) per tick.
     * Legacy: func.php:1103-1116 CheckOverhead() — recalculates refreshLimit.
     * Formula: round(turnterm^0.6 * 3) * refreshLimitCoef
     */
    private fun checkOverhead(world: SessionState) {
        val turnterm = world.tickSeconds.toDouble()
        val refreshLimitCoef = (world.config["refreshLimitCoef"] as? Number)?.toInt() ?: 10
        val nextRefreshLimit = kotlin.math.round(Math.pow(turnterm, 0.6) * 3).toInt() * refreshLimitCoef
        val currentRefreshLimit = (world.meta["refreshLimit"] as? Number)?.toInt() ?: 0
        if (nextRefreshLimit != currentRefreshLimit) {
            world.meta["refreshLimit"] = nextRefreshLimit
        }
    }

    /**
     * H7: Check wander nations for auto-dissolution.
     * Legacy: func_gamerule.php:445-467 — wander nations (level=0) auto-dissolved after startYear+2.
     * Uses CommandRegistry to create and execute 해산 command for each wander chief.
     */
    private fun checkWander(world: SessionState) {
        val startYear = (world.config["startYear"] as? Number)?.toInt() ?: return
        if (world.currentYear.toInt() < startYear + 2) return

        val worldId = world.id.toLong()
        val generals = officerRepository.findBySessionId(worldId)
        val env = buildCommandEnv(world)
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: world.id.toString()

        for (general in generals) {
            if (general.officerLevel.toInt() != 20) continue
            val nation = factionRepository.findById(general.factionId).orElse(null) ?: continue
            if (nation.factionRank.toInt() != 0) continue

            val command = commandRegistry.createOfficerCommand("해산", general, env)
            if (command.checkFullCondition() is com.openlogh.command.constraint.ConstraintResult.Pass) {
                val rng = DeterministicRng.create(
                    hiddenSeed, "checkWander", world.currentYear, world.currentMonth, general.id,
                )
                runBlocking { command.run(rng) }
                logger.info("[Turn] checkWander: 방랑군 자동 해산 - generalId={}", general.id)
            }
        }
    }

    /**
     * H7: Trigger monthly tournament check.
     * Legacy func.php:triggerTournament 패러티:
     *   tournament==0 && tnmt_auto && 40% chance → tnmt_pattern 큐에서 타입 선택 후 startTournament
     */
    private fun triggerTournament(world: SessionState) {
        tournamentService.checkAndTriggerTournament(world)
    }

    /**
     * H7: Register monthly auction entries.
     * Legacy func_auction.php:registerAuction 패러티:
     *   - 비NPC 장수 평균 금/쌀 계산 (클램프 1000~20000)
     *   - 중립 buyRice 경매 수 기반 확률로 쌀 판매(시스템→구매자) 경매 등록
     *   - 중립 sellRice 경매 수 기반 확률로 쌀 구매(시스템←판매자) 경매 등록
     */
    private fun registerAuction(world: SessionState) {
        val worldId = world.id.toLong()
        val generals = officerRepository.findBySessionId(worldId)
        val humanGenerals = generals.filter { it.npcState.toInt() < 2 }

        val avgGold = if (humanGenerals.isEmpty()) 1000.0
            else humanGenerals.map { it.funds.toDouble() }.average()
        val avgRice = if (humanGenerals.isEmpty()) 1000.0
            else humanGenerals.map { it.supplies.toDouble() }.average()

        val clampedGold = avgGold.coerceIn(1000.0, 20000.0)
        val clampedRice = avgRice.coerceIn(1000.0, 20000.0)

        val openAuctions = auctionService.listActiveAuctions(worldId)
            .filter { it.hostGeneralId == 0L }
        val neutralBuyRiceCnt = openAuctions.count { it.subType == "buyRice" }
        val neutralSellRiceCnt = openAuctions.count { it.subType == "sellRice" }

        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: world.id.toString()
        val rng = RandUtil(
            DeterministicRng.create(hiddenSeed, "registerAuction", world.currentYear, world.currentMonth) as LiteHashDRBG
        )

        // 쌀 판매 경매 등록 (시스템이 쌀을 팔고 금을 받음 → buyRice subtype)
        if (rng.nextFloat1() < 1.0 / (neutralBuyRiceCnt + 5)) {
            val mul = rng.nextRangeInt(1, 5)
            val amount = (clampedRice / 20.0 * mul).toLong().coerceAtLeast(100).coerceAtMost(10000).toInt()
            val startBid = (clampedGold / 20.0 * 0.9 * mul)
                .coerceIn(amount * 0.8, amount * 1.2)
                .toLong().coerceAtLeast(100).toInt()
            val finishBid = (amount * 2.0).toLong().coerceAtLeast((startBid * 1.1).toLong()).toInt()
            val term = rng.nextRangeInt(3, 12)
            auctionService.openSystemResourceAuction(worldId, "buyRice", amount, term, startBid, finishBid)
        }

        // 쌀 구매 경매 등록 (시스템이 쌀을 사고 금을 줌 → sellRice subtype)
        if (rng.nextFloat1() < 1.0 / (neutralSellRiceCnt + 5)) {
            val mul = rng.nextRangeInt(1, 5)
            val amount = (clampedGold / 20.0 * mul).toLong().coerceAtLeast(100).coerceAtMost(10000).toInt()
            val startBid = (clampedRice / 20.0 * 1.1 * mul)
                .coerceIn(amount * 0.8, amount * 1.2)
                .toLong().coerceAtLeast(100).toInt()
            val finishBid = (amount * 2.0).toLong().coerceAtLeast((startBid * 1.1).toLong()).toInt()
            val term = rng.nextRangeInt(3, 12)
            auctionService.openSystemResourceAuction(worldId, "sellRice", amount, term, startBid, finishBid)
        }
    }

    /**
     * H7: Update nation general count and refresh static nation info.
     * Legacy: func_gamerule.php:174-186 — updateGeneralNumber / refreshNationStaticInfo.
     * Counts generals per nation (excluding npcState=5) and saves nation.officerCount.
     */
    private fun updateGeneralNumber(world: SessionState) {
        val worldId = world.id.toLong()
        val generals = officerRepository.findBySessionId(worldId)
        val nations = factionRepository.findBySessionId(worldId)

        val genCountByNation = generals
            .filter { it.npcState.toInt() != 5 && it.factionId > 0 }
            .groupingBy { it.factionId }
            .eachCount()

        for (nation in nations) {
            if (nation.id == 0L) continue
            nation.officerCount = genCountByNation[nation.id] ?: 0
        }
        factionRepository.saveAll(nations)
    }

    /**
     * Decrement strategic command limits for all nations each turn.
     * Per legacy: strategicCmdLimit decreases by 1 each turn until 0.
     */
    private fun resetStrategicCommandLimits(world: SessionState) {
        val worldId = world.id.toLong()
        val ports = worldPortFactory.create(worldId)
        val generalSnapshots = ports.allOfficers()
        val generals = generalSnapshots.map { it.toEntity() }
        val nationSnapshots = ports.allFactions()
        val nations = nationSnapshots.map { it.toEntity() }
        val citySnapshots = ports.allPlanets()
        val cities = citySnapshots.map { it.toEntity() }

        val originalGenerals = generalSnapshots.associateBy { it.id }
        val originalNations = nationSnapshots.associateBy { it.id }
        val originalCities = citySnapshots.associateBy { it.id }

        for (general in generals) {
            if (general.makeLimit > 0) {
                general.makeLimit = (general.makeLimit - 1).coerceIn(0, 32767).toShort()
            }
        }

        val activeGeneralCountByNation = generals
            .filter { !(it.npcState.toInt() == 5 && it.factionId == 0L) && it.factionId > 0 }
            .groupingBy { it.factionId }
            .eachCount()

        for (nation in nations) {
            if (nation.strategicCmdLimit > 0) {
                nation.strategicCmdLimit = (nation.strategicCmdLimit - 1).coerceIn(0, 72).toShort()
            }
            if (nation.surrenderLimit > 0) {
                nation.surrenderLimit = (nation.surrenderLimit - 1).coerceIn(0, 120).toShort()
            }
            nation.conscriptionRateTmp = nation.conscriptionRate
            nation.officerCount = activeGeneralCountByNation[nation.id] ?: 0
            nation.spy = decaySpyDurations(nation.spy)
        }

        transitionCityStates(cities)
        updateDevelCost(world)
        decayRefreshScoreTotals(worldId)

        for (g in generals) {
            val snap = g.toSnapshot()
            if (snap != originalGenerals[g.id]) ports.putOfficer(snap)
        }
        for (c in cities) {
            val snap = c.toSnapshot()
            if (snap != originalCities[c.id]) ports.putPlanet(snap)
        }
        nations.forEach { ports.putFaction(it.toSnapshot()) }
    }

    private fun firePreTurnTriggers(
        world: SessionState,
        general: com.openlogh.entity.Officer,
        nation: Faction?,
        cityMates: List<com.openlogh.entity.Officer> = emptyList(),
    ) {
        val modifiers = modifierService.getModifiers(general, nation)
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: world.id.toString()
        val preTurnRng = DeterministicRng.create(
            hiddenSeed, "preTurnTrigger", general.id, world.currentYear, world.currentMonth
        )
        val triggers = buildPreTurnTriggers(general, modifiers, cityMates, preTurnRng)
        if (triggers.isEmpty()) return

        val caller = TriggerCaller()
        caller.addAll(triggers)
        caller.fire(
            TriggerEnv(
                worldId = world.id.toLong(),
                year = world.currentYear.toInt(),
                month = world.currentMonth.toInt(),
                generalId = general.id,
            )
        )
    }

    private fun applyPerTurnCrewConsumption(general: com.openlogh.entity.Officer, city: com.openlogh.entity.Planet?) {
        if (general.ships <= 0) {
            return
        }

        val crewTypeCost = CrewType.fromCode(general.shipClass.toInt())?.cost ?: 0
        val baseLoss = kotlin.math.ceil(general.ships.toDouble() * crewTypeCost / 1000.0).toInt().coerceAtLeast(1)
        val unsupplied = city?.supplyState?.toInt() == 0
        val crewLoss = if (unsupplied) baseLoss * 2 else baseLoss
        general.ships = (general.ships - crewLoss).coerceAtLeast(0)

        if (unsupplied) {
            val atmosDrop = minOf(5, general.morale.toInt())
            general.morale = (general.morale - atmosDrop).coerceIn(0, 150).toShort()
        }
    }

    private fun transitionCityStates(cities: List<Planet>) {
        for (city in cities) {
            city.state = when (city.state.toInt()) {
                31 -> 0
                32 -> 31
                33 -> 0
                34 -> 33
                41 -> 0
                42 -> 41
                43 -> 42
                else -> city.state.toInt()
            }.coerceIn(0, 32767).toShort()

            val nextTerm = (city.term.toInt() - 1).coerceAtLeast(0)
            city.term = nextTerm.coerceIn(0, 32767).toShort()
            if (nextTerm == 0) {
                city.conflict = mutableMapOf()
            }
        }
    }

    private fun updateDevelCost(world: SessionState) {
        val startYear = (world.config["startyear"] as? Number)?.toInt() ?: world.currentYear.toInt()
        val develCost = (world.currentYear.toInt() - startYear + 10) * 2
        world.config["develCost"] = develCost
        world.config["develcost"] = develCost
    }

    private fun decayRefreshScoreTotals(worldId: Long) {
        val accessLogs = officerAccessLogRepository.findBySessionId(worldId)
        if (accessLogs.isEmpty()) {
            return
        }

        for (accessLog in accessLogs) {
            accessLog.refreshScoreTotal = kotlin.math.floor(accessLog.refreshScoreTotal * 0.99).toInt()
        }
        officerAccessLogRepository.saveAll(accessLogs)
    }

    private fun decaySpyDurations(spy: MutableMap<String, Any>): MutableMap<String, Any> {
        val nextSpy = mutableMapOf<String, Any>()
        for ((cityId, remainRaw) in spy) {
            val remain = (remainRaw as? Number)?.toInt() ?: continue
            if (remain > 1) {
                nextSpy[cityId] = remain - 1
            }
        }
        return nextSpy
    }

    private fun readStringAnyMap(raw: Any?): Map<String, Any>? {
        if (raw !is Map<*, *>) return null
        val result = mutableMapOf<String, Any>()
        raw.forEach { (k, v) ->
            if (k is String && v != null) {
                result[k] = v
            }
        }
        return result
    }
}
