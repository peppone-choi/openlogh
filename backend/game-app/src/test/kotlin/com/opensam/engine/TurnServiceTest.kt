package com.opensam.engine

import com.opensam.command.CommandExecutor
import com.opensam.command.CommandRegistry
import com.opensam.engine.ai.GeneralAI
import com.opensam.engine.ai.NationAI
import com.opensam.engine.turn.TurnPipeline
import com.opensam.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.opensam.engine.turn.steps.DiplomacyStep
import com.opensam.engine.turn.steps.DisasterAndTradeStep
import com.opensam.engine.turn.steps.EconomyPostUpdateStep
import com.opensam.engine.turn.steps.GeneralMaintenanceStep
import com.opensam.repository.TrafficSnapshotRepository
import com.opensam.service.GameEventService
import com.opensam.service.WorldService
import com.opensam.entity.General
import com.opensam.entity.GeneralTurn
import com.opensam.entity.Nation
import com.opensam.entity.WorldState
import com.opensam.repository.*
import com.opensam.service.InheritanceService
import com.opensam.service.ScenarioService
import com.opensam.engine.UniqueLotteryService
import org.mockito.ArgumentCaptor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.OffsetDateTime

class TurnServiceTest {

    private lateinit var service: TurnService
    private lateinit var worldStateRepository: WorldStateRepository
    private lateinit var generalRepository: GeneralRepository
    private lateinit var generalTurnRepository: GeneralTurnRepository
    private lateinit var nationTurnRepository: NationTurnRepository
    private lateinit var cityRepository: CityRepository
    private lateinit var nationRepository: NationRepository
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var commandRegistry: CommandRegistry
    private lateinit var scenarioService: ScenarioService
    private lateinit var economyService: EconomyService
    private lateinit var eventService: EventService
    private lateinit var diplomacyService: DiplomacyService
    private lateinit var generalMaintenanceService: GeneralMaintenanceService
    private lateinit var specialAssignmentService: SpecialAssignmentService
    private lateinit var npcSpawnService: NpcSpawnService
    private lateinit var unificationService: UnificationService
    private lateinit var inheritanceService: InheritanceService
    private lateinit var uniqueLotteryService: UniqueLotteryService
    private lateinit var generalAI: GeneralAI
    private lateinit var nationAI: NationAI
    private lateinit var auctionService: com.opensam.service.AuctionService
    private lateinit var tournamentService: com.opensam.service.TournamentService

