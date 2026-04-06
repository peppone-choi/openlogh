package com.openlogh.engine

import com.fasterxml.jackson.databind.ObjectMapper
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import java.util.*

class YearbookServiceTest {

    private lateinit var service: YearbookService
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var yearbookHistoryRepository: YearbookHistoryRepository

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T = any<T>() as T

    @BeforeEach
    fun setUp() {
        sessionStateRepository = mock(SessionStateRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        messageRepository = mock(MessageRepository::class.java)
        yearbookHistoryRepository = mock(YearbookHistoryRepository::class.java)

        service = YearbookService(
            sessionStateRepository,
            planetRepository,
            factionRepository,
            officerRepository,
            messageRepository,
            yearbookHistoryRepository,
            ObjectMapper(),
        )

        `when`(messageRepository.findBySessionIdAndYearAndMonthOrderBySentAtAsc(anyLong(), anyInt(), anyInt()))
            .thenReturn(emptyList())
    }

    private fun createWorld(): SessionState {
        return SessionState(
            id = 1,
            name = "테스트서버",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 6,
            tickSeconds = 300,
            config = mutableMapOf("startYear" to 184),
            meta = mutableMapOf(),
        )
    }

    @Test
    fun `saveMonthlySnapshot throws for unknown world`() {
        `when`(sessionStateRepository.findById(1.toShort())).thenReturn(Optional.empty())

        assertThrows(IllegalArgumentException::class.java) {
            service.saveMonthlySnapshot(1L, 200, 6)
        }
    }

    @Test
    fun `saveMonthlySnapshot creates snapshot with cities and nations`() {
        val world = createWorld()
        `when`(sessionStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(
            listOf(
                Planet(id = 1, sessionId = 1, name = "낙양", level = 5, factionId = 1,
                    population = 5000, populationMax = 10000, production = 3000, productionMax = 5000,
                    commerce = 2000, commerceMax = 5000, security = 1000, securityMax = 3000,
                    fortress = 500, fortressMax = 1000, orbitalDefense = 300, orbitalDefenseMax = 500),
            )
        )
        `when`(factionRepository.findBySessionId(1L)).thenReturn(
            listOf(
                Faction(id = 1, sessionId = 1, name = "위", color = "#FF0000", factionRank = 7, capitalPlanetId = 1),
            )
        )
        `when`(officerRepository.findBySessionId(1L)).thenReturn(
            listOf(
                Officer(id = 1, sessionId = 1, name = "조조", factionId = 1, planetId = 1,
                    leadership = 90, command = 70, intelligence = 90, funds = 5000, supplies = 5000,
                    experience = 1000, dedication = 500, npcState = 0,
                    turnTime = OffsetDateTime.now()),
            )
        )
        `when`(yearbookHistoryRepository.findBySessionIdAndYearAndMonth(1L, 200.toShort(), 6.toShort()))
            .thenReturn(null)
        `when`(yearbookHistoryRepository.save(anyNonNull())).thenAnswer { it.arguments[0] }

        val result = service.saveMonthlySnapshot(1L, 200, 6)

        assertNotNull(result)
        verify(yearbookHistoryRepository).save(anyNonNull())
    }

    @Test
    fun `saveMonthlySnapshot generates non-empty hash`() {
        val world = createWorld()
        `when`(sessionStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(yearbookHistoryRepository.findBySessionIdAndYearAndMonth(1L, 200.toShort(), 6.toShort()))
            .thenReturn(null)
        `when`(yearbookHistoryRepository.save(anyNonNull())).thenAnswer { it.arguments[0] }

        val result = service.saveMonthlySnapshot(1L, 200, 6)

        assertNotNull(result.hash)
        assertTrue(result.hash.isNotBlank(), "Hash should not be blank")
        assertEquals(64, result.hash.length, "SHA-256 hex should be 64 chars")
    }

    @Test
    fun `saveMonthlySnapshot deterministic hash for same data`() {
        val world = createWorld()
        `when`(sessionStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(yearbookHistoryRepository.findBySessionIdAndYearAndMonth(1L, 200.toShort(), 6.toShort()))
            .thenReturn(null)
        `when`(yearbookHistoryRepository.save(anyNonNull())).thenAnswer { it.arguments[0] }

        val result1 = service.saveMonthlySnapshot(1L, 200, 6)
        val result2 = service.saveMonthlySnapshot(1L, 200, 6)

        assertEquals(result1.hash, result2.hash, "Same data should produce same hash")
    }
}
