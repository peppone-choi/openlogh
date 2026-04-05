package com.openlogh.service

import com.openlogh.dto.ResourceDistributionRequest
import com.openlogh.dto.TimeControlRequest
import com.openlogh.engine.EventActionService
import com.openlogh.entity.Officer
import com.openlogh.entity.GeneralTurn
import com.openlogh.entity.SessionState
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.OfficerTurnRepository
import com.openlogh.repository.HallOfFameRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.HistoryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.Optional

class AdminServiceTest {

    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var officerTurnRepository: OfficerTurnRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var diplomacyRepository: DiplomacyRepository
    private lateinit var hallOfFameRepository: HallOfFameRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var eventActionService: EventActionService
    private lateinit var inheritanceService: InheritanceService
    private lateinit var service: AdminService

    @BeforeEach
    fun setUp() {
        sessionStateRepository = mock(SessionStateRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        officerTurnRepository = mock(OfficerTurnRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        appUserRepository = mock(AppUserRepository::class.java)
        diplomacyRepository = mock(DiplomacyRepository::class.java)
        hallOfFameRepository = mock(HallOfFameRepository::class.java)
        messageRepository = mock(MessageRepository::class.java)
        eventActionService = mock(EventActionService::class.java)
        inheritanceService = mock(InheritanceService::class.java)
        val historyService = mock(HistoryService::class.java)

        service = AdminService(
            sessionStateRepository,
            officerRepository,
            officerTurnRepository,
            factionRepository,
            planetRepository,
            appUserRepository,
            diplomacyRepository,
            hallOfFameRepository,
            messageRepository,
            eventActionService,
            inheritanceService,
            historyService,
        )
    }

    @Test
    fun `getDashboard exposes derived turn term aliases`() {
        val world = SessionState(
            id = 1,
            name = "world-1",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 6,
            tickSeconds = 300,
            config = mutableMapOf("locked" to true),
        )
        `when`(sessionStateRepository.findAll()).thenReturn(listOf(world))

        val dashboard = service.getDashboard(1L)

        val currentWorld = dashboard.currentWorld
        assertNotNull(currentWorld)
        assertEquals(5, currentWorld!!.config["turnTerm"])
        assertEquals(5, currentWorld.config["turnterm"])
        assertEquals(true, currentWorld.config["locked"])
    }

    @Test
    fun `timeControl updates timing config and distributes resources to generals`() {
        val world = SessionState(
            id = 1,
            name = "world-1",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 6,
            tickSeconds = 300,
            config = mutableMapOf(),
        )
        val general1 = Officer(
            id = 1,
            sessionId = 1,
            name = "장수1",
            funds = 100,
            supplies = 200,
            turnTime = OffsetDateTime.now(),
        )
        val general2 = Officer(
            id = 2,
            sessionId = 1,
            name = "장수2",
            funds = 300,
            supplies = 400,
            turnTime = OffsetDateTime.now(),
        )

        `when`(sessionStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(general1, general2))

        val result = service.timeControl(
            1L,
            TimeControlRequest(
                year = 210,
                month = 7,
                startYear = 190,
                locked = true,
                turnTerm = 10,
                distribute = ResourceDistributionRequest(gold = 50, rice = 75, target = "all"),
                auctionSync = true,
                auctionCloseMinutes = 45,
                opentime = "2026-04-01T00:00:00Z",
                startTime = "2026-03-25T00:00:00Z",
                reserveOpen = "2026-04-01T00:00",
                preReserveOpen = "2026-03-25T00:00",
            )
        )

        assertTrue(result)
        assertEquals(210.toShort(), world.currentYear)
        assertEquals(7.toShort(), world.currentMonth)
        assertEquals(600, world.tickSeconds)
        assertEquals(190, world.config["startYear"])
        assertEquals(190, world.config["startyear"])
        assertEquals(true, world.config["locked"])
        assertEquals(10, world.config["turnTerm"])
        assertEquals(10, world.config["turnterm"])
        assertEquals(true, world.config["auctionSync"])
        assertEquals(45, world.config["auctionCloseMinutes"])
        assertEquals("2026-04-01T00:00:00Z", world.config["opentime"])
        assertEquals("2026-03-25T00:00:00Z", world.config["startTime"])
        assertEquals("2026-04-01T00:00", world.config["reserveOpen"])
        assertEquals("2026-03-25T00:00", world.config["preReserveOpen"])
        assertEquals(150, general1.funds)
        assertEquals(275, general1.supplies)
        assertEquals(350, general2.funds)
        assertEquals(475, general2.supplies)
        verify(sessionStateRepository).save(world)
        verify(officerRepository).saveAll(listOf(general1, general2))
    }

    @Test
    fun `timeControl rejects invalid distribute target`() {
        val world = SessionState(
            id = 1,
            name = "world-1",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 6,
            tickSeconds = 300,
        )
        `when`(sessionStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))

        val result = service.timeControl(
            1L,
            TimeControlRequest(
                distribute = ResourceDistributionRequest(gold = 10, rice = 10, target = "invalid"),
            )
        )

        assertFalse(result)
        verify(sessionStateRepository, never()).save(world)
    }

    @Test
    fun `generalAction block applies legacy stage one semantics`() {
        val general = Officer(
            id = 7,
            sessionId = 1,
            name = "장수",
            blockState = 0,
            killTurn = null,
            turnTime = OffsetDateTime.now(),
        )
        `when`(officerRepository.findById(7L)).thenReturn(Optional.of(general))

        val result = service.generalAction(1L, 7L, "block")

        assertTrue(result)
        assertEquals(1.toShort(), general.blockState)
        assertEquals(24.toShort(), general.killTurn)
        verify(officerRepository).save(general)
    }

    @Test
    fun `generalAction unblock clears block state but preserves kill turn`() {
        val general = Officer(
            id = 8,
            sessionId = 1,
            name = "장수",
            blockState = 3,
            killTurn = 11,
            turnTime = OffsetDateTime.now(),
        )
        `when`(officerRepository.findById(8L)).thenReturn(Optional.of(general))

        val result = service.generalAction(1L, 8L, "unblock")

        assertTrue(result)
        assertEquals(0.toShort(), general.blockState)
        assertEquals(11.toShort(), general.killTurn)
        verify(officerRepository).save(general)
    }

    @Test
    fun `generalAction kill schedules forced death and rewrites first turn`() {
        val initialTurnTime = OffsetDateTime.now().minusDays(1)
        val general = Officer(
            id = 9,
            sessionId = 1,
            name = "장수",
            turnTime = initialTurnTime,
        )
        val queuedTurn = GeneralTurn(
            id = 1,
            worldId = 1,
            generalId = 9,
            turnIdx = 0,
            actionCode = "che_출병",
            arg = mutableMapOf("dest" to 12),
            brief = "출병",
        )
        `when`(officerRepository.findById(9L)).thenReturn(Optional.of(general))
        `when`(officerTurnRepository.findByOfficerIdOrderByTurnIdx(9L)).thenReturn(listOf(queuedTurn))

        val result = service.generalAction(1L, 9L, "kill")

        assertTrue(result)
        assertEquals(0.toShort(), general.killTurn)
        assertTrue(general.turnTime.isAfter(initialTurnTime))

        val captor = ArgumentCaptor.forClass(GeneralTurn::class.java)
        verify(officerTurnRepository).save(captor.capture())
        val savedTurn = captor.value
        assertEquals(0.toShort(), savedTurn.turnIdx)
        assertEquals("휴식", savedTurn.actionCode)
        assertEquals("휴식", savedTurn.brief)
        assertTrue(savedTurn.arg.isEmpty())
        verify(officerRepository).save(general)
    }

    @Test
    fun `generalAction supports resignation and dismissal queue edits`() {
        val general = Officer(
            id = 10,
            sessionId = 1,
            name = "장수",
            turnTime = OffsetDateTime.now(),
        )
        `when`(officerRepository.findById(10L)).thenReturn(Optional.of(general))
        `when`(officerTurnRepository.findByOfficerIdOrderByTurnIdx(10L)).thenReturn(emptyList())

        val resignResult = service.generalAction(1L, 10L, "resign")
        val wanderResult = service.generalAction(1L, 10L, "wanderDismiss")

        assertTrue(resignResult)
        assertTrue(wanderResult)

        val captor = ArgumentCaptor.forClass(GeneralTurn::class.java)
        verify(officerTurnRepository, org.mockito.Mockito.times(3)).save(captor.capture())
        val savedTurns = captor.allValues
        assertEquals(listOf("che_하야", "che_방랑", "che_해산"), savedTurns.map { it.actionCode })
        assertEquals(listOf<Short>(0, 0, 1), savedTurns.map { it.turnIdx })
    }

    @Test
    fun `generalAction rejects unsupported action`() {
        val general = Officer(
            id = 11,
            sessionId = 1,
            name = "장수",
            turnTime = OffsetDateTime.now(),
        )
        `when`(officerRepository.findById(11L)).thenReturn(Optional.of(general))

        val result = service.generalAction(1L, 11L, "unknown")

        assertFalse(result)
        assertNull(general.killTurn)
        verify(officerRepository, never()).save(general)
    }
}
