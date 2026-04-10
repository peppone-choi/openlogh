package com.openlogh.engine

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
 * Plan 23-09: Gin7EconomyService.randomizePlanetTradeRate
 *
 * Ports upstream `com.opensam.engine.EconomyService.randomizeCityTradeRate` (last seen
 * intact in the Phase A3 wave — LOGH `EconomyService.kt:648-672`) into
 * `Gin7EconomyService.randomizePlanetTradeRate`.
 *
 * ### Legacy reference (EconomyService.randomizeCityTradeRate, 25 lines)
 *
 * ```
 *   fun randomizeCityTradeRate(world) {
 *     val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
 *     val rng = DeterministicRng.create(hiddenSeed, "tradeRate", world.currentYear, world.currentMonth)
 *     val probByLevel = mapOf(4 to 0.2, 5 to 0.4, 6 to 0.6, 7 to 0.8, 8 to 1.0)
 *     for (city in cities) {
 *       val prob = probByLevel[city.level.toInt()] ?: 0.0
 *       if (prob > 0 && rng.nextDouble() < prob) {
 *         city.tradeRoute = rng.nextInt(95, 106)  // 95..105
 *       } else {
 *         city.tradeRoute = 100
 *       }
 *     }
 *     saveCities(ports, cities)
 *   }
 * ```
 *
 * ### LOGH adjustments vs. upstream body
 *
 * 1. **Domain mapping** — `city.tradeRoute` → `planet.tradeRoute` (same field name, renamed entity).
 * 2. **Isolated planet skip** — mirrors sibling Gin7 methods (processIncome, salary, etc.):
 *    planets with `supplyState != 1` are not touched. The legacy body processes every city
 *    unconditionally; this port excludes isolated planets per Phase 23 convention
 *    (see Gin7EconomyService KDoc line 26).
 * 3. **Deterministic seed** — preserved verbatim: `hiddenSeed | "tradeRate" | year | month`.
 * 4. **Value bounds** — preserved: `nextInt(95, 106)` yields 95..105 inclusive; reset value 100.
 *
 * Per Phase 23 CONTEXT.md decision table, `city.tradeRoute → planet.tradeRoute`.
 */
class Gin7RandomizePlanetTradeRateTest {

    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var service: Gin7EconomyService

    @BeforeEach
    fun setUp() {
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        service = Gin7EconomyService(factionRepository, planetRepository)
    }

    private fun makeWorld(seed: String = "seed-23-09"): SessionState {
        val world = SessionState()
        world.id = 1.toShort()
        world.currentYear = 800.toShort()
        world.currentMonth = 3.toShort()
        world.config["hiddenSeed"] = seed
        return world
    }

    private fun makePlanet(
        id: Long,
        level: Short,
        supplyState: Short = 1,
        tradeRoute: Int = 100,
    ): Planet {
        val p = Planet()
        p.id = id
        p.sessionId = 1L
        p.factionId = 10L
        p.level = level
        p.supplyState = supplyState
        p.tradeRoute = tradeRoute
        return p
    }

    /**
     * Test 1: Seeded RNG yields trade rates within legacy bounds `95..105`.
     *
     * Every level-8 planet is guaranteed to be randomised (prob 1.0), so every
     * output must fall in the legacy `nextInt(95, 106)` range.
     */
    @Test
    fun `level 8 planets receive trade rate in 95 to 105 range`() {
        val world = makeWorld()
        val planets = (1..10).map { i ->
            makePlanet(id = 100L + i, level = 8.toShort(), tradeRoute = 100)
        }
        `when`(planetRepository.findBySessionId(1L)).thenReturn(planets)

        service.randomizePlanetTradeRate(world)

        for (p in planets) {
            assertTrue(
                p.tradeRoute in 95..105,
                "planet ${p.id} tradeRoute=${p.tradeRoute} out of legacy bounds 95..105",
            )
        }
    }

