package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.service.MapService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Plan 23-06 regression guard for `Gin7EconomyService.updatePlanetSupplyState`.
 *
 * The logic was moved from legacy `EconomyService.updateCitySupplyState` (EconomyService.kt:134-353)
 * into Gin7EconomyService with LOGH domain-var renames. The computation itself is unchanged:
 *
 *   1. Build a BFS from each faction's capital through connected planets using
 *      `mapService.getAdjacentCities(mapCode, planetMapId)`.
 *   2. Planets reachable from the capital get `supplyState = 1` (supplied).
 *   3. Isolated planets (`supplyState = 0`) suffer 10% decay on
 *      population/approval/production/commerce/security/orbital_defense/fortress;
 *      their officers lose 5% ships/morale/training.
 *   4. Isolated planets below `approval < 30` defect to neutral (`factionId = 0`).
 *   5. Neutral planets (`factionId = 0`) are always supplied.
 *
 * Tests stub `MapService.getAdjacentCities` with synthetic adjacency data so the
 * algorithm can be exercised without depending on the real `logh.json` map file.
 */
class Gin7UpdatePlanetSupplyStateTest {

    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var mapService: MapService
    private lateinit var service: Gin7EconomyService

    @BeforeEach
    fun setUp() {
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        mapService = mock(MapService::class.java)
        service = Gin7EconomyService(factionRepository, planetRepository, officerRepository, mapService)
    }

    private fun makeWorld(sessionId: Short = 1): SessionState {
        val world = SessionState()
        world.id = sessionId
        world.currentMonth = 1
        world.currentYear = 800
        // Use "test" mapCode so the implementation delegates entirely to the mock.
        world.config = mutableMapOf("mapCode" to "test")
        return world
    }

    private fun makeFaction(
        id: Long,
        sessionId: Long,
        capitalPlanetId: Long?,
    ): Faction {
        val f = Faction()
        f.id = id
        f.sessionId = sessionId
        f.capitalPlanetId = capitalPlanetId
        f.factionType = "empire"
        return f
    }

    private fun makePlanet(
        id: Long,
        sessionId: Long,
        factionId: Long,
        mapPlanetId: Int,
        population: Int = 10_000,
        populationMax: Int = 20_000,
        production: Int = 5_000,
        productionMax: Int = 10_000,
        commerce: Int = 5_000,
        commerceMax: Int = 10_000,
        security: Int = 5_000,
        securityMax: Int = 10_000,
        orbitalDefense: Int = 5_000,
        orbitalDefenseMax: Int = 10_000,
        fortress: Int = 5_000,
        fortressMax: Int = 10_000,
        approval: Float = 60f,
        supplyState: Short = 1,
    ): Planet {
        val p = Planet()
        p.id = id
        p.sessionId = sessionId
        p.factionId = factionId
        p.mapPlanetId = mapPlanetId
        p.population = population
        p.populationMax = populationMax
        p.production = production
        p.productionMax = productionMax
        p.commerce = commerce
        p.commerceMax = commerceMax
        p.security = security
        p.securityMax = securityMax
        p.orbitalDefense = orbitalDefense
        p.orbitalDefenseMax = orbitalDefenseMax
        p.fortress = fortress
        p.fortressMax = fortressMax
        p.approval = approval
        p.supplyState = supplyState
        return p
    }

    private fun makeOfficer(
        id: Long,
        sessionId: Long,
        factionId: Long,
        planetId: Long,
        ships: Int = 1000,
        morale: Short = 100,
        training: Short = 80,
    ): Officer {
        val o = Officer()
        o.id = id
        o.sessionId = sessionId
        o.factionId = factionId
        o.planetId = planetId
        o.ships = ships
        o.morale = morale
        o.training = training
        return o
    }

    /**
     * Test 1: Connected planet chain — capital → P1 → P2, all under same faction, all supplied.
     *
     * mapPlanetIds: capital=1, p1=2, p2=3
     * Adjacency: 1→[2], 2→[1,3], 3→[2]
     */
    @Test
    fun `connected planet chain receives supplyState 1 and no decay`() {
        val world = makeWorld()
        val faction = makeFaction(id = 10L, sessionId = 1L, capitalPlanetId = 100L)
        val capital = makePlanet(id = 100L, sessionId = 1L, factionId = 10L, mapPlanetId = 1)
        val planetB = makePlanet(id = 101L, sessionId = 1L, factionId = 10L, mapPlanetId = 2)
        val planetC = makePlanet(id = 102L, sessionId = 1L, factionId = 10L, mapPlanetId = 3)

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(capital, planetB, planetC))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(mapService.getAdjacentCities("test", 1)).thenReturn(listOf(2))
        `when`(mapService.getAdjacentCities("test", 2)).thenReturn(listOf(1, 3))
        `when`(mapService.getAdjacentCities("test", 3)).thenReturn(listOf(2))

        service.updatePlanetSupplyState(world)

