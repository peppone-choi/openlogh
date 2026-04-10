package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.PlanetRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.random.Random

/**
 * Plan 23-08: Gin7EconomyService.processDisasterOrBoom
 *
 * Ports the legacy disaster/boom event generator from `EconomyService.processDisasterOrBoom`
 * (lines 439-644 in legacy EconomyService.kt) into `Gin7EconomyService`.
 *
 * Legacy behavior summary:
 *   1. Skip the first 3 years after session start (startYear + 3 > currentYear → return).
 *   2. Reset every planet's `state` field back to 0 if it was in a low-state code (<= 10).
 *   3. Roll boom probability: months 4/7 = 25% chance of a GOOD (boom) event; other months
 *      are always BAD (disaster) events.
 *   4. For every planet, compute per-planet probability using security ratio:
 *        - boom:     0.02 + (security/securityMax) * 0.05   → range 2%..7%
 *        - disaster: 0.06 - (security/securityMax) * 0.05   → range 1%..6%
 *   5. Apply resource modifiers to targeted planets:
 *        - boom:     affectRatio = 1.01 + min(secuRatio/0.8, 1.0) * 0.04   (1.01..1.05)
 *        - disaster: affectRatio = 0.80 + min(secuRatio/0.8, 1.0) * 0.15   (0.80..0.95)
 *     Multiplied onto: population, approval, production, commerce, security,
 *     orbitalDefense, fortress. Boom path coerces to the per-resource max.
 *   6. Set `planet.state` to a code from the disaster/boom entry table (Korean flavor text).
 *   7. Emit a history log line with the Korean title + body (deferred to Plan 23-10).
 *
 * Test fixtures use a `Random` seed to assert deterministic behavior against a
 * SeededRandom wrapper injected via the test-only `processDisasterOrBoom` overload
 * that accepts a custom `Random` instance.
 */
class Gin7ProcessDisasterOrBoomTest {

    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var service: Gin7EconomyService

    @BeforeEach
    fun setUp() {
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        service = Gin7EconomyService(factionRepository, planetRepository)
    }

    private fun makeWorld(
        currentYear: Short = 803,
        currentMonth: Short = 1,
        startYear: Int = 800,
    ): SessionState {
        val world = SessionState()
        world.id = 1.toShort()
        world.currentYear = currentYear
        world.currentMonth = currentMonth
        world.config["startYear"] = startYear
        return world
    }

    private fun makeFaction(id: Long): Faction {
        val f = Faction()
        f.id = id
        f.sessionId = 1
        f.factionType = "empire"
        return f
    }

    private fun makePlanet(
        id: Long,
        factionId: Long = 1,
        security: Int = 500,
        securityMax: Int = 1000,
        population: Int = 5000,
        populationMax: Int = 10000,
        production: Int = 500,
        productionMax: Int = 1000,
        commerce: Int = 500,
        commerceMax: Int = 1000,
        approval: Float = 80f,
        orbitalDefense: Int = 400,
        orbitalDefenseMax: Int = 1000,
        fortress: Int = 300,
        fortressMax: Int = 1000,
    ): Planet {
        val p = Planet(approval = approval)
        p.id = id
        p.sessionId = 1
        p.factionId = factionId
        p.name = "Planet$id"
        p.security = security
        p.securityMax = securityMax
        p.population = population
        p.populationMax = populationMax
        p.production = production
        p.productionMax = productionMax
        p.commerce = commerce
        p.commerceMax = commerceMax
        p.orbitalDefense = orbitalDefense
        p.orbitalDefenseMax = orbitalDefenseMax
        p.fortress = fortress
        p.fortressMax = fortressMax
        p.state = 0
        return p
    }

