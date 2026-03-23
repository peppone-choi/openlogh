package com.openlogh.engine

import com.openlogh.entity.*
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.InheritanceService
import com.openlogh.service.MapService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime

class EconomyServiceTest {

    private lateinit var service: EconomyService
    private lateinit var planetRepository: PlanetRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var mapService: MapService

    private val planets = linkedMapOf<Long, Planet>()
    private val factions = linkedMapOf<Long, Faction>()
    private val officers = linkedMapOf<Long, Officer>()

    @BeforeEach
    fun setUp() {
        planetRepository = mock(PlanetRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        mapService = mock(MapService::class.java)
        service = EconomyService(planetRepository, factionRepository, officerRepository, mock(MessageRepository::class.java), mapService, mock(com.openlogh.service.HistoryService::class.java), mock(InheritanceService::class.java))
        wireRepos()
        `when`(mapService.getAdjacentCities(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())).thenReturn(emptyList())
    }

    private fun wireRepos() {
        `when`(planetRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            planets.values.filter { it.sessionId == sessionId }.map { clonePlanet(it) }
        }
        `when`(planetRepository.findByFactionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val factionId = inv.arguments[0] as Long
            planets.values.filter { it.factionId == factionId }.map { clonePlanet(it) }
        }
        `when`(planetRepository.save(ArgumentMatchers.any(Planet::class.java))).thenAnswer { inv ->
            val planet = inv.arguments[0] as Planet
            planets[planet.id] = clonePlanet(planet)
            planet
        }

        `when`(factionRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            factions.values.filter { it.sessionId == sessionId }.map { cloneFaction(it) }
        }
        `when`(factionRepository.save(ArgumentMatchers.any(Faction::class.java))).thenAnswer { inv ->
            val faction = inv.arguments[0] as Faction
            factions[faction.id] = cloneFaction(faction)
            faction
        }

        `when`(officerRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            officers.values.filter { it.sessionId == sessionId }.map { cloneOfficer(it) }
        }
        `when`(officerRepository.save(ArgumentMatchers.any(Officer::class.java))).thenAnswer { inv ->
            val officer = inv.arguments[0] as Officer
            officers[officer.id] = cloneOfficer(officer)
            officer
        }
    }

    private fun clonePlanet(planet: Planet): Planet = planet
    private fun cloneFaction(faction: Faction): Faction = faction
    private fun cloneOfficer(officer: Officer): Officer = officer

    private fun seed(world: SessionState, planetList: List<Planet>, factionList: List<Faction>, officerList: List<Officer>) {
        planets.clear()
        factions.clear()
        officers.clear()
        planetList.forEach { planets[it.id] = clonePlanet(it) }
        factionList.forEach { factions[it.id] = cloneFaction(it) }
        officerList.forEach { officers[it.id] = cloneOfficer(it) }
    }

    private fun world(year: Short = 200, month: Short = 3): SessionState =
        SessionState(id = 1, scenarioCode = "test", currentYear = year, currentMonth = month, tickSeconds = 300)

    private fun planet(
        id: Long = 1,
        factionId: Long = 1,
        population: Int = 10000,
        populationMax: Int = 50000,
        production: Int = 500,
        productionMax: Int = 1000,
        commerce: Int = 500,
        commerceMax: Int = 1000,
        security: Int = 500,
        securityMax: Int = 1000,
        orbitalDefense: Int = 500,
        orbitalDefenseMax: Int = 1000,
        fortress: Int = 500,
        fortressMax: Int = 1000,
        approval: Float = 80f,
        supplyState: Short = 1,
        level: Short = 5,
        dead: Int = 0,
    ): Planet = Planet(
        id = id,
        sessionId = 1,
        name = "테스트도시$id",
        mapPlanetId = id.toInt(),
        factionId = factionId,
        population = population,
        populationMax = populationMax,
        production = production,
        productionMax = productionMax,
        commerce = commerce,
        commerceMax = commerceMax,
        security = security,
        securityMax = securityMax,
        orbitalDefense = orbitalDefense,
        orbitalDefenseMax = orbitalDefenseMax,
        fortress = fortress,
        fortressMax = fortressMax,
        approval = approval,
        supplyState = supplyState,
        level = level,
        dead = dead,
    )

    private fun faction(
        id: Long = 1,
        funds: Int = 10000,
        supplies: Int = 10000,
        factionRank: Short = 1,
        conscriptionRateTmp: Short = 15,
        taxRate: Short = 100,
        capitalPlanetId: Long? = 1,
    ): Faction = Faction(
        id = id,
        sessionId = 1,
        name = "국가$id",
        color = "#FF0000",
        funds = funds,
        supplies = supplies,
        factionRank = factionRank,
        conscriptionRateTmp = conscriptionRateTmp,
        taxRate = taxRate,
        capitalPlanetId = capitalPlanetId,
    )

    private fun officer(
        id: Long = 1,
        factionId: Long = 1,
        planetId: Long = 1,
        funds: Int = 1000,
        supplies: Int = 1000,
        dedication: Int = 1000,
        rank: Short = 1,
        stationedSystem: Int = 0,
        npcState: Short = 0,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "장수$id",
        factionId = factionId,
        planetId = planetId,
        funds = funds,
        supplies = supplies,
        dedication = dedication,
        rank = rank,
        stationedSystem = stationedSystem,
        npcState = npcState,
        turnTime = OffsetDateTime.now(),
    )

    @Test
    fun `processMonthly adds income to faction treasury`() {
        val w = world(month = 3)
        seed(w, listOf(planet()), listOf(faction(funds = 0, supplies = 0, taxRate = 0)), listOf(officer(dedication = 0)))

        service.processMonthly(w)

        val updated = factions[1L]!!
        assertTrue(updated.funds > 0)
        assertTrue(updated.supplies > 0)
    }

    @Test
    fun `processMonthly skips unsupplied planet for income`() {
        val w = world(month = 3)
        seed(w, listOf(planet(supplyState = 0)), listOf(faction(funds = 0, supplies = 0, taxRate = 0)), listOf(officer(dedication = 0)))

        service.processMonthly(w)

        assertEquals(0, factions[1L]!!.funds)
    }

    @Test
    fun `processMonthly distributes salary to officers except npcState 5`() {
        val w = world(month = 3)
        seed(
            w,
            listOf(planet(population = 30000, commerce = 800, approval = 100f)),
            listOf(faction(funds = 50000, supplies = 50000, taxRate = 100)),
            listOf(
                officer(id = 1, funds = 0, supplies = 0, dedication = 10000, npcState = 0),
                officer(id = 2, funds = 0, supplies = 0, dedication = 10000, npcState = 5),
            ),
        )

        service.processMonthly(w)

        assertTrue(officers[1L]!!.funds > 0)
        assertEquals(0, officers[2L]!!.funds)
    }

    @Test
    fun `processMonthly converts dead count to war income`() {
        val w = world(month = 3)
        seed(
            w,
            listOf(planet(dead = 100, population = 5000, populationMax = 50000)),
            listOf(faction(funds = 0, supplies = 0, taxRate = 0)),
            listOf(officer(dedication = 0)),
        )

        service.processMonthly(w)

        assertTrue(factions[1L]!!.funds >= 10)
        assertTrue(planets[1L]!!.population >= 5020)
        assertEquals(0, planets[1L]!!.dead)
    }

    @Test
    fun `semi annual applies on January and July only`() {
        val jan = world(month = 1)
        seed(
            jan,
            listOf(planet(production = 1000, productionMax = 1000), planet(id = 2, factionId = 0, approval = 80f, production = 1000, productionMax = 1000)),
            listOf(faction(funds = 50000, supplies = 50000, conscriptionRateTmp = 25, taxRate = 0)),
            listOf(officer(funds = 20000, supplies = 20000, dedication = 0)),
        )
        service.processMonthly(jan)
        assertTrue(planets[1L]!!.production < 1000)
        assertEquals(50f, planets[2L]!!.approval)

        val jul = world(month = 7)
        seed(jul, listOf(planet(production = 1000, productionMax = 1000)), listOf(faction(funds = 50000, supplies = 50000, conscriptionRateTmp = 25, taxRate = 0)), listOf(officer(funds = 20000, supplies = 20000, dedication = 0)))
        service.processMonthly(jul)
        assertTrue(planets[1L]!!.production < 1000)

        val mar = world(month = 3)
        seed(mar, listOf(planet(id = 2, factionId = 0, approval = 80f, production = 1000, productionMax = 1000)), listOf(faction(funds = 0, supplies = 0, taxRate = 0)), emptyList())
        service.processMonthly(mar)
        assertEquals(80f, planets[2L]!!.approval)
    }

    @Test
    fun `semi annual applies decay on officer and faction high resources`() {
        val w = world(month = 1)
        seed(
            w,
            listOf(planet()),
            listOf(faction(funds = 200000, supplies = 200000, taxRate = 0)),
            listOf(officer(funds = 20000, supplies = 500, dedication = 0)),
        )

        service.processMonthly(w)

        assertTrue(officers[1L]!!.funds < 20000)
        assertTrue(factions[1L]!!.funds < 200000)
    }

    @Test
    fun `faction level up and no level down behaviors`() {
        val w = world(month = 3)
        val highPlanets = (1..5).map { planet(id = it.toLong(), level = 5) }
        seed(w, highPlanets, listOf(faction(factionRank = 1, funds = 0, supplies = 0, taxRate = 0)), listOf(officer(dedication = 0)))
        service.processMonthly(w)
        assertTrue(factions[1L]!!.factionRank >= 3)

        seed(w, listOf(planet(level = 5)), listOf(faction(factionRank = 5, funds = 0, supplies = 0, taxRate = 0)), listOf(officer(dedication = 0)))
        service.processMonthly(w)
        assertEquals(5, factions[1L]!!.factionRank.toInt())
    }

    // ── Legacy parity: semi-annual decay logic ──

    @Test
    fun `semiAnnual supplied faction planet gets growth without pre-decay`() {
        // Legacy popIncrease(): supplied nation cities only get growth (no 0.99 pre-decay)
        // genericRatio = (20 - taxRate) / 200 = (20 - 10) / 200 = 0.05
        val w = world(month = 1)
        seed(
            w,
            listOf(planet(production = 1000, productionMax = 10000, security = 0, securityMax = 1000, supplyState = 1)),
            listOf(faction(conscriptionRateTmp = 10, taxRate = 0)),
            listOf(officer(funds = 0, supplies = 0, dedication = 0)),
        )
        service.processMonthly(w)
        // Legacy: min(10000, floor(1000 * 1.05)) = 1050
        // Bug: floor(floor(1000 * 0.99) * 1.05) = floor(990 * 1.05) = 1039
        assertEquals(1050, planets[1L]!!.production)
    }

    @Test
    fun `semiAnnual neutral planet stats decay exactly once`() {
        // Legacy popIncrease(): neutral cities (nation=0) decay by 0.99 exactly once
        val w = world(month = 1)
        seed(
            w,
            listOf(planet(id = 1, factionId = 0, production = 1000, productionMax = 10000)),
            emptyList(),
            emptyList(),
        )
        service.processMonthly(w)
        // Legacy: floor(1000 * 0.99) = 990
        // Bug: floor(floor(1000 * 0.99) * 0.99) = floor(990 * 0.99) = 980
        assertEquals(990, planets[1L]!!.production)
    }

    @Test
    fun `capital planet produces more income than non capital`() {
        val w = world(month = 3)
        val f1 = faction(id = 1, funds = 0, supplies = 0, taxRate = 0, capitalPlanetId = 1)
        val f2 = faction(id = 2, funds = 0, supplies = 0, taxRate = 0, capitalPlanetId = 99)
        seed(
            w,
            listOf(planet(id = 1, factionId = 1), planet(id = 2, factionId = 2)),
            listOf(f1, f2),
            listOf(officer(id = 1, factionId = 1, dedication = 0), officer(id = 2, factionId = 2, dedication = 0)),
        )

        service.processMonthly(w)

        assertTrue(factions[1L]!!.funds > factions[2L]!!.funds)
    }
}
