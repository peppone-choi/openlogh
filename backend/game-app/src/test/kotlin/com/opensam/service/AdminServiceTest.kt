package com.opensam.service

import com.opensam.dto.ResourceDistributionRequest
import com.opensam.dto.TimeControlRequest
import com.opensam.engine.EventActionService
import com.opensam.entity.General
import com.opensam.entity.GeneralTurn
import com.opensam.entity.WorldState
import com.opensam.repository.AppUserRepository
import com.opensam.repository.CityRepository
import com.opensam.repository.DiplomacyRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.GeneralTurnRepository
import com.opensam.repository.HallOfFameRepository
import com.opensam.repository.MessageRepository
import com.opensam.repository.NationRepository
import com.opensam.repository.WorldStateRepository
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

    private lateinit var worldStateRepository: WorldStateRepository
    private lateinit var generalRepository: GeneralRepository
    private lateinit var generalTurnRepository: GeneralTurnRepository
    private lateinit var nationRepository: NationRepository
    private lateinit var cityRepository: CityRepository
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var diplomacyRepository: DiplomacyRepository
    private lateinit var hallOfFameRepository: HallOfFameRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var eventActionService: EventActionService
    private lateinit var inheritanceService: InheritanceService
    private lateinit var service: AdminService

    @BeforeEach
    fun setUp() {
        worldStateRepository = mock(WorldStateRepository::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        generalTurnRepository = mock(GeneralTurnRepository::class.java)
        nationRepository = mock(NationRepository::class.java)
        cityRepository = mock(CityRepository::class.java)
        appUserRepository = mock(AppUserRepository::class.java)
        diplomacyRepository = mock(DiplomacyRepository::class.java)
        hallOfFameRepository = mock(HallOfFameRepository::class.java)
        messageRepository = mock(MessageRepository::class.java)
        eventActionService = mock(EventActionService::class.java)
        inheritanceService = mock(InheritanceService::class.java)

        service = AdminService(
            worldStateRepository,
            generalRepository,
            generalTurnRepository,
            nationRepository,
            cityRepository,
            appUserRepository,
            diplomacyRepository,
            hallOfFameRepository,
            messageRepository,
            eventActionService,
            inheritanceService,
        )
    }

    @Test
    fun `getDashboard exposes derived turn term aliases`() {
        val world = WorldState(
            id = 1,
            name = "world-1",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 6,
            tickSeconds = 300,
            config = mutableMapOf("locked" to true),
        )
        `when`(worldStateRepository.findAll()).thenReturn(listOf(world))

        val dashboard = service.getDashboard(1L)

        val currentWorld = dashboard.currentWorld
        assertNotNull(currentWorld)
        assertEquals(5, currentWorld!!.config["turnTerm"])
        assertEquals(5, currentWorld.config["turnterm"])
        assertEquals(true, currentWorld.config["locked"])
    }

    @Test
    fun `timeControl updates timing config and distributes resources to generals`() {
        val world = WorldState(
            id = 1,
            name = "world-1",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 6,
            tickSeconds = 300,
            config = mutableMapOf(),
        )
        val general1 = General(
            id = 1,
            worldId = 1,
            name = "장수1",
            gold = 100,
            rice = 200,
            turnTime = OffsetDateTime.now(),
        )
        val general2 = General(
            id = 2,
            worldId = 1,
            name = "장수2",
            gold = 300,
            rice = 400,
            turnTime = OffsetDateTime.now(),
        )

        `when`(worldStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))
        `when`(generalRepository.findByWorldId(1L)).thenReturn(listOf(general1, general2))

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
        assertEquals(150, general1.gold)
        assertEquals(275, general1.rice)
        assertEquals(350, general2.gold)
        assertEquals(475, general2.rice)
        verify(worldStateRepository).save(world)
        verify(generalRepository).saveAll(listOf(general1, general2))
    }

    @Test
    fun `timeControl rejects invalid distribute target`() {
        val world = WorldState(
            id = 1,
            name = "world-1",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 6,
            tickSeconds = 300,
        )
        `when`(worldStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))

        val result = service.timeControl(
            1L,
            TimeControlRequest(
                distribute = ResourceDistributionRequest(gold = 10, rice = 10, target = "invalid"),
            )
        )

        assertFalse(result)
        verify(worldStateRepository, never()).save(world)
    }

    @Test
    fun `generalAction block applies legacy stage one semantics`() {
        val general = General(
            id = 7,
            worldId = 1,
            name = "장수",
            blockState = 0,
            killTurn = null,
            turnTime = OffsetDateTime.now(),
        )
        `when`(generalRepository.findById(7L)).thenReturn(Optional.of(general))

        val result = service.generalAction(1L, 7L, "block")

        assertTrue(result)
        assertEquals(1.toShort(), general.blockState)
        assertEquals(24.toShort(), general.killTurn)
        verify(generalRepository).save(general)
    }

    @Test
    fun `generalAction unblock clears block state but preserves kill turn`() {
        val general = General(
            id = 8,
            worldId = 1,
            name = "장수",
            blockState = 3,
            killTurn = 11,
            turnTime = OffsetDateTime.now(),
        )
        `when`(generalRepository.findById(8L)).thenReturn(Optional.of(general))

        val result = service.generalAction(1L, 8L, "unblock")

        assertTrue(result)
        assertEquals(0.toShort(), general.blockState)
        assertEquals(11.toShort(), general.killTurn)
        verify(generalRepository).save(general)
    }

    @Test
    fun `generalAction kill schedules forced death and rewrites first turn`() {
        val initialTurnTime = OffsetDateTime.now().minusDays(1)
        val general = General(
            id = 9,
            worldId = 1,
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
        `when`(generalRepository.findById(9L)).thenReturn(Optional.of(general))
        `when`(generalTurnRepository.findByGeneralIdOrderByTurnIdx(9L)).thenReturn(listOf(queuedTurn))

        val result = service.generalAction(1L, 9L, "kill")

        assertTrue(result)
        assertEquals(0.toShort(), general.killTurn)
        assertTrue(general.turnTime.isAfter(initialTurnTime))

        val captor = ArgumentCaptor.forClass(GeneralTurn::class.java)
        verify(generalTurnRepository).save(captor.capture())
        val savedTurn = captor.value
        assertEquals(0.toShort(), savedTurn.turnIdx)
        assertEquals("휴식", savedTurn.actionCode)
        assertEquals("휴식", savedTurn.brief)
        assertTrue(savedTurn.arg.isEmpty())
        verify(generalRepository).save(general)
    }

    @Test
    fun `generalAction supports resignation and dismissal queue edits`() {
        val general = General(
            id = 10,
            worldId = 1,
            name = "장수",
            turnTime = OffsetDateTime.now(),
        )
        `when`(generalRepository.findById(10L)).thenReturn(Optional.of(general))
        `when`(generalTurnRepository.findByGeneralIdOrderByTurnIdx(10L)).thenReturn(emptyList())

        val resignResult = service.generalAction(1L, 10L, "resign")
        val wanderResult = service.generalAction(1L, 10L, "wanderDismiss")

        assertTrue(resignResult)
        assertTrue(wanderResult)

        val captor = ArgumentCaptor.forClass(GeneralTurn::class.java)
        verify(generalTurnRepository, org.mockito.Mockito.times(3)).save(captor.capture())
        val savedTurns = captor.allValues
        assertEquals(listOf("che_하야", "che_방랑", "che_해산"), savedTurns.map { it.actionCode })
        assertEquals(listOf<Short>(0, 0, 1), savedTurns.map { it.turnIdx })
    }

    @Test
    fun `generalAction rejects unsupported action`() {
        val general = General(
            id = 11,
            worldId = 1,
            name = "장수",
            turnTime = OffsetDateTime.now(),
        )
        `when`(generalRepository.findById(11L)).thenReturn(Optional.of(general))

        val result = service.generalAction(1L, 11L, "unknown")

        assertFalse(result)
        assertNull(general.killTurn)
        verify(generalRepository, never()).save(general)
    }
}
