package com.opensam.engine.turn.cqrs.memory

import com.opensam.engine.DiplomacyService
import com.opensam.engine.EconomyService
import com.opensam.engine.EventService
import com.opensam.engine.GeneralMaintenanceService
import com.opensam.engine.NpcSpawnService
import com.opensam.engine.SpecialAssignmentService
import com.opensam.engine.UnificationService
import com.opensam.engine.turn.cqrs.TurnDomainEvent
import com.opensam.engine.turn.cqrs.TurnResult
import com.opensam.engine.turn.cqrs.persist.toEntity
import com.opensam.engine.turn.cqrs.persist.toSnapshot
import com.opensam.entity.WorldState
import com.opensam.repository.TrafficSnapshotRepository
import com.opensam.service.AuctionService
import com.opensam.service.InheritanceService
import com.opensam.service.NationService
import com.opensam.service.TournamentService
import com.opensam.service.WorldService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToLong

@Service
class InMemoryTurnProcessor(
    private val economyService: EconomyService,
    private val eventService: EventService,
    private val diplomacyService: DiplomacyService,
    private val generalMaintenanceService: GeneralMaintenanceService,
    private val specialAssignmentService: SpecialAssignmentService,
    private val npcSpawnService: NpcSpawnService,
    private val unificationService: UnificationService,
    private val inheritanceService: InheritanceService,
    private val yearbookService: com.opensam.engine.YearbookService,
    private val auctionService: AuctionService,
    private val tournamentService: TournamentService,
    private val trafficSnapshotRepository: TrafficSnapshotRepository,
    private val worldService: WorldService,
    private val nationService: NationService,
) {
    private val logger = LoggerFactory.getLogger(InMemoryTurnProcessor::class.java)

    fun process(world: WorldState, state: InMemoryWorldState, ports: InMemoryWorldPorts): TurnResult {
        val now = OffsetDateTime.now()
        val tickDuration = Duration.ofSeconds(world.tickSeconds.toLong())
        var nextTurnAt = world.updatedAt.plus(tickDuration)
        val worldId = world.id.toLong()
        var advancedTurns = 0
        val events = mutableListOf<TurnDomainEvent>()

        while (!now.isBefore(nextTurnAt) && advancedTurns < MAX_TURNS_PER_TICK) {
            val previousYear = world.currentYear.toInt()
            val previousMonth = world.currentMonth.toInt()

            executeGeneralCommandsUntil(state, ports, world, nextTurnAt)

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

            try {
                yearbookService.saveMonthlySnapshot(worldId, previousYear, previousMonth)
            } catch (e: Exception) {
                logger.warn("YearbookService.saveMonthlySnapshot failed: ${e.message}")
            }

            try {
                worldService.captureSnapshot(world)
            } catch (e: Exception) {
                logger.warn("WorldService.captureSnapshot failed: ${e.message}")
            }

            try {
                val onlineCount = ports.allGenerals().count { it.userId != null }
                val snapshot = com.opensam.entity.TrafficSnapshot(
                    worldId = worldId,
                    year = world.currentYear,
                    month = world.currentMonth,
                    refresh = (world.meta["refresh"] as? Number)?.toInt() ?: 0,
                    online = onlineCount,
                )
                trafficSnapshotRepository.save(snapshot)
                world.meta["refresh"] = 0
            } catch (e: Exception) {
                logger.warn("TrafficSnapshot recording failed: ${e.message}")
            }

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

            try {
                nationService.recalcAllFronts(worldId)
            } catch (e: Exception) {
                logger.warn("NationService.recalcAllFronts failed: ${e.message}")
            }

            try {
                resetStrategicCommandLimits(ports)
            } catch (e: Exception) {
                logger.warn("resetStrategicCommandLimits failed: ${e.message}")
            }

            try {
                val generals = ports.allGenerals().map { it.toEntity() }
                generalMaintenanceService.processGeneralMaintenance(world, generals)
                specialAssignmentService.checkAndAssignSpecials(world, generals)
                generals.forEach { ports.putGeneral(it.toSnapshot()) }

                for (general in generals.filter { it.npcState.toInt() == 0 }) {
                    inheritanceService.accruePoints(general, "lived_month", 1)
                }
            } catch (e: Exception) {
                logger.warn("GeneralMaintenanceService failed: ${e.message}")
            }

            try {
                unificationService.checkAndSettleUnification(world)
            } catch (e: Exception) {
                logger.warn("UnificationService.checkAndSettleUnification failed: ${e.message}")
            }

            world.updatedAt = nextTurnAt
            nextTurnAt = nextTurnAt.plus(tickDuration)
            advancedTurns += 1

            events += TurnDomainEvent(
                type = EVENT_TURN_ADVANCED,
                payload = mapOf(
                    "worldId" to world.id.toLong(),
                    "year" to world.currentYear.toInt(),
                    "month" to world.currentMonth.toInt(),
                ),
            )
        }

        try {
            tournamentService.processTournamentTurn(worldId)
        } catch (e: Exception) {
            logger.warn("TournamentService.processTournamentTurn failed: ${e.message}")
        }

        try {
            auctionService.processExpiredAuctions()
        } catch (e: Exception) {
            logger.warn("AuctionService.processExpiredAuctions failed: ${e.message}")
        }

        return TurnResult(
            advancedTurns = advancedTurns,
            events = events,
        )
    }

    private fun executeGeneralCommandsUntil(
        state: InMemoryWorldState,
        ports: InMemoryWorldPorts,
        world: WorldState,
        targetTime: OffsetDateTime,
    ) {
        val now = OffsetDateTime.now()
        val generals = state.generals.values.sortedBy { it.turnTime }

        for (general in generals) {
            if (general.turnTime >= targetTime) {
                break
            }

            if (general.npcState == com.opensam.engine.EmperorConstants.NPC_STATE_EMPEROR) {
                general.turnTime = calculateNextGeneralTurnTime(general.turnTime, general.meta, world.tickSeconds)
                general.updatedAt = now
                ports.putGeneral(general)
                continue
            }

            if (general.blockState >= 2) {
                val killTurn = general.killTurn
                if (killTurn != null) {
                    val nextKillTurn = killTurn - 1
                    if (nextKillTurn <= 0) {
                        val deadGeneral = general.copy(userId = null)
                        deadGeneral.npcState = 5
                        deadGeneral.nationId = 0
                        deadGeneral.officerLevel = 0
                        deadGeneral.officerCity = 0
                        deadGeneral.killTurn = null
                        deadGeneral.updatedAt = now
                        ports.putGeneral(deadGeneral)
                        continue
                    } else {
                        general.killTurn = nextKillTurn.toShort()
                    }
                }
                general.turnTime = calculateNextGeneralTurnTime(general.turnTime, general.meta, world.tickSeconds)
                general.updatedAt = now
                ports.putGeneral(general)
                continue
            }

            if (general.officerLevel >= 5 && general.nationId > 0) {
                val nationKey = NationTurnKey(general.nationId, general.officerLevel)
                val nationQueue = state.nationTurnsByNationAndLevel[nationKey]
                if (!nationQueue.isNullOrEmpty()) {
                    nationQueue.removeAt(0)
                    if (nationQueue.isEmpty()) {
                        state.nationTurnsByNationAndLevel.remove(nationKey)
                    }
                }
            }

            val actionCode: String
            val arg: MutableMap<String, Any>
            if (general.npcState >= 2) {
                actionCode = "휴식"
                arg = mutableMapOf()
                state.generalTurnsByGeneralId.remove(general.id)
            } else {
                val queue = state.generalTurnsByGeneralId[general.id]
                if (queue.isNullOrEmpty()) {
                    actionCode = "휴식"
                    arg = mutableMapOf()
                } else {
                    val turn = queue.removeAt(0)
                    actionCode = turn.actionCode
                    arg = turn.arg
                    if (queue.isEmpty()) {
                        state.generalTurnsByGeneralId.remove(general.id)
                    }
                }
            }

            general.lastTurn = mutableMapOf(
                "actionCode" to actionCode,
                "arg" to arg,
                "queuedInMemory" to true,
            )
            general.turnTime = calculateNextGeneralTurnTime(general.turnTime, general.meta, world.tickSeconds)
            general.updatedAt = now
            ports.putGeneral(general)
        }

        logger.debug(
            "Processed in-memory command queues (general queues={}, nation queues={})",
            state.generalTurnsByGeneralId.size,
            state.nationTurnsByNationAndLevel.size,
        )
    }

    private fun resetStrategicCommandLimits(ports: InMemoryWorldPorts) {
        ports.allNations().forEach { nation ->
            if (nation.strategicCmdLimit > 0) {
                nation.strategicCmdLimit = (nation.strategicCmdLimit - 1).toShort()
            }
            ports.putNation(nation)
        }
    }

    private fun updateTraffic(world: WorldState) {
        economyService.updateCitySupplyState(world)
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

    companion object {
        const val EVENT_TURN_ADVANCED = "TURN_ADVANCED"
        private const val MAX_TURNS_PER_TICK = 5
    }

    private fun calculateNextGeneralTurnTime(
        currentTurnTime: OffsetDateTime,
        meta: MutableMap<String, Any>,
        tickSeconds: Int,
    ): OffsetDateTime {
        val defaultNext = currentTurnTime.plusSeconds(tickSeconds.toLong())
        val nextTurnTimeBase = readDouble(meta["nextTurnTimeBase"])
        if (nextTurnTimeBase == null) {
            return defaultNext
        }

        meta.remove("nextTurnTimeBase")
        val turnBoundary = cutTurn(defaultNext, tickSeconds)
        return turnBoundary.plusNanos((nextTurnTimeBase * 1_000_000_000L).roundToLong())
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
}
