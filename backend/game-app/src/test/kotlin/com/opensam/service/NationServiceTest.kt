package com.opensam.service

import com.opensam.entity.AppUser
import com.opensam.entity.General
import com.opensam.entity.Nation
import com.opensam.entity.WorldState
import com.opensam.repository.AppUserRepository
import com.opensam.repository.CityRepository
import com.opensam.repository.DiplomacyRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.NationRepository
import com.opensam.repository.WorldStateRepository
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
    private lateinit var nationRepository: NationRepository
    private lateinit var generalRepository: GeneralRepository
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var officerRankService: OfficerRankService
    private lateinit var cityRepository: CityRepository
    private lateinit var diplomacyRepository: DiplomacyRepository
    private lateinit var worldStateRepository: WorldStateRepository
    private lateinit var mapService: MapService
    private lateinit var service: NationService

    @BeforeEach
    fun setUp() {
        nationRepository = mock(NationRepository::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        appUserRepository = mock(AppUserRepository::class.java)
        officerRankService = mock(OfficerRankService::class.java)
        cityRepository = mock(CityRepository::class.java)
        diplomacyRepository = mock(DiplomacyRepository::class.java)
        worldStateRepository = mock(WorldStateRepository::class.java)
        mapService = mock(MapService::class.java)

        service = NationService(
            nationRepository = nationRepository,
            generalRepository = generalRepository,
            appUserRepository = appUserRepository,
            officerRankService = officerRankService,
            cityRepository = cityRepository,
            diplomacyRepository = diplomacyRepository,
            worldStateRepository = worldStateRepository,
            mapService = mapService,
        )
    }

    @Test
    fun `getPolicy prefers legacy notice and scout message keys`() {
        val nation = Nation(
            id = 1,
            worldId = 1,
            name = "위",
            rate = 20,
            bill = 80,
            secretLimit = 3,
            strategicCmdLimit = 12,
            meta = mutableMapOf(
                "notice" to "plain notice",
                "nationNotice" to mutableMapOf<String, Any>("msg" to "legacy notice"),
                "scoutMsg" to "camel scout",
                "scout_msg" to "legacy scout",
            ),
        )
        `when`(nationRepository.findById(1L)).thenReturn(Optional.of(nation))

        val policy = service.getPolicy(1L)

        assertEquals("legacy notice", policy!!.notice)
        assertEquals("legacy scout", policy.scoutMsg)
    }

    @Test
    fun `verifyPolicyAccess allows ambassador in same nation`() {
        val nation = Nation(id = 1, worldId = 1, name = "위")
        val user = AppUser(id = 7, loginId = "tester", displayName = "Tester", passwordHash = "hash")
        val general = General(
            id = 100,
            worldId = 1,
            userId = 7,
            nationId = 1,
            name = "사절",
            permission = "ambassador",
            officerLevel = 1,
            turnTime = OffsetDateTime.now(),
        )
        `when`(nationRepository.findById(1L)).thenReturn(Optional.of(nation))
        `when`(appUserRepository.findByLoginId("tester")).thenReturn(user)
        `when`(generalRepository.findByWorldIdAndUserId(1L, 7L)).thenReturn(listOf(general))

        val allowed = service.verifyPolicyAccess(1L, "tester")

        assertTrue(allowed)
    }

    @Test
    fun `verifyPolicyAccess rejects penalized chief`() {
        val nation = Nation(id = 1, worldId = 1, name = "위")
        val user = AppUser(id = 8, loginId = "chief", displayName = "Chief", passwordHash = "hash")
        val general = General(
            id = 101,
            worldId = 1,
            userId = 8,
            nationId = 1,
            name = "군주",
            officerLevel = 20,
            penalty = mutableMapOf("noChief" to true),
            turnTime = OffsetDateTime.now(),
        )
        `when`(nationRepository.findById(1L)).thenReturn(Optional.of(nation))
        `when`(appUserRepository.findByLoginId("chief")).thenReturn(user)
        `when`(generalRepository.findByWorldIdAndUserId(1L, 8L)).thenReturn(listOf(general))

        val allowed = service.verifyPolicyAccess(1L, "chief")

        assertFalse(allowed)
    }

    @Test
    fun `updateNotice stores legacy nationNotice envelope`() {
        val nation = Nation(id = 1, worldId = 1, name = "위")
        val author = General(id = 9, worldId = 1, nationId = 1, name = "조조", turnTime = OffsetDateTime.now())
        `when`(nationRepository.findById(1L)).thenReturn(Optional.of(nation))

        val updated = service.updateNotice(1L, "새 방침", author)

        assertTrue(updated)
        @Suppress("UNCHECKED_CAST")
        val notice = nation.meta["nationNotice"] as Map<String, Any>
        assertEquals("새 방침", nation.meta["notice"])
        assertEquals("새 방침", notice["msg"])
        assertEquals("조조", notice["author"])
        assertEquals(9L, notice["authorID"])
        assertTrue((notice["date"] as String).isNotBlank())
        verify(nationRepository).save(nation)
    }

    @Test
    fun `updateNotice escapes html before persisting`() {
        val nation = Nation(id = 1, worldId = 1, name = "위")
        val author = General(id = 9, worldId = 1, nationId = 1, name = "조조", turnTime = OffsetDateTime.now())
        `when`(nationRepository.findById(1L)).thenReturn(Optional.of(nation))

        service.updateNotice(1L, "<script>alert(1)</script>", author)

        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", nation.meta["notice"])
        @Suppress("UNCHECKED_CAST")
        val notice = nation.meta["nationNotice"] as Map<String, Any>
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", notice["msg"])
    }

    @Test
    fun `updateBlockScout respects world lock flag`() {
        val nation = Nation(id = 1, worldId = 1, name = "위", scoutLevel = 0)
        val world = WorldState(id = 1, name = "world", scenarioCode = "test", config = mutableMapOf("blockChangeScout" to true))
        `when`(nationRepository.findById(1L)).thenReturn(Optional.of(nation))
        `when`(worldStateRepository.findById(1)).thenReturn(Optional.of(world))

        val result = service.updateBlockScout(1L, true)

        assertFalse(result.success)
        assertEquals("임관 설정을 바꿀 수 없도록 설정되어 있습니다.", result.reason)
        assertEquals(0.toShort(), nation.scoutLevel)
        verify(nationRepository, never()).save(nation)
    }

    @Test
    fun `updateBlockWar decrements available count and updates war flag`() {
        val nation = Nation(
            id = 1,
            worldId = 1,
            name = "위",
            warState = 0,
            meta = mutableMapOf("available_war_setting_cnt" to 2),
        )
        `when`(nationRepository.findById(1L)).thenReturn(Optional.of(nation))

        val result = service.updateBlockWar(1L, true)

        assertTrue(result.success)
        assertEquals(1, result.availableCnt)
        assertEquals(1.toShort(), nation.warState)
        assertEquals(1, nation.meta["available_war_setting_cnt"])

        val captor = ArgumentCaptor.forClass(Nation::class.java)
        verify(nationRepository).save(captor.capture())
        assertEquals(1.toShort(), captor.value.warState)
    }
}