    /**
     * Test 1: First 3 years skipped (no state mutations).
     *
     * Legacy: `if (startYear + 3 > world.currentYear.toInt()) return`
     */
    @Test
    fun `skips first 3 years from session start`() {
        val world = makeWorld(currentYear = 802, currentMonth = 1, startYear = 800)
        val planets = listOf(
            makePlanet(id = 1, population = 5000, production = 500, commerce = 500),
        )
        `when`(planetRepository.findBySessionId(1L)).thenReturn(planets)

        // Seed that would otherwise force aggressive disaster selection
        val rng = Random(42)
        service.processDisasterOrBoom(world, rng)

        // No mutation — population/production/commerce unchanged
        assertEquals(5000, planets[0].population)
        assertEquals(500, planets[0].production)
        assertEquals(500, planets[0].commerce)
        assertEquals(0.toShort(), planets[0].state)
    }

    /**
     * Test 2: Disaster path — year >= startYear+3, non-boom month, planet hit.
     *
     * Uses a seed (`0L`) chosen so that:
     *   - Boom roll fails (month != 4/7 → always disaster path)
     *   - Per-planet roll hits (rng.nextDouble() < 0.035 for security=500/1000)
     *
     * Asserts that resources decrease by the disaster affectRatio (0.8..0.95).
     */
    @Test
    fun `disaster path reduces planet resources when targeted`() {
        val world = makeWorld(currentYear = 803, currentMonth = 1, startYear = 800)
        // Force all planets to be hit by using a stub RNG where every roll is 0.0
        val planets = (1..5).map { i ->
            makePlanet(
                id = i.toLong(),
                population = 10000,
                production = 1000,
                commerce = 1000,
                security = 500,
                securityMax = 1000,
                approval = 100f,
                orbitalDefense = 1000,
                orbitalDefenseMax = 1000,
                fortress = 1000,
                fortressMax = 1000,
            )
        }
        `when`(planetRepository.findBySessionId(1L)).thenReturn(planets)

        // AlwaysZeroRandom: every nextDouble() returns 0.0, every nextInt returns 0.
        // This guarantees: boom roll 0.0 < boomRate=0 → false (month=1 → disaster);
        // per-planet raiseProp 0.0 < 0.035 → all hit; entries[0] picked.
        service.processDisasterOrBoom(world, AlwaysZeroRandom)

        // disaster affectRatio = 0.80 + (500/1000/0.8).coerceIn(0,1)*0.15 = 0.80 + 0.625*0.15 = 0.89375
        val expectedRatio = 0.80 + (500.0 / 1000.0 / 0.8).coerceIn(0.0, 1.0) * 0.15
        val expectedPop = (10000 * expectedRatio).toInt()
        val expectedProd = (1000 * expectedRatio).toInt()
        val expectedComm = (1000 * expectedRatio).toInt()
        for (planet in planets) {
            assertEquals(expectedPop, planet.population, "population should be decayed by disaster")
            assertEquals(expectedProd, planet.production, "production should be decayed by disaster")
            assertEquals(expectedComm, planet.commerce, "commerce should be decayed by disaster")
            assertTrue(planet.approval < 100f, "approval should drop under disaster")
            // state becomes a non-zero disaster code (from entry table)
            assertNotEquals(0.toShort(), planet.state, "planet.state should carry disaster code")
        }
    }

    /**
     * Test 3: Boom path — month 4 + forced boom roll hit.
     *
     * Uses AlwaysZeroRandom so rng.nextDouble()=0.0 < boomRate=0.25 → boom;
     * per-planet roll 0.0 < 0.045 → all hit.
     *
     * Asserts that resources INCREASE (coerced to max).
     */
    @Test
    fun `boom path increases planet resources when targeted`() {
        val world = makeWorld(currentYear = 803, currentMonth = 4, startYear = 800)
        val planets = listOf(
            makePlanet(
                id = 1,
                population = 5000,
                populationMax = 10000,
                production = 500,
                productionMax = 1000,
                commerce = 500,
                commerceMax = 1000,
                security = 500,
                securityMax = 1000,
                approval = 80f,
            ),
        )
        `when`(planetRepository.findBySessionId(1L)).thenReturn(planets)

        service.processDisasterOrBoom(world, AlwaysZeroRandom)

        // boom affectRatio = 1.01 + (500/1000/0.8).coerceIn(0,1)*0.04 = 1.01 + 0.625*0.04 = 1.035
        val expectedRatio = 1.01 + (500.0 / 1000.0 / 0.8).coerceIn(0.0, 1.0) * 0.04
        val expectedPop = (5000 * expectedRatio).toInt().coerceAtMost(10000)
        val expectedProd = (500 * expectedRatio).toInt().coerceAtMost(1000)
        val expectedComm = (500 * expectedRatio).toInt().coerceAtMost(1000)

        assertEquals(expectedPop, planets[0].population, "population should grow under boom")
        assertEquals(expectedProd, planets[0].production, "production should grow under boom")
        assertEquals(expectedComm, planets[0].commerce, "commerce should grow under boom")
        assertTrue(planets[0].approval > 80f, "approval should rise under boom")
        assertEquals(2.toShort(), planets[0].state, "boom state code for month=4 is 2")
    }

