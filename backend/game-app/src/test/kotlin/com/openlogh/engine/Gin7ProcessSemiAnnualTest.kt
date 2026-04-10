package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Phase 23 Plan 23-02 — Per-resource processSemiAnnual contract.
 *
 * Legacy parity: upstream commit a7a19cc3
 *   com.opensam.engine.EconomyService.processSemiAnnual(world, nations, cities, generals, resourceType)
 *
 * Ported to LOGH domain:
 *   - Nation.gold / Nation.rice         → Faction.funds / Faction.supplies
 *   - General.gold / General.rice       → Officer.funds / Officer.supplies
 *
 * Legacy per-resource decay brackets (ProcessSemiAnnual.php:89-96):
 *   Officer (per-person upkeep):
 *     > 10_000  → * 0.97  (3% decay)
 *     > 1_000   → * 0.99  (1% decay)
 *     ≤ 1_000   → unchanged (below-threshold protection)
 *
 *   Faction treasury (progressive bracket — discourages hoarding):
 *     > 100_000 → * 0.95  (5% decay)
 *     > 10_000  → * 0.97  (3% decay)
 *     > 1_000   → * 0.99  (1% decay)
 *     ≤ 1_000   → unchanged
 *
 * Per-resource isolation is the critical contract: calling with "gold" must
 * leave every supplies field untouched, and vice versa. This is what upstream
 * a7a19cc3 fixed — the pre-fix version decayed BOTH resources per call and was
 * triggered twice per year, resulting in 4x decay.
 *
 * Scope: processSemiAnnual only. Income, war income, salary outlay, faction
 * rank, planet supply state, yearly statistics, disasters/booms, and trade-rate
 * randomization are owned by sibling plans 23-01, 23-03..23-10.
 */
class Gin7ProcessSemiAnnualTest {

    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var service: Gin7EconomyService

    @BeforeEach
    fun setUp() {
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        service = Gin7EconomyService(factionRepository, planetRepository, officerRepository)
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
        sessionId: Long = 1L,
        funds: Int = 0,
        supplies: Int = 0,
    ): Faction {
        val f = Faction()
        f.id = id
        f.sessionId = sessionId
        f.funds = funds
        f.supplies = supplies
        f.factionType = "empire"
        return f
    }

    private fun makeOfficer(
        id: Long,
        sessionId: Long = 1L,
        factionId: Long = 10L,
        funds: Int = 0,
        supplies: Int = 0,
    ): Officer {
        val o = Officer()
        o.id = id
        o.sessionId = sessionId
        o.factionId = factionId
        o.funds = funds
        o.supplies = supplies
        return o
    }

    /**
     * Test 1: processSemiAnnual(world, "gold") decays faction.funds AND officer.funds
     * while leaving every supplies field untouched.
     *
     * Per-resource isolation is the core upstream a7a19cc3 contract.
     */
    @Test
    fun `processSemiAnnual gold decays funds only leaving supplies untouched`() {
        val world = makeWorld(month = 1)
        // Faction in the 10_000 < funds ≤ 100_000 bracket → * 0.97
        val faction = makeFaction(id = 10L, funds = 50_000, supplies = 50_000)
        // Officer in the > 10_000 bracket → * 0.97
        val officer = makeOfficer(id = 100L, factionId = 10L, funds = 20_000, supplies = 20_000)

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(officer))

        service.processSemiAnnual(world, "gold")

