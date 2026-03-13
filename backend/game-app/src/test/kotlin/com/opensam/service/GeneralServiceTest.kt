package com.opensam.service

import com.opensam.dto.BuildPoolGeneralRequest
import com.opensam.dto.CreateGeneralRequest
import com.opensam.entity.AppUser
import com.opensam.entity.City
import com.opensam.entity.General
import com.opensam.entity.GeneralTurn
import com.opensam.entity.WorldState
import com.opensam.repository.AppUserRepository
import com.opensam.repository.CityRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.GeneralTurnRepository
import com.opensam.repository.NationRepository
import com.opensam.repository.WorldStateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

class GeneralServiceTest {
    private lateinit var generalRepository: GeneralRepository
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var worldStateRepository: WorldStateRepository
    private lateinit var cityRepository: CityRepository
    private lateinit var nationRepository: NationRepository
    private lateinit var generalTurnRepository: GeneralTurnRepository
    private lateinit var gameConstService: GameConstService
    private lateinit var service: GeneralService

    @BeforeEach
    fun setUp() {
        generalRepository = mock(GeneralRepository::class.java)
        appUserRepository = mock(AppUserRepository::class.java)
        worldStateRepository = mock(WorldStateRepository::class.java)
        cityRepository = mock(CityRepository::class.java)
        nationRepository = mock(NationRepository::class.java)
        generalTurnRepository = mock(GeneralTurnRepository::class.java)
        gameConstService = mock(GameConstService::class.java)

        service = GeneralService(
            generalRepository,
            appUserRepository,
            worldStateRepository,
            cityRepository,
            nationRepository,
            generalTurnRepository,
            gameConstService,
        )

        val constMap = mapOf(
            "defaultMaxGeneral" to 500,
            "defaultGold" to 1000,
            "defaultRice" to 1000,
            "maxTurn" to 30,
            "retirementYear" to 80,
            "defaultStartYear" to 180,
            "inheritBornSpecialPoint" to 6000,
            "inheritBornCityPoint" to 1000,
            "inheritBornStatPoint" to 1000,
            "inheritBornTurntimePoint" to 2500,
            "initialNationGenLimit" to 10,
        )
        `when`(gameConstService.getInt(anyString())).thenAnswer { invocation ->
            constMap[invocation.getArgument<String>(0)] ?: 0
        }

        `when`(generalRepository.save(any(General::class.java))).thenAnswer { invocation ->
            val general = invocation.getArgument<General>(0)
            if (general.id == 0L) {
                general.id = 77
            }
            general
        }
        `when`(generalTurnRepository.saveAll(anyList())).thenAnswer { invocation ->
            invocation.getArgument<List<GeneralTurn>>(0)
        }
    }

