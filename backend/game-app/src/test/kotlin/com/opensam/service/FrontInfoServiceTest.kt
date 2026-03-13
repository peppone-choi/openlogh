package com.opensam.service

import com.opensam.entity.AppUser
import com.opensam.entity.City
import com.opensam.entity.General
import com.opensam.entity.Nation
import com.opensam.entity.WorldState
import com.opensam.model.ScenarioData
import com.opensam.repository.AppUserRepository
import com.opensam.repository.CityRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.MessageRepository
import com.opensam.repository.NationRepository
import com.opensam.repository.TroopRepository
import com.opensam.repository.WorldStateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.Optional

class FrontInfoServiceTest {
    private lateinit var worldStateRepository: WorldStateRepository
    private lateinit var generalRepository: GeneralRepository
    private lateinit var nationRepository: NationRepository
    private lateinit var cityRepository: CityRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var troopRepository: TroopRepository
    private lateinit var officerRankService: OfficerRankService
    private lateinit var scenarioService: ScenarioService
    private lateinit var cityService: CityService
    private lateinit var service: FrontInfoService

    @BeforeEach
    fun setUp() {
        worldStateRepository = mock(WorldStateRepository::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        nationRepository = mock(NationRepository::class.java)
        cityRepository = mock(CityRepository::class.java)
        messageRepository = mock(MessageRepository::class.java)
        appUserRepository = mock(AppUserRepository::class.java)
        troopRepository = mock(TroopRepository::class.java)
        officerRankService = mock(OfficerRankService::class.java)
        scenarioService = mock(ScenarioService::class.java)
        cityService = mock(CityService::class.java)

        service = FrontInfoService(
            worldStateRepository,
            generalRepository,
            nationRepository,
            cityRepository,
            messageRepository,
            appUserRepository,
            troopRepository,
            officerRankService,
            scenarioService,
            cityService,
        )
    }

    @Test
    fun `getFrontInfo returns numeric crewtype string`() {
        val world = WorldState(
            id = 1,
            scenarioCode = "test",
            currentYear = 180,
            currentMonth = 1,
            config = mutableMapOf("startyear" to 180),
        )
        val user = AppUser(id = 10, loginId = "tester", displayName = "테스터", passwordHash = "pw")
        val nation = Nation(id = 7, worldId = 1, name = "세력", level = 1)
        val city = City(id = 5, worldId = 1, name = "도시", nationId = 7, level = 5)
        val general = General(
            id = 11,
            worldId = 1,
            userId = 10,
            nationId = 7,
            cityId = 5,
            name = "장수",
            crewType = 3,
            turnTime = OffsetDateTime.now(),
        )

        `when`(worldStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(appUserRepository.findByLoginId("tester")).thenReturn(user)
        `when`(generalRepository.findByWorldId(1)).thenReturn(listOf(general))
        `when`(nationRepository.findByWorldId(1)).thenReturn(listOf(nation))
        `when`(cityRepository.findById(5)).thenReturn(Optional.of(city))
        `when`(cityRepository.findByWorldId(1)).thenReturn(listOf(city))
        `when`(cityService.canonicalRegionForDisplay(city)).thenReturn(city.region)
        `when`(scenarioService.getScenario("test")).thenReturn(ScenarioData(title = "테스트 시나리오"))
        `when`(officerRankService.getRankTitle(0, 1)).thenReturn("무품관")
        `when`(messageRepository.findByDestIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(11, "general_action", 0)).thenReturn(emptyList())
        `when`(messageRepository.findByWorldIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(1, "world_record", 0)).thenReturn(emptyList())
        `when`(messageRepository.findByWorldIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(1, "world_history", 0)).thenReturn(emptyList())

        val response = service.getFrontInfo(worldId = 1, loginId = "tester", lastRecordId = null, lastHistoryId = null)

        assertNotNull(response.general)
        assertEquals("3", response.general!!.crewtype)
    }
}
