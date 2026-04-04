package com.openlogh.service

import com.openlogh.entity.AppUser
import com.openlogh.entity.City
import com.openlogh.entity.General
import com.openlogh.entity.Nation
import com.openlogh.entity.WorldState
import com.openlogh.model.ScenarioData
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.CityRepository
import com.openlogh.repository.GeneralRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.NationRepository
import com.openlogh.repository.RecordRepository
import com.openlogh.repository.TroopRepository
import com.openlogh.repository.WorldStateRepository
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
            mock(RecordRepository::class.java),
            appUserRepository,
            troopRepository,
            officerRankService,
            scenarioService,
            cityService,
        )
    }

    private fun buildWorld() = WorldState(
        id = 1,
        scenarioCode = "test",
        currentYear = 180,
        currentMonth = 1,
        config = mutableMapOf("startyear" to 180),
    )

    private fun buildUser() = AppUser(id = 10, loginId = "tester", displayName = "테스터", passwordHash = "pw")
    private fun buildNation() = Nation(id = 7, worldId = 1, name = "세력", level = 1)
    private fun buildCity() = City(id = 5, worldId = 1, name = "도시", nationId = 7, level = 5)

    private fun stubCommon(world: WorldState, user: AppUser, nation: Nation, city: City, general: General) {
        `when`(worldStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(appUserRepository.findByLoginId("tester")).thenReturn(user)
        `when`(generalRepository.findByWorldId(1)).thenReturn(listOf(general))
        `when`(nationRepository.findByWorldId(1)).thenReturn(listOf(nation))
        `when`(cityRepository.findById(5)).thenReturn(Optional.of(city))
        `when`(cityRepository.findByWorldId(1)).thenReturn(listOf(city))
        `when`(cityService.canonicalRegionForDisplay(city)).thenReturn(city.region)
        `when`(scenarioService.getScenario("test")).thenReturn(ScenarioData(title = "테스트 시나리오"))
        `when`(officerRankService.getRankTitle(0, 1)).thenReturn("무품관")
        `when`(messageRepository.findByDestIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(general.id, "general_action", 0)).thenReturn(emptyList())
        `when`(messageRepository.findByWorldIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(1, "world_record", 0)).thenReturn(emptyList())
        `when`(messageRepository.findByWorldIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(1, "world_history", 0)).thenReturn(emptyList())
    }

    @Test
    fun `getFrontInfo returns numeric crewtype string`() {
        val world = buildWorld()
        val user = buildUser()
        val nation = buildNation()
        val city = buildCity()
        val general = General(
            id = 11, worldId = 1, userId = 10, nationId = 7, cityId = 5,
            name = "장수", crewType = 3, turnTime = OffsetDateTime.now(),
        )
        stubCommon(world, user, nation, city, general)

        val response = service.getFrontInfo(worldId = 1, loginId = "tester", lastRecordId = null, lastHistoryId = null)

        assertNotNull(response.general)
        assertEquals("3", response.general!!.crewtype)
    }

    @Test
    fun `getFrontInfo resolves item codes to display names`() {
        val world = buildWorld()
        val user = buildUser()
        val nation = buildNation()
        val city = buildCity()
        val general = General(
            id = 11, worldId = 1, userId = 10, nationId = 7, cityId = 5,
            name = "장수", crewType = 3, turnTime = OffsetDateTime.now(),
        ).apply {
            weaponCode = "che_무기_15_의천검"
            bookCode = "che_회피_태평요술"
            horseCode = "che_명마_15_적토마"
            itemCode = "None"
            personalCode = "che_패권"
            specialCode = "농업"
            special2Code = "che_기병"
        }
        stubCommon(world, user, nation, city, general)

        val response = service.getFrontInfo(worldId = 1, loginId = "tester", lastRecordId = null, lastHistoryId = null)

        assertNotNull(response.general)
        val g = response.general!!
        assertEquals("의천검", g.weapon, "weapon code should resolve via ItemModifiers")
        assertEquals("태평요술", g.book, "book code should resolve via ItemModifiers")
        assertEquals("적토마", g.horse, "horse code should resolve via ItemModifiers")
        assertEquals("None", g.item, "None has no underscore so stays as-is")
        assertEquals("패권", g.personal, "che_패권 should resolve via PersonalityModifiers")
        assertEquals("농업", g.specialDomestic, "농업 should resolve via SpecialModifiers")
        assertEquals("기병", g.specialWar, "che_기병 should resolve via SpecialModifiers")
    }
}
