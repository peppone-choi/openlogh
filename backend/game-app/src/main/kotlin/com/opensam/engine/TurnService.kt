package com.opensam.engine

import com.opensam.command.CommandEnv
import com.opensam.command.CommandExecutor
import com.opensam.command.CommandRegistry
import com.opensam.engine.ai.GeneralAI
import com.opensam.engine.ai.NationAI
import com.opensam.engine.modifier.ItemModifiers
import com.opensam.engine.modifier.ModifierService
import com.opensam.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.opensam.engine.turn.cqrs.persist.toEntity
import com.opensam.engine.turn.cqrs.persist.toSnapshot
import com.opensam.engine.war.BattleService
import com.opensam.engine.trigger.TriggerCaller
import com.opensam.engine.trigger.TriggerEnv
import com.opensam.engine.trigger.buildPreTurnTriggers
import com.opensam.entity.City
import com.opensam.entity.Nation
import com.opensam.entity.WorldState
import com.opensam.model.CrewType
import com.opensam.repository.*
import com.opensam.service.AuctionService
import com.opensam.service.CommandLogDispatcher
import com.opensam.service.InheritanceService
import com.opensam.service.NationService
import com.opensam.service.ScenarioService
import com.opensam.service.TournamentService
import com.opensam.service.WorldService
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
    private val worldStateRepository: WorldStateRepository,
    private val generalRepository: GeneralRepository,
    private val generalTurnRepository: GeneralTurnRepository,
    private val nationTurnRepository: NationTurnRepository,
    private val cityRepository: CityRepository,
    private val nationRepository: NationRepository,
    private val commandExecutor: CommandExecutor,
    private val commandRegistry: CommandRegistry,
    private val scenarioService: ScenarioService,
    private val economyService: EconomyService,
    private val eventService: EventService,
    private val diplomacyService: DiplomacyService,
    private val generalMaintenanceService: GeneralMaintenanceService,
    private val specialAssignmentService: SpecialAssignmentService,
    private val npcSpawnService: NpcSpawnService,
    private val unificationService: UnificationService,
    private val inheritanceService: InheritanceService,
    private val yearbookService: YearbookService,
    private val auctionService: AuctionService,
    private val tournamentService: TournamentService,
    private val trafficSnapshotRepository: com.opensam.repository.TrafficSnapshotRepository,
    private val worldPortFactory: JpaWorldPortFactory,
    private val generalAI: GeneralAI,
    private val nationAI: NationAI,
    private val modifierService: ModifierService,
    private val worldService: WorldService,
    private val nationService: NationService,
    private val battleService: BattleService,
    private val uniqueLotteryService: UniqueLotteryService,
    private val commandLogDispatcher: CommandLogDispatcher,
    private val gameConstService: com.opensam.service.GameConstService,
    private val generalAccessLogRepository: GeneralAccessLogRepository,
    private val turnPipeline: com.opensam.engine.turn.TurnPipeline,
) {
    /** Test-only constructor: omits turnPipeline, uses an empty no-op pipeline. */
    constructor(
        worldStateRepository: WorldStateRepository,
        generalRepository: GeneralRepository,
        generalTurnRepository: GeneralTurnRepository,
        nationTurnRepository: NationTurnRepository,
        cityRepository: CityRepository,
        nationRepository: NationRepository,
        commandExecutor: CommandExecutor,
        commandRegistry: CommandRegistry,
        scenarioService: ScenarioService,
        economyService: EconomyService,
        eventService: EventService,
        diplomacyService: DiplomacyService,
        generalMaintenanceService: GeneralMaintenanceService,
        specialAssignmentService: SpecialAssignmentService,
        npcSpawnService: NpcSpawnService,
        unificationService: UnificationService,
        inheritanceService: InheritanceService,
        yearbookService: YearbookService,
        auctionService: AuctionService,
        tournamentService: TournamentService,
        trafficSnapshotRepository: com.opensam.repository.TrafficSnapshotRepository,
        generalAI: GeneralAI,
        nationAI: NationAI,
        modifierService: ModifierService,
        worldService: WorldService,
        nationService: NationService,
        battleService: BattleService,
        uniqueLotteryService: UniqueLotteryService,
        commandLogDispatcher: CommandLogDispatcher,
        gameConstService: com.opensam.service.GameConstService,
        generalAccessLogRepository: GeneralAccessLogRepository,
    ) : this(
        worldStateRepository = worldStateRepository,
        generalRepository = generalRepository,
        generalTurnRepository = generalTurnRepository,
        nationTurnRepository = nationTurnRepository,
        cityRepository = cityRepository,
        nationRepository = nationRepository,
        commandExecutor = commandExecutor,
        commandRegistry = commandRegistry,
        scenarioService = scenarioService,
        economyService = economyService,
        eventService = eventService,
        diplomacyService = diplomacyService,
        generalMaintenanceService = generalMaintenanceService,
        specialAssignmentService = specialAssignmentService,
        npcSpawnService = npcSpawnService,
        unificationService = unificationService,
        inheritanceService = inheritanceService,
        yearbookService = yearbookService,
        auctionService = auctionService,
        tournamentService = tournamentService,
        trafficSnapshotRepository = trafficSnapshotRepository,
        generalAI = generalAI,
        nationAI = nationAI,
        modifierService = modifierService,
        worldService = worldService,
        nationService = nationService,
        battleService = battleService,
        uniqueLotteryService = uniqueLotteryService,
        commandLogDispatcher = commandLogDispatcher,
        gameConstService = gameConstService,
        generalAccessLogRepository = generalAccessLogRepository,
        turnPipeline = com.opensam.engine.turn.TurnPipeline(emptyList()),
    )

    constructor(
        worldStateRepository: WorldStateRepository,
        generalRepository: GeneralRepository,
        generalTurnRepository: GeneralTurnRepository,
        nationTurnRepository: NationTurnRepository,
        cityRepository: CityRepository,
        nationRepository: NationRepository,
        commandExecutor: CommandExecutor,
        commandRegistry: CommandRegistry,
        scenarioService: ScenarioService,
        economyService: EconomyService,
        eventService: EventService,
        diplomacyService: DiplomacyService,
        generalMaintenanceService: GeneralMaintenanceService,
        specialAssignmentService: SpecialAssignmentService,
        npcSpawnService: NpcSpawnService,
        unificationService: UnificationService,
        inheritanceService: InheritanceService,
        yearbookService: YearbookService,
        auctionService: AuctionService,
        tournamentService: TournamentService,
        trafficSnapshotRepository: com.opensam.repository.TrafficSnapshotRepository,
        generalAI: GeneralAI,
        nationAI: NationAI,
        modifierService: ModifierService,
        worldService: WorldService,
        nationService: NationService,
        battleService: BattleService,
        uniqueLotteryService: UniqueLotteryService,
        commandLogDispatcher: CommandLogDispatcher,
        gameConstService: com.opensam.service.GameConstService,
        generalAccessLogRepository: GeneralAccessLogRepository,
        turnPipeline: com.opensam.engine.turn.TurnPipeline,
    ) : this(
        worldStateRepository = worldStateRepository,
        generalRepository = generalRepository,
        generalTurnRepository = generalTurnRepository,
        nationTurnRepository = nationTurnRepository,
        cityRepository = cityRepository,
        nationRepository = nationRepository,
        commandExecutor = commandExecutor,
        commandRegistry = commandRegistry,
        scenarioService = scenarioService,
        economyService = economyService,
        eventService = eventService,
        diplomacyService = diplomacyService,
        generalMaintenanceService = generalMaintenanceService,
        specialAssignmentService = specialAssignmentService,
        npcSpawnService = npcSpawnService,
        unificationService = unificationService,
        inheritanceService = inheritanceService,
        yearbookService = yearbookService,
        auctionService = auctionService,
        tournamentService = tournamentService,
        trafficSnapshotRepository = trafficSnapshotRepository,
        worldPortFactory = JpaWorldPortFactory(
            generalRepository = generalRepository,
            cityRepository = cityRepository,
            nationRepository = nationRepository,
        ),
        generalAI = generalAI,
        nationAI = nationAI,
        modifierService = modifierService,
        worldService = worldService,
        nationService = nationService,
        battleService = battleService,
        uniqueLotteryService = uniqueLotteryService,
        commandLogDispatcher = commandLogDispatcher,
        gameConstService = gameConstService,
        generalAccessLogRepository = generalAccessLogRepository,
        turnPipeline = turnPipeline,
    )

    private val logger = LoggerFactory.getLogger(TurnService::class.java)
    private val jsonMapper = jacksonObjectMapper()

    private companion object {
        const val MAX_TICK_DURATION_MS = 30_000L
        val SABOTAGE_ACTION_CODES = setOf("화계", "선동", "파괴", "탈취", "첩보")
    }

    @Transactional
    fun processWorld(world: WorldState) {
        val world = worldStateRepository.findById(world.id)
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
                val allGenerals = generalRepository.findByWorldId(worldId)
                var resetCount = 0
                for (general in allGenerals) {
                    if (general.npcState.toInt() == 5) continue
                    val currentKt = general.killTurn?.toInt() ?: continue
                    if (currentKt < globalKillTurn) {
                        general.killTurn = globalKillTurn.toShort()
                        resetCount++
                    }
                }
                if (resetCount > 0) {
                    generalRepository.saveAll(allGenerals)
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
                val turnContext = com.opensam.engine.turn.TurnContext(
                    world = world,
                    worldId = worldId,
                    year = world.currentYear.toInt(),
                    month = world.currentMonth.toInt(),
                    previousYear = previousYear,
                    previousMonth = previousMonth,
                    nextTurnAt = nextTurnAt,
                )
                turnPipeline.execute(turnContext)

                // H7: postUpdateMonthly — functions present in legacy but previously missing
                // Called after pipeline (postUpdateMonthly position: step 1000–1600 range)
                try {
                    checkWander(world)
                } catch (e: Exception) {
                    logger.warn("checkWander failed: ${e.message}")
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
                try {
                    updateGeneralNumber(world)
                } catch (e: Exception) {
                    logger.warn("updateGeneralNumber failed: ${e.message}")
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

            worldStateRepository.save(world)
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

    private fun executeGeneralCommandsUntil(world: WorldState, targetTime: OffsetDateTime) {
        val worldId = world.id.toLong()
        val ports = worldPortFactory.create(worldId)
        val generals = ports.allGenerals().map { it.toEntity() }.sortedBy { it.turnTime }
        val cityCache = ports.allCities().associate { it.id to it.toEntity() }
        val nationCache = ports.allNations().associate { it.id to it.toEntity() }.toMutableMap()
        val env = buildCommandEnv(world)

        logger.info("[Turn] executeGeneralCommands: {} generals for world {}", generals.size, worldId)

        for (general in generals) {
            if (general.turnTime >= targetTime) {
                break
            }
            // Skip dead generals (killGeneral sets npcState=5, nationId=0)
            // Troop leaders also have npcState=5 but retain nationId>0
            if (general.npcState.toInt() == 5 && general.nationId == 0L) {
                continue
            }
            if (general.npcState == EmperorConstants.NPC_STATE_EMPEROR) {
                general.turnTime = calculateNextGeneralTurnTime(general, world.tickSeconds)
                general.updatedAt = OffsetDateTime.now()
                ports.putGeneral(general.toSnapshot())
                continue
            }
            try {
                val city = cityCache[general.cityId]
                val nation = if (general.nationId != 0L) {
                    nationCache[general.nationId]
                } else null
                val cityMates = generals.filter { it.cityId == general.cityId && it.id != general.id }

                firePreTurnTriggers(world, general, nation, cityMates)
                applyPerTurnCrewConsumption(general, city)

                if (general.blockState >= 2) {
                        if (general.killTurn != null) {
                            val kt = general.killTurn!! - 1
                            if (kt <= 0) {
                                val previousNationId = general.nationId
                                generalMaintenanceService.killGeneral(general, world, generals)
                                generals.forEach { ports.putGeneral(it.toSnapshot()) }
                                if (previousNationId > 0L) {
                                    nationRepository.findById(previousNationId).orElse(null)?.let {
                                        nationCache[previousNationId] = it
                                        ports.putNation(it.toSnapshot())
                                    }
                                }
                                continue
                            } else {
                                general.killTurn = kt.toShort()
                        }
                    }
                    general.turnTime = calculateNextGeneralTurnTime(general, world.tickSeconds)
                    general.updatedAt = OffsetDateTime.now()
                    ports.putGeneral(general.toSnapshot())
                    continue
                }

                // Nation command for high-ranking officers
                var didConsumeNationTurn = false
                if (general.officerLevel >= 5 && nation != null) {
                    val nationTurns = nationTurnRepository
                        .findByNationIdAndOfficerLevelOrderByTurnIdx(general.nationId, general.officerLevel)
                    var nationActionCode: String? = null
                    var nationArg: Map<String, Any>? = null
                    var consumedNationTurn: com.opensam.entity.NationTurn? = null

                    val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
                    if (nationTurns.isNotEmpty()) {
                        val nt = nationTurns.first()
                        nationActionCode = nt.actionCode
                        nationArg = nt.arg
                        consumedNationTurn = nt
                    } else if (general.npcState >= 2) {
                        val aiAction = nationAI.decideNationAction(
                            nation,
                            world,
                            DeterministicRng.create(
                                hiddenSeed,
                                "preprocess",
                                general.id,
                                world.currentYear,
                                world.currentMonth,
                            )
                        )
                        if (aiAction != "Nation휴식") {
                            nationActionCode = aiAction
                            if (aiAction == "선전포고") {
                                nationArg = readStringAnyMap(nation.meta.remove("aiWarTarget"))
                            }
                        }
                    }

                    if (nationActionCode != null && commandRegistry.hasNationCommand(nationActionCode)) {
                        try {
                            val nationCmdResult = runBlocking {
                                commandExecutor.executeNationCommand(
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
                                        nationId = if (general.nationId != 0L) general.nationId else null,
                                        year = env.year,
                                        month = env.month,
                                        logs = nationCmdResult.logs,
                                    )
                                } catch (_: Exception) { }
                            }
                        } catch (e: Exception) {
                            logger.warn("Nation command $nationActionCode failed for general ${general.id}: ${e.message}")
                        }
                    }

                    if (consumedNationTurn != null) {
                        nationTurnRepository.delete(consumedNationTurn)
                        nationTurnRepository.shiftTurnsDown(general.nationId, general.officerLevel, consumedNationTurn.turnIdx)
                        if (general.npcState < 2) didConsumeNationTurn = true
                    }
                }

                var hasReservedTurn = didConsumeNationTurn

                // General command
                val actionCode: String
                val arg: Map<String, Any>?
                val executedTurn: com.opensam.entity.GeneralTurn?

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
                    actionCode = generalAI.decideAndExecute(general, world)
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
                    val queuedTurns = generalTurnRepository.findByGeneralIdOrderByTurnIdx(general.id)
                    if (queuedTurns.isNotEmpty()) {
                        generalTurnRepository.deleteAll(queuedTurns)
                    }
                } else {
                    val generalTurns = generalTurnRepository.findByGeneralIdOrderByTurnIdx(general.id)
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

                val generalHiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
                val rng = DeterministicRng.create(
                    generalHiddenSeed, "generalCommand", general.id, world.currentYear, world.currentMonth, actionCode
                )
                val cmdResult = if (commandRegistry.hasNationCommand(actionCode) && general.officerLevel >= 5 && nation != null) {
                    runBlocking {
                        commandExecutor.executeNationCommand(actionCode, general, env, arg, city, nation, rng)
                    }
                } else {
                    runBlocking {
                        commandExecutor.executeGeneralCommand(actionCode, general, env, arg, city, nation, rng)
                    }
                }

                if (cmdResult.logs.isNotEmpty()) {
                    try {
                        commandLogDispatcher.dispatchLogs(
                            worldId = worldId,
                            generalId = general.id,
                            nationId = if (general.nationId != 0L) general.nationId else null,
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
                                ports.city(targetCityId)?.toEntity()
                            } else {
                                null
                            }
                            if (targetCity != null && targetCity.nationId != general.nationId) {
                                logger.info("[Turn] Battle triggered: {} ({}) attacks city {} (nation {})",
                                    general.id, general.name, targetCity.name, targetCity.nationId)
                                val battleResult = battleService.executeBattle(general, targetCity, world)
                                // Sync battle damage back to ports cache so subsequent
                                // attackers this turn see the reduced def/wall
                                ports.putCity(targetCity.toSnapshot())
                                ports.putGeneral(general.toSnapshot())
                                if (battleResult.cityOccupied) {
                                    general.cityId = targetCity.id
                                    ports.putGeneral(general.toSnapshot())
                                    logger.info("[Turn] City {} conquered by {} ({}) — general moved to conquered city",
                                        targetCity.name, general.id, general.name)
                                } else {
                                    logger.info("[Turn] Battle at {} — not conquered, {} ({}) stays at city {}",
                                        targetCity.name, general.id, general.name, general.cityId)
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

                if (executedTurn != null) {
                    generalTurnRepository.delete(executedTurn)
                    generalTurnRepository.shiftTurnsDown(general.id, executedTurn.turnIdx)
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
                        general.killTurn = configuredKillTurn.toShort()
                    } else {
                        val kt = general.killTurn!! - 1
                        if (kt <= 0) {
                            val previousNationId = general.nationId
                            generalMaintenanceService.killGeneral(general, world, generals)
                            generals.forEach { ports.putGeneral(it.toSnapshot()) }
                            if (previousNationId > 0L) {
                                nationRepository.findById(previousNationId).orElse(null)?.let {
                                    nationCache[previousNationId] = it
                                    ports.putNation(it.toSnapshot())
                                }
                            }
                            continue
                        } else {
                            general.killTurn = kt.toShort()
                        }
                    }
                }

                general.turnTime = calculateNextGeneralTurnTime(general, world.tickSeconds)
                general.updatedAt = OffsetDateTime.now()
                ports.putGeneral(general.toSnapshot())
            } catch (e: Exception) {
                logger.error("Error processing general ${general.id}: ${e.message}", e)
            }
        }
    }

    private fun buildCommandEnv(world: WorldState): CommandEnv {
        val startYear = try {
            scenarioService.getScenario(world.scenarioCode).startYear
        } catch (_: Exception) {
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
            killturn = killturn.toShort(),
        )
    }

    private fun accrueYearlyInheritancePoints(generals: List<com.opensam.entity.General>) {
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
    private fun resolveGlobalKillTurn(world: WorldState, env: CommandEnv?): Int {
        return (world.config["killturn"] as? Number)?.toInt()
            ?: (world.config["killTurn"] as? Number)?.toInt()
            ?: env?.killturn?.toInt()
            ?: calcDefaultKillTurn(world)
    }

    private fun calcDefaultKillTurn(world: WorldState): Int {
        val turnterm = (world.tickSeconds / 60).coerceAtLeast(1)
        val base = 4800 / turnterm
        val npcmode = (world.config["npcmode"] as? Number)?.toInt() ?: 0
        return if (npcmode == 1) base / 3 else base
    }

    private fun calculateNextGeneralTurnTime(general: com.opensam.entity.General, tickSeconds: Int): OffsetDateTime {
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
        world: WorldState,
        ports: com.opensam.engine.turn.cqrs.persist.WorldPorts,
        general: com.opensam.entity.General,
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
        } catch (_: Exception) {
            world.currentYear.toInt()
        }
        val scenarioId = world.scenarioCode.toIntOrNull() ?: 0
        val generalItems = generalItemSlotsOf(general)
        val allGeneralSlots = ports.allGenerals().map { generalItemSlotsOf(it.toEntity()) }
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
            userCount = ports.allGenerals().count { it.npcState.toInt() < 2 },
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

    private fun generalItemSlotsOf(general: com.opensam.entity.General): UniqueLotteryService.GeneralItemSlots {
        return UniqueLotteryService.GeneralItemSlots(
            horse = normalizeItemCode(general.horseCode),
            weapon = normalizeItemCode(general.weaponCode),
            book = normalizeItemCode(general.bookCode),
            item = normalizeItemCode(general.itemCode),
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

    private fun advanceMonth(world: WorldState) {
        val startYear = (world.config["startyear"] as? Number)?.toInt() ?: world.currentYear.toInt()
        val elapsedTurns = (world.currentYear.toInt() - startYear) * 12 + (world.currentMonth.toInt() - 1) + 1
        val totalMonths = startYear.toLong() * 12 + elapsedTurns
        world.currentYear = (totalMonths / 12).toShort()
        world.currentMonth = (1 + totalMonths % 12).toShort()
    }

    /**
     * Recalculate city supply state (traffic/supply routes) per turn.
     * Delegates to EconomyService which already has BFS-based supply logic.
     * Renamed from updateTraffic to clarify the actual operation.
     */
    private fun recalculateCitySupply(world: WorldState) {
        economyService.updateCitySupplyState(world)
    }

    /**
     * M-online: Update online status bookkeeping per tick.
     * Legacy: daemon.ts updateOnline() — updates online general count in world state.
     * TODO: implement per-tick online count update if needed beyond TrafficSnapshotStep(700).
     */
    private fun updateOnline(@Suppress("UNUSED_PARAMETER") world: WorldState) {
        // TODO: legacy updateOnline() per-tick online count snapshot
    }

    /**
     * M-online: Check overhead (resource/process overhead) per tick.
     * Legacy: daemon.ts CheckOverhead() — guards against runaway processes.
     * TODO: implement overhead guard if needed.
     */
    private fun checkOverhead(@Suppress("UNUSED_PARAMETER") world: WorldState) {
        // TODO: legacy CheckOverhead() per-tick overhead guard
    }

    /**
     * H7: Check wander nations for auto-dissolution.
     * Legacy: postUpdateMonthly — 재야 세력이 건국 후 startYear+2 이상 경과시 자동 해산.
     * TODO: implement full wander dissolution logic.
     */
    private fun checkWander(@Suppress("UNUSED_PARAMETER") world: WorldState) {
        // TODO: legacy checkWander — dissolve wander nations where world.year >= foundYear + 2
    }

    /**
     * H7: Trigger monthly tournament check.
     * Legacy func.php:triggerTournament 패러티:
     *   tournament==0 && tnmt_auto && 40% chance → tnmt_pattern 큐에서 타입 선택 후 startTournament
     */
    private fun triggerTournament(world: WorldState) {
        tournamentService.checkAndTriggerTournament(world)
    }

    /**
     * H7: Register monthly auction entries.
     * Legacy func_auction.php:registerAuction 패러티:
     *   - 비NPC 장수 평균 금/쌀 계산 (클램프 1000~20000)
     *   - 중립 buyRice 경매 수 기반 확률로 쌀 판매(시스템→구매자) 경매 등록
     *   - 중립 sellRice 경매 수 기반 확률로 쌀 구매(시스템←판매자) 경매 등록
     */
    private fun registerAuction(world: WorldState) {
        val worldId = world.id.toLong()
        val generals = generalRepository.findByWorldId(worldId)
        val humanGenerals = generals.filter { it.npcState.toInt() < 2 }

        val avgGold = if (humanGenerals.isEmpty()) 1000.0
            else humanGenerals.map { it.gold.toDouble() }.average()
        val avgRice = if (humanGenerals.isEmpty()) 1000.0
            else humanGenerals.map { it.rice.toDouble() }.average()

        val clampedGold = avgGold.coerceIn(1000.0, 20000.0)
        val clampedRice = avgRice.coerceIn(1000.0, 20000.0)

        val openAuctions = auctionService.listActiveAuctions(worldId)
            .filter { it.hostGeneralId == 0L }
        val neutralBuyRiceCnt = openAuctions.count { it.subType == "buyRice" }
        val neutralSellRiceCnt = openAuctions.count { it.subType == "sellRice" }

        val rng = java.util.Random()

        // 쌀 판매 경매 등록 (시스템이 쌀을 팔고 금을 받음 → buyRice subtype)
        if (rng.nextDouble() < 1.0 / (neutralBuyRiceCnt + 5)) {
            val mul = rng.nextInt(5) + 1
            val amount = (clampedRice / 20.0 * mul).toLong().coerceAtLeast(100).coerceAtMost(10000).toInt()
            val startBid = (clampedGold / 20.0 * 0.9 * mul)
                .coerceIn(amount * 0.8, amount * 1.2)
                .toLong().coerceAtLeast(100).toInt()
            val finishBid = (amount * 2.0).toLong().coerceAtLeast((startBid * 1.1).toLong()).toInt()
            val term = rng.nextInt(10) + 3
            auctionService.openSystemResourceAuction(worldId, "buyRice", amount, term, startBid, finishBid)
        }

        // 쌀 구매 경매 등록 (시스템이 쌀을 사고 금을 줌 → sellRice subtype)
        if (rng.nextDouble() < 1.0 / (neutralSellRiceCnt + 5)) {
            val mul = rng.nextInt(5) + 1
            val amount = (clampedGold / 20.0 * mul).toLong().coerceAtLeast(100).coerceAtMost(10000).toInt()
            val startBid = (clampedRice / 20.0 * 1.1 * mul)
                .coerceIn(amount * 0.8, amount * 1.2)
                .toLong().coerceAtLeast(100).toInt()
            val finishBid = (amount * 2.0).toLong().coerceAtLeast((startBid * 1.1).toLong()).toInt()
            val term = rng.nextInt(10) + 3
            auctionService.openSystemResourceAuction(worldId, "sellRice", amount, term, startBid, finishBid)
        }
    }

    /**
     * H7: Update nation general count and refresh static nation info.
     * Legacy: postUpdateMonthly — updateGeneralNumber / refreshNationStaticInfo.
     * Note: gennum is already updated in resetStrategicCommandLimits; this placeholder
     * covers any additional static info refresh needed.
     * TODO: implement refreshNationStaticInfo if additional fields need updating.
     */
    private fun updateGeneralNumber(@Suppress("UNUSED_PARAMETER") world: WorldState) {
        // TODO: legacy refreshNationStaticInfo — nationService.refreshNationGenCount(world)
    }

    /**
     * Decrement strategic command limits for all nations each turn.
     * Per legacy: strategicCmdLimit decreases by 1 each turn until 0.
     */
    private fun resetStrategicCommandLimits(world: WorldState) {
        val worldId = world.id.toLong()
        val ports = worldPortFactory.create(worldId)
        val generalSnapshots = ports.allGenerals()
        val generals = generalSnapshots.map { it.toEntity() }
        val nationSnapshots = ports.allNations()
        val nations = nationSnapshots.map { it.toEntity() }
        val citySnapshots = ports.allCities()
        val cities = citySnapshots.map { it.toEntity() }

        val originalGenerals = generalSnapshots.associateBy { it.id }
        val originalNations = nationSnapshots.associateBy { it.id }
        val originalCities = citySnapshots.associateBy { it.id }

        for (general in generals) {
            if (general.makeLimit > 0) {
                general.makeLimit = (general.makeLimit - 1).toShort()
            }
        }

        val activeGeneralCountByNation = generals
            .filter { !(it.npcState.toInt() == 5 && it.nationId == 0L) && it.nationId > 0 }
            .groupingBy { it.nationId }
            .eachCount()

        for (nation in nations) {
            if (nation.strategicCmdLimit > 0) {
                nation.strategicCmdLimit = (nation.strategicCmdLimit - 1).toShort()
            }
            if (nation.surrenderLimit > 0) {
                nation.surrenderLimit = (nation.surrenderLimit - 1).toShort()
            }
            nation.rateTmp = nation.rate
            nation.gennum = activeGeneralCountByNation[nation.id] ?: 0
            nation.spy = decaySpyDurations(nation.spy)
        }

        transitionCityStates(cities)
        updateDevelCost(world)
        decayRefreshScoreTotals(worldId)

        for (g in generals) {
            val snap = g.toSnapshot()
            if (snap != originalGenerals[g.id]) ports.putGeneral(snap)
        }
        for (c in cities) {
            val snap = c.toSnapshot()
            if (snap != originalCities[c.id]) ports.putCity(snap)
        }
        nations.forEach { ports.putNation(it.toSnapshot()) }
    }

    private fun firePreTurnTriggers(
        world: WorldState,
        general: com.opensam.entity.General,
        nation: Nation?,
        cityMates: List<com.opensam.entity.General> = emptyList(),
    ) {
        val modifiers = modifierService.getModifiers(general, nation)
        val triggers = buildPreTurnTriggers(general, modifiers, cityMates)
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

    private fun applyPerTurnCrewConsumption(general: com.opensam.entity.General, city: com.opensam.entity.City?) {
        if (general.crew <= 0) {
            return
        }

        val crewTypeCost = CrewType.fromCode(general.crewType.toInt())?.cost ?: 0
        val baseLoss = kotlin.math.ceil(general.crew.toDouble() * crewTypeCost / 1000.0).toInt().coerceAtLeast(1)
        val unsupplied = city?.supplyState?.toInt() == 0
        val crewLoss = if (unsupplied) baseLoss * 2 else baseLoss
        general.crew = (general.crew - crewLoss).coerceAtLeast(0)

        if (unsupplied) {
            val atmosDrop = minOf(5, general.atmos.toInt())
            general.atmos = (general.atmos - atmosDrop).toShort()
        }
    }

    private fun transitionCityStates(cities: List<City>) {
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
            }.toShort()

            val nextTerm = (city.term.toInt() - 1).coerceAtLeast(0)
            city.term = nextTerm.toShort()
            if (nextTerm == 0) {
                city.conflict = mutableMapOf()
            }
        }
    }

    private fun updateDevelCost(world: WorldState) {
        val startYear = (world.config["startyear"] as? Number)?.toInt() ?: world.currentYear.toInt()
        val develCost = (world.currentYear.toInt() - startYear + 10) * 2
        world.config["develCost"] = develCost
        world.config["develcost"] = develCost
    }

    private fun decayRefreshScoreTotals(worldId: Long) {
        val accessLogs = generalAccessLogRepository.findByWorldId(worldId)
        if (accessLogs.isEmpty()) {
            return
        }

        for (accessLog in accessLogs) {
            accessLog.refreshScoreTotal = kotlin.math.floor(accessLog.refreshScoreTotal * 0.99).toInt()
        }
        generalAccessLogRepository.saveAll(accessLogs)
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
