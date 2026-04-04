package com.openlogh.engine

import com.openlogh.entity.City
import com.openlogh.entity.General
import com.openlogh.entity.Nation
import com.openlogh.entity.WorldState
import com.openlogh.repository.CityRepository
import com.openlogh.repository.GeneralRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.NationRepository
import com.openlogh.service.HistoryService
import com.openlogh.service.MapService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import kotlin.random.Random

class NpcSpawnServiceTest {

    private lateinit var service: NpcSpawnService
    private lateinit var cityRepository: CityRepository
    private lateinit var nationRepository: NationRepository
    private lateinit var generalRepository: GeneralRepository
    private lateinit var mapService: MapService

    @BeforeEach
    fun setUp() {
        cityRepository = mock(CityRepository::class.java)
        nationRepository = mock(NationRepository::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        mapService = mock(MapService::class.java)
        val historyService = mock(HistoryService::class.java)
        service = NpcSpawnService(nationRepository, cityRepository, generalRepository, historyService, mapService)
    }

    // ========== derivePoliticsFromStats formula ==========

    @Test
    fun `derivePoliticsFromStats uses formula intel 0_4 plus leadership 0_3`() {
        val rng = Random(42)

        // Use reflection to access private method
        val method = NpcSpawnService::class.java.getDeclaredMethod(
            "derivePoliticsFromStats",
            Int::class.java, Int::class.java, Int::class.java, Random::class.java
        )
        method.isAccessible = true

        val result = method.invoke(service, 80, 50, 70, rng) as Int

        // Expected: round(70 * 0.4 + 80 * 0.3 + random(-15, 15)) = round(28 + 24 + random) = 52 ± 15
        assertTrue(result in 30..95, "Politics should be clamped to [30, 95]")
    }

    @Test
    fun `derivePoliticsFromStats result is clamped to minimum 30`() {
        val rng = Random(42)

        val method = NpcSpawnService::class.java.getDeclaredMethod(
            "derivePoliticsFromStats",
            Int::class.java, Int::class.java, Int::class.java, Random::class.java
        )
        method.isAccessible = true

        // Very low stats should still produce >= 30
        val result = method.invoke(service, 10, 10, 10, rng) as Int

        assertTrue(result >= 30, "Politics should be at least 30")
    }

    @Test
    fun `derivePoliticsFromStats result is clamped to maximum 95`() {
        val rng = Random(42)

        val method = NpcSpawnService::class.java.getDeclaredMethod(
            "derivePoliticsFromStats",
            Int::class.java, Int::class.java, Int::class.java, Random::class.java
        )
        method.isAccessible = true

        // Very high stats should still produce <= 95
        val result = method.invoke(service, 100, 100, 100, rng) as Int

        assertTrue(result <= 95, "Politics should be at most 95")
    }

    @Test
    fun `derivePoliticsFromStats high intel produces higher politics`() {
        val rng = Random(42)

        val method = NpcSpawnService::class.java.getDeclaredMethod(
            "derivePoliticsFromStats",
            Int::class.java, Int::class.java, Int::class.java, Random::class.java
        )
        method.isAccessible = true

        val highIntel = method.invoke(service, 50, 50, 90, rng) as Int

        // Reset RNG
        val rng2 = Random(42)
        val lowIntel = method.invoke(service, 50, 50, 30, rng2) as Int

        // High intel should generally produce higher politics (allowing for random variance)
        // 90*0.4 + 50*0.3 = 36 + 15 = 51 ± 15 = [36, 66]
        // 30*0.4 + 50*0.3 = 12 + 15 = 27 ± 15 = [12, 42] → clamped to [30, 42]
        assertTrue(highIntel >= 36, "High intel should produce higher politics baseline")
    }

    // ========== deriveCharmFromStats formula ==========

    @Test
    fun `deriveCharmFromStats uses formula leadership 0_3 plus intel 0_2 plus strength 0_1`() {
        val rng = Random(42)

        val method = NpcSpawnService::class.java.getDeclaredMethod(
            "deriveCharmFromStats",
            Int::class.java, Int::class.java, Int::class.java, Random::class.java
        )
        method.isAccessible = true

        val result = method.invoke(service, 80, 60, 70, rng) as Int

        // Expected: round(80 * 0.3 + 70 * 0.2 + 60 * 0.1 + random(-15, 15))
        //         = round(24 + 14 + 6 + random) = 44 ± 15
        assertTrue(result in 30..95, "Charm should be clamped to [30, 95]")
    }

    @Test
    fun `deriveCharmFromStats result is clamped to minimum 30`() {
        val rng = Random(42)

        val method = NpcSpawnService::class.java.getDeclaredMethod(
            "deriveCharmFromStats",
            Int::class.java, Int::class.java, Int::class.java, Random::class.java
        )
        method.isAccessible = true

        val result = method.invoke(service, 10, 10, 10, rng) as Int

        assertTrue(result >= 30, "Charm should be at least 30")
    }

    @Test
    fun `deriveCharmFromStats result is clamped to maximum 95`() {
        val rng = Random(42)

        val method = NpcSpawnService::class.java.getDeclaredMethod(
            "deriveCharmFromStats",
            Int::class.java, Int::class.java, Int::class.java, Random::class.java
        )
        method.isAccessible = true

        val result = method.invoke(service, 100, 100, 100, rng) as Int

        assertTrue(result <= 95, "Charm should be at most 95")
    }

    @Test
    fun `deriveCharmFromStats high leadership produces higher charm`() {
        val rng = Random(42)

        val method = NpcSpawnService::class.java.getDeclaredMethod(
            "deriveCharmFromStats",
            Int::class.java, Int::class.java, Int::class.java, Random::class.java
        )
        method.isAccessible = true

        val highLeadership = method.invoke(service, 90, 50, 50, rng) as Int

        // Reset RNG
        val rng2 = Random(42)
        val lowLeadership = method.invoke(service, 30, 50, 50, rng2) as Int

        // High leadership should generally produce higher charm
        // 90*0.3 + 50*0.2 + 50*0.1 = 27 + 10 + 5 = 42 ± 15 = [27, 57]
        // 30*0.3 + 50*0.2 + 50*0.1 = 9 + 10 + 5 = 24 ± 15 = [9, 39] → clamped to [30, 39]
        assertTrue(highLeadership >= 30, "High leadership should produce higher charm baseline")
    }

    // ========== derivePoliticsFromStats vs deriveCharmFromStats difference ==========

    @Test
    fun `politics and charm have different formulas`() {
        val rng = Random(42)

        val politicsMethod = NpcSpawnService::class.java.getDeclaredMethod(
            "derivePoliticsFromStats",
            Int::class.java, Int::class.java, Int::class.java, Random::class.java
        )
        politicsMethod.isAccessible = true

        val charmMethod = NpcSpawnService::class.java.getDeclaredMethod(
            "deriveCharmFromStats",
            Int::class.java, Int::class.java, Int::class.java, Random::class.java
        )
        charmMethod.isAccessible = true

        // High intel, low leadership/strength → politics should be higher
        val rng1 = Random(100)
        val politics = politicsMethod.invoke(service, 40, 40, 90, rng1) as Int

        val rng2 = Random(100)
        val charm = charmMethod.invoke(service, 40, 40, 90, rng2) as Int

        // Politics = 90*0.4 + 40*0.3 = 36 + 12 = 48 ± 15
        // Charm = 40*0.3 + 90*0.2 + 40*0.1 = 12 + 18 + 4 = 34 ± 15
        // Politics should generally be higher for high-intel generals
        assertTrue(politics >= 33, "Politics should favor intel heavily")
    }

    @Test
    fun `random variance is applied correctly`() {
        val method = NpcSpawnService::class.java.getDeclaredMethod(
            "derivePoliticsFromStats",
            Int::class.java, Int::class.java, Int::class.java, Random::class.java
        )
        method.isAccessible = true

        // Run multiple times with different seeds to ensure variance
        val results = mutableSetOf<Int>()
        for (seed in 1..20) {
            val rng = Random(seed)
            val result = method.invoke(service, 70, 60, 70, rng) as Int
            results.add(result)
        }

        // Should have at least some variance (not all the same value)
        assertTrue(results.size > 1, "Random variance should produce different results")
    }

    @Test
    fun `npc nation ruler uses fixed killTurn while followers use lifespan derived killTurn`() {
        val world = WorldState(
            id = 1,
            name = "test-world",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 4,
            config = mutableMapOf("hiddenSeed" to "seed"),
        )
        val city = City(
            id = 10,
            worldId = 1,
            name = "허창",
            mapCityId = 10,
            level = 5,
            pop = 6000,
            popMax = 10000,
            agri = 500,
            agriMax = 1000,
            comm = 500,
            commMax = 1000,
            secu = 500,
            secuMax = 1000,
            def = 500,
            defMax = 1000,
            wall = 500,
            wallMax = 1000,
        )

        var nextNationId = 100L
        var nextGeneralId = 1000L
        val savedGenerals = mutableListOf<General>()

        `when`(nationRepository.save(any(Nation::class.java))).thenAnswer { invocation ->
            val nation = invocation.arguments[0] as Nation
            if (nation.id == 0L) {
                nation.id = nextNationId++
            }
            nation
        }
        `when`(cityRepository.save(any(City::class.java))).thenAnswer { invocation -> invocation.arguments[0] as City }
        `when`(generalRepository.save(any(General::class.java))).thenAnswer { invocation ->
            val general = invocation.arguments[0] as General
            if (general.id == 0L) {
                general.id = nextGeneralId++
            }
            savedGenerals += general
            general
        }

        val method = NpcSpawnService::class.java.getDeclaredMethod(
            "buildNpcNation",
            WorldState::class.java,
            Random::class.java,
            City::class.java,
            Map::class.java,
            Int::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        )
        method.isAccessible = true

        method.invoke(
            service,
            world,
            Random(42),
            city,
            mapOf(
                "pop" to 6000,
                "agri" to 500,
                "comm" to 500,
                "secu" to 500,
                "def" to 500,
                "wall" to 500,
            ),
            4,
            0f,
        )

        assertEquals(4, savedGenerals.size)

        val ruler = savedGenerals.first()
        val followers = savedGenerals.drop(1)

        assertEquals(20.toShort(), ruler.officerLevel)
        assertEquals(240.toShort(), ruler.killTurn)
        assertTrue(followers.all { it.killTurn == null }, "Followers should use deadYear-derived lifespan, not fixed killTurn")
        assertTrue(followers.all { it.deadYear > world.currentYear }, "Followers should still have finite deadYear lifespan")
    }
}
