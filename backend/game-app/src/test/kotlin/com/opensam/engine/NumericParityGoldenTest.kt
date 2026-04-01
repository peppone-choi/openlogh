package com.opensam.engine

import com.opensam.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.opensam.engine.turn.cqrs.persist.toEntity
import com.opensam.engine.turn.cqrs.persist.toSnapshot
import com.opensam.entity.City
import com.opensam.entity.General
import com.opensam.entity.Nation
import com.opensam.entity.WorldState
import com.opensam.repository.CityRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.MessageRepository
import com.opensam.repository.NationRepository
import com.opensam.service.HistoryService
import com.opensam.service.InheritanceService
import com.opensam.service.MapService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * 200-turn golden snapshot integration test for cumulative numeric drift detection.
 *
 * Drives EconomyService.processMonthly() through 200 iterations with fixed inputs
 * and asserts cumulative values match recorded golden baseline.
 *
 * Golden values recorded from Kotlin baseline. PHP baseline comparison deferred
 * per RESEARCH open question #3.
 */
@DisplayName("200-Turn Numeric Parity Golden Snapshot")
class NumericParityGoldenTest {

    private lateinit var service: EconomyService
    private lateinit var cityRepository: CityRepository
    private lateinit var nationRepository: NationRepository
    private lateinit var generalRepository: GeneralRepository
    private lateinit var mapService: MapService

    private val cities = linkedMapOf<Long, City>()
    private val nations = linkedMapOf<Long, Nation>()
    private val generals = linkedMapOf<Long, General>()

