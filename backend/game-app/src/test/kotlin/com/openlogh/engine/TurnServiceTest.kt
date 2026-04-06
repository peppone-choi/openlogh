package com.openlogh.engine

import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandRegistry
import com.openlogh.engine.ai.OfficerAI
import com.openlogh.engine.ai.FactionAI
import com.openlogh.engine.turn.TurnPipeline
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.steps.DiplomacyStep
import com.openlogh.engine.turn.steps.DisasterAndTradeStep
import com.openlogh.engine.turn.steps.EconomyPostUpdateStep
import com.openlogh.engine.turn.steps.OfficerMaintenanceStep
import com.openlogh.repository.TrafficSnapshotRepository
import com.openlogh.service.GameEventService
import com.openlogh.service.WorldService
import com.openlogh.entity.Officer
import com.openlogh.entity.OfficerTurn
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.repository.*
import com.openlogh.service.InheritanceService
import com.openlogh.service.ScenarioService
import com.openlogh.engine.UniqueLotteryService
import org.mockito.ArgumentCaptor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.OffsetDateTime

class TurnServiceTest {

    private lateinit var service: TurnService
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var officerTurnRepository: OfficerTurnRepository
    private lateinit var factionTurnRepository: FactionTurnRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var commandRegistry: CommandRegistry
    private lateinit var scenarioService: ScenarioService
    private lateinit var economyService: EconomyService
    private lateinit var eventService: EventService
    private lateinit var diplomacyService: DiplomacyService
    private lateinit var officerMaintenanceService: OfficerMaintenanceService
    private lateinit var specialAssignmentService: SpecialAssignmentService
    private lateinit var npcSpawnService: NpcSpawnService
    private lateinit var unificationService: UnificationService
    private lateinit var inheritanceService: InheritanceService
    private lateinit var uniqueLotteryService: UniqueLotteryService
    private lateinit var officerAI: OfficerAI
    private lateinit var factionAI: FactionAI
    private lateinit var auctionService: com.openlogh.service.AuctionService
    private lateinit var tournamentService: com.openlogh.service.TournamentService

