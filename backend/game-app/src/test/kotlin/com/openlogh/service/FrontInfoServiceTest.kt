package com.openlogh.service

import com.openlogh.entity.AppUser
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.model.ScenarioData
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.RecordRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.SessionStateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.Optional

class FrontInfoServiceTest {
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var fleetRepository: FleetRepository
    private lateinit var officerRankService: OfficerRankService
    private lateinit var scenarioService: ScenarioService
    private lateinit var planetService: PlanetService
    private lateinit var service: FrontInfoService

    @BeforeEach
    fun setUp() {
        sessionStateRepository = mock(SessionStateRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        messageRepository = mock(MessageRepository::class.java)
        appUserRepository = mock(AppUserRepository::class.java)
        fleetRepository = mock(FleetRepository::class.java)
        officerRankService = mock(OfficerRankService::class.java)
        scenarioService = mock(ScenarioService::class.java)
        planetService = mock(PlanetService::class.java)

        service = FrontInfoService(
            sessionStateRepository,
            officerRepository,
            factionRepository,
            planetRepository,
            messageRepository,
            mock(RecordRepository::class.java),
            appUserRepository,
            fleetRepository,
            officerRankService,
            scenarioService,
            planetService,
        )
    }

    private fun buildWorld() = SessionState(
        id = 1,
        scenarioCode = "test",
        currentYear = 180,
        currentMonth = 1,
        config = mutableMapOf("startyear" to 180),
    )

    private fun buildUser() = AppUser(id = 10, loginId = "tester", displayName = "테스터", passwordHash = "pw")
    private fun buildNation() = Faction(id = 7, sessionId = 1, name = "세력", factionRank = 1)
    private fun buildCity() = Planet(id = 5, sessionId = 1, name = "도시", factionId = 7, level = 5)

    private fun stubCommon(world: SessionState, user: AppUser, nation: Faction, city: Planet, general: Officer) {
        `when`(sessionStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(appUserRepository.findByLoginId("tester")).thenReturn(user)
        `when`(officerRepository.findBySessionId(1)).thenReturn(listOf(general))
        `when`(factionRepository.findBySessionId(1)).thenReturn(listOf(nation))
        `when`(planetRepository.findById(5)).thenReturn(Optional.of(city))
        `when`(planetRepository.findBySessionId(1)).thenReturn(listOf(city))
        `when`(planetService.canonicalRegionForDisplay(city)).thenReturn(city.region)
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
        val general = Officer(
            id = 11, sessionId = 1, userId = 10, factionId = 7, planetId = 5,
            name = "장수", shipClass = 3, turnTime = OffsetDateTime.now(),
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
        val general = Officer(
            id = 11, sessionId = 1, userId = 10, factionId = 7, planetId = 5,
            name = "장수", shipClass = 3, turnTime = OffsetDateTime.now(),
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
