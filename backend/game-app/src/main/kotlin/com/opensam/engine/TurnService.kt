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
import com.opensam.entity.Nation
import com.opensam.entity.WorldState
import com.opensam.repository.*
import com.opensam.service.AuctionService
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
) {
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
    )

    private val logger = LoggerFactory.getLogger(TurnService::class.java)
    private companion object {
        const val MAX_TURNS_PER_TICK = 5
    }

    @Transactional
    fun processWorld(world: WorldState) {
        val now = OffsetDateTime.now()
        val tickDuration = Duration.ofSeconds(world.tickSeconds.toLong())
        var nextTurnAt = world.updatedAt.plus(tickDuration)
        val worldId = world.id.toLong()

        var turnsProcessed = 0
        while (!now.isBefore(nextTurnAt) && turnsProcessed < MAX_TURNS_PER_TICK) {
            turnsProcessed++
            // 진행 전 이전 월 기록 (연감 스냅샷용)
            val previousYear = world.currentYear.toInt()
            val previousMonth = world.currentMonth.toInt()

            executeGeneralCommandsUntil(world, nextTurnAt)

            try {
                eventService.dispatchEvents(world, "PRE_MONTH")
            } catch (e: Exception) {
                logger.warn("EventService.dispatchEvents(PRE_MONTH) failed: ${e.message}")
            }

            try {
                economyService.preUpdateMonthly(world)
            } catch (e: Exception) {
                logger.warn("EconomyService.preUpdateMonthly failed: ${e.message}")
            }

            advanceMonth(world)

            // 연감 스냅샷: 매월 변경 시 이전 월의 맵/국가 상태를 기록
            // core2026 yearbookHandler.onMonthChanged 패러티
            try {
                yearbookService.saveMonthlySnapshot(worldId, previousYear, previousMonth)
            } catch (e: Exception) {
                logger.warn("YearbookService.saveMonthlySnapshot failed: ${e.message}")
            }

            // 월드 스냅샷 기록: 히스토리 맵 재현에 사용 (world_history.event_type=snapshot)
            try {
                worldService.captureSnapshot(world)
            } catch (e: Exception) {
                logger.warn("WorldService.captureSnapshot failed: ${e.message}")
            }

            // 트래픽 스냅샷 기록 (legacy recentTraffic 패러티)
            try {
                val onlineCount = worldPortFactory.create(worldId).allGenerals().count { it.userId != null }
                val snapshot = com.opensam.entity.TrafficSnapshot(
                    worldId = worldId,
                    year = world.currentYear,
                    month = world.currentMonth,
                    refresh = (world.meta["refresh"] as? Number)?.toInt() ?: 0,
                    online = onlineCount,
                )
                trafficSnapshotRepository.save(snapshot)
                // Reset per-turn refresh counter
                world.meta["refresh"] = 0
            } catch (e: Exception) {
                logger.warn("TrafficSnapshot recording failed: ${e.message}")
            }

            // 1월: 연초 통계 (legacy checkStatistic 패러티)
            if (world.currentMonth.toInt() == 1) {
                try {
                    economyService.processYearlyStatistics(world)
                } catch (e: Exception) {
                    logger.warn("EconomyService.processYearlyStatistics failed: ${e.message}")
                }
            }

            try {
                eventService.dispatchEvents(world, "MONTH")
            } catch (e: Exception) {
                logger.warn("EventService.dispatchEvents failed: ${e.message}")
            }

            try {
                economyService.postUpdateMonthly(world)
            } catch (e: Exception) {
                logger.warn("EconomyService.postUpdateMonthly failed: ${e.message}")
            }

            try {
                economyService.processDisasterOrBoom(world)
            } catch (e: Exception) {
                logger.warn("EconomyService.processDisasterOrBoom failed: ${e.message}")
            }

            try {
                economyService.randomizeCityTradeRate(world)
            } catch (e: Exception) {
                logger.warn("EconomyService.randomizeCityTradeRate failed: ${e.message}")
            }

            try {
                diplomacyService.processDiplomacyTurn(world)
            } catch (e: Exception) {
                logger.warn("DiplomacyService.processDiplomacyTurn failed: ${e.message}")
            }

            // Recalculate war front status for all nations (legacy SetNationFront parity)
            try {
                nationService.recalcAllFronts(worldId)
            } catch (e: Exception) {
                logger.warn("NationService.recalcAllFronts failed: ${e.message}")
            }

            try {
                resetStrategicCommandLimits(world)
            } catch (e: Exception) {
                logger.warn("resetStrategicCommandLimits failed: ${e.message}")
            }

            try {
                val ports = worldPortFactory.create(worldId)
                val generals = ports.allGenerals().map { it.toEntity() }
                generalMaintenanceService.processGeneralMaintenance(world, generals)
                specialAssignmentService.checkAndAssignSpecials(world, generals)
                generals.forEach { ports.putGeneral(it.toSnapshot()) }

                // Accrue inheritance points for player generals
                for (general in generals.filter { it.npcState.toInt() == 0 }) {
                    inheritanceService.accruePoints(general, "lived_month", 1)
                }
            } catch (e: Exception) {
                logger.warn("GeneralMaintenanceService failed: ${e.message}")
            }

            try {
                npcSpawnService.checkNpcSpawn(world)
            } catch (e: Exception) {
                logger.warn("NpcSpawnService.checkNpcSpawn failed: ${e.message}")
            }

            try {
                unificationService.checkAndSettleUnification(world)
            } catch (e: Exception) {
                logger.warn("UnificationService.checkAndSettleUnification failed: ${e.message}")
            }

            world.updatedAt = nextTurnAt
            nextTurnAt = nextTurnAt.plus(tickDuration)
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
    }

    private fun executeGeneralCommandsUntil(world: WorldState, targetTime: OffsetDateTime) {
        val worldId = world.id.toLong()
        val ports = worldPortFactory.create(worldId)
        val generals = ports.allGenerals().map { it.toEntity() }.sortedBy { it.turnTime }
        val env = buildCommandEnv(world)

        logger.info("[Turn] executeGeneralCommands: {} generals for world {}", generals.size, worldId)

        for (general in generals) {
            if (general.turnTime >= targetTime) {
                break
            }
            try {
                val city = ports.city(general.cityId)?.toEntity()
                val nation = if (general.nationId != 0L) {
                    ports.nation(general.nationId)?.toEntity()
                } else null

                firePreTurnTriggers(world, general, nation)

                if (general.blockState >= 2) {
                    if (general.killTurn != null) {
                        val kt = general.killTurn!! - 1
                        if (kt <= 0) {
                            general.npcState = 5
                            general.nationId = 0
                            general.killTurn = null
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
                        }
                    }

                    if (nationActionCode != null && commandRegistry.hasNationCommand(nationActionCode)) {
                        try {
                            runBlocking {
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
                        } catch (e: Exception) {
                            logger.warn("Nation command $nationActionCode failed for general ${general.id}: ${e.message}")
                        }
                    }

                    if (consumedNationTurn != null) {
                        nationTurnRepository.delete(consumedNationTurn)
                        nationTurnRepository.shiftTurnsDown(general.nationId, general.officerLevel, consumedNationTurn.turnIdx)
                    }
                }

                // General command
                val actionCode: String
                val arg: Map<String, Any>?
                val executedTurn: com.opensam.entity.GeneralTurn?
                var hasReservedTurn = false

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
                    arg = readStringAnyMap(general.meta.remove("aiArg"))
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

                // Handle battleTriggered: invoke BattleService for war resolution
                // Legacy: after 출병 run(), StaticEventHandler calls ConquerCity/warProcess
                if (cmdResult.success && cmdResult.message != null) {
                    try {
                        val msgJson = jacksonObjectMapper().readTree(cmdResult.message!!)
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

                // KillTurn handling
                if (general.killTurn != null) {
                    if (actionCode != "휴식" && general.npcState.toInt() == 0) {
                        general.killTurn = null
                    } else {
                        val kt = general.killTurn!! - 1
                        if (kt <= 0) {
                            general.npcState = 5
                            general.nationId = 0
                            general.killTurn = null
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
        val gameStor = mutableMapOf<String, Any>("mapName" to mapCode)

        return CommandEnv(
            year = world.currentYear.toInt(),
            month = world.currentMonth.toInt(),
            startYear = startYear,
            worldId = world.id.toLong(),
            realtimeMode = world.realtimeMode,
            gameStor = gameStor,
        )
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

    private fun advanceMonth(world: WorldState) {
        val nextMonth = world.currentMonth + 1
        if (nextMonth > 12) {
            world.currentMonth = 1
            world.currentYear = (world.currentYear + 1).toShort()
        } else {
            world.currentMonth = nextMonth.toShort()
        }
    }

    /**
     * Recalculate city supply state (traffic/supply routes) per turn.
     * Delegates to EconomyService which already has BFS-based supply logic.
     */
    private fun updateTraffic(world: WorldState) {
        economyService.updateCitySupplyState(world)
    }

    /**
     * Decrement strategic command limits for all nations each turn.
     * Per legacy: strategicCmdLimit decreases by 1 each turn until 0.
     */
    private fun resetStrategicCommandLimits(world: WorldState) {
        val ports = worldPortFactory.create(world.id.toLong())
        val nations = ports.allNations().map { it.toEntity() }
        for (nation in nations) {
            if (nation.strategicCmdLimit > 0) {
                nation.strategicCmdLimit = (nation.strategicCmdLimit - 1).toShort()
            }
        }
        nations.forEach { ports.putNation(it.toSnapshot()) }
    }

    private fun firePreTurnTriggers(world: WorldState, general: com.opensam.entity.General, nation: Nation?) {
        val modifiers = modifierService.getModifiers(general, nation)
        val triggers = buildPreTurnTriggers(general, modifiers)
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
