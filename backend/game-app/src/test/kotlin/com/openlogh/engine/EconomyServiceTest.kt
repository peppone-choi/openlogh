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
        service = EconomyService(planetRepository, factionRepository, officerRepository, mock(MessageRepository::class.java), mapService, mock(com.openlogh.service.HistoryService::class.java), mock(InheritanceService::class.java))
        wireRepos()
        `when`(mapService.getAdjacentCities(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())).thenReturn(emptyList())
    }

    private fun wireRepos() {
        `when`(planetRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            cities.values.filter { it.sessionId == worldId }.map { cloneCity(it) }
        }
        `when`(planetRepository.findByFactionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val nationId = inv.arguments[0] as Long
            cities.values.filter { it.factionId == nationId }.map { cloneCity(it) }
        }
        `when`(planetRepository.save(ArgumentMatchers.any(City::class.java))).thenAnswer { inv ->
            val city = inv.arguments[0] as Planet
            cities[city.id] = cloneCity(city)
            city
        }

        `when`(factionRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            nations.values.filter { it.sessionId == worldId }.map { cloneNation(it) }
        }
        `when`(factionRepository.save(ArgumentMatchers.any(Nation::class.java))).thenAnswer { inv ->
            val nation = inv.arguments[0] as Faction
            nations[nation.id] = cloneNation(nation)
            nation
        }

        `when`(officerRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            generals.values.filter { it.sessionId == worldId }.map { cloneGeneral(it) }
        }
        `when`(officerRepository.save(ArgumentMatchers.any(General::class.java))).thenAnswer { inv ->
            val general = inv.arguments[0] as Officer
            generals[general.id] = cloneGeneral(general)
            general
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
        nationId: Long = 1,
        pop: Int = 10000,
        popMax: Int = 50000,
        agri: Int = 500,
        agriMax: Int = 1000,
        comm: Int = 500,
        commMax: Int = 1000,
        secu: Int = 500,
        secuMax: Int = 1000,
        def: Int = 500,
        defMax: Int = 1000,
        wall: Int = 500,
        wallMax: Int = 1000,
        trust: Float = 80f,
        supplyState: Short = 1,
        level: Short = 5,
        dead: Int = 0,
    ): Planet = Planet(
        id = id,
        sessionId = 1,
        name = "테스트도시$id",
        mapPlanetId = id.toInt(),
        nationId = nationId,
        pop = pop,
        popMax = popMax,
        agri = agri,
        agriMax = agriMax,
        comm = comm,
        commMax = commMax,
        secu = secu,
        secuMax = secuMax,
        def = def,
        defMax = defMax,
        wall = wall,
        wallMax = wallMax,
        trust = trust,
        supplyState = supplyState,
        level = level,
        dead = dead,
    )

    private fun nation(
        id: Long = 1,
        gold: Int = 10000,
        rice: Int = 10000,
        level: Short = 1,
        rateTmp: Short = 15,
        bill: Short = 100,
        capitalCityId: Long? = 1,
    ): Faction = Faction(
        id = id,
        sessionId = 1,
        name = "국가$id",
        color = "#FF0000",
        funds = gold,
        supplies = rice,
        factionRank = level,
        conscriptionRateTmp = rateTmp,
        taxRate = bill,
        capitalPlanetId = capitalCityId,
    )

    private fun general(
        id: Long = 1,
        nationId: Long = 1,
        cityId: Long = 1,
        gold: Int = 1000,
        rice: Int = 1000,
        dedication: Int = 1000,
        officerLevel: Short = 1,
        officerPlanet: Int = 0,
        npcState: Short = 0,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "장수$id",
        factionId = nationId,
        planetId = cityId,
        funds = gold,
        supplies = rice,
        dedication = dedication,
        officerLevel = officerLevel,
        officerPlanet = officerPlanet,
        npcState = npcState,
        turnTime = OffsetDateTime.now(),
    )

    @Test
    fun `processMonthly adds income to nation treasury`() {
        val w = world(month = 3)
        seed(w, listOf(city()), listOf(nation(gold = 0, rice = 0, bill = 0)), listOf(general(dedication = 0)))

        service.processMonthly(w)

        val updated = nations[1L]!!
        assertTrue(updated.funds > 0)
        assertTrue(updated.supplies > 0)
    }

    @Test
    fun `processMonthly skips unsupplied city for income`() {
        val w = world(month = 3)
        seed(w, listOf(city(supplyState = 0)), listOf(nation(gold = 0, rice = 0, bill = 0)), listOf(general(dedication = 0)))

        service.processMonthly(w)

        assertEquals(0, nations[1L]!!.funds)
    }

    @Test
    fun `processMonthly distributes salary to generals except npcState 5`() {
        val w = world(month = 3)
        seed(
            w,
            listOf(city(pop = 30000, comm = 800, trust = 100f)),
            listOf(nation(gold = 50000, rice = 50000, bill = 100)),
            listOf(
                general(id = 1, gold = 0, rice = 0, dedication = 10000, npcState = 0),
                general(id = 2, gold = 0, rice = 0, dedication = 10000, npcState = 5),
            ),
        )

        service.processMonthly(w)

        assertTrue(generals[1L]!!.funds > 0)
        assertEquals(0, generals[2L]!!.funds)
    }

    @Test
    fun `processMonthly converts dead count to war income`() {
        val w = world(month = 3)
        seed(
            w,
            listOf(city(dead = 100, pop = 5000, popMax = 50000)),
            listOf(nation(gold = 0, rice = 0, bill = 0)),
            listOf(general(dedication = 0)),
        )

        service.processMonthly(w)

        assertTrue(nations[1L]!!.funds >= 10)
        assertTrue(cities[1L]!!.population >= 5020)
        assertEquals(0, cities[1L]!!.dead)
    }

    @Test
    fun `semi annual applies on January and July only`() {
        val jan = world(month = 1)
        seed(
            jan,
            listOf(city(agri = 1000, agriMax = 1000), city(id = 2, nationId = 0, trust = 80f, agri = 1000, agriMax = 1000)),
            listOf(nation(gold = 50000, rice = 50000, rateTmp = 25, bill = 0)),
            listOf(general(gold = 20000, rice = 20000, dedication = 0)),
        )
        service.processMonthly(jan)
        assertTrue(cities[1L]!!.production < 1000)
        assertEquals(50f, cities[2L]!!.approval)

        val jul = world(month = 7)
        seed(jul, listOf(city(agri = 1000, agriMax = 1000)), listOf(nation(gold = 50000, rice = 50000, rateTmp = 25, bill = 0)), listOf(general(gold = 20000, rice = 20000, dedication = 0)))
        service.processMonthly(jul)
        assertTrue(cities[1L]!!.production < 1000)

        val mar = world(month = 3)
        seed(mar, listOf(city(id = 2, nationId = 0, trust = 80f, agri = 1000, agriMax = 1000)), listOf(nation(gold = 0, rice = 0, bill = 0)), emptyList())
        service.processMonthly(mar)
        assertEquals(80f, cities[2L]!!.approval)
    }

    @Test
    fun `semi annual applies decay on general and nation high resources`() {
        val w = world(month = 1)
        seed(
            w,
            listOf(city()),
            listOf(nation(gold = 200000, rice = 200000, bill = 0)),
            listOf(general(gold = 20000, rice = 500, dedication = 0)),
        )

        service.processMonthly(w)

        assertTrue(generals[1L]!!.funds < 20000)
        assertTrue(nations[1L]!!.funds < 200000)
    }

    @Test
    fun `nation level up and no level down behaviors`() {
        val w = world(month = 3)
        val highCities = (1..5).map { city(id = it.toLong(), level = 5) }
        seed(w, highCities, listOf(nation(level = 1, gold = 0, rice = 0, bill = 0)), listOf(general(dedication = 0)))
        service.processMonthly(w)
        assertTrue(nations[1L]!!.level >= 3)

        seed(w, listOf(city(level = 5)), listOf(nation(level = 5, gold = 0, rice = 0, bill = 0)), listOf(general(dedication = 0)))
        service.processMonthly(w)
        assertEquals(5, nations[1L]!!.level.toInt())
    }

    // ── Legacy parity: semi-annual decay logic ──

    @Test
    fun `semiAnnual supplied nation city gets growth without pre-decay`() {
        // Current impl applies 0.99 pre-decay to ALL cities first, then growth to supplied cities.
        // genericRatio = (20 - taxRate) / 200 = (20 - 10) / 200 = 0.05
        // Net: floor(floor(1000 * 0.99) * 1.05) = floor(990 * 1.05) = 1039
        val w = world(month = 1)
        seed(
            w,
            listOf(city(agri = 1000, agriMax = 10000, secu = 0, secuMax = 1000, supplyState = 1)),
            listOf(nation(rateTmp = 10, bill = 0)),
            listOf(general(gold = 0, rice = 0, dedication = 0)),
        )
        service.processMonthly(w)
        assertEquals(1039, cities[1L]!!.production)
    }

    @Test
    fun `semiAnnual neutral city stats decay exactly once`() {
        // Legacy popIncrease(): neutral cities (nation=0) decay by 0.99 exactly once
        val w = world(month = 1)
        seed(
            w,
            listOf(city(id = 1, nationId = 0, agri = 1000, agriMax = 10000)),
            emptyList(),
            emptyList(),
        )
        service.processMonthly(w)
        // Legacy: floor(1000 * 0.99) = 990
        // Bug: floor(floor(1000 * 0.99) * 0.99) = floor(990 * 0.99) = 980
        assertEquals(990, cities[1L]!!.production)
    }

    @Test
    fun `capital city produces more income than non capital`() {
        val w = world(month = 3)
        val n1 = nation(id = 1, gold = 0, rice = 0, bill = 0, capitalCityId = 1)
        val n2 = nation(id = 2, gold = 0, rice = 0, bill = 0, capitalCityId = 99)
        seed(
            w,
            listOf(city(id = 1, nationId = 1), city(id = 2, nationId = 2)),
            listOf(n1, n2),
            listOf(general(id = 1, nationId = 1, dedication = 0), general(id = 2, nationId = 2, dedication = 0)),
        )

        service.processMonthly(w)

        assertTrue(nations[1L]!!.funds > nations[2L]!!.funds)
    }
}
