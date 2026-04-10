package com.openlogh.engine.ai

import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.entity.*
import com.openlogh.repository.*
import com.openlogh.service.MapService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.OffsetDateTime
import kotlin.random.Random

/**
 * Plan 22-02: probability gate + divide-by-zero guard on `OfficerAI.doDonate` excess branches.
 *
 * Ports upstream commit `a7a19cc3cd5b3fa5a7c8720484d289fc55845adc`
 * (`GeneralAI.doDonate` — 금/쌀 무조건 헌납 분기에 확률 게이트 추가).
 *
 * Bug repro: pre-fix LOGH `OfficerAI.doDonate` line 2394/2404 unconditionally donated when
 *   `general.funds > reqGold * 5 && general.funds > 5000` (and analogous for supplies),
 * draining personal stockpiles every tick at ~11.5%/year in the 1만 초과 구간. The legacy
 * PHP formula (doNPC헌납 line 2841) is `rng.nextDouble() < (funds / reqGold - 0.5)`.
 *
 * These tests assert:
 *  1. Probability gate is consulted: a seeded RNG returning 10.0 (always above any reasonable
 *     probability) MUST NOT donate.
 *  2. Divide-by-zero guard: `reqNationGold == 0` MUST NOT donate even with massive funds.
 *  3. Legacy formula anchor: at funds=60_000 / reqGold=10_000, the effective probability is
 *     `60000/10000 - 0.5 = 5.5`; any RNG output < 5.5 donates, any output >= 5.5 does not.
 *  4. Supplies branch mirrors funds branch symmetrically.
 */
class OfficerAIDonateGateTest {

    private lateinit var ai: OfficerAI

    @BeforeEach
    fun setUp() {
        val officerRepository = mock(OfficerRepository::class.java)
        val planetRepository = mock(PlanetRepository::class.java)
        val factionRepository = mock(FactionRepository::class.java)
        val diplomacyRepository = mock(DiplomacyRepository::class.java)
        val mapService = mock(MapService::class.java)
        ai = OfficerAI(
            JpaWorldPortFactory(
                officerRepository = officerRepository,
                planetRepository = planetRepository,
                factionRepository = factionRepository,
                diplomacyRepository = diplomacyRepository,
            ),
            mapService,
        )
    }

    // ──────────────────────────────────────────────────────────
    //  Fixtures
    // ──────────────────────────────────────────────────────────

    private fun createWorld(): SessionState = SessionState(
        id = 1,
        scenarioCode = "test",
        currentYear = 200,
        currentMonth = 3,
        tickSeconds = 300,
    )

    private fun createOfficer(funds: Int = 1000, supplies: Int = 1000): Officer = Officer(
        id = 1,
        sessionId = 1,
        name = "NPC-Donor",
        factionId = 1,
        planetId = 1,
        leadership = 50, // >= minNPCWarLeadership(40) → COMMANDER flag → isWarGen=true
        command = 50,
        intelligence = 50,
        ships = 0,
        training = 50,
        morale = 50,
        funds = funds,
        supplies = supplies,
        officerLevel = 1,
        npcState = 2,
        injury = 0,
        dedication = 100,
        turnTime = OffsetDateTime.now(),
    )

    private fun createPlanet(): Planet = Planet(
        id = 1,
        sessionId = 1,
        name = "행성1",
        factionId = 1,
        production = 500,
        productionMax = 1000,
        commerce = 500,
        commerceMax = 1000,
        security = 500,
        securityMax = 1000,
        frontState = 0,
        population = 60000,
        populationMax = 100000,
    )

    /** Faction with enough funds/supplies that the "nation-needs" branch does NOT fire,
     *  forcing evaluation through the "excess resource" branch we are gating. */
    private fun createRichFaction(): Faction = Faction(
        id = 1,
        sessionId = 1,
        name = "Rich Faction",
        color = "#4466FF",
        factionRank = 1,
        funds = 999_999,
        supplies = 999_999,
        militaryPower = 100,
        warState = 0,
    )

    private fun buildCtx(
        world: SessionState,
        officer: Officer,
        planet: Planet,
        faction: Faction,
        policy: NpcNationPolicy,
    ): AIContext = AIContext(
        world = world,
        general = officer,
        city = planet,
        nation = faction,
        diplomacyState = DiplomacyState.PEACE,
        generalType = ai.classifyGeneral(officer, Random(0), policy.minNPCWarLeadership),
        allCities = listOf(planet),
        allGenerals = listOf(officer),
        allNations = listOf(faction),
        frontCities = emptyList(),
        rearCities = listOf(planet),
        nationGenerals = listOf(officer),
    )

