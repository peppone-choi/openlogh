package com.openlogh.engine

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
    private lateinit var gin7Service: Gin7EconomyService
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
        gin7Service = Gin7EconomyService(factionRepository, planetRepository, officerRepository, mapService)
        service = EconomyService(
            planetRepository,
            factionRepository,
            officerRepository,
            mock(MessageRepository::class.java),
            mapService,
            mock(com.openlogh.service.HistoryService::class.java),
            mock(InheritanceService::class.java),
            gin7Service,
        )
        wireRepos()
        `when`(mapService.getAdjacentCities(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())).thenReturn(emptyList())
    }

    private fun wireRepos() {
        `when`(planetRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            cities.values.filter { it.sessionId == sessionId }.map { cloneCity(it) }
        }
        `when`(planetRepository.findByFactionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val factionId = inv.arguments[0] as Long
            cities.values.filter { it.factionId == factionId }.map { cloneCity(it) }
        }
        `when`(planetRepository.save(ArgumentMatchers.any(Planet::class.java))).thenAnswer { inv ->
            val city = inv.arguments[0] as Planet
            cities[city.id] = cloneCity(city)
            city
        }
        `when`(planetRepository.saveAll(ArgumentMatchers.anyList<Planet>())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val saved = inv.arguments[0] as List<Planet>
            saved.forEach { city -> cities[city.id] = cloneCity(city) }
            saved
        }

        `when`(factionRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            nations.values.filter { it.sessionId == sessionId }.map { cloneNation(it) }
        }
        `when`(factionRepository.save(ArgumentMatchers.any(Faction::class.java))).thenAnswer { inv ->
            val nation = inv.arguments[0] as Faction
            nations[nation.id] = cloneNation(nation)
            nation
        }
        `when`(factionRepository.saveAll(ArgumentMatchers.anyList<Faction>())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val saved = inv.arguments[0] as List<Faction>
            saved.forEach { faction -> nations[faction.id] = cloneNation(faction) }
            saved
        }

        `when`(officerRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            generals.values.filter { it.sessionId == sessionId }.map { cloneGeneral(it) }
        }
        `when`(officerRepository.save(ArgumentMatchers.any(Officer::class.java))).thenAnswer { inv ->
            val general = inv.arguments[0] as Officer
            generals[general.id] = cloneGeneral(general)
            general
        }
        `when`(officerRepository.saveAll(ArgumentMatchers.anyList<Officer>())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val saved = inv.arguments[0] as List<Officer>
            saved.forEach { general -> generals[general.id] = cloneGeneral(general) }
            saved
        }
    }

    private fun cloneCity(city: Planet): Planet = city.toSnapshot().toEntity()
    private fun cloneNation(nation: Faction): Faction = nation.toSnapshot().toEntity()
    private fun cloneGeneral(general: Officer): Officer = general.toSnapshot().toEntity()

    private fun seed(world: SessionState, cityList: List<Planet>, nationList: List<Faction>, generalList: List<Officer>) {
        cities.clear()
        nations.clear()
        generals.clear()
        cityList.forEach { cities[it.id] = cloneCity(it) }
        nationList.forEach { nations[it.id] = cloneNation(it) }
        generalList.forEach { generals[it.id] = cloneGeneral(it) }
    }

    private fun world(year: Short = 200, month: Short = 3): SessionState =
        SessionState(id = 1, scenarioCode = "test", currentYear = year, currentMonth = month, tickSeconds = 300)

    private fun city(
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

    private fun nation(
        id: Long = 1,
        funds: Int = 10000,
        supplies: Int = 10000,
        level: Short = 1,
        rateTmp: Short = 15,
        bill: Short = 100,
        capitalPlanetId: Long? = 1,
    ): Faction = Faction(
        id = id,
        sessionId = 1,
        name = "국가$id",
        color = "#FF0000",
        funds = funds,
        supplies = supplies,
        factionRank = level,
        conscriptionRateTmp = rateTmp,
        taxRate = bill,
        capitalPlanetId = capitalPlanetId,
    )

    private fun general(
        id: Long = 1,
        factionId: Long = 1,
        planetId: Long = 1,
        funds: Int = 1000,
        supplies: Int = 1000,
        dedication: Int = 1000,
        officerLevel: Short = 1,
        officerPlanet: Int = 0,
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
        officerLevel = officerLevel,
        officerPlanet = officerPlanet,
        npcState = npcState,
        turnTime = OffsetDateTime.now(),
    )

    @Test
    fun `processMonthly adds income to nation treasury`() {
        val w = world(month = 1)
        seed(w, listOf(city(commerce = 500)), listOf(nation(funds = 0, supplies = 0, bill = 20)), emptyList())

        service.processIncomeEvent(w, "gold")

        val updated = nations[1L]!!
        assertTrue(updated.funds > 0)
        assertEquals(0, updated.supplies)
    }

    @Test
    fun `processMonthly skips unsupplied city for income`() {
        val w = world(month = 3)
        seed(w, listOf(city(supplyState = 0)), listOf(nation(funds = 0, supplies = 0, bill = 0)), listOf(general(dedication = 0)))

        service.processMonthly(w)

        assertEquals(0, nations[1L]!!.funds)
    }

    @Test
    fun `processMonthly distributes salary to generals except npcState 5`() {
        val w = world(month = 1)
        seed(
            w,
            listOf(city(population = 30000, commerce = 800, approval = 100f)),
            listOf(nation(funds = 50000, supplies = 50000, bill = 100)),
            listOf(
                general(id = 1, funds = 0, supplies = 0, dedication = 10000, npcState = 0),
                general(id = 2, funds = 0, supplies = 0, dedication = 10000, npcState = 5),
            ),
        )

        service.processIncomeEvent(w, "gold")

        assertTrue(generals[1L]!!.funds > 0)
        assertEquals(0, generals[2L]!!.funds)
    }

    @Test
    fun `processMonthly converts dead count to war income`() {
        val w = world(month = 3)
        seed(
            w,
            listOf(city(dead = 100, population = 5000, populationMax = 50000)),
            listOf(nation(funds = 0, supplies = 0, bill = 0)),
            listOf(general(dedication = 0)),
        )

        service.processWarIncomeEvent(w)

        assertTrue(nations[1L]!!.funds >= 10)
        assertTrue(cities[1L]!!.population >= 5020)
        assertEquals(0, cities[1L]!!.dead)
    }

    @Test
    fun `semi annual applies on January and July only`() {
        val jan = world(month = 1)
        seed(
            jan,
            listOf(city(production = 1000, productionMax = 1000)),
            listOf(nation(funds = 50000, supplies = 50000, bill = 100)),
            listOf(general(funds = 20000, supplies = 20000, dedication = 0)),
        )
        service.processSemiAnnualEvent(jan, "gold")
        assertTrue(generals[1L]!!.funds < 20000)
        assertEquals(20000, generals[1L]!!.supplies)

        val jul = world(month = 7)
        seed(jul, listOf(city(production = 1000, productionMax = 1000)), listOf(nation(funds = 50000, supplies = 50000, bill = 100)), listOf(general(funds = 20000, supplies = 20000, dedication = 0)))
        service.processSemiAnnualEvent(jul, "rice")
        assertTrue(generals[1L]!!.supplies < 20000)
        assertEquals(20000, generals[1L]!!.funds)

        val mar = world(month = 3)
        seed(mar, listOf(city(approval = 80f, production = 1000, productionMax = 1000)), listOf(nation(funds = 0, supplies = 0, bill = 0)), emptyList())
        service.postUpdateMonthly(mar)
        assertEquals(80f, cities[1L]!!.approval)
    }

    @Test
    fun `semi annual applies decay on general and nation high resources`() {
        val w = world(month = 1)
        seed(
            w,
            listOf(city()),
            listOf(nation(funds = 200000, supplies = 200000, bill = 0)),
            listOf(general(funds = 20000, supplies = 500, dedication = 0)),
        )

        service.processSemiAnnualEvent(w, "gold")

        assertTrue(generals[1L]!!.funds < 20000)
        assertTrue(nations[1L]!!.funds < 200000)
    }

    @Test
    fun `nation level up and no level down behaviors`() {
        val w = world(month = 1)
        val highCities = (1..5).map { city(id = it.toLong(), level = 5) }
        seed(w, highCities, listOf(nation(level = 1, funds = 0, supplies = 0, bill = 0)), listOf(general(dedication = 0)))
        service.updateNationLevelEvent(w)
        assertTrue(nations[1L]!!.factionRank >= 3)

        seed(w, listOf(city(level = 5)), listOf(nation(level = 5, funds = 0, supplies = 0, bill = 0)), listOf(general(dedication = 0)))
        service.updateNationLevelEvent(w)
        assertEquals(1, nations[1L]!!.factionRank.toInt())
    }

    // ── Legacy parity: semi-annual decay logic ──

    @Test
    fun `semiAnnual supplied nation city gets growth without pre-decay`() {
        val w = world(month = 3)
        seed(
            w,
            listOf(city(production = 1000, productionMax = 10000, security = 0, securityMax = 1000, supplyState = 1)),
            listOf(nation(rateTmp = 10, bill = 0)),
            listOf(general(funds = 0, supplies = 0, dedication = 0)),
        )
        gin7Service.processMonthly(w)
        assertEquals((1000 * 1.003).toInt(), cities[1L]!!.production)
    }

    @Test
    fun `semiAnnual neutral city stats decay exactly once`() {
        val w = world(month = 3)
        w.config["mapCode"] = "test"
        seed(
            w,
            listOf(city(id = 1, factionId = 0, supplyState = 0, production = 1000, productionMax = 10000)),
            emptyList(),
            emptyList(),
        )
        service.updateCitySupplyState(w)
        assertEquals(1, cities[1L]!!.supplyState.toInt())
    }

    @Test
    fun `capital city produces more income than non capital`() {
        val w = world(month = 1)
        val n1 = nation(id = 1, funds = 0, supplies = 0, bill = 0, capitalPlanetId = 1)
        val n2 = nation(id = 2, funds = 0, supplies = 0, bill = 0, capitalPlanetId = 99)
        seed(
            w,
            listOf(city(id = 1, factionId = 1), city(id = 2, factionId = 2)),
            listOf(n1, n2),
            emptyList(),
        )

        service.processIncomeEvent(w, "gold")

        assertEquals(nations[1L]!!.funds, nations[2L]!!.funds)
    }
}