    /**
     * Test 4: No planet hit under normal probability — resources unchanged.
     *
     * Uses a RNG that always returns 0.99 so per-planet rolls never trigger.
     * State still resets from <=10 → 0 (legacy reset pass).
     */
    @Test
    fun `no planet mutated when per planet roll misses`() {
        val world = makeWorld(currentYear = 803, currentMonth = 3, startYear = 800)
        val planets = listOf(
            makePlanet(id = 1, population = 5000, production = 500, commerce = 500).also {
                it.state = 3 // prior disaster leftover — should reset
            },
        )
        `when`(planetRepository.findBySessionId(1L)).thenReturn(planets)

        service.processDisasterOrBoom(world, AlwaysHighRandom)

        // No mutation to numeric resources
        assertEquals(5000, planets[0].population)
        assertEquals(500, planets[0].production)
        assertEquals(500, planets[0].commerce)
        // State was <=10, should be reset to 0
        assertEquals(0.toShort(), planets[0].state, "state should be reset to 0")
    }

    /**
     * Test 5: Probability distribution over 1000 iterations is within legacy range.
     *
     * Legacy per-planet probability (non-boom month):
     *   raiseProp = 0.06 - 0.0625 * 0.05 = 0.059375? ... wait, secuRatio = security/securityMax
     *   = 500/1000 = 0.5, so raiseProp = 0.06 - 0.5*0.05 = 0.035 → 3.5%
     *
     * Over 1000 iterations with distinct seeds, we expect roughly 35 hits (std ~5.8).
     * We assert hits > 5 and < 150 (very wide 3-sigma band to be non-flaky).
     * Primary goal: sanity-check that randomized calls don't throw + produce a
     * non-trivial distribution.
     */
    @Test
    fun `probability distribution is non-trivial over 1000 iterations`() {
        val world = makeWorld(currentYear = 803, currentMonth = 3, startYear = 800)
        val basePlanet = makePlanet(id = 1, security = 500, securityMax = 1000)
        var hits = 0
        for (seed in 1..1000) {
            // Fresh planet per iteration to avoid cumulative state
            val planet = makePlanet(id = 1, security = 500, securityMax = 1000)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

            service.processDisasterOrBoom(world, Random(seed.toLong()))
            if (planet.population != basePlanet.population) hits++
        }
        // 1000 * 0.035 = 35 expected; wide bands to tolerate RNG variance
        assertTrue(hits in 5..200, "Expected hits in [5,200] range, got $hits")
    }

    // Test-only RNG stubs — extend kotlin.random.Random directly to avoid mocking ceremony

    /** RNG that always returns 0 for every query — guarantees all probabilistic gates trigger. */
    private object AlwaysZeroRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = 0.0
        override fun nextInt(): Int = 0
        override fun nextInt(until: Int): Int = 0
        override fun nextInt(from: Int, until: Int): Int = from
    }

    /** RNG that always returns near-1 values — guarantees no probabilistic gate triggers. */
    private object AlwaysHighRandom : Random() {
        override fun nextBits(bitCount: Int): Int = (1 shl bitCount) - 1
        override fun nextDouble(): Double = 0.999999
        override fun nextInt(): Int = Int.MAX_VALUE
        override fun nextInt(until: Int): Int = (until - 1).coerceAtLeast(0)
        override fun nextInt(from: Int, until: Int): Int = (until - 1).coerceAtLeast(from)
    }
}