        assertEquals(1, capital.supplyState.toInt())
        assertEquals(1, planetB.supplyState.toInt())
        assertEquals(1, planetC.supplyState.toInt())
        // No decay — values remain untouched
        assertEquals(10_000, planetB.population)
        assertEquals(5_000, planetB.production)
        assertEquals(60f, planetB.approval, 0.001f)
    }

    /**
     * Test 2: Disconnected planet — capital at map id 1, isolated planet at map id 99 with no adjacency.
     * Isolated planet should be marked supplyState=0 and all resource fields decayed by 10%.
     * Officer stationed there loses 5% ships/morale/training.
     */
    @Test
    fun `disconnected planet is marked isolated and decays 10 percent`() {
        val world = makeWorld()
        val faction = makeFaction(id = 10L, sessionId = 1L, capitalPlanetId = 100L)
        val capital = makePlanet(id = 100L, sessionId = 1L, factionId = 10L, mapPlanetId = 1)
        val isolated = makePlanet(
            id = 200L, sessionId = 1L, factionId = 10L, mapPlanetId = 99,
            population = 10_000,
            production = 5_000,
            commerce = 4_000,
            security = 3_000,
            orbitalDefense = 2_000,
            fortress = 1_000,
            approval = 50f,
        )
        val officer = makeOfficer(
            id = 300L, sessionId = 1L, factionId = 10L, planetId = 200L,
            ships = 1_000, morale = 100, training = 80,
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(capital, isolated))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(officer))
        `when`(mapService.getAdjacentCities("test", 1)).thenReturn(emptyList())
        `when`(mapService.getAdjacentCities("test", 99)).thenReturn(emptyList())

        service.updatePlanetSupplyState(world)

        assertEquals(1, capital.supplyState.toInt(), "capital must remain supplied")
        assertEquals(0, isolated.supplyState.toInt(), "isolated planet must be supplyState=0")
        // 10% decay on all resource fields
        assertEquals((10_000 * 0.9).toInt(), isolated.population)
        assertEquals((5_000 * 0.9).toInt(), isolated.production)
        assertEquals((4_000 * 0.9).toInt(), isolated.commerce)
        assertEquals((3_000 * 0.9).toInt(), isolated.security)
        assertEquals((2_000 * 0.9).toInt(), isolated.orbitalDefense)
        assertEquals((1_000 * 0.9).toInt(), isolated.fortress)
        assertEquals(50f * 0.9f, isolated.approval, 0.001f)
        // Officer loses 5% ships/morale/training
        assertEquals((1_000 * 0.95).toInt(), officer.ships)
        assertEquals((100 * 0.95).toInt().toShort(), officer.morale)
        assertEquals((80 * 0.95).toInt().toShort(), officer.training)
        // approval drops to 45f (>= 30), does NOT defect
        assertEquals(10L, isolated.factionId, "approval >= 30 — must stay with original faction")
    }

    /**
     * Test 3: Isolated planet with approval starting at 28f — after 10% decay approval=25.2f, below 30.
     * Must defect to neutral (factionId = 0) and clear officerSet/term/frontState.
     */
    @Test
    fun `isolated planet with approval below 30 defects to neutral`() {
        val world = makeWorld()
        val faction = makeFaction(id = 10L, sessionId = 1L, capitalPlanetId = 100L)
        val capital = makePlanet(id = 100L, sessionId = 1L, factionId = 10L, mapPlanetId = 1)
        val isolated = makePlanet(
            id = 200L, sessionId = 1L, factionId = 10L, mapPlanetId = 99,
            approval = 28f,
        ).apply {
            officerSet = 42
            term = 7
            frontState = 3
            conflict = mutableMapOf("foo" to "bar")
        }

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(capital, isolated))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(mapService.getAdjacentCities("test", 1)).thenReturn(emptyList())
        `when`(mapService.getAdjacentCities("test", 99)).thenReturn(emptyList())

        service.updatePlanetSupplyState(world)

        // Planet defected to neutral
        assertEquals(0L, isolated.factionId, "planet must defect to neutral faction 0")
        assertEquals(0, isolated.officerSet)
        assertEquals(0.toShort(), isolated.term)
        assertEquals(0.toShort(), isolated.frontState)
        assertTrue(isolated.conflict.isEmpty(), "conflict map must be cleared on defection")
    }

    /**
     * Test 4: Neutral planets (factionId = 0) are always forced to supplyState = 1, even if initially 0.
     */
    @Test
    fun `neutral planets are always supplied regardless of initial supplyState`() {
        val world = makeWorld()
        // No factions — only neutral planets
        val neutralA = makePlanet(
            id = 500L, sessionId = 1L, factionId = 0L, mapPlanetId = 50,
            supplyState = 0,
        )
        val neutralB = makePlanet(
            id = 501L, sessionId = 1L, factionId = 0L, mapPlanetId = 51,
            supplyState = 0,
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(neutralA, neutralB))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.updatePlanetSupplyState(world)

        assertEquals(1, neutralA.supplyState.toInt(), "neutral planet must always be supplied")
        assertEquals(1, neutralB.supplyState.toInt(), "neutral planet must always be supplied")
    }

    /**
     * Test 5: Empty world — no planets, no factions — runs cleanly without error.
     */
    @Test
    fun `empty world runs without error`() {
        val world = makeWorld()

        `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        // Should not throw
        service.updatePlanetSupplyState(world)
    }
}
