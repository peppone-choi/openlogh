package com.openlogh.service

import com.openlogh.dto.BuyInheritBuffRequest
import com.openlogh.engine.modifier.TraitSpecRegistry
import com.openlogh.entity.AppUser
import com.openlogh.entity.Officer
import com.openlogh.entity.SessionState
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.RankDataRepository
import com.openlogh.repository.SessionStateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.Optional

class InheritanceServiceTest {
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var rankDataRepository: RankDataRepository
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var gameConstService: GameConstService
    private lateinit var service: InheritanceService

    @BeforeEach
    fun setUp() {
        appUserRepository = mock(AppUserRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        rankDataRepository = mock(RankDataRepository::class.java)
        sessionStateRepository = mock(SessionStateRepository::class.java)
        gameConstService = mock(GameConstService::class.java)

        service = InheritanceService(
            appUserRepository = appUserRepository,
            planetRepository = planetRepository,
            officerRepository = officerRepository,
            rankDataRepository = rankDataRepository,
            sessionStateRepository = sessionStateRepository,
            gameConstService = gameConstService,
        )

        `when`(gameConstService.getInt("inheritSpecificSpecialPoint")).thenReturn(4000)
        `when`(gameConstService.getInt("inheritBornCityPoint")).thenReturn(1000)
        `when`(gameConstService.getInt("inheritItemRandomPoint")).thenReturn(3000)
        `when`(gameConstService.getInt("inheritCheckOwnerPoint")).thenReturn(1000)
        `when`(gameConstService.getInt("inheritItemUniqueMinPoint")).thenReturn(5000)
        `when`(gameConstService.getInt("inheritBornStatPoint")).thenReturn(1000)

        `when`(appUserRepository.save(any(AppUser::class.java))).thenAnswer { it.arguments[0] }
        `when`(officerRepository.save(any(Officer::class.java))).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `resetTurn stores next turn base on current general`() {
        val user = createUser(points = 5000)
        val general = createGeneral(userId = user.id)
        val world = createWorld()
        stubOwnership(user, general, world)

        val result = service.resetTurn(world.id.toLong(), user.loginId)

        assertNull(result?.error)
        assertEquals(4000, result?.remainingPoints)
        assertEquals(4000, user.meta["inheritPoints"])
        assertEquals(0, general.meta["inheritResetTurnTime"])
        assertNotNull(general.meta["nextTurnTimeBase"])
    }

    @Test
    fun `setInheritSpecial reserves special on current general instead of user meta`() {
        val user = createUser(points = 5000)
        val general = createGeneral(userId = user.id)
        val world = createWorld()
        stubOwnership(user, general, world)
        val specialCode = TraitSpecRegistry.war.first().key

        val result = service.setInheritSpecial(world.id.toLong(), user.loginId, specialCode)

        assertNull(result?.error)
        assertEquals(1000, user.meta["inheritPoints"])
        assertEquals(specialCode, general.meta["inheritSpecificSpecialWar"])
        assertEquals(null, user.meta["inheritSpecificSpecialWar"])
    }

    @Test
    fun `buyRandomUnique stores reservation on current general and rejects duplicate`() {
        val user = createUser(points = 7000)
        val general = createGeneral(userId = user.id)
        val world = createWorld()
        stubOwnership(user, general, world)

        val first = service.buyRandomUnique(world.id.toLong(), user.loginId)
        val second = service.buyRandomUnique(world.id.toLong(), user.loginId)

        assertNull(first?.error)
        assertEquals(4000, first?.remainingPoints)
        assertNotNull(general.meta["inheritRandomUnique"])
        assertTrue(second?.error?.contains("이미 구입 명령") == true)
    }

    @Test
    fun `resetSpecialWar clears current special and tracks previous special list`() {
        val user = createUser(points = 5000)
        val general = createGeneral(userId = user.id).apply {
            special2Code = "che_저격"
        }
        val world = createWorld()
        stubOwnership(user, general, world)

        val result = service.resetSpecialWar(world.id.toLong(), user.loginId)

        assertNull(result?.error)
        assertEquals(4000, result?.remainingPoints)
        assertEquals("None", general.special2Code)
        assertEquals(0, general.meta["inheritResetSpecialWar"])
        assertEquals(listOf("che_저격"), general.meta["prev_special2"])
    }

    // ── Phase 24-30 (gap E13): 60세 인계 제한 ──

    @Test
    fun `E13 - buyInheritBuff blocked when current officer age is 60`() {
        val user = createUser(points = 10_000)
        val general = createGeneral(userId = user.id).also { it.age = 60 }
        val world = createWorld()
        stubOwnership(user, general, world)

        val result = service.buyInheritBuff(
            world.id.toLong(), user.loginId,
            BuyInheritBuffRequest(type = "warAvoidRatio", level = 1),
        )

        assertNotNull(result)
        assertNotNull(result!!.error)
        assertTrue(result.error!!.contains("인계 불가"),
            "60 세 cutoff 도달 시 인계 에러 메시지를 반환해야 한다. got: ${result.error}")
        // 포인트는 그대로 유지되어야 한다.
        assertEquals(10_000, user.meta["inheritPoints"])
    }

    @Test
    fun `E13 - buyInheritBuff blocked when current officer age is above 60`() {
        val user = createUser(points = 10_000)
        val general = createGeneral(userId = user.id).also { it.age = 75 }
        val world = createWorld()
        stubOwnership(user, general, world)

        val result = service.buyInheritBuff(
            world.id.toLong(), user.loginId,
            BuyInheritBuffRequest(type = "warAvoidRatio", level = 2),
        )

        assertNotNull(result!!.error)
        assertEquals(10_000, user.meta["inheritPoints"])
    }

    @Test
    fun `E13 - buyInheritBuff allowed when current officer age is 59`() {
        val user = createUser(points = 10_000)
        val general = createGeneral(userId = user.id).also { it.age = 59 }
        val world = createWorld()
        stubOwnership(user, general, world)

        val result = service.buyInheritBuff(
            world.id.toLong(), user.loginId,
            BuyInheritBuffRequest(type = "warAvoidRatio", level = 1),
        )

        assertNull(result?.error, "59 세는 cutoff 이하이므로 인계 가능해야 한다")
        assertEquals(9_800, user.meta["inheritPoints"], "Lv1 비용 200 차감")
    }

    @Test
    fun `E13 - setInheritSpecial blocked when age is at cutoff`() {
        val user = createUser(points = 10_000)
        val general = createGeneral(userId = user.id).also { it.age = 60 }
        val world = createWorld()
        stubOwnership(user, general, world)
        val specialCode = TraitSpecRegistry.war.first().key

        val result = service.setInheritSpecial(world.id.toLong(), user.loginId, specialCode)

        assertNotNull(result!!.error)
        assertTrue(result.error!!.contains("인계 불가"))
        // 특기 예약도 일어나지 않아야 한다.
        assertNull(general.meta["inheritSpecificSpecialWar"])
    }

    @Test
    fun `E13 - cutoff constant is pinned at 60`() {
        assertEquals(60, InheritanceService.AGE_INHERITANCE_CUTOFF)
    }

    @Test
    fun `E13 - buyInheritBuff still works when user has no current officer`() {
        // Edge case: 사용자가 장교 없이(사망 후 재접속) 인계 버프를 사야 할 때는
        // 현역 장교가 없으므로 age gate 가 발동하지 않아야 한다.
        val user = createUser(points = 10_000)
        val world = createWorld()
        `when`(appUserRepository.findByLoginId(user.loginId)).thenReturn(user)
        `when`(appUserRepository.findById(user.id)).thenReturn(java.util.Optional.of(user))
        `when`(officerRepository.findBySessionIdAndUserId(world.id.toLong(), user.id)).thenReturn(emptyList())
        `when`(sessionStateRepository.findById(world.id)).thenReturn(java.util.Optional.of(world))

        val result = service.buyInheritBuff(
            world.id.toLong(), user.loginId,
            BuyInheritBuffRequest(type = "warAvoidRatio", level = 1),
        )

        assertNull(result?.error,
            "현역 장교가 없을 때는 인계 버프 구매를 가로막지 않아야 한다 (post-death 시나리오)")
    }

    private fun stubOwnership(user: AppUser, general: Officer, world: SessionState) {
        `when`(appUserRepository.findByLoginId(user.loginId)).thenReturn(user)
        `when`(appUserRepository.findById(user.id)).thenReturn(Optional.of(user))
        `when`(officerRepository.findBySessionIdAndUserId(world.id.toLong(), user.id)).thenReturn(listOf(general))
        `when`(sessionStateRepository.findById(world.id)).thenReturn(Optional.of(world))
    }

    private fun createUser(points: Int): AppUser {
        return AppUser(
            id = 1,
            loginId = "tester",
            displayName = "Tester",
            passwordHash = "hash",
            meta = mutableMapOf("inheritPoints" to points),
        )
    }

    private fun createGeneral(userId: Long): Officer {
        return Officer(
            id = 10,
            sessionId = 1,
            userId = userId,
            name = "장수",
            factionId = 1,
            planetId = 1,
            turnTime = OffsetDateTime.parse("2026-03-08T10:05:00+09:00"),
        )
    }

    private fun createWorld(): SessionState {
        return SessionState(
            id = 1,
            scenarioCode = "1",
            tickSeconds = 300,
            config = mutableMapOf(
                "hiddenSeed" to "seed",
                "isunited" to 0,
            ),
        )
    }
}
