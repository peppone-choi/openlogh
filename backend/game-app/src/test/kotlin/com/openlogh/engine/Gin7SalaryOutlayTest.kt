package com.openlogh.engine

import com.openlogh.engine.economy.BillFormula
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
 * Phase 23 Plan 23-04 — Salary outlay (faction → officer funds transfer).
 *
 * Legacy reference: upstream opensam inlines salary payment in
 *   com.opensam.engine.EconomyService.processIncome(world, …, "gold")
 * but the LOGH port extracts it as its own public method so that Plan 23-04
 * can unit-test it in isolation and sibling plans 23-05/23-06 can compose it.
 *
 * Contract under test — `Gin7EconomyService.payOfficerSalaries(world, faction, officers): Int`:
 *
 *   1. Per active officer, individualSalary =
 *        BillFormula.fromDedication(officer.dedication) * faction.taxRate / 100
 *   2. faction.funds -= sum(individualSalary)
 *   3. officer.funds += individualSalary (per officer)
 *   4. Inactive officers (npcState.toInt() == 5 — legacy "graveyard" sentinel)
 *      are EXCLUDED from the outlay
 *   5. Negative funds are allowed — the legacy PHP does not guard against
 *      overdraft (matches hwe/sammo/Event/Action/ProcessIncome.php)
 *   6. Method returns totalPaid (sum across active officers) so the caller can
 *      log the payout cleanly
 *
 * Anchors (conservation law): sum of individual salary credits == totalPaid
 * return value == (faction.funds delta * -1).
 *
 * Scope boundary: this suite tests payOfficerSalaries in isolation via a
 * direct call. Integration with processIncome(world, "gold") is verified
 * separately via Gin7ProcessIncomeTest (Plan 23-01) coexistence and a dedicated
 * processIncome-wiring test here.
 */
class Gin7SalaryOutlayTest {

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
        id: Long = 10L,
        sessionId: Long = 1L,
        taxRate: Int = 100,
        funds: Int = 100_000,
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

    private fun makeOfficer(
        id: Long,
        sessionId: Long = 1L,
        factionId: Long = 10L,
        dedication: Int = 400,
        funds: Int = 0,
        npcState: Int = 0,
    ): Officer {
        val o = Officer()
        o.id = id
        o.sessionId = sessionId
        o.factionId = factionId
        o.dedication = dedication
        o.funds = funds
        o.npcState = npcState.toShort()
        return o
    }

    /**
     * Test 1 — Baseline salary transfer + conservation law.
     *
     * 3 active officers, dedication=400 each → BillFormula.fromDedication(400):
     *   ceil(sqrt(400)/10) = ceil(2.0) = 2 → 2*200 + 400 = 800 per officer
     * taxRate = 100 → individualSalary = 800 * 100 / 100 = 800 each
     * totalPaid = 2400
     *
     * Asserts:
     *   - return value = 2400 (sum across officers)
     *   - faction.funds decreased by 2400 (100_000 → 97_600)
     *   - each officer.funds increased by 800 (0 → 800)
     *   - conservation: Σ officer.fund credits == totalPaid == faction delta
     */
    @Test
    fun `payOfficerSalaries credits each active officer and debits faction by sum`() {
        val world = makeWorld()
        val faction = makeFaction(taxRate = 100, funds = 100_000)
        val officers = listOf(
            makeOfficer(id = 1L, dedication = 400, funds = 0),
            makeOfficer(id = 2L, dedication = 400, funds = 0),
            makeOfficer(id = 3L, dedication = 400, funds = 0),
        )

        // Verify formula anchor (locks Phase 22-01 BillFormula parity)
        assertEquals(800, BillFormula.fromDedication(400), "formula anchor: ded=400 → 800")

        val totalPaid = service.payOfficerSalaries(world, faction, officers)

        assertEquals(2400, totalPaid, "totalPaid = Σ(800) across 3 active officers")
        assertEquals(100_000 - 2400, faction.funds, "faction.funds must decrease by totalPaid")
        officers.forEach { o ->
            assertEquals(800, o.funds, "officer ${o.id} must receive 800 credit")
        }

        // Conservation: Σ officer credits == totalPaid == -faction delta
        val totalOfficerCredits = officers.sumOf { it.funds }
        assertEquals(totalPaid, totalOfficerCredits, "conservation: officer credits match totalPaid")
        assertEquals(-(faction.funds - 100_000), totalPaid, "conservation: faction delta matches totalPaid")
    }

    /**
     * Test 2 — taxRate scales the outlay linearly.
     *
     * Same 3 officers at dedication=400 (bill=800 each), but taxRate=50.
     *   individualSalary = 800 * 50 / 100 = 400 each
     *   totalPaid        = 1200 (half of the taxRate=100 case)
     *
     * This locks the taxRate scaling factor — a regression here would reintroduce
     * the Phase 22-01 4x-underestimate bug if taxRate dropped out of the formula.
     */
    @Test
    fun `payOfficerSalaries scales salary by faction taxRate`() {
        val world = makeWorld()
        val faction = makeFaction(taxRate = 50, funds = 100_000)
        val officers = listOf(
            makeOfficer(id = 1L, dedication = 400),
            makeOfficer(id = 2L, dedication = 400),
            makeOfficer(id = 3L, dedication = 400),
        )

        val totalPaid = service.payOfficerSalaries(world, faction, officers)

        assertEquals(1200, totalPaid, "taxRate=50 yields half the taxRate=100 outlay")
        assertEquals(100_000 - 1200, faction.funds)
        officers.forEach { o ->
            assertEquals(400, o.funds, "officer ${o.id} receives 400 at taxRate=50")
        }
    }

