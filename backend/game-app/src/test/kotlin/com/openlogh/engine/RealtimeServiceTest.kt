package com.openlogh.engine

import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandRegistry
import com.openlogh.engine.modifier.ModifierService
import com.openlogh.entity.*
import com.openlogh.repository.*
import com.openlogh.service.GameEventService
import com.openlogh.service.ScenarioService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import java.util.*

class RealtimeServiceTest {

    private lateinit var service: RealtimeService
    private lateinit var officerRepository: OfficerRepository
    private lateinit var officerTurnRepository: OfficerTurnRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var commandRegistry: CommandRegistry
    private lateinit var gameEventService: GameEventService
    private lateinit var scenarioService: ScenarioService
    private lateinit var modifierService: ModifierService

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T = any<T>() as T

    @BeforeEach
    fun setUp() {
        officerRepository = mock(OfficerRepository::class.java)
        officerTurnRepository = mock(OfficerTurnRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        sessionStateRepository = mock(SessionStateRepository::class.java)
        commandExecutor = mock(CommandExecutor::class.java)
        commandRegistry = mock(CommandRegistry::class.java)
        gameEventService = mock(GameEventService::class.java)
        scenarioService = mock(ScenarioService::class.java)
        modifierService = mock(ModifierService::class.java)

        service = RealtimeService(
            officerRepository,
            officerTurnRepository,
            planetRepository,
            factionRepository,
            sessionStateRepository,
            commandExecutor,
            commandRegistry,
            gameEventService,
            scenarioService,
            modifierService,
            mock(com.openlogh.service.CommandLogDispatcher::class.java),
            mock(com.openlogh.service.GameConstService::class.java),
        )
    }

    private fun createOfficer(
        id: Long = 1,
        sessionId: Long = 1,
        commandEndTime: OffsetDateTime? = null,
        commandPoints: Int = 10,
        rank: Short = 1,
    ): Officer {
        return Officer(
            id = id,
            sessionId = sessionId,
            name = "테스트",
            factionId = 1,
            planetId = 1,
            commandEndTime = commandEndTime,
            commandPoints = commandPoints,
            rank = rank,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createWorld(realtimeMode: Boolean = true): SessionState {
        return SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 6,
            tickSeconds = 300,
            realtimeMode = realtimeMode,
            commandPointRegenRate = 1,
        )
    }

    @Test
    fun `submitCommand fails when world is not in realtime mode`() {
        val officer = createOfficer()
        val world = createWorld(realtimeMode = false)

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(officer))
        `when`(sessionStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))

        val result = service.submitCommand(1L, "징병", null)

        assertFalse(result.success)
        assertTrue(result.logs.any { it.contains("realtime") })
    }

    @Test
    fun `submitCommand fails when command already in progress`() {
        val officer = createOfficer(commandEndTime = OffsetDateTime.now().plusMinutes(5))
        val world = createWorld()

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(officer))
        `when`(sessionStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))

        val result = service.submitCommand(1L, "징병", null)

        assertFalse(result.success)
        assertTrue(result.logs.any { it.contains("in progress") })
    }

    @Test
    fun `submitNationCommand fails when officer level too low`() {
        val officer = createOfficer(rank = 3)

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(officer))

        val result = service.submitNationCommand(1L, "천도", null)

        assertFalse(result.success)
        assertTrue(result.logs.any { it.contains("권한") })
    }

    @Test
    fun `regenerateCommandPoints increases command points up to cap`() {
        val world = createWorld()
        world.commandPointRegenRate = 5
        val officer = createOfficer(commandPoints = 97)

        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(officer))

        service.regenerateCommandPoints(world)

        assertEquals(100, officer.commandPoints, "Should cap at 100")
        verify(officerRepository).save(officer)
    }

    @Test
    fun `getRealtimeStatus returns null for unknown general`() {
        `when`(officerRepository.findById(999L)).thenReturn(Optional.empty())

        val result = service.getRealtimeStatus(999L)

        assertNull(result)
    }

    @Test
    fun `executePreOpenCommand does not require realtimeMode`() {
        val officer = createOfficer()
        val world = createWorld(realtimeMode = false)

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(officer))
        `when`(sessionStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))
        `when`(planetRepository.findById(1L)).thenReturn(Optional.empty())
        `when`(factionRepository.findById(1L)).thenReturn(Optional.empty())
        `when`(commandRegistry.createGeneralCommand(anyNonNull(), anyNonNull(), anyNonNull(), anyNonNull()))
            .thenThrow(IllegalArgumentException("Unknown command"))

        try {
            service.executePreOpenCommand(1L, "거병")
        } catch (_: IllegalArgumentException) {
            // Expected: command registry throws for unknown command in test
        }
    }
}