    @BeforeEach
    fun setUp() {
        cityRepository = mock(CityRepository::class.java)
        nationRepository = mock(NationRepository::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        mapService = mock(MapService::class.java)
        service = EconomyService(
            cityRepository, nationRepository, generalRepository,
            mock(MessageRepository::class.java), mapService,
            mock(HistoryService::class.java), mock(InheritanceService::class.java),
        )
        wireRepos()
        `when`(mapService.getAdjacentCities(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
            .thenReturn(emptyList())
    }

    private fun wireRepos() {
        `when`(cityRepository.findByWorldId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            cities.values.filter { it.worldId == worldId }.map { it.toSnapshot().toEntity() }
        }
        `when`(nationRepository.findByWorldId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            nations.values.filter { it.worldId == worldId }.map { it.toSnapshot().toEntity() }
        }
        `when`(generalRepository.findByWorldId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            generals.values.filter { it.worldId == worldId }.map { it.toSnapshot().toEntity() }
        }
        `when`(generalRepository.findByWorldIdAndCityIdIn(ArgumentMatchers.anyLong(), ArgumentMatchers.anyList()))
            .thenReturn(emptyList())
        `when`(cityRepository.save(ArgumentMatchers.any(City::class.java))).thenAnswer { inv ->
            val city = inv.arguments[0] as City
            cities[city.id] = city.toSnapshot().toEntity()
            city
        }
        `when`(nationRepository.save(ArgumentMatchers.any(Nation::class.java))).thenAnswer { inv ->
            val nation = inv.arguments[0] as Nation
            nations[nation.id] = nation.toSnapshot().toEntity()
            nation
        }
        `when`(generalRepository.save(ArgumentMatchers.any(General::class.java))).thenAnswer { inv ->
            val general = inv.arguments[0] as General
            generals[general.id] = general.toSnapshot().toEntity()
            general
        }
    }

    private fun seedFixture() {
        cities.clear(); nations.clear(); generals.clear()

        val cityWei = City(
            id = 1, worldId = 1, name = "허창", mapCityId = 1,
            nationId = 1, supplyState = 1, frontState = 0,
            pop = 10000, popMax = 50000,
            agri = 500, agriMax = 1000,
            comm = 500, commMax = 1000,
            secu = 500, secuMax = 1000,
            trust = 80f,
            def = 300, defMax = 1000,
            wall = 500, wallMax = 1000,
            level = 5, trade = 100,
        )
        val cityShu = City(
            id = 2, worldId = 1, name = "성도", mapCityId = 2,
            nationId = 2, supplyState = 1, frontState = 0,
            pop = 10000, popMax = 50000,
            agri = 500, agriMax = 1000,
            comm = 500, commMax = 1000,
            secu = 500, secuMax = 1000,
            trust = 80f,
            def = 300, defMax = 1000,
            wall = 500, wallMax = 1000,
            level = 5, trade = 100,
        )
        val cityNeutral = City(
            id = 3, worldId = 1, name = "중립도시", mapCityId = 3,
            nationId = 0, supplyState = 1, frontState = 0,
            pop = 10000, popMax = 50000,
            agri = 500, agriMax = 1000,
            comm = 500, commMax = 1000,
            secu = 500, secuMax = 1000,
            trust = 80f,
            def = 300, defMax = 1000,
            wall = 500, wallMax = 1000,
            level = 5, trade = 100,
        )

        val nationWei = Nation(
            id = 1, worldId = 1, name = "위", color = "#FF0000",
            gold = 10000, rice = 10000, level = 3,
            bill = 100, rateTmp = 15, rate = 15,
            capitalCityId = 1,
        )
        val nationShu = Nation(
            id = 2, worldId = 1, name = "촉", color = "#00FF00",
            gold = 10000, rice = 10000, level = 3,
            bill = 100, rateTmp = 15, rate = 15,
            capitalCityId = 2,
        )

        val g1 = General(
            id = 1, worldId = 1, name = "조조",
            nationId = 1, cityId = 1,
            leadership = 80, strength = 70, intel = 90,
            politics = 60, charm = 60,
            crew = 200, crewType = 0, train = 60, atmos = 70,
            gold = 500, rice = 500, npcState = 0,
            dedication = 1000, officerLevel = 1, officerCity = 0,
        )
        val g2 = General(
            id = 2, worldId = 1, name = "하후돈",
            nationId = 1, cityId = 1,
            leadership = 60, strength = 85, intel = 50,
            politics = 40, charm = 45,
            crew = 300, crewType = 0, train = 40, atmos = 65,
            gold = 500, rice = 500, npcState = 0,
            dedication = 800, officerLevel = 1, officerCity = 0,
        )
        val g3 = General(
            id = 3, worldId = 1, name = "유비",
            nationId = 2, cityId = 2,
            leadership = 75, strength = 65, intel = 70,
            politics = 70, charm = 85,
            crew = 250, crewType = 0, train = 55, atmos = 75,
            gold = 500, rice = 500, npcState = 0,
            dedication = 1200, officerLevel = 1, officerCity = 0,
        )
        val g4 = General(
            id = 4, worldId = 1, name = "관우",
            nationId = 2, cityId = 2,
            leadership = 50, strength = 95, intel = 60,
            politics = 45, charm = 70,
            crew = 400, crewType = 0, train = 30, atmos = 60,
            gold = 500, rice = 500, npcState = 0,
            dedication = 600, officerLevel = 1, officerCity = 0,
        )

        listOf(cityWei, cityShu, cityNeutral).forEach { cities[it.id] = it.toSnapshot().toEntity() }
        listOf(nationWei, nationShu).forEach { nations[it.id] = it.toSnapshot().toEntity() }
        listOf(g1, g2, g3, g4).forEach { generals[it.id] = it.toSnapshot().toEntity() }
    }

    private fun runEconomySimulation(): SimulationResult {
        seedFixture()
        val world = WorldState(
            id = 1, scenarioCode = "test",
            currentYear = 200, currentMonth = 1,
            tickSeconds = 300,
        )

        repeat(200) { turn ->
            service.processMonthly(world)

            // Advance month (1-12 cycle)
            val nextMonth = (world.currentMonth.toInt() % 12) + 1
            world.currentMonth = nextMonth.toShort()
            if (nextMonth == 1) {
                world.currentYear = (world.currentYear + 1).toShort()
            }
        }

        val finalCities = cities.values.sortedBy { it.id }
        val finalNations = nations.values.sortedBy { it.id }
        val finalGenerals = generals.values.sortedBy { it.id }

        return SimulationResult(
            nationGold1 = finalNations[0].gold,
            nationRice1 = finalNations[0].rice,
            nationGold2 = finalNations[1].gold,
            nationRice2 = finalNations[1].rice,
            city1Pop = finalCities[0].pop,
            city1Agri = finalCities[0].agri,
            city1Comm = finalCities[0].comm,
            city1Secu = finalCities[0].secu,
            city1Def = finalCities[0].def,
            city1Wall = finalCities[0].wall,
            city2Pop = finalCities[1].pop,
            city2Agri = finalCities[1].agri,
            city2Comm = finalCities[1].comm,
            city3Pop = finalCities[2].pop,
            city3Agri = finalCities[2].agri,
            generals = finalGenerals.map { g ->
                GeneralSnapshot(
                    id = g.id,
                    gold = g.gold,
                    rice = g.rice,
                    leadership = g.leadership,
                    strength = g.strength,
                    intel = g.intel,
                    train = g.train,
                    atmos = g.atmos,
                    injury = g.injury,
                )
            },
        )
    }

    @Test
    fun `200-turn economy simulation is deterministic`() {
        val result1 = runEconomySimulation()
        val result2 = runEconomySimulation()
        assertEquals(result1, result2, "Two runs with same inputs must produce identical output")
    }

    @Test
    fun `200-turn economy golden values match baseline`() {
        val result = runEconomySimulation()

        // Golden values recorded from Kotlin baseline.
        // PHP baseline comparison deferred per RESEARCH open question #3.
        // Nation economy
        assertEquals(0, result.nationGold1, "nation1 gold")
        assertEquals(2000, result.nationRice1, "nation1 rice")
        assertEquals(0, result.nationGold2, "nation2 gold")
        assertEquals(2000, result.nationRice2, "nation2 rice")

        // City 1 (Wei)
        assertEquals(50000, result.city1Pop, "city1 pop")
        assertEquals(776, result.city1Agri, "city1 agri")
        assertEquals(776, result.city1Comm, "city1 comm")
        assertEquals(776, result.city1Secu, "city1 secu")
        assertEquals(444, result.city1Def, "city1 def")
        assertEquals(776, result.city1Wall, "city1 wall")

        // City 2 (Shu) - same initial conditions as city 1
        assertEquals(50000, result.city2Pop, "city2 pop")
        assertEquals(776, result.city2Agri, "city2 agri")
        assertEquals(776, result.city2Comm, "city2 comm")

        // City 3 (neutral) - no nation, no income processing growth
        assertEquals(10000, result.city3Pop, "city3 neutral pop")
        assertEquals(344, result.city3Agri, "city3 neutral agri")

        // General gold/rice accumulation from salary distribution
        assertEquals(65277, result.generals[0].gold, "general1 (조조) gold")
        assertEquals(69621, result.generals[0].rice, "general1 (조조) rice")
        assertEquals(54500, result.generals[1].gold, "general2 (하후돈) gold")
        assertEquals(58106, result.generals[1].rice, "general2 (하후돈) rice")
        assertEquals(65277, result.generals[2].gold, "general3 (유비) gold")
        assertEquals(69621, result.generals[2].rice, "general3 (유비) rice")
        assertEquals(54500, result.generals[3].gold, "general4 (관우) gold")
        assertEquals(58106, result.generals[3].rice, "general4 (관우) rice")
    }

    @Test
    fun `no Short field exceeds domain bounds after 200 turns`() {
        val result = runEconomySimulation()

        for (g in result.generals) {
            assertTrue(g.leadership in 0..100, "general ${g.id} leadership=${g.leadership} out of bounds")
            assertTrue(g.strength in 0..100, "general ${g.id} strength=${g.strength} out of bounds")
            assertTrue(g.intel in 0..100, "general ${g.id} intel=${g.intel} out of bounds")
            assertTrue(g.train in 0..110, "general ${g.id} train=${g.train} out of bounds")
            assertTrue(g.atmos in 0..110, "general ${g.id} atmos=${g.atmos} out of bounds")
            assertTrue(g.injury in 0..100, "general ${g.id} injury=${g.injury} out of bounds")
        }
    }

    private data class SimulationResult(
        val nationGold1: Int,
        val nationRice1: Int,
        val nationGold2: Int,
        val nationRice2: Int,
        val city1Pop: Int,
        val city1Agri: Int,
        val city1Comm: Int,
        val city1Secu: Int,
        val city1Def: Int,
        val city1Wall: Int,
        val city2Pop: Int,
        val city2Agri: Int,
        val city2Comm: Int,
        val city3Pop: Int,
        val city3Agri: Int,
        val generals: List<GeneralSnapshot>,
    )

    private data class GeneralSnapshot(
        val id: Long,
        val gold: Int,
        val rice: Int,
        val leadership: Short,
        val strength: Short,
        val intel: Short,
        val train: Short,
        val atmos: Short,
        val injury: Short,
    )
}
