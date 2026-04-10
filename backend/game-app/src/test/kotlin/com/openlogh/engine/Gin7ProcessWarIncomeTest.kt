package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.PlanetRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Plan 23-03: Gin7EconomyService.processWarIncome
 *
 * Ports upstream commit a7a19cc3 `com.opensam.engine.EconomyService.processWarIncome`
 * into LOGH's Gin7EconomyService.
 *
 * Legacy reference (a7a19cc3 opensam EconomyService.kt:processWarIncome):
 * ```
 *   private fun processWarIncome(nations: List<Nation>, cities: List<City>) {
 *     val nationMap = nations.associateBy { it.id }
 *     for (city in cities) {
 *       if (city.dead > 0) {
 *         val nation = nationMap[city.nationId] ?: continue
 *         nation.gold += (city.dead / 10)
 *         val popGain = (city.dead * 0.2).toInt()
 *           .coerceAtMost((city.popMax - city.pop).coerceAtLeast(0))
 *         city.pop += popGain
 *         city.dead = 0
 *       }
 *     }
 *   }
 * ```
 *
 * Note on plan vs. upstream: Plan 23-03 described filtering "factions with warState > 0"
 * but the actual upstream implementation has no such filter — it iterates EVERY planet
 * with `dead > 0`. War income = casualty salvage: every dead soldier returns 0.1 funds
 * to the faction treasury and 0.2 population to the planet. The gate is `dead > 0`,
 * not `faction.warState > 0`. This test suite follows the upstream body faithfully.
 *
 * Domain mapping (삼국지 → LOGH):
 *   - Nation.gold → Faction.funds
 *   - City.dead → Planet.dead (casualty count)
 *   - City.pop → Planet.population
 *   - City.popMax → Planet.populationMax
 */
class Gin7ProcessWarIncomeTest {

    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var service: Gin7EconomyService

    @BeforeEach
    fun setUp() {
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        service = Gin7EconomyService(factionRepository, planetRepository)
    }

    private fun makeWorld(): SessionState {
        val world = SessionState()
        world.id = 1.toShort()
        world.currentMonth = 3.toShort() // any month — war income runs every month
        world.currentYear = 800.toShort()
        return world
    }

    private fun makeFaction(id: Long, sessionId: Long, funds: Int = 0): Faction {
        val f = Faction()
        f.id = id
        f.sessionId = sessionId
        f.funds = funds
        f.factionType = "empire"
        return f
    }

    private fun makePlanet(
        id: Long,
        sessionId: Long,
        factionId: Long,
        dead: Int = 0,
        population: Int = 5000,
        populationMax: Int = 10000,
    ): Planet {
        val p = Planet()
        p.id = id
        p.sessionId = sessionId
        p.factionId = factionId
        p.dead = dead
        p.population = population
        p.populationMax = populationMax
        return p
    }

