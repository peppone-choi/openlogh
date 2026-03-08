package com.opensam.service

import com.opensam.engine.modifier.TraitSpecRegistry
import com.opensam.entity.AppUser
import com.opensam.entity.General
import com.opensam.entity.WorldState
import com.opensam.repository.AppUserRepository
import com.opensam.repository.CityRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.WorldStateRepository
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
    private lateinit var cityRepository: CityRepository
    private lateinit var generalRepository: GeneralRepository
    private lateinit var worldStateRepository: WorldStateRepository
    private lateinit var gameConstService: GameConstService
    private lateinit var service: InheritanceService

    @BeforeEach
    fun setUp() {
        appUserRepository = mock(AppUserRepository::class.java)
        cityRepository = mock(CityRepository::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        worldStateRepository = mock(WorldStateRepository::class.java)
        gameConstService = mock(GameConstService::class.java)

        service = InheritanceService(
            appUserRepository = appUserRepository,
            cityRepository = cityRepository,
            generalRepository = generalRepository,
            worldStateRepository = worldStateRepository,
            gameConstService = gameConstService,
        )

        `when`(gameConstService.getInt("inheritSpecificSpecialPoint")).thenReturn(4000)
        `when`(gameConstService.getInt("inheritBornCityPoint")).thenReturn(1000)
        `when`(gameConstService.getInt("inheritItemRandomPoint")).thenReturn(3000)
        `when`(gameConstService.getInt("inheritCheckOwnerPoint")).thenReturn(1000)
        `when`(gameConstService.getInt("inheritItemUniqueMinPoint")).thenReturn(5000)
        `when`(gameConstService.getInt("inheritBornStatPoint")).thenReturn(1000)

        `when`(appUserRepository.save(any(AppUser::class.java))).thenAnswer { it.arguments[0] }
        `when`(generalRepository.save(any(General::class.java))).thenAnswer { it.arguments[0] }
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

    private fun stubOwnership(user: AppUser, general: General, world: WorldState) {
        `when`(appUserRepository.findByLoginId(user.loginId)).thenReturn(user)
        `when`(appUserRepository.findById(user.id)).thenReturn(Optional.of(user))
        `when`(generalRepository.findByWorldIdAndUserId(world.id.toLong(), user.id)).thenReturn(listOf(general))
        `when`(worldStateRepository.findById(world.id)).thenReturn(Optional.of(world))
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

    private fun createGeneral(userId: Long): General {
        return General(
            id = 10,
            worldId = 1,
            userId = userId,
            name = "장수",
            nationId = 1,
            cityId = 1,
            turnTime = OffsetDateTime.parse("2026-03-08T10:05:00+09:00"),
        )
    }

    private fun createWorld(): WorldState {
        return WorldState(
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
