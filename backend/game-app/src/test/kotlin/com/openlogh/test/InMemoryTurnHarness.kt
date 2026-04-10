package com.openlogh.test

import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandRegistry
import com.openlogh.command.Gin7CommandRegistry
import com.openlogh.engine.*
// BattleService removed in Phase 1 (삼국지 전투 엔진 삭제)
import com.openlogh.engine.ai.OfficerAI
import com.openlogh.engine.ai.FactionAI
import com.openlogh.engine.modifier.ModifierService
import com.openlogh.engine.turn.TurnPipeline
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.steps.DiplomacyStep
import com.openlogh.engine.turn.steps.DisasterAndTradeStep
import com.openlogh.engine.turn.steps.EconomyPostUpdateStep
import com.openlogh.engine.turn.steps.OfficerMaintenanceStep
import com.openlogh.engine.turn.steps.UnificationCheckStep
import com.openlogh.repository.TrafficSnapshotRepository
import com.openlogh.service.WorldService
import com.openlogh.entity.*
import com.openlogh.repository.*
import com.openlogh.service.InheritanceService
import com.openlogh.service.MapService
import com.openlogh.service.ScenarioService
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class InMemoryTurnHarness {
    private val worlds = mutableMapOf<Short, SessionState>()
    private val officers = mutableMapOf<Long, Officer>()
    private val cities = mutableMapOf<Long, Planet>()
    private val factions = mutableMapOf<Long, Faction>()
    private val generalTurns = mutableMapOf<Long, MutableList<OfficerTurn>>()
    private val nationTurns = mutableMapOf<Pair<Long, Short>, MutableList<FactionTurn>>()
    private val turnIdSeq = AtomicLong(1)

    val sessionStateRepository: SessionStateRepository = mock(SessionStateRepository::class.java)
    val officerRepository: OfficerRepository = mock(OfficerRepository::class.java)
    val officerTurnRepository: OfficerTurnRepository = mock(OfficerTurnRepository::class.java)
    val factionTurnRepository: FactionTurnRepository = mock(FactionTurnRepository::class.java)
    val planetRepository: PlanetRepository = mock(PlanetRepository::class.java)
    val factionRepository: FactionRepository = mock(FactionRepository::class.java)
    val diplomacyRepository: DiplomacyRepository = mock(DiplomacyRepository::class.java)
    private val mapService: MapService = MapService().apply { init() }

    private val scenarioService: ScenarioService = mock(ScenarioService::class.java)
    private val economyService: EconomyService = mock(EconomyService::class.java)
    private val eventService: EventService = mock(EventService::class.java)
    private val diplomacyService: DiplomacyService = mock(DiplomacyService::class.java)
    private val officerMaintenanceService: OfficerMaintenanceService = mock(OfficerMaintenanceService::class.java)
    private val specialAssignmentService: SpecialAssignmentService = mock(SpecialAssignmentService::class.java)
    private val npcSpawnService: NpcSpawnService = mock(NpcSpawnService::class.java)
    val unificationService: UnificationService = mock(UnificationService::class.java)
    private val inheritanceService: InheritanceService = mock(InheritanceService::class.java)
    private val officerAI: OfficerAI = mock(OfficerAI::class.java)
    private val factionAI: FactionAI = mock(FactionAI::class.java)
    private val statChangeService: StatChangeService = mock(StatChangeService::class.java)
    private val modifierService: ModifierService = mock(ModifierService::class.java)

    val commandRegistry: CommandRegistry = Gin7CommandRegistry()
    private val messageService: com.openlogh.service.MessageService = mock(com.openlogh.service.MessageService::class.java)
    val commandExecutor = CommandExecutor(
        commandRegistry,
        officerRepository,
        planetRepository,
        factionRepository,
        diplomacyRepository,
        diplomacyService,
        mapService,
        statChangeService,
        modifierService,
        messageService,
    )

    private val yearbookService: YearbookService = mock(YearbookService::class.java)
    private val auctionService: com.openlogh.service.AuctionService = mock(com.openlogh.service.AuctionService::class.java)
    private val tournamentService: com.openlogh.service.TournamentService = mock(com.openlogh.service.TournamentService::class.java)
    private val trafficSnapshotRepository: TrafficSnapshotRepository = mock(TrafficSnapshotRepository::class.java)
    private val worldService: WorldService = mock(WorldService::class.java)
    private val factionService: com.openlogh.service.FactionService = mock(com.openlogh.service.FactionService::class.java)
    // battleService removed in Phase 1 (삼국지 전투 엔진 삭제)
    private val uniqueLotteryService: UniqueLotteryService = UniqueLotteryService()
    private val worldPortFactory: JpaWorldPortFactory = JpaWorldPortFactory(
        officerRepository = officerRepository,
        planetRepository = planetRepository,
        factionRepository = factionRepository,
    )
    private val turnPipeline: TurnPipeline = TurnPipeline(listOf(
        EconomyPostUpdateStep(economyService),
        DisasterAndTradeStep(economyService),
        DiplomacyStep(diplomacyService),
        OfficerMaintenanceStep(officerMaintenanceService, specialAssignmentService, inheritanceService, worldPortFactory),
        UnificationCheckStep(unificationService),
    ))

    inner class StubTurnService {
        fun processWorld(world: SessionState) {
            val officerTurnSnapshot = generalTurns.toMap()
            officerTurnSnapshot.forEach { (officerId, turns) ->
                val officer = officers[officerId] ?: return@forEach
                val lastTurn = turns.maxByOrNull { it.turnIdx } ?: return@forEach
                officer.lastTurn = mutableMapOf(
                    "command" to lastTurn.actionCode,
                    "turnIdx" to lastTurn.turnIdx,
                )
            }
            generalTurns.clear()
            nationTurns.clear()

            val tickSeconds = world.tickSeconds.toLong().coerceAtLeast(1)
            val elapsedSeconds = world.updatedAt?.let { java.time.Duration.between(it, OffsetDateTime.now()).seconds } ?: tickSeconds
            val ticksToProcess = (elapsedSeconds / tickSeconds).coerceAtLeast(1)

            repeat(ticksToProcess.toInt()) {
                val nextMonth = (world.currentMonth.toInt() % 12) + 1
                world.currentMonth = nextMonth.toShort()
                if (nextMonth == 1) {
                    world.currentYear = (world.currentYear + 1).toShort()
                }
                unificationService.checkAndSettleUnification(world)
            }
            worlds[world.id] = world
        }
    }

    val turnService: StubTurnService = StubTurnService()

    init {
        wireRepositories()
    }

    fun putWorld(world: SessionState) {
        worlds[world.id] = world
    }

    fun putGeneral(general: Officer) {
        officers[general.id] = general
    }
    fun putOfficer(officer: Officer) = putGeneral(officer)

    fun putCity(city: Planet) {
        cities[city.id] = city
    }
    fun putPlanet(planet: Planet) = putCity(planet)

    fun putNation(nation: Faction) {
        factions[nation.id] = nation
    }
    fun putFaction(faction: Faction) = putNation(faction)

    fun queueOfficerTurn(officerId: Long, actionCode: String, arg: MutableMap<String, Any> = mutableMapOf(), turnIdx: Short = 0) {
        val general = officers[officerId] ?: error("General not found: $officerId")
        val turn = OfficerTurn(
            id = turnIdSeq.getAndIncrement(),
            sessionId = general.sessionId,
            officerId = officerId,
            turnIdx = turnIdx,
            actionCode = actionCode,
            arg = arg,
            createdAt = OffsetDateTime.now(),
        )
        generalTurns.getOrPut(officerId) { mutableListOf() }.add(turn)
        generalTurns[officerId]!!.sortBy { it.turnIdx }
    }
    fun queueGeneralTurn(officerId: Long, actionCode: String, arg: MutableMap<String, Any> = mutableMapOf(), turnIdx: Short = 0) =
        queueOfficerTurn(officerId, actionCode, arg, turnIdx)

    fun queueFactionTurn(
        factionId: Long,
        officerLevel: Short,
        actionCode: String,
        arg: MutableMap<String, Any> = mutableMapOf(),
        turnIdx: Short = 0,
    ) {
        val nation = factions[factionId] ?: error("Nation not found: $factionId")
        val turn = FactionTurn(
            id = turnIdSeq.getAndIncrement(),
            sessionId = nation.sessionId,
            factionId = factionId,
            officerLevel = officerLevel,
            turnIdx = turnIdx,
            actionCode = actionCode,
            arg = arg,
            createdAt = OffsetDateTime.now(),
        )
        val key = factionId to officerLevel
        nationTurns.getOrPut(key) { mutableListOf() }.add(turn)
        nationTurns[key]!!.sortBy { it.turnIdx }
    }
    fun queueNationTurn(factionId: Long, officerLevel: Short, actionCode: String, arg: MutableMap<String, Any> = mutableMapOf(), turnIdx: Short = 0) =
        queueFactionTurn(factionId, officerLevel, actionCode, arg, turnIdx)

    fun generalTurnsFor(officerId: Long): List<OfficerTurn> = generalTurns[officerId]?.toList() ?: emptyList()

    fun nationTurnsFor(factionId: Long, officerLevel: Short): List<FactionTurn> =
        nationTurns[factionId to officerLevel]?.toList() ?: emptyList()

    private fun wireRepositories() {
        `when`(sessionStateRepository.save(org.mockito.Mockito.any(SessionState::class.java))).thenAnswer {
            val world = it.arguments[0] as SessionState
            worlds[world.id] = world
            world
        }
        `when`(sessionStateRepository.findById(org.mockito.Mockito.anyShort())).thenAnswer {
            Optional.ofNullable(worlds[it.arguments[0] as Short])
        }

        `when`(officerRepository.findBySessionId(org.mockito.Mockito.anyLong())).thenAnswer {
            val sessionId = it.arguments[0] as Long
            officers.values.filter { g -> g.sessionId == sessionId }
        }
        `when`(officerRepository.findByFactionId(org.mockito.Mockito.anyLong())).thenAnswer {
            val factionId = it.arguments[0] as Long
            officers.values.filter { g -> g.factionId == factionId }
        }
        `when`(officerRepository.findBySessionIdAndFactionId(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyLong())).thenAnswer {
            val sessionId = it.arguments[0] as Long
            val factionId = it.arguments[1] as Long
            officers.values.filter { g -> g.sessionId == sessionId && g.factionId == factionId }
        }
        `when`(officerRepository.findByPlanetId(org.mockito.Mockito.anyLong())).thenAnswer {
            val planetId = it.arguments[0] as Long
            officers.values.filter { g -> g.planetId == planetId }
        }
        `when`(officerRepository.findById(org.mockito.Mockito.anyLong())).thenAnswer {
            Optional.ofNullable(officers[it.arguments[0] as Long])
        }
        `when`(officerRepository.save(org.mockito.Mockito.any(Officer::class.java))).thenAnswer {
            val g = it.arguments[0] as Officer
            officers[g.id] = g
            g
        }
        `when`(officerRepository.saveAll(org.mockito.Mockito.anyList<Officer>())).thenAnswer {
            val list = it.arguments[0] as List<Officer>
            list.forEach { g -> officers[g.id] = g }
            list
        }

        `when`(officerTurnRepository.findByOfficerIdOrderByTurnIdx(org.mockito.Mockito.anyLong())).thenAnswer {
            val officerId = it.arguments[0] as Long
            generalTurns[officerId]?.sortedBy { t -> t.turnIdx } ?: emptyList<OfficerTurn>()
        }

        doAnswer { invocation ->
            val officerId = invocation.arguments[0] as Long
            generalTurns.remove(officerId)
            null
        }.`when`(officerTurnRepository).deleteByOfficerId(org.mockito.Mockito.anyLong())
        doAnswer { invocation ->
            val turn = invocation.arguments[0] as OfficerTurn
            generalTurns[turn.officerId]?.removeIf { t -> t.id == turn.id }
            null
        }.`when`(officerTurnRepository).delete(org.mockito.Mockito.any(OfficerTurn::class.java))
        doAnswer { invocation ->
            val turns = invocation.arguments[0] as List<OfficerTurn>
            turns.forEach { turn ->
                generalTurns[turn.officerId]?.removeIf { t -> t.id == turn.id }
            }
            null
        }.`when`(officerTurnRepository).deleteAll(org.mockito.Mockito.anyList<OfficerTurn>())

        `when`(factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyShort())).thenAnswer {
            val factionId = it.arguments[0] as Long
            val officerLevel = it.arguments[1] as Short
            nationTurns[factionId to officerLevel]?.sortedBy { t -> t.turnIdx } ?: emptyList<FactionTurn>()
        }
        doAnswer { invocation ->
            val turn = invocation.arguments[0] as FactionTurn
            nationTurns[turn.factionId to turn.officerLevel]?.removeIf { t -> t.id == turn.id }
            null
        }.`when`(factionTurnRepository).delete(org.mockito.Mockito.any(FactionTurn::class.java))
        doAnswer { invocation ->
            val factionId = invocation.arguments[0] as Long
            val officerLevel = invocation.arguments[1] as Short
            nationTurns.remove(factionId to officerLevel)
            null
        }.`when`(factionTurnRepository).deleteByFactionIdAndOfficerLevel(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyShort())

        `when`(planetRepository.findById(org.mockito.Mockito.anyLong())).thenAnswer {
            Optional.ofNullable(cities[it.arguments[0] as Long])
        }
        `when`(planetRepository.findBySessionId(org.mockito.Mockito.anyLong())).thenAnswer {
            val sessionId = it.arguments[0] as Long
            cities.values.filter { c -> c.sessionId == sessionId }
        }
        `when`(planetRepository.findByFactionId(org.mockito.Mockito.anyLong())).thenAnswer {
            val factionId = it.arguments[0] as Long
            cities.values.filter { c -> c.factionId == factionId }
        }
        `when`(planetRepository.save(org.mockito.Mockito.any(Planet::class.java))).thenAnswer {
            val city = it.arguments[0] as Planet
            cities[city.id] = city
            city
        }

        `when`(factionRepository.findById(org.mockito.Mockito.anyLong())).thenAnswer {
            Optional.ofNullable(factions[it.arguments[0] as Long])
        }
        `when`(factionRepository.findBySessionId(org.mockito.Mockito.anyLong())).thenAnswer {
            val sessionId = it.arguments[0] as Long
            factions.values.filter { n -> n.sessionId == sessionId }
        }
        `when`(factionRepository.save(org.mockito.Mockito.any(Faction::class.java))).thenAnswer {
            val nation = it.arguments[0] as Faction
            if (nation.id <= 0L) {
                nation.id = (factions.keys.maxOrNull() ?: 0L) + 1L
            }
            factions[nation.id] = nation
            nation
        }
        `when`(factionRepository.saveAll(org.mockito.Mockito.anyList<Faction>())).thenAnswer {
            val list = it.arguments[0] as List<Faction>
            list.forEach { n -> factions[n.id] = n }
            list
        }

        `when`(diplomacyRepository.findBySessionId(org.mockito.Mockito.anyLong())).thenReturn(emptyList())
        `when`(
            diplomacyRepository.findBySessionIdAndSrcFactionIdOrDestFactionId(
                org.mockito.Mockito.anyLong(),
                org.mockito.Mockito.anyLong(),
                org.mockito.Mockito.anyLong(),
            )
        ).thenReturn(emptyList())
        `when`(diplomacyRepository.findBySessionIdAndIsDeadFalse(org.mockito.Mockito.anyLong())).thenReturn(emptyList<com.openlogh.entity.Diplomacy>())
        `when`(diplomacyRepository.save(org.mockito.Mockito.any(Diplomacy::class.java))).thenAnswer { it.arguments[0] }
    }
}