    private fun invokeDoDonate(
        ctx: AIContext,
        rng: Random,
        policy: NpcNationPolicy,
    ): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doDonate",
            AIContext::class.java,
            Random::class.java,
            NpcNationPolicy::class.java,
        )
        method.isAccessible = true
        return method.invoke(ai, ctx, rng, policy) as String?
    }

    // ──────────────────────────────────────────────────────────
    //  FixedRandom: deterministic test double for kotlin.random.Random
    // ──────────────────────────────────────────────────────────

    private class FixedRandom(private vararg val doubles: Double) : Random() {
        private var index = 0
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double {
            val v = doubles.getOrElse(index) { doubles.lastOrNull() ?: 0.0 }
            index++
            return v
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Tests
    // ──────────────────────────────────────────────────────────

    /**
     * Test 1 — probability gate is consulted on the excess-funds branch.
     * Pre-fix: funds > reqGold*5 && funds > 5000 unconditionally donates.
     * Post-fix: the branch calls `rng.nextDouble() < (funds/reqGold - 0.5)`.
     * With FixedRandom returning 10.0 (always above max prob), donation MUST NOT fire.
     */
    @Test
    fun `doDonate excess funds branch consults RNG and does not donate when gate blocks`() {
        val world = createWorld()
        // funds = 60_000 > 10_000 * 5, > 5_000 → excess branch arm active
        val officer = createOfficer(funds = 60_000, supplies = 0)
        val planet = createPlanet()
        val faction = createRichFaction() // faction.funds > reqNationGold, so "nation-needs" branch inert
        val policy = NpcNationPolicy() // reqNationGold=10_000
        val ctx = buildCtx(world, officer, planet, faction, policy)

        // FixedRandom(10.0): nextDouble() always returns 10.0. Any probability check
        // `rng.nextDouble() < p` is false for all p < 10.0, so excess branch MUST NOT fire.
        val result = invokeDoDonate(ctx, FixedRandom(10.0), policy)

        assertNull(
            result,
            "Excess funds branch MUST be probability-gated. Pre-fix code returned '헌납' unconditionally.",
        )
        assertNull(
            officer.meta["aiArg"],
            "No donation should have been staged when the RNG gate rejects the excess branch.",
        )
    }

    /**
     * Test 2 — divide-by-zero guard on excess-funds branch.
     * When `policy.reqNationGold == 0`, `funds / reqGold - 0.5` is infinite/undefined.
     * Post-fix: the `reqGold > 0 &&` short-circuit guard MUST prevent the gate from firing
     * and the branch MUST NOT donate.
     */
    @Test
    fun `doDonate excess funds branch does not donate when reqNationGold is zero`() {
        val world = createWorld()
        // Massive funds (> 5_000 and trivially > 0 * 5 since reqGold==0)
        val officer = createOfficer(funds = 1_000_000, supplies = 0)
        val planet = createPlanet()
        val faction = createRichFaction()
        val policy = NpcNationPolicy(reqNationGold = 0, reqNationRice = 0)
        val ctx = buildCtx(world, officer, planet, faction, policy)

        // RNG value doesn't matter: the guard must fire before the comparison.
        // FixedRandom(0.0): would always pass `nextDouble() < anything-positive` check.
        val result = invokeDoDonate(ctx, FixedRandom(0.0), policy)

        assertNull(
            result,
            "When reqNationGold == 0, the excess-funds branch MUST short-circuit via `reqGold > 0 &&` guard " +
                "and MUST NOT donate (divide-by-zero protection).",
        )
    }

    /**
     * Test 3 — legacy PHP formula anchor.
     * funds=60_000, reqGold=10_000 → prob = 60000/10000 - 0.5 = 5.5.
     * - rng < 5.5 → donates (gate passes)
     * - rng >= 5.5 → does NOT donate (gate blocks)
     */
    @Test
    fun `doDonate excess funds branch matches legacy probability formula`() {
        val world = createWorld()
        val planet = createPlanet()
        val faction = createRichFaction()
        val policy = NpcNationPolicy()

        // Case A: rng returns 1.0 < 5.5 → donates
        val officerA = createOfficer(funds = 60_000, supplies = 0)
        val ctxA = buildCtx(world, officerA, planet, faction, policy)
        val resultA = invokeDoDonate(ctxA, FixedRandom(1.0), policy)
        assertEquals(
            "헌납",
            resultA,
            "rng=1.0 < (60000/10000 - 0.5)=5.5 MUST donate — legacy PHP formula anchor.",
        )
        @Suppress("UNCHECKED_CAST")
        val aiArgA = officerA.meta["aiArg"] as Map<String, Any>
        assertEquals(true, aiArgA["isGold"], "Donation should be gold (isGold=true).")

        // Case B: rng returns 6.0 >= 5.5 → does NOT donate (gate blocks)
        val officerB = createOfficer(funds = 60_000, supplies = 0)
        val ctxB = buildCtx(world, officerB, planet, faction, policy)
        val resultB = invokeDoDonate(ctxB, FixedRandom(6.0), policy)
        assertNull(
            resultB,
            "rng=6.0 >= (60000/10000 - 0.5)=5.5 MUST NOT donate — probability gate blocks.",
        )
    }

    /**
     * Test 4 — supplies branch mirrors funds branch (symmetric fix).
     * Both 0 and null funds are used; emergency supplies branch is avoided by setting
     * faction.supplies high (not critically low <= 500).
     * The excess-supplies branch at line 2404 must have the same probability gate.
     */
    @Test
    fun `doDonate excess supplies branch also consults RNG gate`() {
        val world = createWorld()
        // Force funds branch fully inactive: funds well below reqGold*1.5 and reqGold*5.
        // supplies = 70_000 > reqNationRice(12_000) * 5 = 60_000 → excess-supplies branch active.
        val officer = createOfficer(funds = 0, supplies = 70_000)
        val planet = createPlanet()
        val faction = createRichFaction() // high supplies → no "nation-needs" and no emergency (>500)
        val policy = NpcNationPolicy()
        val ctx = buildCtx(world, officer, planet, faction, policy)

        // FixedRandom(10.0): nextDouble() returns 10.0 every call → excess-supplies gate blocks.
        val result = invokeDoDonate(ctx, FixedRandom(10.0), policy)

        assertNull(
            result,
            "Excess supplies branch MUST also be probability-gated (symmetric upstream fix).",
        )
        assertNull(
            officer.meta["aiArg"],
            "No donation should be staged when the supplies RNG gate rejects the branch.",
        )
    }
}
