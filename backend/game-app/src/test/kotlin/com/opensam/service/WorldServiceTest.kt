package com.opensam.service

import com.opensam.entity.WorldState
import com.opensam.repository.CityRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.NationRepository
import com.opensam.repository.WorldHistoryRepository
import com.opensam.repository.WorldStateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

class WorldServiceTest {
    private lateinit var worldStateRepository: WorldStateRepository
    private lateinit var nationRepository: NationRepository
    private lateinit var cityRepository: CityRepository
    private lateinit var generalRepository: GeneralRepository
    private lateinit var worldHistoryRepository: WorldHistoryRepository
    private lateinit var service: WorldService

    @BeforeEach
    fun setUp() {
        worldStateRepository = mock(WorldStateRepository::class.java)
        nationRepository = mock(NationRepository::class.java)
        cityRepository = mock(CityRepository::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        worldHistoryRepository = mock(WorldHistoryRepository::class.java)

        service = WorldService(
            worldStateRepository = worldStateRepository,
            nationRepository = nationRepository,
            cityRepository = cityRepository,
            generalRepository = generalRepository,
            worldHistoryRepository = worldHistoryRepository,
        )
    }

    @Test
    fun `deleteWorld calls repository deleteById`() {
        service.deleteWorld(1.toShort())

        verify(worldStateRepository).deleteById(1.toShort())
    }

    @Test
    fun `deleteWorld does not throw when world does not exist`() {
        // deleteById is void and doesn't throw for missing entities by default
        service.deleteWorld(99.toShort())

        verify(worldStateRepository).deleteById(99.toShort())
    }

    private fun createWorld(config: MutableMap<String, Any> = mutableMapOf()): WorldState {
        return WorldState(
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
        `when`(nationRepository.findByWorldId(1L)).thenReturn(emptyList())

        val phase = service.getGamePhase(world)

        assertEquals(WorldService.PHASE_OPENING, phase)
    }
}
