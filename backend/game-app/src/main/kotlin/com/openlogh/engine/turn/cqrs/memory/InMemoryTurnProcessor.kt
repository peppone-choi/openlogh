package com.openlogh.engine.turn.cqrs.memory

import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.EconomyService
import com.openlogh.engine.EventService
import com.openlogh.engine.OfficerMaintenanceService
import com.openlogh.engine.NpcSpawnService
import com.openlogh.engine.SpecialAssignmentService
import com.openlogh.engine.UnificationService
import com.openlogh.engine.turn.cqrs.TurnDomainEvent
import com.openlogh.engine.turn.cqrs.TurnResult
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.entity.SessionState
import com.openlogh.repository.TrafficSnapshotRepository
import com.openlogh.service.AuctionService
import com.openlogh.service.InheritanceService
import com.openlogh.service.FactionService
import com.openlogh.service.TournamentService
import com.openlogh.service.WorldService
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
    private val officerMaintenanceService: OfficerMaintenanceService,
    private val specialAssignmentService: SpecialAssignmentService,
    private val npcSpawnService: NpcSpawnService,
    private val unificationService: UnificationService,
    private val inheritanceService: InheritanceService,
    private val yearbookService: com.openlogh.engine.YearbookService,
    private val auctionService: AuctionService,
    private val tournamentService: TournamentService,
    private val trafficSnapshotRepository: TrafficSnapshotRepository,
    private val worldService: WorldService,
    private val factionService: FactionService,
) {
    private val logger = LoggerFactory.getLogger(InMemoryTurnProcessor::class.java)

    fun process(session: SessionState, state: InMemoryWorldState, ports: InMemoryWorldPorts): TurnResult {
        val now = OffsetDateTime.now()
        val tickDuration = Duration.ofSeconds(session.tickSeconds.toLong())
        var nextTurnAt = session.updatedAt.plus(tickDuration)
        val sessionId = session.id.toLong()
        var advancedTurns = 0
        val events = mutableListOf<TurnDomainEvent>()

        while (!now.isBefore(nextTurnAt) && advancedTurns < MAX_TURNS_PER_TICK) {
            val previousYear = session.currentYear.toInt()
            val previousMonth = session.currentMonth.toInt()

            executeOfficerCommandsUntil(state, ports, session, nextTurnAt)

            try {
                eventService.dispatchEvents(session, "PRE_MONTH")
            } catch (e: Exception) {
                logger.warn("EventService.dispatchEvents(PRE_MONTH) failed: ${e.message}")
            }

            try {
                economyService.preUpdateMonthly(session)
            } catch (e: Exception) {
                logger.warn("EconomyService.preUpdateMonthly failed: ${e.message}")
            }

            advanceMonth(session)

            try {
                yearbookService.saveMonthlySnapshot(sessionId, previousYear, previousMonth)
            } catch (e: Exception) {
                logger.warn("YearbookService.saveMonthlySnapshot failed: ${e.message}")
            }

            try {
                worldService.captureSnapshot(session)
            } catch (e: Exception) {
                logger.warn("WorldService.captureSnapshot failed: ${e.message}")
            }

            try {
                val onlineCount = ports.allOfficers().count { it.userId != null }
                val snapshot = com.openlogh.entity.TrafficSnapshot(
                    sessionId = sessionId,
                    year = session.currentYear,
                    month = session.currentMonth,
                    refresh = (session.meta["refresh"] as? Number)?.toInt() ?: 0,
                    online = onlineCount,
                )
                trafficSnapshotRepository.save(snapshot)
                session.meta["refresh"] = 0
            } catch (e: Exception) {
                logger.warn("TrafficSnapshot recording failed: ${e.message}")
            }

            if (session.currentMonth.toInt() == 1) {
                try {
                    economyService.processYearlyStatistics(session)
                } catch (e: Exception) {
                    logger.warn("EconomyService.processYearlyStatistics failed: ${e.message}")
                }
            }

            try {
                eventService.dispatchEvents(session, "MONTH")
            } catch (e: Exception) {
                logger.warn("EventService.dispatchEvents failed: ${e.message}")
            }

            try {
                economyService.postUpdateMonthly(session)
            } catch (e: Exception) {
                logger.warn("EconomyService.postUpdateMonthly failed: ${e.message}")
            }

            try {
                economyService.processDisasterOrBoom(session)
            } catch (e: Exception) {
                logger.warn("EconomyService.processDisasterOrBoom failed: ${e.message}")
            }

            try {
                economyService.randomizeCityTradeRate(session)
            } catch (e: Exception) {
                logger.warn("EconomyService.randomizeCityTradeRate failed: ${e.message}")
            }

            try {
                diplomacyService.processDiplomacyTurn(session)
            } catch (e: Exception) {
                logger.warn("DiplomacyService.processDiplomacyTurn failed: ${e.message}")
            }

            try {
                factionService.recalcAllFronts(sessionId)
            } catch (e: Exception) {
                logger.warn("FactionService.recalcAllFronts failed: ${e.message}")
            }

            try {
                resetStrategicCommandLimits(ports)
            } catch (e: Exception) {
                logger.warn("resetStrategicCommandLimits failed: ${e.message}")
            }

            try {
                val officers = ports.allOfficers().map { it.toEntity() }
                officerMaintenanceService.processGeneralMaintenance(session, officers)
                specialAssignmentService.checkAndAssignSpecials(session, officers)
                officers.forEach { ports.putOfficer(it.toSnapshot()) }

                for (officer in officers.filter { it.npcState.toInt() == 0 }) {
                    inheritanceService.accruePoints(officer, "lived_month", 1)
                }
            } catch (e: Exception) {
                logger.warn("OfficerMaintenanceService failed: ${e.message}")
            }

            try {
                unificationService.checkAndSettleUnification(session)
            } catch (e: Exception) {
                logger.warn("UnificationService.checkAndSettleUnification failed: ${e.message}")
            }

            session.updatedAt = nextTurnAt
            nextTurnAt = nextTurnAt.plus(tickDuration)
            advancedTurns += 1

            events += TurnDomainEvent(
                type = EVENT_TURN_ADVANCED,
                payload = mapOf(
                    "worldId" to session.id.toLong(),
                    "year" to session.currentYear.toInt(),
                    "month" to session.currentMonth.toInt(),
                ),
            )
        }

        try {
            tournamentService.processTournamentTurn(sessionId)
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

    private fun executeOfficerCommandsUntil(
        state: InMemoryWorldState,
        ports: InMemoryWorldPorts,
        session: SessionState,
        targetTime: OffsetDateTime,
    ) {
        val now = OffsetDateTime.now()
        val officers = state.officers.values.sortedWith(
            compareBy<OfficerSnapshot> { it.turnTime }.thenBy { it.id }
        )

        for (officer in officers) {
            if (officer.turnTime >= targetTime) {
                break
            }

            if (officer.npcState == com.openlogh.engine.SovereignConstants.NPC_STATE_EMPEROR) {
                officer.turnTime = calculateNextOfficerTurnTime(officer.turnTime, officer.meta, session.tickSeconds)
                officer.updatedAt = now
                ports.putOfficer(officer)
                continue
            }

            if (officer.blockState >= 2) {
                val killTurn = officer.killTurn
                if (killTurn != null) {
                    val nextKillTurn = killTurn - 1
                    if (nextKillTurn <= 0) {
                        val deadOfficer = officer.copy(userId = null)
                        deadOfficer.npcState = 5
                        deadOfficer.factionId = 0
                        deadOfficer.officerLevel = 0
                        deadOfficer.officerPlanet = 0
                        deadOfficer.killTurn = null
                        deadOfficer.updatedAt = now
                        ports.putOfficer(deadOfficer)
                        continue
                    } else {
                        officer.killTurn = nextKillTurn.coerceIn(-32768, 32767).toShort()
                    }
                }
                officer.turnTime = calculateNextOfficerTurnTime(officer.turnTime, officer.meta, session.tickSeconds)
                officer.updatedAt = now
                ports.putOfficer(officer)
                continue
            }

            if (officer.factionId > 0) {
                val factionKey = FactionTurnKey(officer.factionId, officer.officerLevel)
                val factionQueue = state.factionTurnsByFactionAndLevel[factionKey]
                if (!factionQueue.isNullOrEmpty()) {
                    factionQueue.removeAt(0)
                    if (factionQueue.isEmpty()) {
                        state.factionTurnsByFactionAndLevel.remove(factionKey)
                    }
                }
            }

            val actionCode: String
            val arg: MutableMap<String, Any>
            if (officer.npcState >= 2) {
                actionCode = "휴식"
                arg = mutableMapOf()
                state.officerTurnsByOfficerId.remove(officer.id)
            } else {
                val queue = state.officerTurnsByOfficerId[officer.id]
                if (queue.isNullOrEmpty()) {
                    actionCode = "휴식"
                    arg = mutableMapOf()
                } else {
                    val turn = queue.removeAt(0)
                    actionCode = turn.actionCode
                    arg = turn.arg
                    if (queue.isEmpty()) {
                        state.officerTurnsByOfficerId.remove(officer.id)
                    }
                }
            }

            officer.lastTurn = mutableMapOf(
                "actionCode" to actionCode,
                "arg" to arg,
                "queuedInMemory" to true,
            )
            officer.turnTime = calculateNextOfficerTurnTime(officer.turnTime, officer.meta, session.tickSeconds)
            officer.updatedAt = now
            ports.putOfficer(officer)
        }

        logger.debug(
            "Processed in-memory command queues (officer queues={}, faction queues={})",
            state.officerTurnsByOfficerId.size,
            state.factionTurnsByFactionAndLevel.size,
        )
    }

    private fun resetStrategicCommandLimits(ports: InMemoryWorldPorts) {
        ports.allFactions().forEach { faction ->
            if (faction.strategicCmdLimit > 0) {
                faction.strategicCmdLimit = (faction.strategicCmdLimit - 1).coerceIn(0, 72).toShort()
            }
            ports.putFaction(faction)
        }
    }

    private fun updateTraffic(session: SessionState) {
        economyService.updateCitySupplyState(session)
    }

    private fun advanceMonth(session: SessionState) {
        val nextMonth = session.currentMonth + 1
        if (nextMonth > 12) {
            session.currentMonth = 1
            session.currentYear = (session.currentYear + 1).coerceIn(0, 32767).toShort()
        } else {
            session.currentMonth = nextMonth.coerceIn(1, 12).toShort()
        }
    }

    companion object {
        const val EVENT_TURN_ADVANCED = "TURN_ADVANCED"
        private const val MAX_TURNS_PER_TICK = 5
    }

    private fun calculateNextOfficerTurnTime(
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