    /**
     * Test 1: Planet with dead=0 produces NO war income.
     *
     * Legacy gate: `if (city.dead > 0)` — planets with no casualties are skipped entirely.
     * Faction funds must remain unchanged and population must not grow from this call.
     */
    @Test
    fun `planet with no casualties yields no war income`() {
        val world = makeWorld()
        val faction = makeFaction(id = 10L, sessionId = 1L, funds = 500)
        val planet = makePlanet(
            id = 100L, sessionId = 1L, factionId = 10L,
            dead = 0, population = 5000, populationMax = 10000,
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

        service.processWarIncome(world)

        assertEquals(500, faction.funds, "funds must be unchanged when dead=0")
        assertEquals(5000, planet.population, "population must be unchanged when dead=0")
        assertEquals(0, planet.dead, "dead must remain 0")
    }

    /**
     * Test 2: Planet with dead=1000 → faction gains 100 funds, planet gains 200 pop, dead cleared.
     *
     * Legacy formula:
     *   funds += dead / 10   → 1000/10 = 100
     *   popGain = dead * 0.2 → 1000*0.2 = 200 (not capped when headroom > 200)
     *   dead = 0
     */
    @Test
    fun `planet with casualties credits funds and restores population`() {
        val world = makeWorld()
        val faction = makeFaction(id = 10L, sessionId = 1L, funds = 0)
        val planet = makePlanet(
            id = 100L, sessionId = 1L, factionId = 10L,
            dead = 1000, population = 5000, populationMax = 10000,
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

        service.processWarIncome(world)

        assertEquals(100, faction.funds, "funds must gain dead/10 = 100")
        assertEquals(5200, planet.population, "population must gain dead*0.2 = 200")
        assertEquals(0, planet.dead, "dead must be cleared after payout")
    }

    /**
     * Test 3: Multiple planets across multiple factions each credit independently.
     *
     * Upstream iterates every city regardless of faction war state; each hit pays its owner.
     * - Faction 10: 2 planets (dead=500, dead=2000) → funds += 50 + 200 = 250
     * - Faction 20: 1 planet (dead=300) → funds += 30
     */
    @Test
    fun `multiple warring factions each receive independent war income`() {
        val world = makeWorld()
        val factionA = makeFaction(id = 10L, sessionId = 1L, funds = 1000)
        val factionB = makeFaction(id = 20L, sessionId = 1L, funds = 2000)

        val planetA1 = makePlanet(
            id = 101L, sessionId = 1L, factionId = 10L,
            dead = 500, population = 4000, populationMax = 10000,
        )
        val planetA2 = makePlanet(
            id = 102L, sessionId = 1L, factionId = 10L,
            dead = 2000, population = 6000, populationMax = 10000,
        )
        val planetB1 = makePlanet(
            id = 201L, sessionId = 1L, factionId = 20L,
            dead = 300, population = 3000, populationMax = 10000,
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(factionA, factionB))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planetA1, planetA2, planetB1))

        service.processWarIncome(world)

        // Faction A: 1000 + 500/10 + 2000/10 = 1000 + 50 + 200 = 1250
        assertEquals(1250, factionA.funds, "faction A funds must include both planets' salvage")
        // Faction B: 2000 + 300/10 = 2030
        assertEquals(2030, factionB.funds, "faction B funds must include its planet's salvage only")

        // Population gains: A1 +100, A2 +400, B1 +60 (all within headroom)
        assertEquals(4100, planetA1.population)
        assertEquals(6400, planetA2.population)
        assertEquals(3060, planetB1.population)

        // All dead cleared
        assertEquals(0, planetA1.dead)
        assertEquals(0, planetA2.dead)
        assertEquals(0, planetB1.dead)
    }

    /**
     * Test 4: Empty world (no factions, no planets) runs cleanly without error.
     */
    @Test
    fun `empty world runs cleanly with no side effects`() {
        val world = makeWorld()

        `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())

        // Must not throw
        service.processWarIncome(world)

        // Nothing to assert on — just ensure no NPE / crash with empty collections
        assertTrue(true)
    }

    /**
     * Test 5 (bonus coverage — documents the population cap edge case from upstream):
     * Population gain is capped at (populationMax - population), and never negative.
     * Planet near population max: population=9900, populationMax=10000, dead=1000.
     *   Uncapped popGain = 200, headroom = 100 → actual popGain = 100.
     *   Funds still credited fully: 1000/10 = 100.
     */
    @Test
    fun `population gain is capped at populationMax headroom`() {
        val world = makeWorld()
        val faction = makeFaction(id = 10L, sessionId = 1L, funds = 0)
        val planet = makePlanet(
            id = 100L, sessionId = 1L, factionId = 10L,
            dead = 1000, population = 9900, populationMax = 10000,
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

        service.processWarIncome(world)

        assertEquals(100, faction.funds, "funds credit unaffected by pop cap")
        assertEquals(10000, planet.population, "population capped at populationMax")
        assertEquals(0, planet.dead)
    }
}
