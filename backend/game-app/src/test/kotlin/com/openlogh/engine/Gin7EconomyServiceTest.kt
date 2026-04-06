package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.PlanetRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class Gin7EconomyServiceTest {

    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var service: Gin7EconomyService

    @BeforeEach
    fun setUp() {
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        service = Gin7EconomyService(factionRepository, planetRepository)
    }

    private fun makeWorld(month: Int): SessionState {
        val world = SessionState()
        world.id = 1.toShort()
        world.currentMonth = month.toShort()
        world.currentYear = 800.toShort()
        return world
    }

    private fun makeFaction(id: Long, sessionId: Long, taxRate: Int, funds: Int = 0): Faction {
        val f = Faction()
        f.id = id
        f.sessionId = sessionId
        f.taxRate = taxRate.toShort()
        f.funds = funds
        f.factionType = "empire"
        return f
    }

    private fun makePlanet(
        id: Long,
        sessionId: Long,
        factionId: Long,
        commerce: Int = 10000,
        population: Int = 5000,
        populationMax: Int = 10000,
        production: Int = 3000,
        productionMax: Int = 6000,
        commerceMax: Int = 20000,
        approval: Float = 50f,
        supplyState: Short = 1,
    ): Planet {
        val p = Planet()
        p.id = id
        p.sessionId = sessionId
        p.factionId = factionId
        p.commerce = commerce
        p.population = population
        p.populationMax = populationMax
        p.production = production
        p.productionMax = productionMax
        p.commerceMax = commerceMax
        p.approval = approval
        p.supplyState = supplyState
        return p
    }

    /**
     * Test 1: taxRate=30일 때 planet.commerce=10000이면 세수 = 10000 * 0.30 = 3000
     */
    @Test
    fun `taxRate 30 commerce 10000 yields tax revenue 3000`() {
        val world = makeWorld(month = 1)
        val faction = makeFaction(id = 10L, sessionId = 1L, taxRate = 30, funds = 0)
        val planet = makePlanet(id = 100L, sessionId = 1L, factionId = 10L, commerce = 10000)

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

        service.processMonthly(world)

        assertEquals(3000, faction.funds)
    }

    /**
     * Test 2: taxRate=50일 때 approval이 감소한다 (approval -= (taxRate - 30) * 0.5f per month)
     */
    @Test
    fun `taxRate 50 decreases approval`() {
        val world = makeWorld(month = 1)
        val faction = makeFaction(id = 10L, sessionId = 1L, taxRate = 50, funds = 0)
        val planet = makePlanet(id = 100L, sessionId = 1L, factionId = 10L, approval = 60f)

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

        service.processMonthly(world)

        // approval -= (50 - 30) * 0.5f = 10f → 60f - 10f = 50f
        assertEquals(50f, planet.approval, 0.001f)
    }

    /**
     * Test 3: taxRate=20일 때 approval이 증가한다 (approval += (30 - taxRate) * 0.3f per month)
     */
    @Test
    fun `taxRate 20 increases approval`() {
        val world = makeWorld(month = 1)
        val faction = makeFaction(id = 10L, sessionId = 1L, taxRate = 20, funds = 0)
        val planet = makePlanet(id = 100L, sessionId = 1L, factionId = 10L, approval = 50f)

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

        service.processMonthly(world)

        // approval += (30 - 20) * 0.3f = 3f → 50f + 3f = 53f
        assertEquals(53f, planet.approval, 0.001f)
    }

    /**
     * Test 4: population < populationMax일 때 매달 0.5% 성장한다
     */
    @Test
    fun `population grows by 0_5 percent per month when below max`() {
        val world = makeWorld(month = 2) // non-tax month
        val faction = makeFaction(id = 10L, sessionId = 1L, taxRate = 30)
        val planet = makePlanet(
            id = 100L, sessionId = 1L, factionId = 10L,
            population = 5000, populationMax = 10000,
            production = 3000, productionMax = 6000,
            commerce = 8000, commerceMax = 20000,
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

        service.processMonthly(world)

        // population: (5000 * 1.005).toInt() = 5024 (floating point: 5000 * 1.005 = 5024.999...)
        assertEquals(5024, planet.population)
    }

    /**
     * Test 5: month=1,4,7,10일 때만 세금 징수 실행된다 (90일 주기)
     */
    @Test
    fun `tax is only collected on months 1 4 7 10`() {
        // Non-tax months: 2, 3, 5, 6, 8, 9, 11, 12
        val nonTaxMonths = listOf(2, 3, 5, 6, 8, 9, 11, 12)
        for (month in nonTaxMonths) {
            val world = makeWorld(month = month)
            val faction = makeFaction(id = 10L, sessionId = 1L, taxRate = 30, funds = 0)
            val planet = makePlanet(id = 100L, sessionId = 1L, factionId = 10L, commerce = 10000)

            `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

            service.processMonthly(world)

            assertEquals(0, faction.funds, "Month $month should NOT collect tax, but funds changed")
        }

        // Tax months: 1, 4, 7, 10
        val taxMonths = listOf(1, 4, 7, 10)
        for (month in taxMonths) {
            val world = makeWorld(month = month)
            val faction = makeFaction(id = 10L, sessionId = 1L, taxRate = 30, funds = 0)
            val planet = makePlanet(id = 100L, sessionId = 1L, factionId = 10L, commerce = 10000)

            `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

            service.processMonthly(world)

            assertTrue(faction.funds > 0, "Month $month SHOULD collect tax, but funds=0")
        }
    }

    /**
     * Test 6: 행성 supplyState=0(고립)이면 자원 성장 없음
     */
    @Test
    fun `isolated planet supplyState 0 does not grow resources`() {
        val world = makeWorld(month = 2) // non-tax month
        val faction = makeFaction(id = 10L, sessionId = 1L, taxRate = 30)
        val planet = makePlanet(
            id = 100L, sessionId = 1L, factionId = 10L,
            population = 5000, populationMax = 10000,
            production = 3000, productionMax = 6000,
            commerce = 8000, commerceMax = 20000,
            supplyState = 0,
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

        service.processMonthly(world)

        // No growth — all values remain unchanged
        assertEquals(5000, planet.population)
        assertEquals(3000, planet.production)
        assertEquals(8000, planet.commerce)
    }
}