    /** Mockito `any()` returns null which breaks Kotlin non-null params. This helper casts it. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T = any<T>() as T

    @BeforeEach
    fun setUp() {
        sessionStateRepository = mock(SessionStateRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        officerTurnRepository = mock(OfficerTurnRepository::class.java)
        factionTurnRepository = mock(FactionTurnRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        commandExecutor = mock(CommandExecutor::class.java)
        commandRegistry = mock(CommandRegistry::class.java)
        scenarioService = mock(ScenarioService::class.java)
        economyService = mock(EconomyService::class.java)
        eventService = mock(EventService::class.java)
        diplomacyService = mock(DiplomacyService::class.java)
        officerMaintenanceService = mock(OfficerMaintenanceService::class.java)
        specialAssignmentService = mock(SpecialAssignmentService::class.java)
        npcSpawnService = mock(NpcSpawnService::class.java)
        unificationService = mock(UnificationService::class.java)
        inheritanceService = mock(InheritanceService::class.java)
        uniqueLotteryService = mock(UniqueLotteryService::class.java)
        officerAI = mock(OfficerAI::class.java)
        factionAI = mock(FactionAI::class.java)

        val yearbookService = mock(YearbookService::class.java)
        auctionService = mock(com.openlogh.service.AuctionService::class.java)
        tournamentService = mock(com.openlogh.service.TournamentService::class.java)
        val worldPortFactory = JpaWorldPortFactory(
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
        )

        // Build a real pipeline with the steps that the tests verify,
        // backed by the mocked service instances defined above.
        val pipeline = TurnPipeline(listOf(
            EconomyPostUpdateStep(economyService),
            DisasterAndTradeStep(economyService),
            DiplomacyStep(diplomacyService),
            OfficerMaintenanceStep(officerMaintenanceService, specialAssignmentService, inheritanceService, worldPortFactory),
        ))

        service = TurnService(
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
            mock(TrafficSnapshotRepository::class.java),
            officerAI,
            factionAI,
            mock(com.openlogh.engine.modifier.ModifierService::class.java),
            mock(WorldService::class.java),
            mock(com.openlogh.service.FactionService::class.java),
            mock(com.openlogh.engine.war.BattleService::class.java),
            uniqueLotteryService,
            mock(com.openlogh.service.CommandLogDispatcher::class.java),
            mock(com.openlogh.service.GameConstService::class.java),
            mock(OfficerAccessLogRepository::class.java),
            pipeline,
            mock(com.openlogh.engine.war.FieldBattleTrigger::class.java),
        )
        // Default: sessionStateRepository.save returns the argument
        `when`(sessionStateRepository.save(anyNonNull<SessionState>())).thenAnswer { it.arguments[0] }
    }

    private fun createWorld(
        year: Short = 200,
        month: Short = 6,
        tickSeconds: Int = 300,
        updatedAt: OffsetDateTime = OffsetDateTime.now().minusSeconds(600),
    ): SessionState {
        return SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = year,
            currentMonth = month,
            tickSeconds = tickSeconds,
            updatedAt = updatedAt,
        )
    }

    // ========== advanceMonth (tested indirectly through processWorld) ==========

    @Test
    fun `processWorld advances month by 1 when tick elapsed`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        assertEquals(7.toShort(), world.currentMonth, "Month should advance from 6 to 7")
        assertEquals(200.toShort(), world.currentYear, "Year should stay 200")
    }

    @Test
    fun `processWorld advances year when month goes past 12`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 12, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        assertEquals(1.toShort(), world.currentMonth, "Month should wrap to 1")
        assertEquals(201.toShort(), world.currentYear, "Year should advance to 201")
    }

    @Test
    fun `processWorld does not advance when tick not yet elapsed`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.plusSeconds(100))

        service.processWorld(world)

        assertEquals(6.toShort(), world.currentMonth, "Month should not change")
        assertEquals(200.toShort(), world.currentYear, "Year should not change")
    }

    @Test
    fun `processWorld advances multiple months when multiple ticks elapsed`() {
        val now = OffsetDateTime.now()
        // 2 ticks worth of time elapsed (700s > 2 * 300s)
        val world = createWorld(year = 200, month = 3, tickSeconds = 300, updatedAt = now.minusSeconds(700))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        assertEquals(5.toShort(), world.currentMonth, "Month should advance by 2")
    }

    // ========== processWorld calls services ==========

    @Test
    fun `processWorld calls economy and diplomacy services`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        verify(economyService).preUpdateMonthly(anyNonNull())
        verify(economyService).postUpdateMonthly(anyNonNull())
        verify(economyService).processDisasterOrBoom(anyNonNull())
        verify(economyService).randomizeCityTradeRate(anyNonNull())
        verify(diplomacyService).processDiplomacyTurn(anyNonNull())
    }

    @Test
    fun `processWorld calls generalMaintenance`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        val generals = listOf(
            Officer(id = 1, sessionId = 1, name = "테스트", factionId = 1, planetId = 1, turnTime = OffsetDateTime.now())
        )

        `when`(officerRepository.findBySessionId(1L)).thenReturn(generals)
        `when`(officerTurnRepository.findByOfficerIdOrderByTurnIdx(1L)).thenReturn(emptyList())
        `when`(planetRepository.findById(1L)).thenReturn(java.util.Optional.empty())

        service.processWorld(world)

        verify(officerMaintenanceService).processGeneralMaintenance(anyNonNull(), anyList())
    }

    // ========== processWorld: service failure resilience ==========

    @Test
    fun `processWorld continues when economyService throws`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
        doThrow(RuntimeException("DB error")).`when`(economyService).postUpdateMonthly(anyNonNull())

        // Should not throw - continues processing
        assertDoesNotThrow { service.processWorld(world) }

        // Should still advance month
        assertEquals(7.toShort(), world.currentMonth)
    }

    @Test
    fun `processWorld continues when eventService throws`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
        doThrow(RuntimeException("Event error")).`when`(eventService).dispatchEvents(anyNonNull(), anyString())

        assertDoesNotThrow { service.processWorld(world) }
        assertEquals(7.toShort(), world.currentMonth)
    }

    // ========== processWorld: saves world at end ==========

    @Test
    fun `processWorld saves world state`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        verify(sessionStateRepository).save(world)
    }

    // ========== processWorld: updatedAt progression ==========

    @Test
    fun `processWorld updates world updatedAt by tickDuration`() {
        val now = OffsetDateTime.now()
        val startTime = now.minusSeconds(400)
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = startTime)
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        // updatedAt should be startTime + 300s
        val expected = startTime.plusSeconds(300)
        assertEquals(expected, world.updatedAt, "updatedAt should advance by tick duration")
    }

    // ========== supply recalc (via postUpdateMonthly) ==========

    // ========== strategic command limit reset ==========

    @Test
    fun `processWorld decrements strategic command limits`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        val nation = Faction(
            id = 1, sessionId = 1, name = "위", color = "#FF0000",
            strategicCmdLimit = 5,
        )
        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(nation))

        service.processWorld(world)

        val captor = ArgumentCaptor.forClass(Faction::class.java)
        verify(factionRepository).save(captor.capture())
        assertEquals(4.toShort(), captor.value.strategicCmdLimit, "Strategic limit should decrement by 1")
    }

    @Test
    fun `processWorld does not decrement strategic limit below zero`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        val nation = Faction(
            id = 1, sessionId = 1, name = "위", color = "#FF0000",
            strategicCmdLimit = 0,
        )
        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(nation))

        service.processWorld(world)

        val captor = ArgumentCaptor.forClass(Faction::class.java)
        verify(factionRepository).save(captor.capture())
        assertEquals(0.toShort(), captor.value.strategicCmdLimit, "Strategic limit should stay at 0")
    }

    @Test
    fun `processWorld decrements strategic limit for each catch-up turn`() {
        val now = OffsetDateTime.now()
        // 2 ticks elapsed
        val world = createWorld(year = 200, month = 3, tickSeconds = 300, updatedAt = now.minusSeconds(700))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        val nation = Faction(
            id = 1, sessionId = 1, name = "위", color = "#FF0000",
            strategicCmdLimit = 10,
        )
        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(nation))

        service.processWorld(world)

        val captor = ArgumentCaptor.forClass(Faction::class.java)
        verify(factionRepository, times(2)).save(captor.capture())
        val saved = captor.allValues.last()
        assertTrue(saved.strategicCmdLimit <= 9, "Strategic limit should be decremented during catch-up turns")
    }

    // ========== catch-up resilience ==========

    // ========== 가오픈→오픈 killTurn 리셋 ==========

    @Test
    fun `processWorld resets low killTurn on first open after pre-open`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 3600, updatedAt = now.minusSeconds(4000))
        // tickSeconds=3600 → turnterm=60 → globalKillTurn=4800/60=80
        // meta에 openKillTurnReset 없음 → 리셋 발생해야 함

        val general = Officer(
            id = 1, sessionId = 1, name = "가오픈장수", factionId = 1, planetId = 1,
            killTurn = 6, npcState = 0, turnTime = now.plusSeconds(100),
        )
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(general))

        service.processWorld(world)

        assertEquals(80.toShort(), general.killTurn, "killTurn should be reset to global value (80)")
        assertEquals(true, world.meta["openKillTurnReset"], "openKillTurnReset flag should be set")
    }

    @Test
    fun `processWorld does not reset killTurn when flag already set`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 3600, updatedAt = now.minusSeconds(4000))
        world.meta["openKillTurnReset"] = true

        val general = Officer(
            id = 1, sessionId = 1, name = "기존장수", factionId = 1, planetId = 1,
            killTurn = 6, npcState = 0, turnTime = now.plusSeconds(100),
        )
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(general))

        service.processWorld(world)

        assertEquals(6.toShort(), general.killTurn, "killTurn should NOT be reset when flag already set")
    }

    @Test
    fun `processWorld does not lower existing high killTurn on reset`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 3600, updatedAt = now.minusSeconds(4000))

        val general = Officer(
            id = 1, sessionId = 1, name = "NPC장수", factionId = 1, planetId = 1,
            killTurn = 200, npcState = 2, turnTime = now.plusSeconds(100),
        )
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(general))

        service.processWorld(world)

        assertEquals(200.toShort(), general.killTurn, "killTurn above global should not be lowered")
    }

    // ========== triggerTournament ==========

    @Test
    fun `triggerTournament delegates to tournamentService checkAndTriggerTournament`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        // checkAndTriggerTournament should be called once per turn processed
        verify(tournamentService, atLeastOnce()).checkAndTriggerTournament(anyNonNull())
    }

    // ========== registerAuction ==========

    @Test
    fun `registerAuction queries active auctions and may open system auction`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
        // Return empty list so both probabilities are at 1/5 (neutral counts = 0)
        `when`(auctionService.listActiveAuctions(1L)).thenReturn(emptyList())

        service.processWorld(world)

        // listActiveAuctions must be called to check neutral auction counts
        verify(auctionService, atLeastOnce()).listActiveAuctions(1L)
    }

    @Test
    fun `processWorld preserves per general turn offset for blocked generals`() {
        val updatedAt = OffsetDateTime.now().withNano(0).minusSeconds(400)
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = updatedAt)
        val originalTurnTime = updatedAt.plusSeconds(180)
        val general = Officer(
            id = 1,
            sessionId = 1,
            name = "봉쇄장수",
            factionId = 1,
            planetId = 1,
            blockState = 2,
            turnTime = originalTurnTime,
        )

        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(general))
        `when`(officerRepository.findById(1L)).thenReturn(java.util.Optional.of(general))
        `when`(planetRepository.findById(1L)).thenReturn(java.util.Optional.empty())

        service.processWorld(world)

        val captor = ArgumentCaptor.forClass(Officer::class.java)
        verify(officerRepository, atLeastOnce()).save(captor.capture())
        assertTrue(captor.allValues.any { it.id == 1L && it.turnTime == originalTurnTime.plusSeconds(300) })
    }

    @Test
    fun `processWorld fires fireCommand consumed after consuming a nation turn`() {
        val gameEventService = mock(GameEventService::class.java)
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))

        // Officer-level 5 general whose turnTime is well before nextTurnAt (updatedAt+tick = now-100s)
        val general = Officer(
            id = 1,
            sessionId = 1,
            name = "대신",
            factionId = 1,
            planetId = 1,
            officerLevel = 5,
            npcState = 0,
            turnTime = now.minusSeconds(500),
        )
        val nation = com.openlogh.entity.Faction(id = 1, sessionId = 1, name = "위", color = "#FF0000")
        val nationTurnEntry = com.openlogh.entity.FactionTurn(
            sessionId = 1,
            factionId = 1,
            officerLevel = 5,
            turnIdx = 0,
            actionCode = "Nation휴식",
            brief = "Nation휴식",
        )

        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(general))
        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(nation))
        // nationCache is populated via cityCache/nationCache inside executeGeneralCommandsUntil
        `when`(factionRepository.findById(1L)).thenReturn(java.util.Optional.of(nation))
        `when`(factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(1L, 5)).thenReturn(listOf(nationTurnEntry))
        `when`(officerTurnRepository.findByOfficerIdOrderByTurnIdx(1L)).thenReturn(emptyList())
        `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())
        // hasNationCommand("Nation휴식") must return true so nationActionCode is set
        `when`(commandRegistry.hasNationCommand("Nation휴식")).thenReturn(true)

        val yearbookService = mock(YearbookService::class.java)
        val worldPortFactory = JpaWorldPortFactory(
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
        )
        val pipeline = TurnPipeline(listOf(
            EconomyPostUpdateStep(economyService),
            DisasterAndTradeStep(economyService),
            DiplomacyStep(diplomacyService),
            OfficerMaintenanceStep(officerMaintenanceService, specialAssignmentService, inheritanceService, worldPortFactory),
        ))

        val serviceWithEvent = TurnService(
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
            trafficSnapshotRepository = mock(TrafficSnapshotRepository::class.java),
            worldPortFactory = worldPortFactory,
            officerAI = officerAI,
            factionAI = factionAI,
            modifierService = mock(com.openlogh.engine.modifier.ModifierService::class.java),
            worldService = mock(WorldService::class.java),
            factionService = mock(com.openlogh.service.FactionService::class.java),
            battleService = mock(com.openlogh.engine.war.BattleService::class.java),
            uniqueLotteryService = uniqueLotteryService,
            commandLogDispatcher = mock(com.openlogh.service.CommandLogDispatcher::class.java),
            gameConstService = mock(com.openlogh.service.GameConstService::class.java),
            officerAccessLogRepository = mock(OfficerAccessLogRepository::class.java),
            turnPipeline = pipeline,
            fieldBattleTrigger = mock(com.openlogh.engine.war.FieldBattleTrigger::class.java),
            gameEventService = gameEventService,
        )
        `when`(sessionStateRepository.save(anyNonNull<SessionState>())).thenAnswer { it.arguments[0] }

        serviceWithEvent.processWorld(world)

        // Commands execute before month advances: month is still 6 at time of fireCommand
        verify(gameEventService).fireCommand(
            sessionId = 1L,
            year = 200.toShort(),
            month = 6.toShort(),
            officerId = 1L,
            commandEventType = "consumed",
            detail = mapOf("actionCode" to "Nation휴식", "factionId" to 1L),
        )
    }

    // ========== checkWander ==========

    @Test
    fun `checkWander dissolves wander nation when year ge startYear plus 2`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 202, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        world.config["startYear"] = 200

        val chief = Officer(
            id = 10, sessionId = 1, name = "방랑군주", factionId = 5, planetId = 1,
            officerLevel = 20, npcState = 0, turnTime = now,
        )
        val wanderNation = Faction(id = 5, sessionId = 1, name = "방랑", color = "#888888", factionRank = 0)

        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(chief))
        `when`(factionRepository.findById(5L)).thenReturn(java.util.Optional.of(wanderNation))

        service.processWorld(world)

        // checkWander should have called createOfficerCommand("해산") for the wander chief
        verify(commandRegistry, atLeastOnce()).createOfficerCommand(eq("해산"), anyNonNull(), anyNonNull(), anyOrNull())
    }

    @Test
    fun `checkWander does nothing when year lt startYear plus 2`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 201, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        world.config["startYear"] = 200

        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        verify(commandRegistry, never()).createOfficerCommand(eq("해산"), anyNonNull(), anyNonNull(), anyOrNull())
    }

    @Test
    fun `checkWander skips non-wander nation`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 202, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        world.config["startYear"] = 200

        val chief = Officer(
            id = 10, sessionId = 1, name = "정상군주", factionId = 5, planetId = 1,
            officerLevel = 20, npcState = 0, turnTime = now,
        )
        val regularNation = Faction(id = 5, sessionId = 1, name = "위", color = "#FF0000", factionRank = 3)

        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(chief))
        `when`(factionRepository.findById(5L)).thenReturn(java.util.Optional.of(regularNation))

        service.processWorld(world)

        verify(commandRegistry, never()).createOfficerCommand(eq("해산"), anyNonNull(), anyNonNull(), anyOrNull())
    }

    // ========== updateOnline ==========

    @Test
    fun `updateOnline sets online count and nation string in world meta`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))

        val log1 = com.openlogh.entity.OfficerAccessLog(id = 1, officerId = 1, sessionId = 1, accessedAt = now.minusSeconds(10))
        val log2 = com.openlogh.entity.OfficerAccessLog(id = 2, officerId = 2, sessionId = 1, accessedAt = now.minusSeconds(20))
        val general1 = Officer(id = 1, sessionId = 1, name = "장수1", factionId = 1, planetId = 1, turnTime = now)
        val general2 = Officer(id = 2, sessionId = 1, name = "장수2", factionId = 2, planetId = 1, turnTime = now)
        val nation1 = Faction(id = 1, sessionId = 1, name = "위", color = "#FF0000")
        val nation2 = Faction(id = 2, sessionId = 1, name = "촉", color = "#00FF00")

        val accessLogRepo = mock(OfficerAccessLogRepository::class.java)
        `when`(accessLogRepo.findBySessionId(1L)).thenReturn(listOf(log1, log2))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(general1, general2))
        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(nation1, nation2))

        // Build a service instance with the custom accessLogRepo
        val yearbookService = mock(YearbookService::class.java)
        val worldPortFactory = JpaWorldPortFactory(
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
        )
        val pipeline = com.openlogh.engine.turn.TurnPipeline(listOf(
            EconomyPostUpdateStep(economyService),
            DisasterAndTradeStep(economyService),
            DiplomacyStep(diplomacyService),
            OfficerMaintenanceStep(officerMaintenanceService, specialAssignmentService, inheritanceService, worldPortFactory),
        ))
        val svcWithLog = TurnService(
            sessionStateRepository, officerRepository, officerTurnRepository, factionTurnRepository,
            planetRepository, factionRepository, commandExecutor, commandRegistry,
            scenarioService, economyService, eventService, diplomacyService,
            officerMaintenanceService, specialAssignmentService, npcSpawnService,
            unificationService, inheritanceService, yearbookService, auctionService,
            tournamentService, mock(TrafficSnapshotRepository::class.java),
            worldPortFactory, officerAI, factionAI,
            mock(com.openlogh.engine.modifier.ModifierService::class.java),
            mock(WorldService::class.java),
            mock(com.openlogh.service.FactionService::class.java),
            mock(com.openlogh.engine.war.BattleService::class.java),
            uniqueLotteryService,
            mock(com.openlogh.service.CommandLogDispatcher::class.java),
            mock(com.openlogh.service.GameConstService::class.java),
            accessLogRepo,
            pipeline,
            mock(com.openlogh.engine.war.FieldBattleTrigger::class.java),
        )
        `when`(sessionStateRepository.save(anyNonNull<SessionState>())).thenAnswer { it.arguments[0] }

        svcWithLog.processWorld(world)

        assertEquals(2, world.meta["online_user_cnt"])
        assertNotNull(world.meta["online_nation"])
    }

    // ========== checkOverhead ==========

    @Test
    fun `checkOverhead calculates refreshLimit using legacy formula`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        world.config["refreshLimitCoef"] = 10
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        // round(300^0.6 * 3) * 10 = round(30.64 * 3) * 10 = round(91.92) * 10 = 920
        val refreshLimit = (world.meta["refreshLimit"] as? Number)?.toInt()
        assertNotNull(refreshLimit, "refreshLimit should be set in world.meta")
        assertEquals(920, refreshLimit)
    }

    @Test
    fun `checkOverhead uses default refreshLimitCoef of 10`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        // No refreshLimitCoef in config
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        val refreshLimit = (world.meta["refreshLimit"] as? Number)?.toInt()
        assertNotNull(refreshLimit, "refreshLimit should be set even with default coef")
        assertEquals(920, refreshLimit)
    }

    // ========== updateGeneralNumber ==========

    @Test
    fun `updateGeneralNumber updates nation officerCount to count of non-npcState5 generals`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))

        val gen1 = Officer(id = 1, sessionId = 1, name = "장수1", factionId = 1, planetId = 1, npcState = 0, turnTime = now)
        val gen2 = Officer(id = 2, sessionId = 1, name = "장수2", factionId = 1, planetId = 1, npcState = 2, turnTime = now)
        val gen3 = Officer(id = 3, sessionId = 1, name = "죽은장수", factionId = 1, planetId = 1, npcState = 5, turnTime = now)
        val gen4 = Officer(id = 4, sessionId = 1, name = "장수3", factionId = 2, planetId = 1, npcState = 0, turnTime = now)
        val nation1 = Faction(id = 1, sessionId = 1, name = "위", color = "#FF0000", officerCount = 0)
        val nation2 = Faction(id = 2, sessionId = 1, name = "촉", color = "#00FF00", officerCount = 0)

        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(gen1, gen2, gen3, gen4))
        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(nation1, nation2))

        service.processWorld(world)

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Faction>>
        verify(factionRepository, atLeastOnce()).saveAll(captor.capture())
        val saved = captor.value
        val savedNation1 = saved.find { it.id == 1L }
        val savedNation2 = saved.find { it.id == 2L }
        // nation1: gen1(npc=0) + gen2(npc=2) = 2 (gen3 excluded as npc=5)
        assertEquals(2, savedNation1?.officerCount, "Nation1 should have 2 active generals")
        // nation2: gen4(npc=0) = 1
        assertEquals(1, savedNation2?.officerCount, "Nation2 should have 1 active general")
    }

    @Test
    fun `updateGeneralNumber sets zero for nation with no active generals`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))

        val nation1 = Faction(id = 1, sessionId = 1, name = "위", color = "#FF0000", officerCount = 5)
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(nation1))

        service.processWorld(world)

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Faction>>
        verify(factionRepository, atLeastOnce()).saveAll(captor.capture())
        val saved = captor.value
        assertEquals(0, saved.find { it.id == 1L }?.officerCount, "Nation officerCount should be 0 when no generals")
    }

    /** Helper: Mockito eq() wrapper returning non-null for Kotlin. */
    private fun <T> eq(value: T): T = org.mockito.ArgumentMatchers.eq(value) ?: value

    /** Helper: Mockito anyOrNull() for nullable parameters. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyOrNull(): T? = any<T>() as T?
}