    /**
     * Test 2: Only supplied planets (supplyState=1) receive new trade rate; isolated
     * planets (supplyState=0) are untouched.
     *
     * Mirrors the sibling Gin7 convention documented in the class KDoc (line 26):
     * "고립 행성(supplyState=0)은 세금 징수에서 제외되며 자원 성장도 없다." Plan 23-09
     * extends this invariant to trade-rate randomisation (Rule 2 additive port).
     */
    @Test
    fun `isolated planets are not touched`() {
        val world = makeWorld()
        val isolatedInitial = 77
        val supplied = makePlanet(id = 200L, level = 8.toShort(), supplyState = 1, tradeRoute = 100)
        val isolated = makePlanet(id = 201L, level = 8.toShort(), supplyState = 0, tradeRoute = isolatedInitial)
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(supplied, isolated))

        service.randomizePlanetTradeRate(world)

        assertEquals(
            isolatedInitial, isolated.tradeRoute,
            "isolated planet (supplyState=0) must remain untouched",
        )
        assertTrue(
            supplied.tradeRoute in 95..105,
            "supplied level-8 planet must be randomised into 95..105 (got ${supplied.tradeRoute})",
        )
    }

    /**
     * Test 3: Low-level planets (level < 4) are reset to the default trade route = 100.
     *
     * Legacy behaviour: `probByLevel[level] ?: 0.0` → `prob == 0.0` for levels 1..3 →
     * else-branch → `tradeRoute = 100`. A planet that had drifted to a non-default
     * value is pulled back to 100.
     */
    @Test
    fun `low level planets reset to default trade route 100`() {
        val world = makeWorld()
        val p1 = makePlanet(id = 300L, level = 1.toShort(), tradeRoute = 88)
        val p2 = makePlanet(id = 301L, level = 3.toShort(), tradeRoute = 120)
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(p1, p2))

        service.randomizePlanetTradeRate(world)

        assertEquals(100, p1.tradeRoute, "level 1 planet must be reset to 100")
        assertEquals(100, p2.tradeRoute, "level 3 planet must be reset to 100")
    }

    /**
     * Test 4: Empty world (no planets) runs cleanly without NPE.
     *
     * Mirrors sibling Gin7 tests defending against NPE on empty Iterables.
     */
    @Test
    fun `empty world runs cleanly`() {
        val world = makeWorld()
        `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())

        // Must not throw
        service.randomizePlanetTradeRate(world)
        assertTrue(true)
    }

    /**
     * Test 5: Two invocations with identical seed/year/month produce identical outputs.
     *
     * Determinism is the core replay guarantee of `DeterministicRng` — identical
     * inputs must yield identical outputs across calls so the TickEngine can replay
     * scenarios consistently.
     */
    @Test
    fun `two calls with same seed produce identical trade rates`() {
        val world1 = makeWorld()
        val planets1 = (1..5).map { i ->
            makePlanet(id = 400L + i, level = 8.toShort(), tradeRoute = 100)
        }
        `when`(planetRepository.findBySessionId(1L)).thenReturn(planets1)
        service.randomizePlanetTradeRate(world1)
        val firstRates = planets1.map { it.tradeRoute }

        // Second invocation — fresh instance, fresh planets, same world state.
        val factionRepo2 = mock(FactionRepository::class.java)
        val planetRepo2 = mock(PlanetRepository::class.java)
        val service2 = Gin7EconomyService(factionRepo2, planetRepo2)
        val world2 = makeWorld()
        val planets2 = (1..5).map { i ->
            makePlanet(id = 400L + i, level = 8.toShort(), tradeRoute = 100)
        }
        `when`(planetRepo2.findBySessionId(1L)).thenReturn(planets2)
        service2.randomizePlanetTradeRate(world2)
        val secondRates = planets2.map { it.tradeRoute }

        assertEquals(
            firstRates, secondRates,
            "deterministic RNG must produce identical trade rates across invocations",
        )
        // Sanity probe: at least one rate must diverge from default 100 — otherwise the
        // test would pass trivially if the method silently no-op'd.
        assertTrue(
            firstRates.any { it != 100 },
            "at least one level-8 planet must be randomised (not all 100)",
        )
    }
}