    /**
     * Test 3 — Negative funds are allowed (matches legacy PHP behavior).
     *
     * faction.funds = 500, 3 officers at ded=400 → totalPaid = 2400 > 500.
     * The legacy `hwe/sammo/Event/Action/ProcessIncome.php` does not guard
     * against overdraft — funds are simply written back, potentially negative.
     * NPC FactionAI later recovers via adjustTaxAndBill / disband cycles.
     */
    @Test
    fun `payOfficerSalaries allows faction funds to go negative`() {
        val world = makeWorld()
        val faction = makeFaction(taxRate = 100, funds = 500)
        val officers = listOf(
            makeOfficer(id = 1L, dedication = 400),
            makeOfficer(id = 2L, dedication = 400),
            makeOfficer(id = 3L, dedication = 400),
        )

        val totalPaid = service.payOfficerSalaries(world, faction, officers)

        assertEquals(2400, totalPaid)
        assertEquals(500 - 2400, faction.funds, "funds must go negative (500 - 2400 = -1900)")
        assertTrue(faction.funds < 0, "overdraft state is legal — no clamp")
        // Officers are still paid in full
        officers.forEach { o ->
            assertEquals(800, o.funds, "officers are still paid even under overdraft")
        }
    }

    /**
     * Test 4 — Inactive officers (npcState == 5) are excluded from outlay.
     *
     * Legacy "graveyard" sentinel — npcState=5 means the officer has exited
     * active duty (dead / retired / captured-and-not-yet-rehomed). They do NOT
     * receive salary and their bill does NOT count against faction.funds.
     *
     * Setup: 2 active (npcState=0) + 2 inactive (npcState=5). Only the active
     * officers should be paid; inactive funds stay at their pre-call value.
     */
    @Test
    fun `payOfficerSalaries excludes inactive officers with npcState equals 5`() {
        val world = makeWorld()
        val faction = makeFaction(taxRate = 100, funds = 100_000)
        val officers = listOf(
            makeOfficer(id = 1L, dedication = 400, npcState = 0, funds = 0),       // active
            makeOfficer(id = 2L, dedication = 400, npcState = 0, funds = 0),       // active
            makeOfficer(id = 3L, dedication = 400, npcState = 5, funds = 9999),    // INACTIVE
            makeOfficer(id = 4L, dedication = 400, npcState = 5, funds = 8888),    // INACTIVE
        )

        val totalPaid = service.payOfficerSalaries(world, faction, officers)

        // Only 2 active × 800 each = 1600
        assertEquals(1600, totalPaid, "inactive officers must not contribute to totalPaid")
        assertEquals(100_000 - 1600, faction.funds, "faction deducts only active-officer bills")

        assertEquals(800, officers[0].funds, "active officer 1 paid")
        assertEquals(800, officers[1].funds, "active officer 2 paid")
        assertEquals(9999, officers[2].funds, "inactive officer 3 funds untouched")
        assertEquals(8888, officers[3].funds, "inactive officer 4 funds untouched")
    }

    /**
     * Test 5 — Conservation anchor with heterogeneous dedication.
     *
     * Mixed dedication values exercise the BillFormula bracket math end-to-end:
     *   - ded=0      → bill=400  (floor case)
     *   - ded=100    → bill=600  (dedLevel=1)
     *   - ded=10000  → bill=2400 (dedLevel=10, Phase 22-01 anchor)
     *   - ded=1_000_000 → bill=6400 (dedLevel=30 cap, Phase 22-01 anchor)
     *
     * At taxRate=100:
     *   individual = bill  →  sum = 400 + 600 + 2400 + 6400 = 9800
     *
     * The critical assertion is the conservation law: the sum of officer credits
     * must equal the returned totalPaid must equal (-faction delta). This is the
     * structural anchor — if any officer is double-paid or dropped, it breaks.
     */
    @Test
    fun `payOfficerSalaries conservation law holds across heterogeneous dedication`() {
        val world = makeWorld()
        val faction = makeFaction(taxRate = 100, funds = 100_000)
        val officers = listOf(
            makeOfficer(id = 1L, dedication = 0),         // bill 400
            makeOfficer(id = 2L, dedication = 100),       // bill 600
            makeOfficer(id = 3L, dedication = 10_000),    // bill 2400
            makeOfficer(id = 4L, dedication = 1_000_000), // bill 6400 (clamped)
        )

        val totalPaid = service.payOfficerSalaries(world, faction, officers)

        assertEquals(400 + 600 + 2400 + 6400, totalPaid, "sum of per-dedication bills")
        assertEquals(9800, totalPaid, "explicit anchor: 400+600+2400+6400 = 9800")

        // Individual credits
        assertEquals(400, officers[0].funds)
        assertEquals(600, officers[1].funds)
        assertEquals(2400, officers[2].funds)
        assertEquals(6400, officers[3].funds)

        // Conservation: Σ credits == totalPaid
        val sumCredits = officers.sumOf { it.funds }
        assertEquals(totalPaid, sumCredits, "Σ officer.funds credits == totalPaid")

        // Conservation: faction delta == -totalPaid
        assertEquals(100_000 - totalPaid, faction.funds)
    }
}
