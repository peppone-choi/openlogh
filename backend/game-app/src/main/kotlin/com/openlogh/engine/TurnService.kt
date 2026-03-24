package com.openlogh.engine

import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandRegistry
import com.openlogh.command.CommandServices
import com.openlogh.engine.ai.OfficerAI
import com.openlogh.engine.ai.FactionAI
import com.openlogh.engine.modifier.ModifierService
import com.openlogh.engine.modifier.OfficerLevelModifier
import com.openlogh.engine.FezzanNeutralityService
import com.openlogh.engine.SafeZoneService
import com.openlogh.engine.CoupExecutionService
import com.openlogh.engine.fleet.TransportExecutionService
import com.openlogh.engine.planet.PlanetProductionService
import com.openlogh.engine.war.BattleService
import com.openlogh.entity.SessionState
import com.openlogh.repository.*
import com.openlogh.service.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class TurnService(
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
    private val trafficSnapshotRepository: TrafficSnapshotRepository,
    private val officerAI: OfficerAI,
    private val factionAI: FactionAI,
    private val modifierService: ModifierService,
    private val worldService: WorldService,
    private val factionService: FactionService,
    private val battleService: BattleService,
    private val uniqueLotteryService: UniqueLotteryService,
    private val commandLogDispatcher: CommandLogDispatcher,
    private val gameConstService: GameConstService,
    private val officerAccessLogRepository: OfficerAccessLogRepository,
    private val commandPointService: CommandPointService,
    private val ageGrowthService: AgeGrowthService,
    private val officerLevelModifier: OfficerLevelModifier,
    private val victoryService: com.openlogh.service.VictoryService,
    private val rankLadderService: com.openlogh.service.RankLadderService,
    private val safeZoneService: SafeZoneService,
    private val planetProductionService: PlanetProductionService,
    private val fezzanNeutralityService: FezzanNeutralityService,
    private val transportExecutionService: TransportExecutionService,
    private val coupExecutionService: CoupExecutionService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TurnService::class.java)
        private const val GLOBAL_KILL_TURN_BASE = 4800
    }

    fun processWorld(world: SessionState) {
        val now = OffsetDateTime.now()
        val sessionId = world.id.toLong()

        // Check if any tick has elapsed
        if (world.updatedAt.plusSeconds(world.tickSeconds.toLong()).isAfter(now)) {
            return
        }

        // One-time killTurn reset on first open after pre-open
        resetKillTurnIfNeeded(world, sessionId)

        // Process each elapsed tick
        while (world.updatedAt.plusSeconds(world.tickSeconds.toLong()) <= now) {
            // Advance month
            world.currentMonth = (world.currentMonth + 1).toShort()
            if (world.currentMonth > 12) {
                world.currentMonth = 1
                world.currentYear = (world.currentYear + 1).toShort()
            }

            // Advance updatedAt by tick duration
            world.updatedAt = world.updatedAt.plusSeconds(world.tickSeconds.toLong())

            // Load session data
            val officers = officerRepository.findBySessionId(sessionId)

            // Monthly sub-services (resilient - continue on failure)
            tryRun("economyService.preUpdateMonthly") { economyService.preUpdateMonthly(world) }
            tryRun("economyService.postUpdateMonthly") { economyService.postUpdateMonthly(world) }
            tryRun("economyService.processDisasterOrBoom") { economyService.processDisasterOrBoom(world) }
            tryRun("economyService.randomizeCityTradeRate") { economyService.randomizeCityTradeRate(world) }
            tryRun("diplomacyService.processDiplomacyTurn") { diplomacyService.processDiplomacyTurn(world) }
            tryRun("eventService.dispatchEvents") { eventService.dispatchEvents(world, "monthly") }
            tryRun("officerMaintenanceService") { officerMaintenanceService.processOfficerMaintenance(world, officers) }
            tryRun("commandPointService.recoverAllCp") { commandPointService.recoverAllCp(sessionId) }
            tryRun("ageGrowthService.processMonthlyGrowth") { ageGrowthService.processMonthlyGrowth(world) }
            tryRun("planetProductionService.processMonthlyProduction") { planetProductionService.processMonthlyProduction(world) }
            tryRun("fezzanNeutralityService.processPenaltyDecay") { fezzanNeutralityService.processPenaltyDecay(sessionId, world.currentYear.toInt(), world.currentMonth.toInt()) }
            tryRun("transportExecutionService.processTransports") { transportExecutionService.processTransports(sessionId) }
            tryRun("coupExecution") { processCoupAttempts(world, sessionId, officers) }
            tryRun("officerLevelModifier.applyMonthlyModifiers") { officerLevelModifier.applyMonthlyModifiers(officers, world) }
            tryRun("tournamentService.processTournament") { tournamentService.processTournament(world) }
            tryRun("auctionService.processAuctions") { auctionService.processAuctions(world) }
            tryRun("doctrineAndPromotion") { processDoctrineAndPromotion(world, sessionId, officers) }

            val services = CommandServices(
                generalRepository = officerRepository,
                cityRepository = planetRepository,
                nationRepository = factionRepository,
                diplomacyService = diplomacyService,
                modifierService = modifierService,
            )
            val env = CommandEnv(
                year = world.currentYear.toInt(),
                month = world.currentMonth.toInt(),
                worldId = sessionId,
            )

            // Per-officer processing
            for (officer in officers) {
                try {
                    val planet = planetRepository.findById(officer.planetId).orElse(null)
                    val turns = officerTurnRepository.findByOfficerIdOrderByTurnIdx(officer.id)

                    if (officer.blockState > 0) {
                        officer.turnTime = officer.turnTime.plusSeconds(world.tickSeconds.toLong())
                        officerRepository.save(officer)
                        continue
                    }

                    if (turns.isNotEmpty()) {
                        val turn = turns.first()
                        val faction = if (officer.factionId != 0L) factionRepository.findById(officer.factionId).orElse(null) else null
                        try {
                            runBlocking {
                                commandExecutor.executeGeneralCommand(
                                    actionCode = turn.actionCode,
                                    general = officer,
                                    env = env,
                                    city = planet,
                                    nation = faction,
                                    arg = turn.arg.ifEmpty { null },
                                    services = services,
                                )
                            }
                        } catch (e: Exception) {
                            log.warn("Command failed officer={} action={}: {}", officer.id, turn.actionCode, e.message)
                        }
                        officerTurnRepository.delete(turn)
                    } else if (officer.npcState.toInt() in 2..4) {
                        val aiAction = try { officerAI.decideAndExecute(officer, world) } catch (_: Exception) { "" }
                        if (aiAction.isNotEmpty()) {
                            val faction = if (officer.factionId != 0L) factionRepository.findById(officer.factionId).orElse(null) else null
                            try {
                                runBlocking {
                                    commandExecutor.executeGeneralCommand(
                                        actionCode = aiAction,
                                        general = officer,
                                        env = env,
                                        city = planet,
                                        nation = faction,
                                        services = services,
                                    )
                                }
                            } catch (e: Exception) {
                                log.warn("AI command failed officer={} action={}: {}", officer.id, aiAction, e.message)
                            }
                        }
                    }

                    officerRepository.save(officer)
                } catch (e: Exception) {
                    log.warn("Error processing officer {}", officer.id, e)
                }
            }

            // Faction turn processing
            val factionLevelPairs: List<Pair<Long, Short>> = officers
                .filter { it.factionId != 0L }
                .map { Pair(it.factionId, it.rank) }
                .distinct()
            for ((fId, oLevel) in factionLevelPairs) {
                try {
                    val factionTurns = factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(fId, oLevel)
                    val turn = factionTurns.firstOrNull() ?: continue
                    factionTurnRepository.delete(turn)
                } catch (e: Exception) {
                    log.warn("Error processing faction turn ({}, {}): {}", fId, oLevel, e.message)
                }
            }

            // Faction processing: decrement strategic command limits
            val factions = factionRepository.findBySessionId(sessionId)
            for (faction in factions) {
                if (faction.strategicCmdLimit > 0) {
                    faction.strategicCmdLimit = (faction.strategicCmdLimit - 1).toShort()
                }
                factionRepository.save(faction)
            }

            // Unification check
            tryRun("unificationService.checkAndSettleUnification") { unificationService.checkAndSettleUnification(world) }

            // Victory conditions check
            tryRun("victoryService.checkVictoryConditions") { victoryService.checkVictoryConditions(world) }

            // Rank ladder: auto-promotion and auto-demotion
            tryRun("rankLadderService.processAutoPromotion") { rankLadderService.processAutoPromotion(sessionId) }
            tryRun("rankLadderService.processAutoDemotion") { rankLadderService.processAutoDemotion(sessionId) }
        }

        // Save world state after all ticks
        sessionStateRepository.save(world)
    }

    private fun resetKillTurnIfNeeded(world: SessionState, sessionId: Long) {
        if (world.meta["openKillTurnReset"] == true) return

        val turnTerm = world.tickSeconds / 60
        if (turnTerm <= 0) return
        val globalKillTurn = (GLOBAL_KILL_TURN_BASE / turnTerm).toShort()

        val officers = officerRepository.findBySessionId(sessionId)
        for (officer in officers) {
            val currentKillTurn = officer.killTurn ?: 0
            if (currentKillTurn < globalKillTurn) {
                officer.killTurn = globalKillTurn
            }
        }

        world.meta["openKillTurnReset"] = true
    }

    /**
     * 이데올로기 자연 표류 + 자동 승진 + 체제 전환 진행.
     * 매 턴 호출.
     */
    private fun processDoctrineAndPromotion(world: SessionState, sessionId: Long, officers: List<com.openlogh.entity.Officer>) {
        val rng = kotlin.random.Random(world.currentYear * 12L + world.currentMonth)
        val factions = factionRepository.findBySessionId(sessionId)

        for (faction in factions) {
            // 이데올로기 자연 표류
            val doctrine = com.openlogh.engine.doctrine.FactionDoctrine.fromMeta(faction.meta)
            if (doctrine != null) {
                val warTurns = (faction.meta["warTurns"] as? Number)?.toInt() ?: 0
                val peaceTurns = (faction.meta["peaceTurns"] as? Number)?.toInt() ?: 0
                val approval = officers.filter { it.factionId == faction.id }
                    .mapNotNull { planetRepository.findById(it.planetId).orElse(null)?.approval?.toDouble() }
                    .average().let { if (it.isNaN()) 0.5 else it / 100.0 }

                val newIdeology = com.openlogh.engine.doctrine.DoctrineChangeEngine.calculateIdeologyDrift(
                    doctrine.ideology, approval, warTurns, peaceTurns, 0.0, rng,
                )
                if (newIdeology != null) {
                    val updated = doctrine.copy(ideology = newIdeology)
                    faction.meta.putAll(com.openlogh.engine.doctrine.FactionDoctrine.toMeta(updated))
                    log.info("Faction {} ideology drift: {} → {}", faction.name, doctrine.ideology.code, newIdeology.code)
                }

                // 체제 전환 진행 중이면 턴 진행
                val transitionMeta = faction.meta["doctrineTransition"] as? Map<String, Any>
                if (transitionMeta != null) {
                    @Suppress("UNCHECKED_CAST")
                    val transition = com.openlogh.engine.doctrine.DoctrineTransition.fromMeta(transitionMeta as Map<String, Any>)
                    if (transition != null) {
                        val complete = com.openlogh.engine.doctrine.DoctrineChangeEngine.advanceTransition(transition)
                        if (complete) {
                            val newDoctrine = com.openlogh.engine.doctrine.DoctrineChangeEngine.applyCompletedTransition(doctrine, transition)
                            faction.meta.putAll(com.openlogh.engine.doctrine.FactionDoctrine.toMeta(newDoctrine))
                            faction.meta.remove("doctrineTransition")
                            log.info("Faction {} doctrine transition complete: {}", faction.name, transition.targetCode)
                        } else {
                            faction.meta["doctrineTransition"] = transition.toMeta()
                        }
                    }
                }
            }

            factionRepository.save(faction)
        }

        // 자동 승진 (매 턴 체크, gin7은 30일 주기)
        for (officer in officers) {
            if (officer.factionId == 0L) continue
            val currentRank = officer.rank.toInt()
            val targetRank = currentRank + 1
            val factionOfficers = officers.filter { it.factionId == officer.factionId }
            val hasSlot = officerLevelModifier.hasRankSlot(targetRank, factionOfficers)
            if (com.openlogh.engine.strategic.AutoPromotionSystem.canPromote(
                    currentRank, officer.experience, rankSlotAvailable = hasSlot)) {
                officerLevelModifier.applyPromotionEffects(officer)
                log.debug("Auto-promotion: {} rank {} → {}", officer.name, currentRank, targetRank)
            }
        }
    }

    /**
     * Check for officers with enough coup supporters and execute coups.
     */
    private fun processCoupAttempts(world: SessionState, sessionId: Long, officers: List<com.openlogh.entity.Officer>) {
        val coupLeaders = officers.filter { it.meta["coupLeader"] == true }
        for (leader in coupLeaders) {
            // Count conspirators at same system
            val conspirators = officers.filter {
                it.id != leader.id &&
                it.stationedSystem == leader.stationedSystem &&
                it.meta["conspiracyTarget"]?.toString() == leader.id.toString()
            }
            // Need at least 2 conspirators to trigger coup
            if (conspirators.size >= 2) {
                val rebelFaction = factionRepository.findBySessionId(sessionId)
                    .firstOrNull { it.factionType == "rebel" && it.supremeCommanderId == leader.id }
                if (rebelFaction != null) {
                    coupExecutionService.executeCoup(
                        sessionId = sessionId,
                        coupLeaderOfficerId = leader.id,
                        targetFactionId = leader.factionId,
                        rebelFactionId = rebelFaction.id,
                        stationedSystem = leader.stationedSystem,
                    )
                    // Clear coup flags
                    leader.meta.remove("coupLeader")
                    leader.meta.remove("rebellionIntent")
                    for (c in conspirators) {
                        c.meta.remove("conspiracyTarget")
                    }
                }
            }
        }
    }

    private fun tryRun(label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.warn("Sub-service failed [{}]: {}", label, e.message)
        }
    }
}
