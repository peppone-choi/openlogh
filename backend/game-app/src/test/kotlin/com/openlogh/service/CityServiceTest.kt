package com.openlogh.service

import com.openlogh.entity.City
import com.openlogh.entity.General
import com.openlogh.model.CityConst
import com.openlogh.repository.CityRepository
import com.openlogh.repository.GeneralRepository
import com.openlogh.repository.NationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime

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

    @Test
    fun `neutral city is masked for generals with a nation`() {
        val neutralCity = City(id = 1, worldId = 1, name = "낙양", nationId = 0, agri = 500, comm = 300)
        val myCity = City(id = 2, worldId = 1, name = "서주", nationId = 10, agri = 400, comm = 200)
        `when`(cityRepository.findByWorldId(1L)).thenReturn(listOf(neutralCity, myCity))

        val general = General(
            id = 1, worldId = 1, name = "장수", nationId = 10, cityId = 2,
            turnTime = OffsetDateTime.now(),
        )

        val result = cityService.listByWorldMaskedForGeneral(1L, general)
        val maskedNeutral = result.find { it.id == 1L }!!
        val ownCity = result.find { it.id == 2L }!!

        assertEquals(0, maskedNeutral.agri, "Neutral city agri should be masked")
        assertEquals(0, maskedNeutral.comm, "Neutral city comm should be masked")
        assertEquals(400, ownCity.agri, "Own nation city should not be masked")
    }

    @Test
    fun `own city is visible even if neutral general`() {
        val city = City(id = 1, worldId = 1, name = "낙양", nationId = 0, agri = 500)
        `when`(cityRepository.findByWorldId(1L)).thenReturn(listOf(city))

        val general = General(
            id = 1, worldId = 1, name = "장수", nationId = 0, cityId = 1,
            turnTime = OffsetDateTime.now(),
        )

        val result = cityService.listByWorldMaskedForGeneral(1L, general)
        assertEquals(500, result.first().agri, "General's own city should be fully visible")
    }
}
