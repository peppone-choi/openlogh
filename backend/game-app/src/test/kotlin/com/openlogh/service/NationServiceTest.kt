package com.openlogh.service

import com.openlogh.entity.AppUser
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.SessionStateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.Optional

class NationServiceTest {
    private lateinit var factionRepository: FactionRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var officerRankService: OfficerRankService
    private lateinit var planetRepository: PlanetRepository
    private lateinit var diplomacyRepository: DiplomacyRepository
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var mapService: MapService
    private lateinit var service: FactionService

    @BeforeEach
    fun setUp() {
        factionRepository = mock(FactionRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        appUserRepository = mock(AppUserRepository::class.java)
        officerRankService = mock(OfficerRankService::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        diplomacyRepository = mock(DiplomacyRepository::class.java)
        sessionStateRepository = mock(SessionStateRepository::class.java)
        mapService = mock(MapService::class.java)

        service = FactionService(
            factionRepository = factionRepository,
            officerRepository = officerRepository,
            appUserRepository = appUserRepository,
            officerRankService = officerRankService,
            planetRepository = planetRepository,
            diplomacyRepository = diplomacyRepository,
            sessionStateRepository = sessionStateRepository,
            mapService = mapService,
        )
    }

    @Test
    fun `getPolicy prefers legacy notice and scout message keys`() {
        val nation = Faction(
            id = 1,
            sessionId = 1,
            name = "위",
            conscriptionRate = 20,
            taxRate = 80,
            secretLimit = 3,
            strategicCmdLimit = 12,
            meta = mutableMapOf(
                "notice" to "plain notice",
                "nationNotice" to mutableMapOf<String, Any>("msg" to "legacy notice"),
                "scoutMsg" to "camel scout",
                "scout_msg" to "legacy scout",
            ),
        )
        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(nation))

        val policy = service.getPolicy(1L)

        assertEquals("legacy notice", policy!!.notice)
        assertEquals("legacy scout", policy.scoutMsg)
    }

    @Test
    fun `verifyPolicyAccess allows ambassador in same nation`() {
        val nation = Faction(id = 1, sessionId = 1, name = "위")
        val user = AppUser(id = 7, loginId = "tester", displayName = "Tester", passwordHash = "hash")
        val general = Officer(
            id = 100,
            sessionId = 1,
            userId = 7,
            factionId = 1,
            name = "사절",
            permission = "ambassador",
            officerLevel = 1,
            turnTime = OffsetDateTime.now(),
        )
        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(nation))
        `when`(appUserRepository.findByLoginId("tester")).thenReturn(user)
        `when`(officerRepository.findBySessionIdAndUserId(1L, 7L)).thenReturn(listOf(general))

        val allowed = service.verifyPolicyAccess(1L, "tester")

        assertTrue(allowed)
    }

    @Test
    fun `verifyPolicyAccess rejects penalized chief`() {
        val nation = Faction(id = 1, sessionId = 1, name = "위")
        val user = AppUser(id = 8, loginId = "chief", displayName = "Chief", passwordHash = "hash")
        val general = Officer(
            id = 101,
            sessionId = 1,
            userId = 8,
            factionId = 1,
            name = "군주",
            officerLevel = 20,
            penalty = mutableMapOf("noChief" to true),
            turnTime = OffsetDateTime.now(),
        )
        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(nation))
        `when`(appUserRepository.findByLoginId("chief")).thenReturn(user)
        `when`(officerRepository.findBySessionIdAndUserId(1L, 8L)).thenReturn(listOf(general))

        val allowed = service.verifyPolicyAccess(1L, "chief")

        assertFalse(allowed)
    }

    @Test
    fun `updateNotice stores legacy nationNotice envelope`() {
        val nation = Faction(id = 1, sessionId = 1, name = "위")
        val author = Officer(id = 9, sessionId = 1, factionId = 1, name = "조조", turnTime = OffsetDateTime.now())
        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(nation))

        val updated = service.updateNotice(1L, "새 방침", author)

        assertTrue(updated)
        @Suppress("UNCHECKED_CAST")
        val notice = nation.meta["nationNotice"] as Map<String, Any>
        assertEquals("새 방침", nation.meta["notice"])
        assertEquals("새 방침", notice["msg"])
        assertEquals("조조", notice["author"])
        assertEquals(9L, notice["authorID"])
        assertTrue((notice["date"] as String).isNotBlank())
        verify(factionRepository).save(nation)
    }

    @Test
    fun `updateNotice escapes html before persisting`() {
        val nation = Faction(id = 1, sessionId = 1, name = "위")
        val author = Officer(id = 9, sessionId = 1, factionId = 1, name = "조조", turnTime = OffsetDateTime.now())
        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(nation))

        service.updateNotice(1L, "<script>alert(1)</script>", author)

        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", nation.meta["notice"])
        @Suppress("UNCHECKED_CAST")
        val notice = nation.meta["nationNotice"] as Map<String, Any>
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", notice["msg"])
    }

    @Test
    fun `updateBlockScout respects world lock flag`() {
        val nation = Faction(id = 1, sessionId = 1, name = "위", scoutLevel = 0)
        val world = SessionState(id = 1, name = "world", scenarioCode = "test", config = mutableMapOf("blockChangeScout" to true))
        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(nation))
        `when`(sessionStateRepository.findById(1)).thenReturn(Optional.of(world))

        val result = service.updateBlockScout(1L, true)

        assertFalse(result.success)
        assertEquals("임관 설정을 바꿀 수 없도록 설정되어 있습니다.", result.reason)
        assertEquals(0.toShort(), nation.scoutLevel)
        verify(factionRepository, never()).save(nation)
    }

    @Test
    fun `updateBlockWar decrements available count and updates war flag`() {
        val nation = Faction(
            id = 1,
            sessionId = 1,
            name = "위",
            warState = 0,
            meta = mutableMapOf("available_war_setting_cnt" to 2),
        )
        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(nation))

        val result = service.updateBlockWar(1L, true)

        assertTrue(result.success)
        assertEquals(1, result.availableCnt)
        assertEquals(1.toShort(), nation.warState)
        assertEquals(1, nation.meta["available_war_setting_cnt"])

        val captor = ArgumentCaptor.forClass(Nation::class.java)
        verify(factionRepository).save(captor.capture())
        assertEquals(1.toShort(), captor.value.warState)
    }
}
