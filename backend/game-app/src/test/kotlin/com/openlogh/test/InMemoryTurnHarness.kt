package com.openlogh.test

import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandRegistry
import com.openlogh.engine.*
import com.openlogh.engine.war.BattleService
import com.openlogh.engine.ai.OfficerAI
import com.openlogh.engine.ai.FactionAI
import com.openlogh.engine.modifier.ModifierService
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
    private val planets = mutableMapOf<Long, Planet>()
    private val factions = mutableMapOf<Long, Faction>()
    private val officerTurns = mutableMapOf<Long, MutableList<OfficerTurn>>()
    private val factionTurns = mutableMapOf<Pair<Long, Short>, MutableList<FactionTurn>>()
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

    val commandRegistry = CommandRegistry()
    private val messageService: com.openlogh.service.MessageService = mock(com.openlogh.service.MessageService::class.java)
    val commandExecutor = CommandExecutor(
        defaultRegistry = commandRegistry,
        factionRepository = factionRepository,
        planetRepository = planetRepository,
        officerRepository = officerRepository,
    )

    // Compatibility aliases
    val generalRepository: OfficerRepository get() = officerRepository
    val cityRepository: PlanetRepository get() = planetRepository
    val nationRepository: FactionRepository get() = factionRepository

    fun putGeneral(officer: Officer) = putOfficer(officer)
    fun putCity(planet: Planet) = putPlanet(planet)
    fun putNation(faction: Faction) = putFaction(faction)

    fun buildServices(): com.openlogh.command.CommandServices = com.openlogh.command.CommandServices(
        generalRepository = officerRepository,
        cityRepository = planetRepository,
        nationRepository = factionRepository,
        diplomacyService = diplomacyService,
        modifierService = modifierService,
    )

    private val yearbookService: YearbookService = mock(YearbookService::class.java)
    private val auctionService: com.openlogh.service.AuctionService = mock(com.openlogh.service.AuctionService::class.java)
    private val tournamentService: com.openlogh.service.TournamentService = mock(com.openlogh.service.TournamentService::class.java)
    private val trafficSnapshotRepository: TrafficSnapshotRepository = mock(TrafficSnapshotRepository::class.java)
    private val worldService: WorldService = mock(WorldService::class.java)
    private val factionService: com.openlogh.service.FactionService = mock(com.openlogh.service.FactionService::class.java)
    val battleService: BattleService = mock(BattleService::class.java)
    private val uniqueLotteryService: UniqueLotteryService = UniqueLotteryService()

    val turnService = TurnService(
        sessionStateRepository,
        officerRepository,
        officerTurnRepository,
        factionTurnRepository,
        planetRepository,
        factionRepository,
        commandExecutor,
        commandRegistry,
        scenarioService,
        economyService,
        eventService,
        diplomacyService,
        officerMaintenanceService,
        specialAssignmentService,
        npcSpawnService,
        unificationService,
        inheritanceService,
        yearbookService,
        auctionService,
        tournamentService,
        trafficSnapshotRepository,
        officerAI,
        factionAI,
        modifierService,
        worldService,
        factionService,
        battleService,
        uniqueLotteryService,
        mock(com.openlogh.service.CommandLogDispatcher::class.java),
        mock(com.openlogh.service.GameConstService::class.java),
        mock(com.openlogh.repository.OfficerAccessLogRepository::class.java),
        com.openlogh.engine.CommandPointService(officerRepository),
        com.openlogh.engine.AgeGrowthService(officerRepository),
        com.openlogh.engine.modifier.OfficerLevelModifier(),
        mock(com.openlogh.service.VictoryService::class.java),
        mock(com.openlogh.service.RankLadderService::class.java),
        mock(com.openlogh.engine.SafeZoneService::class.java),
        mock(com.openlogh.engine.planet.PlanetProductionService::class.java),
        mock(com.openlogh.engine.FezzanNeutralityService::class.java),
        mock(com.openlogh.engine.fleet.TransportExecutionService::class.java),
        mock(com.openlogh.engine.CoupExecutionService::class.java),
    )

    init {
        wireRepositories()
    }

    fun putWorld(world: SessionState) {
        worlds[world.id] = world
    }

    fun putOfficer(officer: Officer) {
        officers[officer.id] = officer
    }

    fun putPlanet(planet: Planet) {
        planets[planet.id] = planet
    }

    fun putFaction(faction: Faction) {
        factions[faction.id] = faction
    }

    fun queueOfficerTurn(officerId: Long, actionCode: String, arg: MutableMap<String, Any> = mutableMapOf(), turnIdx: Short = 0) {
        val officer = officers[officerId] ?: error("Officer not found: $officerId")
        val turn = OfficerTurn(
            id = turnIdSeq.getAndIncrement(),
            sessionId = officer.sessionId,
            officerId = officerId,
            turnIdx = turnIdx,
            actionCode = actionCode,
            arg = arg,
            createdAt = OffsetDateTime.now(),
        )
        officerTurns.getOrPut(officerId) { mutableListOf() }.add(turn)
        officerTurns[officerId]!!.sortBy { it.turnIdx }
    }

    fun queueFactionTurn(
        factionId: Long,
        officerLevel: Short,
        actionCode: String,
        arg: MutableMap<String, Any> = mutableMapOf(),
        turnIdx: Short = 0,
    ) {
        val faction = factions[factionId] ?: error("Faction not found: $factionId")
        val turn = FactionTurn(
            id = turnIdSeq.getAndIncrement(),
            sessionId = faction.sessionId,
            factionId = factionId,
            officerLevel = officerLevel,
            turnIdx = turnIdx,
            actionCode = actionCode,
            arg = arg,
            createdAt = OffsetDateTime.now(),
        )
        val key = factionId to officerLevel
        factionTurns.getOrPut(key) { mutableListOf() }.add(turn)
        factionTurns[key]!!.sortBy { it.turnIdx }
    }

    fun officerTurnsFor(officerId: Long): List<OfficerTurn> = officerTurns[officerId]?.toList() ?: emptyList()

    fun factionTurnsFor(factionId: Long, officerLevel: Short): List<FactionTurn> =
        factionTurns[factionId to officerLevel]?.toList() ?: emptyList()

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
        `when`(officerRepository.findByNationId(org.mockito.Mockito.anyLong())).thenAnswer {
            val factionId = it.arguments[0] as Long
            officers.values.filter { g -> g.factionId == factionId }
        }
        `when`(officerRepository.findByWorldId(org.mockito.Mockito.anyLong())).thenAnswer {
            val worldId = it.arguments[0] as Long
            officers.values.filter { g -> g.sessionId == worldId }
        }
        `when`(officerRepository.findByWorldIdAndNationId(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyLong())).thenAnswer {
            val worldId = it.arguments[0] as Long
            val factionId = it.arguments[1] as Long
            officers.values.filter { g -> g.sessionId == worldId && g.factionId == factionId }
        }
        `when`(officerRepository.findBySessionIdAndNationId(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyLong())).thenAnswer {
            val sessionId = it.arguments[0] as Long
            val factionId = it.arguments[1] as Long
            officers.values.filter { g -> g.sessionId == sessionId && g.factionId == factionId }
        }
        `when`(officerRepository.findByCityId(org.mockito.Mockito.anyLong())).thenAnswer {
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
            officerTurns[officerId]?.sortedBy { t -> t.turnIdx } ?: emptyList<OfficerTurn>()
        }

        doAnswer { invocation ->
            val officerId = invocation.arguments[0] as Long
            officerTurns.remove(officerId)
            null
        }.`when`(officerTurnRepository).deleteByOfficerId(org.mockito.Mockito.anyLong())
        doAnswer { invocation ->
            val turn = invocation.arguments[0] as OfficerTurn
            officerTurns[turn.officerId]?.removeIf { t -> t.id == turn.id }
            null
        }.`when`(officerTurnRepository).delete(org.mockito.Mockito.any(OfficerTurn::class.java))
        doAnswer { invocation ->
            val turns = invocation.arguments[0] as List<OfficerTurn>
            turns.forEach { turn ->
                officerTurns[turn.officerId]?.removeIf { t -> t.id == turn.id }
            }
            null
        }.`when`(officerTurnRepository).deleteAll(org.mockito.Mockito.anyList<OfficerTurn>())

        `when`(factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyShort())).thenAnswer {
            val factionId = it.arguments[0] as Long
            val officerLevel = it.arguments[1] as Short
            factionTurns[factionId to officerLevel]?.sortedBy { t -> t.turnIdx } ?: emptyList<FactionTurn>()
        }
        doAnswer { invocation ->
            val turn = invocation.arguments[0] as FactionTurn
            factionTurns[turn.factionId to turn.officerLevel]?.removeIf { t -> t.id == turn.id }
            null
        }.`when`(factionTurnRepository).delete(org.mockito.Mockito.any(FactionTurn::class.java))
        doAnswer { invocation ->
            val factionId = invocation.arguments[0] as Long
            val officerLevel = invocation.arguments[1] as Short
            factionTurns.remove(factionId to officerLevel)
            null
        }.`when`(factionTurnRepository).deleteByFactionIdAndOfficerLevel(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyShort())

        `when`(planetRepository.findById(org.mockito.Mockito.anyLong())).thenAnswer {
            Optional.ofNullable(planets[it.arguments[0] as Long])
        }
        `when`(planetRepository.findBySessionId(org.mockito.Mockito.anyLong())).thenAnswer {
            val sessionId = it.arguments[0] as Long
            planets.values.filter { c -> c.sessionId == sessionId }
        }
        `when`(planetRepository.findByWorldId(org.mockito.Mockito.anyLong())).thenAnswer {
            val worldId = it.arguments[0] as Long
            planets.values.filter { c -> c.sessionId == worldId }
        }
        `when`(planetRepository.findByFactionId(org.mockito.Mockito.anyLong())).thenAnswer {
            val factionId = it.arguments[0] as Long
            planets.values.filter { c -> c.factionId == factionId }
        }
        `when`(planetRepository.save(org.mockito.Mockito.any(Planet::class.java))).thenAnswer {
            val planet = it.arguments[0] as Planet
            planets[planet.id] = planet
            planet
        }

        `when`(factionRepository.findById(org.mockito.Mockito.anyLong())).thenAnswer {
            Optional.ofNullable(factions[it.arguments[0] as Long])
        }
        `when`(factionRepository.findBySessionId(org.mockito.Mockito.anyLong())).thenAnswer {
            val sessionId = it.arguments[0] as Long
            factions.values.filter { n -> n.sessionId == sessionId }
        }
        `when`(factionRepository.findByWorldId(org.mockito.Mockito.anyLong())).thenAnswer {
            val worldId = it.arguments[0] as Long
            factions.values.filter { n -> n.sessionId == worldId }
        }
        `when`(factionRepository.save(org.mockito.Mockito.any(Faction::class.java))).thenAnswer {
            val faction = it.arguments[0] as Faction
            if (faction.id <= 0L) {
                faction.id = (factions.keys.maxOrNull() ?: 0L) + 1L
            }
            factions[faction.id] = faction
            faction
        }
        `when`(factionRepository.saveAll(org.mockito.Mockito.anyList<Faction>())).thenAnswer {
            val list = it.arguments[0] as List<Faction>
            list.forEach { n -> factions[n.id] = n }
            list
        }

        `when`(diplomacyRepository.findBySessionId(org.mockito.Mockito.anyLong())).thenReturn(emptyList())
        `when`(
            diplomacyRepository.findBySessionIdAndSrcNationIdOrDestFactionId(
                org.mockito.Mockito.anyLong(),
                org.mockito.Mockito.anyLong(),
                org.mockito.Mockito.anyLong(),
            )
        ).thenReturn(emptyList())
        `when`(diplomacyRepository.findBySessionIdAndIsDeadFalse(org.mockito.Mockito.anyLong())).thenReturn(emptyList())
        `when`(diplomacyRepository.save(org.mockito.Mockito.any(Diplomacy::class.java))).thenAnswer { it.arguments[0] }
    }
}
