package com.openlogh.service

import com.openlogh.entity.Fleet
import com.openlogh.entity.Planet
import com.openlogh.model.UnitType
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.PlanetRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class FormationCapServiceTest {

    private lateinit var service: FormationCapService
    private lateinit var planetRepository: PlanetRepository
    private lateinit var fleetRepository: FleetRepository

    @BeforeEach
    fun setUp() {
        planetRepository = mock(PlanetRepository::class.java)
        fleetRepository = mock(FleetRepository::class.java)
        service = FormationCapService(planetRepository, fleetRepository)
    }

    private fun planetWithPop(pop: Int, factionId: Long = 1L, sessionId: Long = 1L): Planet {
        return Planet().apply {
            this.population = pop
            this.factionId = factionId
            this.sessionId = sessionId
        }
    }

    private fun fleetWithType(unitType: String, factionId: Long = 1L, sessionId: Long = 1L): Fleet {
        return Fleet().apply {
            this.unitType = unitType
            this.factionId = factionId
            this.sessionId = sessionId
        }
    }

    @Nested
    inner class GetFormationCaps {

        @Test
        fun `2 billion population allows 2 fleets, 2 transports, 12 patrols, 12 ground forces`() {
            // 2 billion = population value 200_000 (200,000 * 10,000 = 2,000,000,000)
            `when`(planetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(listOf(planetWithPop(200_000)))
            `when`(fleetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(emptyList())

            val caps = service.getFormationCaps(1L, 1L)

            assertEquals(2, caps[UnitType.FLEET]!!.max)
            assertEquals(2, caps[UnitType.TRANSPORT]!!.max)
            assertEquals(12, caps[UnitType.PATROL]!!.max)
            assertEquals(12, caps[UnitType.GROUND]!!.max)
        }

        @Test
        fun `half billion population allows 0 fleets and 3 patrols`() {
            // 0.5 billion = population value 50_000
            `when`(planetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(listOf(planetWithPop(50_000)))
            `when`(fleetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(emptyList())

            val caps = service.getFormationCaps(1L, 1L)

            assertEquals(0, caps[UnitType.FLEET]!!.max)
            assertEquals(0, caps[UnitType.TRANSPORT]!!.max)
            assertEquals(3, caps[UnitType.PATROL]!!.max)
            assertEquals(3, caps[UnitType.GROUND]!!.max)
        }

        @Test
        fun `returns current count of existing units`() {
            `when`(planetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(listOf(planetWithPop(200_000)))
            `when`(fleetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(listOf(
                    fleetWithType("FLEET"),
                    fleetWithType("FLEET"),
                    fleetWithType("PATROL"),
                ))

            val caps = service.getFormationCaps(1L, 1L)

            assertEquals(2, caps[UnitType.FLEET]!!.current)
            assertEquals(0, caps[UnitType.FLEET]!!.available)
            assertEquals(1, caps[UnitType.PATROL]!!.current)
            assertEquals(11, caps[UnitType.PATROL]!!.available)
        }
    }

    @Nested
    inner class CanFormUnit {

        @Test
        fun `returns false when fleet cap is reached`() {
            `when`(planetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(listOf(planetWithPop(200_000)))
            `when`(fleetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(listOf(fleetWithType("FLEET"), fleetWithType("FLEET")))

            assertFalse(service.canFormUnit(1L, 1L, UnitType.FLEET))
        }

        @Test
        fun `returns true when fleet cap has room`() {
            `when`(planetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(listOf(planetWithPop(200_000)))
            `when`(fleetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(listOf(fleetWithType("FLEET")))

            assertTrue(service.canFormUnit(1L, 1L, UnitType.FLEET))
        }

        @Test
        fun `garrison always returns true regardless of population`() {
            `when`(planetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(emptyList())
            `when`(fleetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(emptyList())

            assertTrue(service.canFormUnit(1L, 1L, UnitType.GARRISON))
        }

        @Test
        fun `solo always returns true regardless of population`() {
            `when`(planetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(emptyList())
            `when`(fleetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(emptyList())

            assertTrue(service.canFormUnit(1L, 1L, UnitType.SOLO))
        }
    }

    @Nested
    inner class GetFactionPopulation {

        @Test
        fun `sums population from multiple planets`() {
            `when`(planetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(listOf(
                    planetWithPop(100_000),
                    planetWithPop(50_000),
                    planetWithPop(25_000),
                ))

            val pop = service.getFactionPopulation(1L, 1L)

            assertEquals(175_000L, pop)
        }

        @Test
        fun `returns 0 for faction with no planets`() {
            `when`(planetRepository.findBySessionIdAndFactionId(1L, 1L))
                .thenReturn(emptyList())

            assertEquals(0L, service.getFactionPopulation(1L, 1L))
        }
    }
}
