package com.openlogh.service

import com.openlogh.entity.*
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.SessionStateRepository
import com.openlogh.repository.WorldHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

class WorldServiceTest {
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var worldHistoryRepository: WorldHistoryRepository
    private lateinit var service: WorldService

    @BeforeEach
    fun setUp() {
        sessionStateRepository = mock(SessionStateRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        worldHistoryRepository = mock(WorldHistoryRepository::class.java)

        service = WorldService(
            sessionStateRepository = sessionStateRepository,
            factionRepository = factionRepository,
            planetRepository = planetRepository,
            officerRepository = officerRepository,
            worldHistoryRepository = worldHistoryRepository,
        )
    }

    @Test
    fun `deleteWorld calls repository deleteById`() {
        service.deleteWorld(1.toShort())

        verify(sessionStateRepository).deleteById(1.toShort())
    }

    @Test
    fun `deleteWorld does not throw when world does not exist`() {
        // deleteById is void and doesn't throw for missing entities by default
        service.deleteWorld(99.toShort())

        verify(sessionStateRepository).deleteById(99.toShort())
    }

    private fun createWorld(config: MutableMap<String, Any> = mutableMapOf()): SessionState {
        return SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 6,
            tickSeconds = 300,
            config = config,
        )
    }

    @Test
    fun `getGamePhase returns CLOSED when startTime is in the future`() {
        val future = java.time.OffsetDateTime.now().plusHours(2).toString()
        val world = createWorld(mutableMapOf("startTime" to future, "opentime" to java.time.OffsetDateTime.now().plusDays(1).toString()))

        val phase = service.getGamePhase(world)

        assertEquals(WorldService.PHASE_CLOSED, phase)
    }

    @Test
    fun `getGamePhase returns PRE_OPEN when startTime passed but opentime in future`() {
        val past = java.time.OffsetDateTime.now().minusHours(1).toString()
        val future = java.time.OffsetDateTime.now().plusDays(1).toString()
        val world = createWorld(mutableMapOf("startTime" to past, "opentime" to future))

        val phase = service.getGamePhase(world)

        assertEquals(WorldService.PHASE_PRE_OPEN, phase)
    }

    @Test
    fun `getGamePhase returns PRE_OPEN when no startTime but opentime in future`() {
        val future = java.time.OffsetDateTime.now().plusDays(1).toString()
        val world = createWorld(mutableMapOf("opentime" to future))

        val phase = service.getGamePhase(world)

        assertEquals(WorldService.PHASE_PRE_OPEN, phase)
    }

    @Test
    fun `getGamePhase returns OPENING when opentime has passed and within opening years`() {
        val past = java.time.OffsetDateTime.now().minusHours(1).toString()
        val world = createWorld(mutableMapOf("opentime" to past, "startYear" to 200))
        `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())

        val phase = service.getGamePhase(world)

        assertEquals(WorldService.PHASE_OPENING, phase)
    }
}
