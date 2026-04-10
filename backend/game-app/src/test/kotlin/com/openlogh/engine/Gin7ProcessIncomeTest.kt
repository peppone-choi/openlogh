package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.PlanetRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Phase 23 Plan 23-01 — Per-resource processIncome contract.
 *
 * Legacy parity: upstream commit a7a19cc3
 *   com.opensam.engine.EconomyService.processIncome(world, nations, cities, generals, resourceType)
 *
 * This suite locks the per-resource isolation contract for
 * Gin7EconomyService.processIncome(world, resource):
 *
 *   - resource="gold"  → mutates faction.funds    ONLY (supplies untouched)
 *   - resource="rice"  → mutates faction.supplies ONLY (funds untouched)
 *   - resource=<other> → IllegalArgumentException
 *   - empty world      → no-op (no repository crash)
 *   - supplyState == 0 → isolated planet excluded from calculation
 *
 * Scope: narrow per-resource body only. Salary outlay, faction-rank updates,
 * and semi-annual decay are owned by sibling plans 23-02..23-10.
 */
class Gin7ProcessIncomeTest {

    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var service: Gin7EconomyService

    @BeforeEach
    fun setUp() {
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        service = Gin7EconomyService(factionRepository, planetRepository)
    }

    private fun makeWorld(sessionId: Int = 1, month: Int = 1): SessionState {
        val world = SessionState()
        world.id = sessionId.toShort()
        world.currentMonth = month.toShort()
        world.currentYear = 800.toShort()
        return world
    }

    private fun makeFaction(
        id: Long,
        sessionId: Long,
        taxRate: Int = 30,
        funds: Int = 0,
        supplies: Int = 0,
    ): Faction {
        val f = Faction()
        f.id = id
        f.sessionId = sessionId
        f.taxRate = taxRate.toShort()
        f.funds = funds
        f.supplies = supplies
        f.factionType = "empire"
        return f
    }

    private fun makePlanet(
        id: Long,
        sessionId: Long,
        factionId: Long,
        commerce: Int = 10000,
        production: Int = 3000,
        supplyState: Short = 1,
    ): Planet {
        val p = Planet()
        p.id = id
        p.sessionId = sessionId
        p.factionId = factionId
        p.commerce = commerce
        p.commerceMax = commerce * 2
        p.production = production
        p.productionMax = production * 2
        p.population = 5000
        p.populationMax = 10000
        p.approval = 50f
        p.supplyState = supplyState
        return p
    }

    /**
     * Test 1: processIncome(world, "gold") mutates funds ONLY.
     *
     * faction.taxRate = 30, planet.commerce = 10000
     *   → expected funds += 10000 * 30 / 100 = 3000
     *   → supplies MUST remain untouched (starts at 777)
     */
    @Test
    fun `processIncome gold mutates funds only`() {
        val world = makeWorld(sessionId = 1)
        val faction = makeFaction(id = 10L, sessionId = 1L, taxRate = 30, funds = 0, supplies = 777)
        val planet = makePlanet(id = 100L, sessionId = 1L, factionId = 10L, commerce = 10000)

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

        service.processIncome(world, "gold")

        assertEquals(3000, faction.funds, "gold branch must add commerce*taxRate/100 to funds")
        assertEquals(777, faction.supplies, "gold branch must NOT touch supplies")
    }

    /**
     * Test 2: processIncome(world, "rice") mutates supplies ONLY.
     *
     * planet.production = 3000 (supplied planet)
     *   → expected supplies += 3000
     *   → funds MUST remain untouched (starts at 555)
     */
    @Test
    fun `processIncome rice mutates supplies only`() {
        val world = makeWorld(sessionId = 1)
        val faction = makeFaction(id = 10L, sessionId = 1L, taxRate = 30, funds = 555, supplies = 0)
        val planet = makePlanet(id = 100L, sessionId = 1L, factionId = 10L, production = 3000)

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

        service.processIncome(world, "rice")

        assertEquals(3000, faction.supplies, "rice branch must add production to supplies")
        assertEquals(555, faction.funds, "rice branch must NOT touch funds")
    }

    /**
     * Test 3: Invalid resource literal throws IllegalArgumentException.
     *
     * Upstream a7a19cc3 contract: only "gold" and "rice" are legal wire values.
     * Anything else must fail fast (structural guard, not a silent no-op).
     */
    @Test
    fun `processIncome rejects invalid resource`() {
        val world = makeWorld(sessionId = 1)
        `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.processIncome(world, "funds")
        }
        assertTrue(
            ex.message!!.contains("funds"),
            "Exception message should echo the bad literal; was: ${ex.message}",
        )
    }

    /**
     * Test 4: Empty world (no factions, no planets) runs cleanly.
     *
     * No exceptions, no repository crashes — supports scenario bootstrap paths
     * where scheduled events fire before factions are seeded.
     */
    @Test
    fun `processIncome empty world runs cleanly`() {
        val world = makeWorld(sessionId = 1)
        `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())

        // Must not throw
        service.processIncome(world, "gold")
        service.processIncome(world, "rice")
    }

    /**
     * Test 5: Isolated planets (supplyState == 0) are excluded.
     *
     * Only the supplied planet (supplyState=1) contributes. The isolated planet's
     * commerce/production must NOT flow to the faction resource.
     *
     * Setup:
     *   - supplied planet:  commerce 10000, production 2000
     *   - isolated planet:  commerce 99999, production 99999 (must be ignored)
     *
     * gold expectation: 10000 * 30 / 100 = 3000 (not 32999)
     * rice expectation: 2000              = 2000 (not 101999)
     */
    @Test
    fun `processIncome excludes isolated planets`() {
        val world = makeWorld(sessionId = 1)
        val faction = makeFaction(id = 10L, sessionId = 1L, taxRate = 30, funds = 0, supplies = 0)
        val supplied = makePlanet(
            id = 100L, sessionId = 1L, factionId = 10L,
            commerce = 10000, production = 2000, supplyState = 1,
        )
        val isolated = makePlanet(
            id = 101L, sessionId = 1L, factionId = 10L,
            commerce = 99999, production = 99999, supplyState = 0,
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(supplied, isolated))

        service.processIncome(world, "gold")
        assertEquals(3000, faction.funds, "isolated planet commerce must be excluded")

        // Reset for rice branch
        faction.funds = 0
        service.processIncome(world, "rice")
        assertEquals(2000, faction.supplies, "isolated planet production must be excluded")
    }
}
