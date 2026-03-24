package com.openlogh.engine

import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandRegistry
import com.openlogh.engine.ai.OfficerAI
import com.openlogh.engine.ai.FactionAI
import com.openlogh.repository.TrafficSnapshotRepository
import com.openlogh.service.WorldService
import com.openlogh.entity.*
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
        val auctionService = mock(com.openlogh.service.AuctionService::class.java)
        val tournamentService = mock(com.openlogh.service.TournamentService::class.java)

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
            CommandPointService(officerRepository),
            AgeGrowthService(officerRepository),
            mock(com.openlogh.engine.modifier.OfficerLevelModifier::class.java),
            mock(com.openlogh.service.VictoryService::class.java),
            mock(com.openlogh.service.RankLadderService::class.java),
            mock(com.openlogh.engine.SafeZoneService::class.java),
            mock(com.openlogh.engine.planet.PlanetProductionService::class.java),
            mock(com.openlogh.engine.FezzanNeutralityService::class.java),
            mock(com.openlogh.engine.fleet.TransportExecutionService::class.java),
            mock(com.openlogh.engine.CoupExecutionService::class.java),
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
        val officers = listOf(
            Officer(id = 1, sessionId = 1, name = "테스트", factionId = 1, planetId = 1, turnTime = OffsetDateTime.now())
        )

        `when`(officerRepository.findBySessionId(1L)).thenReturn(officers)
        `when`(officerTurnRepository.findByOfficerIdOrderByTurnIdx(1L)).thenReturn(emptyList())
        `when`(planetRepository.findById(1L)).thenReturn(java.util.Optional.empty())

        service.processWorld(world)

        verify(officerMaintenanceService).processOfficerMaintenance(anyNonNull(), anyList())
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

    // ========== strategic command limit reset ==========

    @Test
    fun `processWorld decrements strategic command limits`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = now.minusSeconds(400))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        val faction = Faction(
            id = 1, sessionId = 1, name = "위", color = "#FF0000",
            strategicCmdLimit = 5,
        )
        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))

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

        val faction = Faction(
            id = 1, sessionId = 1, name = "위", color = "#FF0000",
            strategicCmdLimit = 0,
        )
        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))

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

        val faction = Faction(
            id = 1, sessionId = 1, name = "위", color = "#FF0000",
            strategicCmdLimit = 10,
        )
        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))

        service.processWorld(world)

        val captor = ArgumentCaptor.forClass(Faction::class.java)
        verify(factionRepository, times(2)).save(captor.capture())
        val saved = captor.allValues.last()
        assertTrue(saved.strategicCmdLimit <= 9, "Strategic limit should be decremented during catch-up turns")
    }

    // ========== 가오픈→오픈 killTurn 리셋 ==========

    @Test
    fun `processWorld resets low killTurn on first open after pre-open`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 3600, updatedAt = now.minusSeconds(4000))
        // tickSeconds=3600 → turnterm=60 → globalKillTurn=4800/60=80

        val officer = Officer(
            id = 1, sessionId = 1, name = "가오픈장수", factionId = 1, planetId = 1,
            killTurn = 6, npcState = 0, turnTime = now.plusSeconds(100),
        )
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(officer))

        service.processWorld(world)

        assertEquals(80.toShort(), officer.killTurn, "killTurn should be reset to global value (80)")
        assertEquals(true, world.meta["openKillTurnReset"], "openKillTurnReset flag should be set")
    }

    @Test
    fun `processWorld does not reset killTurn when flag already set`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 3600, updatedAt = now.minusSeconds(4000))
        world.meta["openKillTurnReset"] = true

        val officer = Officer(
            id = 1, sessionId = 1, name = "기존장수", factionId = 1, planetId = 1,
            killTurn = 6, npcState = 0, turnTime = now.plusSeconds(100),
        )
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(officer))

        service.processWorld(world)

        assertEquals(6.toShort(), officer.killTurn, "killTurn should NOT be reset when flag already set")
    }

    @Test
    fun `processWorld does not lower existing high killTurn on reset`() {
        val now = OffsetDateTime.now()
        val world = createWorld(year = 200, month = 6, tickSeconds = 3600, updatedAt = now.minusSeconds(4000))

        val officer = Officer(
            id = 1, sessionId = 1, name = "NPC장수", factionId = 1, planetId = 1,
            killTurn = 200, npcState = 2, turnTime = now.plusSeconds(100),
        )
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(officer))

        service.processWorld(world)

        assertEquals(200.toShort(), officer.killTurn, "killTurn above global should not be lowered")
    }

    @Test
    fun `processWorld preserves per general turn offset for blocked generals`() {
        val updatedAt = OffsetDateTime.now().withNano(0).minusSeconds(400)
        val world = createWorld(year = 200, month = 6, tickSeconds = 300, updatedAt = updatedAt)
        val originalTurnTime = updatedAt.plusSeconds(180)
        val officer = Officer(
            id = 1,
            sessionId = 1,
            name = "봉쇄장수",
            factionId = 1,
            planetId = 1,
            blockState = 2,
            turnTime = originalTurnTime,
        )

        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(officer))
        `when`(officerRepository.findById(1L)).thenReturn(java.util.Optional.of(officer))
        `when`(planetRepository.findById(1L)).thenReturn(java.util.Optional.empty())

        service.processWorld(world)

        val captor = ArgumentCaptor.forClass(Officer::class.java)
        verify(officerRepository, atLeastOnce()).save(captor.capture())
        assertTrue(captor.allValues.any { it.id == 1L && it.turnTime == originalTurnTime.plusSeconds(300) })
    }
}
