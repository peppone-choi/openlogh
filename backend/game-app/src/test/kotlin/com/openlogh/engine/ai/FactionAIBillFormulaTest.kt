package com.openlogh.engine.ai

import com.openlogh.command.CommandExecutor
import com.openlogh.engine.turn.cqrs.memory.DiplomacySnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionSnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.FleetSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.PlanetSnapshot
import com.openlogh.engine.turn.cqrs.memory.UnitCrewSnapshot
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.port.WorldWritePort
import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OperationPlanRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Phase 22 Plan 01 — FactionAI.adjustTaxAndBill legacy-correct bill formula port.
 *
 * Verifies the PHP-parity bill formula ported from upstream commit a7a19cc3:
 *   getBill(ded) = getDedLevel(ded) * 200 + 400
 *   getDedLevel(ded) = clamp(ceil(sqrt(ded) / 10), 0, 30)
 *
 * The old LOGH formula `(generals + cities) * taxRate` under-reported salary by ~4x,
 * causing the NPC treasury drain bug. Tests are isolated via reflection on the
 * private helper + a capturing fake WriteBack port for the anchor test.
 */
class FactionAIBillFormulaTest {

    private val ai: FactionAI = FactionAI(
        worldPortFactory = mock(JpaWorldPortFactory::class.java),
        commandExecutor = mock(CommandExecutor::class.java),
        fleetRepository = mock(FleetRepository::class.java),
        operationPlanRepository = mock(OperationPlanRepository::class.java),
    )

    private fun callGetBillFromDedication(dedication: Int): Int {
        val m = FactionAI::class.java.getDeclaredMethod("getBillFromDedication", Int::class.javaPrimitiveType)
        m.isAccessible = true
        return m.invoke(ai, dedication) as Int
    }

    private fun callAdjustTaxAndBill(
        writePort: WorldWritePort,
        faction: Faction,
        cities: List<Planet>,
        officers: List<Officer>,
    ) {
        val m = FactionAI::class.java.getDeclaredMethod(
            "adjustTaxAndBill",
            WorldWritePort::class.java,
            Faction::class.java,
            List::class.java,
            List::class.java,
        )
        m.isAccessible = true
        m.invoke(ai, writePort, faction, cities, officers)
    }

    private fun newOfficer(id: Long, dedication: Int, npcState: Short = 2): Officer = Officer(
        id = id,
        sessionId = 1L,
        factionId = 1L,
        name = "O$id",
        dedication = dedication,
        npcState = npcState,
    )

    private fun newFaction(funds: Int, taxRate: Short = 100, conscriptionRateTmp: Short = 15): Faction = Faction(
        id = 1L,
        sessionId = 1L,
        name = "Empire",
        abbreviation = "EM",
        color = "#4466ff",
        funds = funds,
        supplies = 10_000,
        taxRate = taxRate,
        conscriptionRateTmp = conscriptionRateTmp,
    )

    // ── Tests for the pure helper ────────────────────────────────────────────

    @Test
    fun `getBillFromDedication returns 400 at zero dedication`() {
        // dedLevel = ceil(sqrt(0)/10) = 0 → 0*200+400 = 400
        assertEquals(400, callGetBillFromDedication(0))
    }

    @Test
    fun `getBillFromDedication returns 600 at dedication 100`() {
        // sqrt(100)=10, /10=1, ceil=1 → 1*200+400 = 600
        assertEquals(600, callGetBillFromDedication(100))
    }

    @Test
    fun `getBillFromDedication returns 2400 at dedication 10000`() {
        // sqrt(10000)=100, /10=10, ceil=10 → 10*200+400 = 2400
        // NOTE: plan text lists 4400, but per upstream KDoc getBill(ded)=dedLevel*200+400
        //   at dedLevel=10 yields 2400. Plan arithmetic was off — matching the legacy
        //   PHP formula takes precedence (documented as a deviation in 22-01-SUMMARY).
        assertEquals(2400, callGetBillFromDedication(10000))
    }

    @Test
    fun `getBillFromDedication clamps dedLevel at max 30`() {
        // sqrt(1_000_000)=1000, /10=100, ceil=100 → clamped to 30 → 30*200+400 = 6400
        assertEquals(6400, callGetBillFromDedication(1_000_000))
    }

    // ── Anchor test: formula-shape regression vs the old bug ─────────────────