    @Test
    fun `createGeneral applies legacy join options and seeds rest turns`() {
        val user = AppUser(
            id = 1,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
            grade = 2,
            meta = mutableMapOf(
                "picture" to "/icons/me.png",
                "imageServer" to 3,
                "inheritPoints" to 10000,
            ),
        )
        val world = WorldState(
            id = 1,
            currentYear = 185,
            currentMonth = 1,
            tickSeconds = 300,
            config = mutableMapOf(
                "startYear" to 180,
                "maxGeneral" to 500,
                "hiddenSeed" to "seed",
            ),
            meta = mutableMapOf(),
        )
        val city = City(id = 10, worldId = 1, name = "허창", level = 5, nationId = 0)

        `when`(appUserRepository.findByLoginId("user")).thenReturn(user)
        `when`(worldStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))
        `when`(generalRepository.findByWorldIdAndUserId(1L, 1L)).thenReturn(emptyList())
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())
        `when`(generalRepository.findByNameAndWorldId("신장수", 1L)).thenReturn(null)
        `when`(cityRepository.findById(10L)).thenReturn(Optional.of(city))

        val general = service.createGeneral(
            1L,
            "user",
            CreateGeneralRequest(
                name = "신장수",
                cityId = 10L,
                leadership = 70,
                strength = 70,
                intel = 70,
                politics = 70,
                charm = 70,
                crewType = 2,
                personality = "che_대의",
                pic = true,
                inheritSpecial = "che_저격",
                inheritBonusStat = listOf(2, 1, 0),
            ),
        )

        assertNotNull(general)
        assertEquals("/icons/me.png", general?.picture)
        assertEquals(3.toShort(), general?.imageServer)
        assertEquals("che_대의", general?.personalCode)
        assertEquals("che_저격", general?.special2Code)
        assertEquals(72.toShort(), general?.leadership)
        assertEquals(71.toShort(), general?.strength)
        assertEquals(70.toShort(), general?.intel)
        assertEquals(3000, user.meta["inheritPoints"])

        @Suppress("UNCHECKED_CAST")
        val turnCaptor = ArgumentCaptor.forClass(Iterable::class.java) as ArgumentCaptor<Iterable<GeneralTurn>>
        verify(generalTurnRepository).saveAll(turnCaptor.capture())
        val turns = turnCaptor.value.toList()
        assertEquals(30, turns.size)
        assertTrue(turns.all { it.actionCode == "휴식" && it.generalId == 77L })
    }

    @Test
    fun `createGeneral consumes pre purchased inherit city and special without extra charge`() {
        val user = AppUser(
            id = 2,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
            meta = mutableMapOf(
                "inheritPoints" to 500,
                "inheritSpecificSpecialWar" to "che_반계",
                "inheritCity" to 11,
            ),
        )
        val world = WorldState(
            id = 1,
            currentYear = 180,
            currentMonth = 1,
            tickSeconds = 300,
            config = mutableMapOf("hiddenSeed" to "seed"),
            meta = mutableMapOf(),
        )
        val city = City(id = 11, worldId = 1, name = "낙양", level = 5, nationId = 0)

        `when`(appUserRepository.findByLoginId("user")).thenReturn(user)
        `when`(worldStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))
        `when`(generalRepository.findByWorldIdAndUserId(1L, 2L)).thenReturn(emptyList())
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())
        `when`(generalRepository.findByNameAndWorldId("계승장수", 1L)).thenReturn(null)
        `when`(cityRepository.findById(11L)).thenReturn(Optional.of(city))

        val general = service.createGeneral(
            1L,
            "user",
            CreateGeneralRequest(
                name = "계승장수",
                cityId = 5L,
                leadership = 70,
                strength = 70,
                intel = 70,
                politics = 70,
                charm = 70,
                personality = "che_안전",
            ),
        )

        assertNotNull(general)
        assertEquals(11L, general?.cityId)
        assertEquals("che_반계", general?.special2Code)
        assertEquals(500, user.meta["inheritPoints"])
        assertFalse(user.meta.containsKey("inheritSpecificSpecialWar"))
        assertFalse(user.meta.containsKey("inheritCity"))
    }

    @Test
    fun `buildPoolGeneral stores legacy personality and own icon`() {
        val user = AppUser(
            id = 3,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
            grade = 2,
            meta = mutableMapOf(
                "picture" to "/icons/pool.png",
                "imageServer" to 2,
            ),
        )
        val world = WorldState(
            id = 1,
            config = mutableMapOf("hiddenSeed" to "seed"),
            meta = mutableMapOf(),
        )

        `when`(appUserRepository.findByLoginId("user")).thenReturn(user)
        `when`(worldStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))
        `when`(generalRepository.findByWorldIdAndUserId(1L, 3L)).thenReturn(emptyList())

        val general = service.buildPoolGeneral(
            1L,
            "user",
            BuildPoolGeneralRequest(
                name = "풀장수",
                leadership = 70,
                strength = 70,
                intel = 70,
                politics = 70,
                charm = 70,
                personality = "che_왕좌",
            ),
        )

        assertNotNull(general)
        assertEquals(5.toShort(), general?.npcState)
        assertEquals("che_왕좌", general?.personalCode)
        assertEquals("/icons/pool.png", general?.picture)
        assertEquals(2.toShort(), general?.imageServer)
    }

    @Test
    fun `createGeneral finds user with case insensitive login id`() {
        val user = AppUser(
            id = 4,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
            meta = mutableMapOf(),
        )
        val world = WorldState(
            id = 1,
            currentYear = 180,
            currentMonth = 1,
            tickSeconds = 300,
            config = mutableMapOf("hiddenSeed" to "seed"),
            meta = mutableMapOf(),
        )
        val city = City(id = 10, worldId = 1, name = "허창", level = 5, nationId = 0)

        `when`(appUserRepository.findByLoginId("USER")).thenReturn(null)
        `when`(appUserRepository.findByLoginIdIgnoreCase("USER")).thenReturn(user)
        `when`(worldStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))
        `when`(generalRepository.findByWorldIdAndUserId(1L, 4L)).thenReturn(emptyList())
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())
        `when`(generalRepository.findByNameAndWorldId("신장수", 1L)).thenReturn(null)
        `when`(cityRepository.findById(10L)).thenReturn(Optional.of(city))

        val general = service.createGeneral(
            1L,
            "USER",
            CreateGeneralRequest(
                name = "신장수",
                cityId = 10L,
                leadership = 70,
                strength = 70,
                intel = 70,
                politics = 70,
                charm = 70,
                crewType = 0,
            ),
        )

        assertNotNull(general)
        assertEquals(4L, general?.userId)
    }

    @Test
    fun `createGeneral sets turnTime relative to world updatedAt not wall clock`() {
        val user = AppUser(
            id = 5,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
            meta = mutableMapOf(),
        )
        val worldUpdatedAt = java.time.OffsetDateTime.now().minusSeconds(5)
        val world = WorldState(
            id = 1,
            currentYear = 180,
            currentMonth = 1,
            tickSeconds = 60,
            config = mutableMapOf("hiddenSeed" to "seed"),
            meta = mutableMapOf(),
            updatedAt = worldUpdatedAt,
        )
        val city = City(id = 10, worldId = 1, name = "허창", level = 5, nationId = 0)

        `when`(appUserRepository.findByLoginId("user")).thenReturn(user)
        `when`(worldStateRepository.findById(1.toShort())).thenReturn(Optional.of(world))
        `when`(generalRepository.findByWorldIdAndUserId(1L, 5L)).thenReturn(emptyList())
        `when`(generalRepository.findByWorldId(1L)).thenReturn(emptyList())
        `when`(generalRepository.findByNameAndWorldId("턴타임장수", 1L)).thenReturn(null)
        `when`(cityRepository.findById(10L)).thenReturn(Optional.of(city))

        val general = service.createGeneral(
            1L,
            "user",
            CreateGeneralRequest(
                name = "턴타임장수",
                cityId = 10L,
                leadership = 70,
                strength = 70,
                intel = 70,
                politics = 70,
                charm = 70,
            ),
        )

        assertNotNull(general)
        val turnTime = general!!.turnTime
        val earliest = worldUpdatedAt
        val latest = worldUpdatedAt.plusSeconds(2 * 60)
        assertTrue(
            !turnTime.isBefore(earliest) && turnTime.isBefore(latest),
            "turnTime should be within [updatedAt, updatedAt+2*tick), got offset=${java.time.Duration.between(worldUpdatedAt, turnTime).seconds}s",
        )
    }

    @Test
    fun `createGeneral returns clear error when user not found`() {
        `when`(appUserRepository.findByLoginId("ghost")).thenReturn(null)
        `when`(appUserRepository.findByLoginIdIgnoreCase("ghost")).thenReturn(null)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createGeneral(
                1L,
                "ghost",
                CreateGeneralRequest(
                    name = "신장수",
                    cityId = 10L,
                    leadership = 70,
                    strength = 70,
                    intel = 70,
                    politics = 70,
                    charm = 70,
                ),
            )
        }

        assertEquals("계정 정보를 찾을 수 없습니다. 다시 로그인해주세요.", ex.message)
    }
}