        assertEquals((50_000 * 0.97).toInt(), faction.funds, "faction.funds should decay by 3% (50000 bracket)")
        assertEquals(50_000, faction.supplies, "faction.supplies MUST NOT decay when resource='gold'")
        assertEquals((20_000 * 0.97).toInt(), officer.funds, "officer.funds should decay by 3%")
        assertEquals(20_000, officer.supplies, "officer.supplies MUST NOT decay when resource='gold'")
    }

    /**
     * Test 2: processSemiAnnual(world, "rice") decays faction.supplies AND officer.supplies
     * while leaving every funds field untouched.
     */
    @Test
    fun `processSemiAnnual rice decays supplies only leaving funds untouched`() {
        val world = makeWorld(month = 7)
        val faction = makeFaction(id = 10L, funds = 50_000, supplies = 50_000)
        val officer = makeOfficer(id = 100L, factionId = 10L, funds = 20_000, supplies = 20_000)

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(officer))

        service.processSemiAnnual(world, "rice")

        assertEquals(50_000, faction.funds, "faction.funds MUST NOT decay when resource='rice'")
        assertEquals((50_000 * 0.97).toInt(), faction.supplies, "faction.supplies should decay by 3% (50000 bracket)")
        assertEquals(20_000, officer.funds, "officer.funds MUST NOT decay when resource='rice'")
        assertEquals((20_000 * 0.97).toInt(), officer.supplies, "officer.supplies should decay by 3%")
    }

    /**
     * Test 3: Progressive decay bracket anchor — faction with 10_000 funds sits at the
     * lower edge of the 1_000 bracket (> 10_000 requires strictly greater), so 10_000
     * exactly decays by 1% (0.99), not 3% (0.97). This locks the strictly-greater-than
     * semantics against off-by-one regressions.
     *
     * Legacy: IF(%b > 100000, %b*0.95, IF(%b > 10000, %b*0.97, IF(%b > 1000, %b*0.99, %b)))
     */
    @Test
    fun `progressive decay bracket — 10000 funds decays at 1 percent`() {
        val world = makeWorld(month = 1)
        // Four anchor points across every bracket edge
        val f1 = makeFaction(id = 1L, funds = 150_000)  // > 100_000 → 0.95
        val f2 = makeFaction(id = 2L, funds = 50_000)   // > 10_000  → 0.97
        val f3 = makeFaction(id = 3L, funds = 10_000)   // ≤ 10_000  → 0.99 (1% bracket)
        val f4 = makeFaction(id = 4L, funds = 1_000)    // ≤ 1_000   → unchanged
        val f5 = makeFaction(id = 5L, funds = 500)      // ≤ 1_000   → unchanged

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(f1, f2, f3, f4, f5))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.processSemiAnnual(world, "gold")

        assertEquals((150_000 * 0.95).toInt(), f1.funds, "150000 → 0.95 bracket")
        assertEquals((50_000 * 0.97).toInt(), f2.funds, "50000 → 0.97 bracket")
        assertEquals((10_000 * 0.99).toInt(), f3.funds, "10000 → 0.99 bracket (strictly-greater-than 10000 means 10000 itself falls in 0.99)")
        assertEquals(1_000, f4.funds, "1000 → unchanged (below threshold)")
        assertEquals(500, f5.funds, "500 → unchanged (below threshold)")
    }

    /**
     * Test 4: Officers with funds ≤ 1_000 do NOT decay — below-threshold protection
     * prevents upkeep from wiping out poor officers.
     *
     * Legacy: General decay is IF(%b > 10000, %b*0.97, %b*0.99) WHERE %b > 1000
     */
    @Test
    fun `officer with funds at or below 1000 does not decay`() {
        val world = makeWorld(month = 1)
        val faction = makeFaction(id = 10L, funds = 0)
        val poorOfficer = makeOfficer(id = 100L, factionId = 10L, funds = 1_000)
        val veryPoorOfficer = makeOfficer(id = 101L, factionId = 10L, funds = 500)
        val middleOfficer = makeOfficer(id = 102L, factionId = 10L, funds = 5_000)
        val richOfficer = makeOfficer(id = 103L, factionId = 10L, funds = 15_000)

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(officerRepository.findBySessionId(1L))
            .thenReturn(listOf(poorOfficer, veryPoorOfficer, middleOfficer, richOfficer))

        service.processSemiAnnual(world, "gold")

        assertEquals(1_000, poorOfficer.funds, "1000 → unchanged (strictly-greater-than)")
        assertEquals(500, veryPoorOfficer.funds, "500 → unchanged (below threshold)")
        assertEquals((5_000 * 0.99).toInt(), middleOfficer.funds, "5000 → 0.99 (1000 < x ≤ 10000 bracket)")
        assertEquals((15_000 * 0.97).toInt(), richOfficer.funds, "15000 → 0.97 (> 10000 bracket)")
    }

    /**
     * Test 5: Invalid resource literal throws IllegalArgumentException (same
     * contract as sibling processIncome and upstream EconomyService).
     */
    @Test
    fun `invalid resource throws IllegalArgumentException`() {
        val world = makeWorld(month = 1)
        `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            service.processSemiAnnual(world, "all")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.processSemiAnnual(world, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.processSemiAnnual(world, "funds") // LOGH domain literal — wire format is "gold"
        }
    }
}
