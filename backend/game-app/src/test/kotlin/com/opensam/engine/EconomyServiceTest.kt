package com.opensam.engine

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
import com.opensam.service.InheritanceService
import com.opensam.service.MapService
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
        service = EconomyService(cityRepository, nationRepository, generalRepository, mock(MessageRepository::class.java), mapService, mock(com.opensam.service.HistoryService::class.java), mock(InheritanceService::class.java))
        wireRepos()
        `when`(mapService.getAdjacentCities(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())).thenReturn(emptyList())
    }

    private fun wireRepos() {
        `when`(cityRepository.findByWorldId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            cities.values.filter { it.worldId == worldId }.map { cloneCity(it) }
        }
        `when`(cityRepository.findByNationId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val nationId = inv.arguments[0] as Long
            cities.values.filter { it.nationId == nationId }.map { cloneCity(it) }
        }
        `when`(cityRepository.save(ArgumentMatchers.any(City::class.java))).thenAnswer { inv ->
            val city = inv.arguments[0] as City
            cities[city.id] = cloneCity(city)
            city
        }

        `when`(nationRepository.findByWorldId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            nations.values.filter { it.worldId == worldId }.map { cloneNation(it) }
        }
        `when`(nationRepository.save(ArgumentMatchers.any(Nation::class.java))).thenAnswer { inv ->
            val nation = inv.arguments[0] as Nation
            nations[nation.id] = cloneNation(nation)
            nation
        }

        `when`(generalRepository.findByWorldId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            generals.values.filter { it.worldId == worldId }.map { cloneGeneral(it) }
        }
        `when`(generalRepository.save(ArgumentMatchers.any(General::class.java))).thenAnswer { inv ->
            val general = inv.arguments[0] as General
            generals[general.id] = cloneGeneral(general)
            general
        }
    }

    private fun cloneCity(city: City): City = city.toSnapshot().toEntity()
    private fun cloneNation(nation: Nation): Nation = nation.toSnapshot().toEntity()
    private fun cloneGeneral(general: General): General = general.toSnapshot().toEntity()

    private fun seed(world: WorldState, cityList: List<City>, nationList: List<Nation>, generalList: List<General>) {
        cities.clear()
        nations.clear()
        generals.clear()
        cityList.forEach { cities[it.id] = cloneCity(it) }
        nationList.forEach { nations[it.id] = cloneNation(it) }
        generalList.forEach { generals[it.id] = cloneGeneral(it) }
    }

    private fun world(year: Short = 200, month: Short = 3): WorldState =
        WorldState(id = 1, scenarioCode = "test", currentYear = year, currentMonth = month, tickSeconds = 300)

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
    ): City = City(
        id = id,
        worldId = 1,
        name = "테스트도시$id",
        mapCityId = id.toInt(),
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
    ): Nation = Nation(
        id = id,
        worldId = 1,
        name = "국가$id",
        color = "#FF0000",
        gold = gold,
        rice = rice,
        level = level,
        rateTmp = rateTmp,
        bill = bill,
        capitalCityId = capitalCityId,
    )

    private fun general(
        id: Long = 1,
        nationId: Long = 1,
        cityId: Long = 1,
        gold: Int = 1000,
        rice: Int = 1000,
        dedication: Int = 1000,
        officerLevel: Short = 1,
        officerCity: Int = 0,
        npcState: Short = 0,
    ): General = General(
        id = id,
        worldId = 1,
        name = "장수$id",
        nationId = nationId,
        cityId = cityId,
        gold = gold,
        rice = rice,
        dedication = dedication,
        officerLevel = officerLevel,
        officerCity = officerCity,
        npcState = npcState,
        turnTime = OffsetDateTime.now(),
    )

    @Test
    fun `processMonthly adds income to nation treasury`() {
        val w = world(month = 3)
        seed(w, listOf(city()), listOf(nation(gold = 0, rice = 0, bill = 0)), listOf(general(dedication = 0)))

        service.processMonthly(w)

        val updated = nations[1L]!!
        assertTrue(updated.gold > 0)
        assertTrue(updated.rice > 0)
    }

    @Test
    fun `processMonthly skips unsupplied city for income`() {
        val w = world(month = 3)
        seed(w, listOf(city(supplyState = 0)), listOf(nation(gold = 0, rice = 0, bill = 0)), listOf(general(dedication = 0)))

        service.processMonthly(w)

        assertEquals(0, nations[1L]!!.gold)
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

        assertTrue(generals[1L]!!.gold > 0)
        assertEquals(0, generals[2L]!!.gold)
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

        assertTrue(nations[1L]!!.gold >= 10)
        assertTrue(cities[1L]!!.pop >= 5020)
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
        assertTrue(cities[1L]!!.agri < 1000)
        assertEquals(50f, cities[2L]!!.trust)

        val jul = world(month = 7)
        seed(jul, listOf(city(agri = 1000, agriMax = 1000)), listOf(nation(gold = 50000, rice = 50000, rateTmp = 25, bill = 0)), listOf(general(gold = 20000, rice = 20000, dedication = 0)))
        service.processMonthly(jul)
        assertTrue(cities[1L]!!.agri < 1000)

        val mar = world(month = 3)
        seed(mar, listOf(city(id = 2, nationId = 0, trust = 80f, agri = 1000, agriMax = 1000)), listOf(nation(gold = 0, rice = 0, bill = 0)), emptyList())
        service.processMonthly(mar)
        assertEquals(80f, cities[2L]!!.trust)
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

        assertTrue(generals[1L]!!.gold < 20000)
        assertTrue(nations[1L]!!.gold < 200000)
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

        assertTrue(nations[1L]!!.gold > nations[2L]!!.gold)
    }
}