    /** Mockito `any()` returns null which breaks Kotlin non-null params. This helper casts it. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T = any<T>() as T

    @BeforeEach
    fun setUp() {
        worldStateRepository = mock(WorldStateRepository::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        generalTurnRepository = mock(GeneralTurnRepository::class.java)
        nationTurnRepository = mock(NationTurnRepository::class.java)
        cityRepository = mock(CityRepository::class.java)
        nationRepository = mock(NationRepository::class.java)
        commandExecutor = mock(CommandExecutor::class.java)
        commandRegistry = mock(CommandRegistry::class.java)
        scenarioService = mock(ScenarioService::class.java)
        economyService = mock(EconomyService::class.java)
        eventService = mock(EventService::class.java)
        diplomacyService = mock(DiplomacyService::class.java)
        generalMaintenanceService = mock(GeneralMaintenanceService::class.java)
        specialAssignmentService = mock(SpecialAssignmentService::class.java)
        npcSpawnService = mock(NpcSpawnService::class.java)
        unificationService = mock(UnificationService::class.java)
        inheritanceService = mock(InheritanceService::class.java)
        uniqueLotteryService = mock(UniqueLotteryService::class.java)
        generalAI = mock(GeneralAI::class.java)
        nationAI = mock(NationAI::class.java)

        val yearbookService = mock(YearbookService::class.java)
        auctionService = mock(com.opensam.service.AuctionService::class.java)
        tournamentService = mock(com.opensam.service.TournamentService::class.java)
        val worldPortFactory = JpaWorldPortFactory(
            generalRepository = generalRepository,
            cityRepository = cityRepository,
            nationRepository = nationRepository,
        )

        // Build a real pipeline with the steps that the tests verify,
        // backed by the mocked service instances defined above.
        val pipeline = TurnPipeline(listOf(
            EconomyPostUpdateStep(economyService),
            DisasterAndTradeStep(economyService),
            DiplomacyStep(diplomacyService),
            GeneralMaintenanceStep(generalMaintenanceService, specialAssignmentService, inheritanceService, worldPortFactory),
        ))

        service = TurnService(
            worldStateRepository,
            generalRepository,
            generalTurnRepository,
            nationTurnRepository,
            cityRepository,
            nationRepository,
            commandExecutor,
            commandRegistry,
            scenarioService,
            economyService,
            eventService,
            diplomacyService,
            generalMaintenanceService,
            specialAssignmentService,
            npcSpawnService,
            unificationService,
            inheritanceService,
            yearbookService,
            auctionService,
            tournamentService,
            mock(TrafficSnapshotRepository::class.java),
            generalAI,
            nationAI,
            mock(com.opensam.engine.modifier.ModifierService::class.java),
            mock(WorldService::class.java),
            mock(com.opensam.service.NationService::class.java),
            mock(com.opensam.engine.war.BattleService::class.java),
            uniqueLotteryService,
            mock(com.opensam.service.CommandLogDispatcher::class.java),
            mock(com.opensam.service.GameConstService::class.java),
            mock(GeneralAccessLogRepository::class.java),
            pipeline,
            mock(com.opensam.engine.war.FieldBattleTrigger::class.java),
        )
        // Default: worldStateRepository.save returns the argument
        `when`(worldStateRepository.save(anyNonNull<WorldState>())).thenAnswer { it.arguments[0] }
    }

    private fun createWorld(
        year: Short = 200,
        month: Short = 6,
        tickSeconds: Int = 300,
        updatedAt: OffsetDateTime = OffsetDateTime.now().minusSeconds(600),
    ): WorldState {
        return WorldState(
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
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        assertEquals(7.toShort(), world.currentMonth, "Month should advance from 6 to 7")
        assertEquals(200.toShort(), world.currentYear, "Year should stay 200")
    }

    @Test
    fun `processWorld advances year when month goes past 12`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 12, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())

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
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        assertEquals(5.toShort(), world.currentMonth, "Month should advance by 2")
    }

    // ========== processWorld calls services ==========

    @Test
    fun `processWorld calls economy and diplomacy services`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())

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
            General(id = 1, worldId = 1, name = "테스트", nationId = 1, cityId = 1, turnTime = OffsetDateTime.now())
        )

        `when`(generalRepository.findByWorldId(1L)).thenReturn(generals)
        `when`(generalTurnRepository.findByGeneralIdOrderByTurnIdx(1L)).thenReturn(emptyList())
        `when`(cityRepository.findById(1L)).thenReturn(java.util.Optional.empty())

        service.processWorld(world)

        verify(generalMaintenanceService).processGeneralMaintenance(anyNonNull(), anyList())
    }

    // ========== processWorld: service failure resilience ==========

    @Test
    fun `processWorld continues when economyService throws`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())
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
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())
        doThrow(RuntimeException("Event error")).`when`(eventService).dispatchEvents(anyNonNull(), anyString())

        assertDoesNotThrow { service.processWorld(world) }
        assertEquals(7.toShort(), world.currentMonth)
    }

    // ========== processWorld: saves world at end ==========

    @Test
    fun `processWorld saves world state`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        verify(worldStateRepository).save(world)
    }

    // ========== processWorld: updatedAt progression ==========

    @Test
    fun `processWorld updates world updatedAt by tickDuration`() {
        val now = OffsetDateTime.now()
        val startTime = now.minusSeconds(400)
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = startTime)
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())

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
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())

        val nation = Nation(
            id = 1, worldId = 1, name = "위", color = "#FF0000",
            strategicCmdLimit = 5,
        )
        `when`(nationRepository.findByWorldId(1L)).thenReturn(listOf(nation))

        service.processWorld(world)

        val captor = ArgumentCaptor.forClass(Nation::class.java)
        verify(nationRepository).save(captor.capture())
        assertEquals(4.toShort(), captor.value.strategicCmdLimit, "Strategic limit should decrement by 1")
    }

    @Test
    fun `processWorld does not decrement strategic limit below zero`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())

        val nation = Nation(
            id = 1, worldId = 1, name = "위", color = "#FF0000",
            strategicCmdLimit = 0,
        )
        `when`(nationRepository.findByWorldId(1L)).thenReturn(listOf(nation))

        service.processWorld(world)

        val captor = ArgumentCaptor.forClass(Nation::class.java)
        verify(nationRepository).save(captor.capture())
        assertEquals(0.toShort(), captor.value.strategicCmdLimit, "Strategic limit should stay at 0")
    }

    @Test
    fun `processWorld decrements strategic limit for each catch-up turn`() {
        val now = OffsetDateTime.now()
        // 2 ticks elapsed
        val world = createWorld(year = 200, month = 3, tickSeconds = 300, updatedAt = now.minusSeconds(700))
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())

        val nation = Nation(
            id = 1, worldId = 1, name = "위", color = "#FF0000",
            strategicCmdLimit = 10,
        )
        `when`(nationRepository.findByWorldId(1L)).thenReturn(listOf(nation))

        service.processWorld(world)

        val captor = ArgumentCaptor.forClass(Nation::class.java)
        verify(nationRepository, times(2)).save(captor.capture())
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

        val general = General(
            id = 1, worldId = 1, name = "가오픈장수", nationId = 1, cityId = 1,
            killTurn = 6, npcState = 0, turnTime = now.plusSeconds(100),
        )
        `when`(generalRepository.findByWorldId(1L)).thenReturn(listOf(general))

        service.processWorld(world)

        assertEquals(80.toShort(), general.killTurn, "killTurn should be reset to global value (80)")
        assertEquals(true, world.meta["openKillTurnReset"], "openKillTurnReset flag should be set")
    }

    @Test
    fun `processWorld does not reset killTurn when flag already set`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 3600, updatedAt = now.minusSeconds(4000))
        world.meta["openKillTurnReset"] = true

        val general = General(
            id = 1, worldId = 1, name = "기존장수", nationId = 1, cityId = 1,
            killTurn = 6, npcState = 0, turnTime = now.plusSeconds(100),
        )
        `when`(generalRepository.findByWorldId(1L)).thenReturn(listOf(general))

        service.processWorld(world)

        assertEquals(6.toShort(), general.killTurn, "killTurn should NOT be reset when flag already set")
    }

    @Test
    fun `processWorld does not lower existing high killTurn on reset`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 3600, updatedAt = now.minusSeconds(4000))

        val general = General(
            id = 1, worldId = 1, name = "NPC장수", nationId = 1, cityId = 1,
            killTurn = 200, npcState = 2, turnTime = now.plusSeconds(100),
        )
        `when`(generalRepository.findByWorldId(1L)).thenReturn(listOf(general))

        service.processWorld(world)

        assertEquals(200.toShort(), general.killTurn, "killTurn above global should not be lowered")
    }

    // ========== triggerTournament ==========

    @Test
    fun `triggerTournament delegates to tournamentService checkAndTriggerTournament`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        // checkAndTriggerTournament should be called once per turn processed
        verify(tournamentService, atLeastOnce()).checkAndTriggerTournament(anyNonNull())
    }

    // ========== registerAuction ==========

    @Test
    fun `registerAuction queries active auctions and may open system auction`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())
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
        val general = General(
            id = 1,
            worldId = 1,
            name = "봉쇄장수",
            nationId = 1,
            cityId = 1,
            blockState = 2,
            turnTime = originalTurnTime,
        )

        `when`(generalRepository.findByWorldId(1L)).thenReturn(listOf(general))
        `when`(generalRepository.findById(1L)).thenReturn(java.util.Optional.of(general))
        `when`(cityRepository.findById(1L)).thenReturn(java.util.Optional.empty())

        service.processWorld(world)

        val captor = ArgumentCaptor.forClass(General::class.java)
        verify(generalRepository, atLeastOnce()).save(captor.capture())
        assertTrue(captor.allValues.any { it.id == 1L && it.turnTime == originalTurnTime.plusSeconds(300) })
    }

    @Test
    fun `processWorld fires fireCommand consumed after consuming a nation turn`() {
        val gameEventService = mock(GameEventService::class.java)
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))

        // Officer-level 5 general whose turnTime is well before nextTurnAt (updatedAt+tick = now-100s)
        val general = General(
            id = 1,
            worldId = 1,
            name = "대신",
            nationId = 1,
            cityId = 1,
            officerLevel = 5,
            npcState = 0,
            turnTime = now.minusSeconds(500),
        )
        val nation = com.opensam.entity.Nation(id = 1, worldId = 1, name = "위", color = "#FF0000")
        val nationTurnEntry = com.opensam.entity.NationTurn(
            worldId = 1,
            nationId = 1,
            officerLevel = 5,
            turnIdx = 0,
            actionCode = "Nation휴식",
            brief = "Nation휴식",
        )

        `when`(generalRepository.findByWorldId(1L)).thenReturn(listOf(general))
        `when`(nationRepository.findByWorldId(1L)).thenReturn(listOf(nation))
        // nationCache is populated via cityCache/nationCache inside executeGeneralCommandsUntil
        `when`(nationRepository.findById(1L)).thenReturn(java.util.Optional.of(nation))
        `when`(nationTurnRepository.findByNationIdAndOfficerLevelOrderByTurnIdx(1L, 5)).thenReturn(listOf(nationTurnEntry))
        `when`(generalTurnRepository.findByGeneralIdOrderByTurnIdx(1L)).thenReturn(emptyList())
        `when`(cityRepository.findByWorldId(1L)).thenReturn(emptyList())
        // hasNationCommand("Nation휴식") must return true so nationActionCode is set
        `when`(commandRegistry.hasNationCommand("Nation휴식")).thenReturn(true)

        val yearbookService = mock(YearbookService::class.java)
        val worldPortFactory = JpaWorldPortFactory(
            generalRepository = generalRepository,
            cityRepository = cityRepository,
            nationRepository = nationRepository,
        )
        val pipeline = TurnPipeline(listOf(
            EconomyPostUpdateStep(economyService),
            DisasterAndTradeStep(economyService),
            DiplomacyStep(diplomacyService),
            GeneralMaintenanceStep(generalMaintenanceService, specialAssignmentService, inheritanceService, worldPortFactory),
        ))

        val serviceWithEvent = TurnService(
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
            trafficSnapshotRepository = mock(TrafficSnapshotRepository::class.java),
            worldPortFactory = worldPortFactory,
            generalAI = generalAI,
            nationAI = nationAI,
            modifierService = mock(com.opensam.engine.modifier.ModifierService::class.java),
            worldService = mock(WorldService::class.java),
            nationService = mock(com.opensam.service.NationService::class.java),
            battleService = mock(com.opensam.engine.war.BattleService::class.java),
            uniqueLotteryService = uniqueLotteryService,
            commandLogDispatcher = mock(com.opensam.service.CommandLogDispatcher::class.java),
            gameConstService = mock(com.opensam.service.GameConstService::class.java),
            generalAccessLogRepository = mock(GeneralAccessLogRepository::class.java),
            turnPipeline = pipeline,
            fieldBattleTrigger = mock(com.opensam.engine.war.FieldBattleTrigger::class.java),
            gameEventService = gameEventService,
        )
        `when`(worldStateRepository.save(anyNonNull<WorldState>())).thenAnswer { it.arguments[0] }

        serviceWithEvent.processWorld(world)

        // Commands execute before month advances: month is still 6 at time of fireCommand
        verify(gameEventService).fireCommand(
            worldId = 1L,
            year = 200.toShort(),
            month = 6.toShort(),
            generalId = 1L,
            commandEventType = "consumed",
            detail = mapOf("actionCode" to "Nation휴식", "nationId" to 1L),
        )
    }

    // ========== checkWander ==========

    @Test
    fun `checkWander dissolves wander nation when year ge startYear plus 2`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 202, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        world.config["startYear"] = 200

        val chief = General(
            id = 10, worldId = 1, name = "방랑군주", nationId = 5, cityId = 1,
            officerLevel = 20, npcState = 0, turnTime = now,
        )
        val wanderNation = Nation(id = 5, worldId = 1, name = "방랑", color = "#888888", level = 0)

        `when`(generalRepository.findByWorldId(1L)).thenReturn(listOf(chief))
        `when`(nationRepository.findById(5L)).thenReturn(java.util.Optional.of(wanderNation))

        service.processWorld(world)

        // checkWander should have called createGeneralCommand("해산") for the wander chief
        verify(commandRegistry, atLeastOnce()).createGeneralCommand(eq("해산"), anyNonNull(), anyNonNull(), anyOrNull())
    }

    @Test
    fun `checkWander does nothing when year lt startYear plus 2`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 201, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        world.config["startYear"] = 200

        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        verify(commandRegistry, never()).createGeneralCommand(eq("해산"), anyNonNull(), anyNonNull(), anyOrNull())
    }

    @Test
    fun `checkWander skips non-wander nation`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 202, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        world.config["startYear"] = 200

        val chief = General(
            id = 10, worldId = 1, name = "정상군주", nationId = 5, cityId = 1,
            officerLevel = 20, npcState = 0, turnTime = now,
        )
        val regularNation = Nation(id = 5, worldId = 1, name = "위", color = "#FF0000", level = 3)

        `when`(generalRepository.findByWorldId(1L)).thenReturn(listOf(chief))
        `when`(nationRepository.findById(5L)).thenReturn(java.util.Optional.of(regularNation))

        service.processWorld(world)

        verify(commandRegistry, never()).createGeneralCommand(eq("해산"), anyNonNull(), anyNonNull(), anyOrNull())
    }

    // ========== updateOnline ==========

    @Test
    fun `updateOnline sets online count and nation string in world meta`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))

        val log1 = com.opensam.entity.GeneralAccessLog(id = 1, generalId = 1, worldId = 1, accessedAt = now.minusSeconds(10))
        val log2 = com.opensam.entity.GeneralAccessLog(id = 2, generalId = 2, worldId = 1, accessedAt = now.minusSeconds(20))
        val general1 = General(id = 1, worldId = 1, name = "장수1", nationId = 1, cityId = 1, turnTime = now)
        val general2 = General(id = 2, worldId = 1, name = "장수2", nationId = 2, cityId = 1, turnTime = now)
        val nation1 = Nation(id = 1, worldId = 1, name = "위", color = "#FF0000")
        val nation2 = Nation(id = 2, worldId = 1, name = "촉", color = "#00FF00")

        val accessLogRepo = mock(GeneralAccessLogRepository::class.java)
        `when`(accessLogRepo.findByWorldId(1L)).thenReturn(listOf(log1, log2))
        `when`(generalRepository.findByWorldId(1L)).thenReturn(listOf(general1, general2))
        `when`(nationRepository.findByWorldId(1L)).thenReturn(listOf(nation1, nation2))

        // Build a service instance with the custom accessLogRepo
        val yearbookService = mock(YearbookService::class.java)
        val worldPortFactory = JpaWorldPortFactory(
            generalRepository = generalRepository,
            cityRepository = cityRepository,
            nationRepository = nationRepository,
        )
        val pipeline = com.opensam.engine.turn.TurnPipeline(listOf(
            EconomyPostUpdateStep(economyService),
            DisasterAndTradeStep(economyService),
            DiplomacyStep(diplomacyService),
            GeneralMaintenanceStep(generalMaintenanceService, specialAssignmentService, inheritanceService, worldPortFactory),
        ))
        val svcWithLog = TurnService(
            worldStateRepository, generalRepository, generalTurnRepository, nationTurnRepository,
            cityRepository, nationRepository, commandExecutor, commandRegistry,
            scenarioService, economyService, eventService, diplomacyService,
            generalMaintenanceService, specialAssignmentService, npcSpawnService,
            unificationService, inheritanceService, yearbookService, auctionService,
            tournamentService, mock(TrafficSnapshotRepository::class.java),
            worldPortFactory, generalAI, nationAI,
            mock(com.opensam.engine.modifier.ModifierService::class.java),
            mock(WorldService::class.java),
            mock(com.opensam.service.NationService::class.java),
            mock(com.opensam.engine.war.BattleService::class.java),
            uniqueLotteryService,
            mock(com.opensam.service.CommandLogDispatcher::class.java),
            mock(com.opensam.service.GameConstService::class.java),
            accessLogRepo,
            pipeline,
            mock(com.opensam.engine.war.FieldBattleTrigger::class.java),
        )
        `when`(worldStateRepository.save(anyNonNull<WorldState>())).thenAnswer { it.arguments[0] }

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
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())

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
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())

        service.processWorld(world)

        val refreshLimit = (world.meta["refreshLimit"] as? Number)?.toInt()
        assertNotNull(refreshLimit, "refreshLimit should be set even with default coef")
        assertEquals(920, refreshLimit)
    }

    // ========== updateGeneralNumber ==========

    @Test
    fun `updateGeneralNumber updates nation gennum to count of non-npcState5 generals`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))

        val gen1 = General(id = 1, worldId = 1, name = "장수1", nationId = 1, cityId = 1, npcState = 0, turnTime = now)
        val gen2 = General(id = 2, worldId = 1, name = "장수2", nationId = 1, cityId = 1, npcState = 2, turnTime = now)
        val gen3 = General(id = 3, worldId = 1, name = "죽은장수", nationId = 1, cityId = 1, npcState = 5, turnTime = now)
        val gen4 = General(id = 4, worldId = 1, name = "장수3", nationId = 2, cityId = 1, npcState = 0, turnTime = now)
        val nation1 = Nation(id = 1, worldId = 1, name = "위", color = "#FF0000", gennum = 0)
        val nation2 = Nation(id = 2, worldId = 1, name = "촉", color = "#00FF00", gennum = 0)

        `when`(generalRepository.findByWorldId(1L)).thenReturn(listOf(gen1, gen2, gen3, gen4))
        `when`(nationRepository.findByWorldId(1L)).thenReturn(listOf(nation1, nation2))

        service.processWorld(world)

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Nation>>
        verify(nationRepository, atLeastOnce()).saveAll(captor.capture())
        val saved = captor.value
        val savedNation1 = saved.find { it.id == 1L }
        val savedNation2 = saved.find { it.id == 2L }
        // nation1: gen1(npc=0) + gen2(npc=2) = 2 (gen3 excluded as npc=5)
        assertEquals(2, savedNation1?.gennum, "Nation1 should have 2 active generals")
        // nation2: gen4(npc=0) = 1
        assertEquals(1, savedNation2?.gennum, "Nation2 should have 1 active general")
    }

    @Test
    fun `updateGeneralNumber sets zero for nation with no active generals`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))

        val nation1 = Nation(id = 1, worldId = 1, name = "위", color = "#FF0000", gennum = 5)
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())
        `when`(nationRepository.findByWorldId(1L)).thenReturn(listOf(nation1))

        service.processWorld(world)

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Nation>>
        verify(nationRepository, atLeastOnce()).saveAll(captor.capture())
        val saved = captor.value
        assertEquals(0, saved.find { it.id == 1L }?.gennum, "Nation gennum should be 0 when no generals")
    }

    /** Helper: Mockito eq() wrapper returning non-null for Kotlin. */
    private fun <T> eq(value: T): T = org.mockito.ArgumentMatchers.eq(value) ?: value

    /** Helper: Mockito anyOrNull() for nullable parameters. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyOrNull(): T? = any<T>() as T?
}
