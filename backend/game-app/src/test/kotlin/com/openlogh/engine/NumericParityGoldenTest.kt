package com.openlogh.engine

import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.HistoryService
import com.openlogh.service.InheritanceService
import com.openlogh.service.MapService
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
    private lateinit var planetRepository: PlanetRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var mapService: MapService

    private val cities = linkedMapOf<Long, Planet>()
    private val nations = linkedMapOf<Long, Faction>()
    private val generals = linkedMapOf<Long, Officer>()

    @BeforeEach
    fun setUp() {
        planetRepository = mock(PlanetRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        mapService = mock(MapService::class.java)
        service = EconomyService(
            planetRepository, factionRepository, officerRepository,
            mock(MessageRepository::class.java), mapService,
            mock(HistoryService::class.java), mock(InheritanceService::class.java),
        )
        wireRepos()
        `when`(mapService.getAdjacentCities(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
            .thenReturn(emptyList())
    }

    private fun wireRepos() {
        `when`(planetRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            cities.values.filter { it.sessionId == sessionId }.map { it.toSnapshot().toEntity() }
        }
        `when`(factionRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            nations.values.filter { it.sessionId == sessionId }.map { it.toSnapshot().toEntity() }
        }
        `when`(officerRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            generals.values.filter { it.sessionId == sessionId }.map { it.toSnapshot().toEntity() }
        }
        `when`(officerRepository.findBySessionIdAndPlanetIdIn(ArgumentMatchers.anyLong(), ArgumentMatchers.anyList()))
            .thenReturn(emptyList())
        `when`(planetRepository.save(ArgumentMatchers.any(Planet::class.java))).thenAnswer { inv ->
            val city = inv.arguments[0] as Planet
            cities[city.id] = city.toSnapshot().toEntity()
            city
        }
        `when`(factionRepository.save(ArgumentMatchers.any(Faction::class.java))).thenAnswer { inv ->
            val nation = inv.arguments[0] as Faction
            nations[nation.id] = nation.toSnapshot().toEntity()
            nation
        }
        `when`(officerRepository.save(ArgumentMatchers.any(Officer::class.java))).thenAnswer { inv ->
            val general = inv.arguments[0] as Officer
            generals[general.id] = general.toSnapshot().toEntity()
            general
        }
    }

    private fun seedFixture() {
        cities.clear(); nations.clear(); generals.clear()

        val cityWei = Planet(
            id = 1, sessionId = 1, name = "허창", mapPlanetId = 1,
            factionId = 1, supplyState = 1, frontState = 0,
            population = 10000, populationMax = 50000,
            production = 500, productionMax = 1000,
            commerce = 500, commerceMax = 1000,
            security = 500, securityMax = 1000,
            approval = 80f,
            orbitalDefense = 300, orbitalDefenseMax = 1000,
            fortress = 500, fortressMax = 1000,
            level = 5, tradeRoute = 100,
        )
        val cityShu = Planet(
            id = 2, sessionId = 1, name = "성도", mapPlanetId = 2,
            factionId = 2, supplyState = 1, frontState = 0,
            population = 10000, populationMax = 50000,
            production = 500, productionMax = 1000,
            commerce = 500, commerceMax = 1000,
            security = 500, securityMax = 1000,
            approval = 80f,
            orbitalDefense = 300, orbitalDefenseMax = 1000,
            fortress = 500, fortressMax = 1000,
            level = 5, tradeRoute = 100,
        )
        val cityNeutral = Planet(
            id = 3, sessionId = 1, name = "중립도시", mapPlanetId = 3,
            factionId = 0, supplyState = 1, frontState = 0,
            population = 10000, populationMax = 50000,
            production = 500, productionMax = 1000,
            commerce = 500, commerceMax = 1000,
            security = 500, securityMax = 1000,
            approval = 80f,
            orbitalDefense = 300, orbitalDefenseMax = 1000,
            fortress = 500, fortressMax = 1000,
            level = 5, tradeRoute = 100,
        )

        val nationWei = Faction(
            id = 1, sessionId = 1, name = "위", color = "#FF0000",
            funds = 10000, supplies = 10000, level = 3,
            taxRate = 100, conscriptionRateTmp = 15, conscriptionRate = 15,
            capitalPlanetId = 1,
        )
        val nationShu = Faction(
            id = 2, sessionId = 1, name = "촉", color = "#00FF00",
            funds = 10000, supplies = 10000, level = 3,
            taxRate = 100, conscriptionRateTmp = 15, conscriptionRate = 15,
            capitalPlanetId = 2,
        )

        val g1 = Officer(
            id = 1, sessionId = 1, name = "조조",
            factionId = 1, planetId = 1,
            leadership = 80, command = 70, intelligence = 90,
            politics = 60, administration = 60,
            ships = 200, shipClass = 0, training = 60, morale = 70,
            funds = 500, supplies = 500, npcState = 0,
            dedication = 1000, officerLevel = 1, officerPlanet = 0,
        )
        val g2 = Officer(
            id = 2, sessionId = 1, name = "하후돈",
            factionId = 1, planetId = 1,
            leadership = 60, command = 85, intelligence = 50,
            politics = 40, administration = 45,
            ships = 300, shipClass = 0, training = 40, morale = 65,
            funds = 500, supplies = 500, npcState = 0,
            dedication = 800, officerLevel = 1, officerPlanet = 0,
        )
        val g3 = Officer(
            id = 3, sessionId = 1, name = "유비",
            factionId = 2, planetId = 2,
            leadership = 75, command = 65, intelligence = 70,
            politics = 70, administration = 85,
            ships = 250, shipClass = 0, training = 55, morale = 75,
            funds = 500, supplies = 500, npcState = 0,
            dedication = 1200, officerLevel = 1, officerPlanet = 0,
        )
        val g4 = Officer(
            id = 4, sessionId = 1, name = "관우",
            factionId = 2, planetId = 2,
            leadership = 50, command = 95, intelligence = 60,
            politics = 45, administration = 70,
            ships = 400, shipClass = 0, training = 30, morale = 60,
            funds = 500, supplies = 500, npcState = 0,
            dedication = 600, officerLevel = 1, officerPlanet = 0,
        )

        listOf(cityWei, cityShu, cityNeutral).forEach { cities[it.id] = it.toSnapshot().toEntity() }
        listOf(nationWei, nationShu).forEach { nations[it.id] = it.toSnapshot().toEntity() }
        listOf(g1, g2, g3, g4).forEach { generals[it.id] = it.toSnapshot().toEntity() }
    }

    private fun runEconomySimulation(): SimulationResult {
        seedFixture()
        val world = SessionState(
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
            nationGold1 = finalNations[0].funds,
            nationRice1 = finalNations[0].supplies,
            nationGold2 = finalNations[1].funds,
            nationRice2 = finalNations[1].supplies,
            city1Pop = finalCities[0].population,
            city1Agri = finalCities[0].production,
            city1Comm = finalCities[0].commerce,
            city1Secu = finalCities[0].security,
            city1Def = finalCities[0].orbitalDefense,
            city1Wall = finalCities[0].fortress,
            city2Pop = finalCities[1].population,
            city2Agri = finalCities[1].production,
            city2Comm = finalCities[1].commerce,
            city3Pop = finalCities[2].population,
            city3Agri = finalCities[2].production,
            generals = finalGenerals.map { g ->
                OfficerSnapshot(
                    id = g.id,
                    funds = g.funds,
                    supplies = g.supplies,
                    leadership = g.leadership,
                    command = g.command,
                    intelligence = g.intelligence,
                    training = g.training,
                    morale = g.morale,
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
        assertEquals(50000, result.city1Pop, "city1 population")
        assertEquals(776, result.city1Agri, "city1 production")
        assertEquals(776, result.city1Comm, "city1 commerce")
        assertEquals(776, result.city1Secu, "city1 security")
        assertEquals(444, result.city1Def, "city1 orbitalDefense")
        assertEquals(776, result.city1Wall, "city1 fortress")

        // City 2 (Shu) - same initial conditions as city 1
        assertEquals(50000, result.city2Pop, "city2 population")
        assertEquals(776, result.city2Agri, "city2 production")
        assertEquals(776, result.city2Comm, "city2 commerce")

        // City 3 (neutral) - no nation, no income processing growth
        assertEquals(10000, result.city3Pop, "city3 neutral population")
        assertEquals(344, result.city3Agri, "city3 neutral production")

        // General gold/rice accumulation from salary distribution
        assertEquals(65277, result.generals[0].funds, "general1 (조조) gold")
        assertEquals(69621, result.generals[0].supplies, "general1 (조조) rice")
        assertEquals(54500, result.generals[1].funds, "general2 (하후돈) gold")
        assertEquals(58106, result.generals[1].supplies, "general2 (하후돈) rice")
        assertEquals(65277, result.generals[2].funds, "general3 (유비) gold")
        assertEquals(69621, result.generals[2].supplies, "general3 (유비) rice")
        assertEquals(54500, result.generals[3].funds, "general4 (관우) gold")
        assertEquals(58106, result.generals[3].supplies, "general4 (관우) rice")
    }

    @Test
    fun `no Short field exceeds domain bounds after 200 turns`() {
        val result = runEconomySimulation()

        for (g in result.generals) {
            assertTrue(g.leadership in 0..100, "general ${g.id} leadership=${g.leadership} out of bounds")
            assertTrue(g.command in 0..100, "general ${g.id} command =${g.command} out of bounds")
            assertTrue(g.intelligence in 0..100, "general ${g.id} intelligence =${g.intelligence} out of bounds")
            assertTrue(g.training in 0..110, "general ${g.id} training =${g.training} out of bounds")
            assertTrue(g.morale in 0..110, "general ${g.id} morale =${g.morale} out of bounds")
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
        val generals: List<OfficerSnapshot>,
    )

    private data class OfficerSnapshot(
        val id: Long,
        val funds: Int,
        val supplies: Int,
        val leadership: Short,
        val command: Short,
        val intelligence: Short,
        val training: Short,
        val morale: Short,
        val injury: Short,
    )
}