    /**
     * Anchor: 10 officers each with dedication=400 (getBill(400)=1400),
     * at taxRate=100, must use `sum(getBill(ded)) * taxRate / 100` — NOT
     * the legacy-broken `(officers + planets) * taxRate` formula.
     *
     * Correct totalBill = 10 * 1400 * 100 / 100 = 14_000
     * Broken totalBill  = (10 + 0) * 100 = 1000
     *
     * With funds=30_000 (< 14_000 * 3 = 42_000), the GREEN code should lower
     * the tax rate and persist the faction. The broken code would NOT lower
     * (1000 * 3 = 3000 < 30_000), so no putFaction occurs. Asserting that
     * taxRate moves to 95 proves the corrected formula is in play.
     */
    @Test
    fun `adjustTaxAndBill uses sum of per-officer bill not officer-plus-planet count`() {
        // 10 officers at dedication=400 → per-officer bill = getBill(400)
        //   sqrt(400)=20; 20/10=2; ceil(2)=2 → 2*200+400 = 800
        // baseOutcome = 10 * 800 = 8000
        // totalBill at taxRate=100 → 8000 * 100 / 100 = 8000
        // funds=20_000 < 8000*3=24_000 → GREEN must lower taxRate (-5) from 100 to 95
        // Broken (generals+cities)*taxRate formula → totalBill=10*100=1000,
        //   funds=20_000 > 1000*10=10_000 → would try to raise (stuck at 100 cap, no change persisted)
        val officers = (1L..10L).map { newOfficer(it, dedication = 400) }
        val cities = emptyList<Planet>()
        val factionLow = newFaction(funds = 20_000, taxRate = 100)

        val captured = CapturingWritePort()
        callAdjustTaxAndBill(captured, factionLow, cities, officers)

        // Correct formula: baseOutcome=8000, totalBill=8000, 20_000 < 24_000 → lower newBill to 95
        // Broken formula: totalBill = (10+0)*100 = 1000, 20_000 > 1000*10=10000 → RAISE bill to 100 (already max)
        // So correct path must persist a faction with taxRate=95, broken path persists taxRate=100 (no change, nothing written).
        assertEquals(1, captured.factionPuts.size, "corrected adjustTaxAndBill must persist the faction on this scenario")
        val persisted = captured.factionPuts.single()
        assertEquals(95.toShort(), persisted.taxRate, "expected taxRate to lower from 100 to 95 under legacy-correct formula")
        assertNotEquals(100.toShort(), persisted.taxRate, "broken (generals+cities)*bill formula would not have triggered a lower here")
    }

    /**
     * Secondary invariant: minimum taxRate must clamp to 20, never 0.
     * Set up extreme salary load so the lower branch fires repeatedly toward the floor.
     */
    @Test
    fun `adjustTaxAndBill clamps taxRate to minimum 20 not 0`() {
        val faction = newFaction(funds = 0, taxRate = 25)
        val officers = (1L..5L).map { newOfficer(it, dedication = 400) }
        val captured = CapturingWritePort()
        callAdjustTaxAndBill(captured, faction, emptyList(), officers)

        assertEquals(1, captured.factionPuts.size)
        val persisted = captured.factionPuts.single()
        assertTrue(persisted.taxRate >= 20, "taxRate must never drop below 20, got ${persisted.taxRate}")
        assertEquals(20.toShort(), persisted.taxRate, "25 - 5 = 20 should be the new floor")
    }

    // ── Minimal capturing WriteBack port ─────────────────────────────────────

    private class CapturingWritePort : WorldWritePort {
        val factionPuts = mutableListOf<FactionSnapshot>()
        override fun putFaction(snapshot: FactionSnapshot) { factionPuts += snapshot }

        override fun putOfficer(snapshot: OfficerSnapshot) {}
        override fun putPlanet(snapshot: PlanetSnapshot) {}
        override fun putFleet(snapshot: FleetSnapshot) {}
        override fun putUnitCrew(snapshot: UnitCrewSnapshot) {}
        override fun putDiplomacy(snapshot: DiplomacySnapshot) {}
        override fun deleteOfficer(id: Long) {}
        override fun deletePlanet(id: Long) {}
        override fun deleteFaction(id: Long) {}
        override fun deleteFleet(id: Long) {}
        override fun deleteUnitCrew(id: Long) {}
        override fun deleteDiplomacy(id: Long) {}
        override fun setOfficerTurns(officerId: Long, turns: List<OfficerTurnSnapshot>) {}
        override fun setFactionTurns(factionId: Long, officerLevel: Short, turns: List<FactionTurnSnapshot>) {}
        override fun removeOfficerTurns(officerId: Long) {}
        override fun removeFactionTurns(factionId: Long, officerLevel: Short) {}
    }
}
