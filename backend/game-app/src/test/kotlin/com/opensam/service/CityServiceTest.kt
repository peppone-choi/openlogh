package com.opensam.service

import com.opensam.entity.City
import com.opensam.model.CityConst
import com.opensam.repository.CityRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.NationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class CityServiceTest {
    private lateinit var cityRepository: CityRepository
    private lateinit var mapService: MapService
    private lateinit var generalRepository: GeneralRepository
    private lateinit var nationRepository: NationRepository
    private lateinit var cityService: CityService

    @BeforeEach
    fun setUp() {
        cityRepository = mock(CityRepository::class.java)
        mapService = mock(MapService::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        nationRepository = mock(NationRepository::class.java)

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

        cityService = CityService(cityRepository, mapService, generalRepository, nationRepository)
    }

    @Test
    fun `canonicalRegionForDisplay maps 남피 to 하북 code`() {
        val city = City(id = 1, worldId = 1, name = "남피", region = 4)

        val canonicalRegion = cityService.canonicalRegionForDisplay(city)

        assertEquals(1.toShort(), canonicalRegion)
    }
}
