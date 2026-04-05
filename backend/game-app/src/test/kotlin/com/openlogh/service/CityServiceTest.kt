package com.openlogh.service

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.model.CityConst
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FactionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime

class CityServiceTest {
    private lateinit var planetRepository: PlanetRepository
    private lateinit var mapService: MapService
    private lateinit var officerRepository: OfficerRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var planetService: PlanetService

    @BeforeEach
    fun setUp() {
        planetRepository = mock(PlanetRepository::class.java)
        mapService = mock(MapService::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)

        `when`(mapService.getCities("che")).thenReturn(
            listOf(
                CityConst(
                    id = 9,
                    name = "남피",
                    level = 7,
                    region = 1,
                    population = 1,
                    agriculture = 1,
                    commerce = 1,
                    security = 1,
                    defence = 1,
                    wall = 1,
                    x = 0,
                    y = 0,
                    connections = emptyList(),
                ),
            ),
        )

        planetService = PlanetService(planetRepository, mapService, officerRepository, factionRepository)
    }

    @Test
    fun `canonicalRegionForDisplay maps 남피 to 하북 code`() {
        val city = Planet(id = 1, sessionId = 1, name = "남피", region = 4)

        val canonicalRegion = planetService.canonicalRegionForDisplay(city)

        assertEquals(1.toShort(), canonicalRegion)
    }

    @Test
    fun `neutral city is masked for generals with a nation`() {
        val neutralCity = Planet(id = 1, sessionId = 1, name = "낙양", factionId = 0, production = 500, commerce = 300)
        val myCity = Planet(id = 2, sessionId = 1, name = "서주", factionId = 10, production = 400, commerce = 200)
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(neutralCity, myCity))

        val general = Officer(
            id = 1, sessionId = 1, name = "장수", factionId = 10, planetId = 2,
            turnTime = OffsetDateTime.now(),
        )

        val result = planetService.listByWorldMaskedForGeneral(1L, general)
        val maskedNeutral = result.find { it.id == 1L }!!
        val ownCity = result.find { it.id == 2L }!!

        assertEquals(0, maskedNeutral.production, "Neutral city agri should be masked")
        assertEquals(0, maskedNeutral.commerce, "Neutral city comm should be masked")
        assertEquals(400, ownCity.production, "Own nation city should not be masked")
    }

    @Test
    fun `own city is visible even if neutral general`() {
        val city = Planet(id = 1, sessionId = 1, name = "낙양", factionId = 0, production = 500)
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(city))

        val general = Officer(
            id = 1, sessionId = 1, name = "장수", factionId = 0, planetId = 1,
            turnTime = OffsetDateTime.now(),
        )

        val result = planetService.listByWorldMaskedForGeneral(1L, general)
        assertEquals(500, result.first().production, "General's own city should be fully visible")
    }
}
