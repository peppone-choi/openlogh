package com.openlogh.service

import com.openlogh.entity.*
import com.openlogh.model.ScenarioData
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.RecordRepository
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
    private fun buildFaction() = Faction(id = 7, sessionId = 1, name = "세력")
    private fun buildPlanet() = Planet(id = 5, sessionId = 1, name = "도시", factionId = 7, level = 5)

    private fun stubCommon(world: SessionState, user: AppUser, faction: Faction, planet: Planet, officer: Officer) {
        `when`(sessionStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))
        `when`(appUserRepository.findByLoginId("tester")).thenReturn(user)
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(officer))
        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findById(5L)).thenReturn(Optional.of(planet))
        `when`(planetRepository.findByFactionId(7L)).thenReturn(listOf(planet))
        `when`(planetService.canonicalRegionForDisplay(planet)).thenReturn(planet.region)
        `when`(scenarioService.getScenario("test")).thenReturn(ScenarioData(title = "테스트 시나리오"))
        `when`(officerRankService.getRankTitle(0, 1)).thenReturn("무품관")
        `when`(messageRepository.findByDestIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(officer.id, "general_action", 0)).thenReturn(emptyList())
        `when`(messageRepository.findBySessionIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(1L, "world_record", 0L)).thenReturn(emptyList())
        `when`(messageRepository.findBySessionIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(1L, "world_history", 0L)).thenReturn(emptyList())
    }

    @Test
    fun `getFrontInfo returns numeric shipClass string`() {
        val world = buildWorld()
        val user = buildUser()
        val faction = buildFaction()
        val planet = buildPlanet()
        val officer = Officer(
            id = 11, sessionId = 1, userId = 10, factionId = 7, planetId = 5,
            name = "장수", shipClass = 3, turnTime = OffsetDateTime.now(),
        )
        stubCommon(world, user, faction, planet, officer)

        val response = service.getFrontInfo(worldId = 1, loginId = "tester", lastRecordId = null, lastHistoryId = null)

        assertNotNull(response.officer)
        assertEquals("3", response.officer!!.shipClass)
    }

    @Test
    fun `getFrontInfo resolves item codes to display names`() {
        val world = buildWorld()
        val user = buildUser()
        val faction = buildFaction()
        val planet = buildPlanet()
        val officer = Officer(
            id = 11, sessionId = 1, userId = 10, factionId = 7, planetId = 5,
            name = "장수", shipClass = 3, turnTime = OffsetDateTime.now(),
        ).apply {
            flagshipCode = "che_무기_15_의천검"
            equipCode = "che_회피_태평요술"
            engineCode = "che_명마_15_적토마"
            accessoryCode = "None"
            personalCode = "che_패권"
            specialCode = "농업"
            special2Code = "che_기병"
        }
        stubCommon(world, user, faction, planet, officer)

        val response = service.getFrontInfo(worldId = 1, loginId = "tester", lastRecordId = null, lastHistoryId = null)

        assertNotNull(response.officer)
        val g = response.officer!!
        assertEquals("의천검", g.flagship, "flagship code should resolve via ItemModifiers")
        assertEquals("태평요술", g.equipment, "equipment code should resolve via ItemModifiers")
        assertEquals("적토마", g.engine, "engine code should resolve via ItemModifiers")
        assertEquals("None", g.accessory, "None has no underscore so stays as-is")
        assertEquals("패권", g.personal, "che_패권 should resolve via PersonalityModifiers")
        assertEquals("농업", g.specialDomestic, "농업 should resolve via SpecialModifiers")
        assertEquals("기병", g.specialWar, "che_기병 should resolve via SpecialModifiers")
    }
}
