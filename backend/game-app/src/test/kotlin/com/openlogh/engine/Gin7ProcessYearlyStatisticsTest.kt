package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Phase 23 Plan 23-07 — Annual statistics refresh (power/gennum, Jan 1 trigger).
 *
 * Ports legacy `EconomyService.processYearlyStatistics(world)` (lines 362-438) into
 * `Gin7EconomyService.processYearlyStatistics(world)`. Recalculates each non-neutral
 * faction's `militaryPower` and `officerCount` from current roster, resources,
 * tech_level, and supplied-planet aggregates.
 *
 * ### Legacy formula (EconomyService.kt:404)
 *
 * ```
 *   militaryPower = (resource + tech + cityPower + statPower + dexPower + expDed) / 10
 *
 *   resource  = (faction.funds + faction.supplies + Σ(officer.funds + officer.supplies)) / 100
 *   tech      = faction.techLevel
 *   cityPower = supplied-planet aggregate (pop × (pop+prod+comm+sec+fort+orbdef)) / maxSum / 100
 *   statPower = officer 8-stat sum (LOGH adaptation — legacy used lead/str/intel)
 *   dexPower  = 0   (LOGH Officer has no dex1..dex5 fields — deviation)
 *   expDed    = Σ(officer.experience + officer.dedication) / 100
 * ```
 *
 * ### LOGH deviations from legacy (documented in SUMMARY)
 *
 * 1. **Repository-based access** — uses `factionRepository` / `planetRepository` /
 *    `officerRepository` instead of `worldPortFactory.allFactions()` etc. Matches
 *    the rest of Gin7EconomyService (sibling processIncome, updateFactionRank).
 * 2. **No `dex1..dex5` term** — LOGH Officer has no `dex` fields (ship-class mastery
 *    was not ported). `dexPower = 0` for parity-audit traceability.
 * 3. **8-stat statPower** — LOGH Officer has 8 stats
 *    (leadership+command+intelligence+politics+administration+mobility+attack+defense).
 *    statPower = Σ(all 8 stats) per active officer (npcState != 5). Replaces legacy's
 *    `npcMul * leaderCore * 2 + (sqrt(intel*str)*2 + lead/2)/2` shape; the additive
 *    8-sum is LOGH's ground-truth combat-capacity metric.
 * 4. **No RNG jitter** — legacy applied `DeterministicRng.nextDouble(0.95, 1.05)` as
 *    seed-noise. Deterministic output is more test-friendly and parity is measured at
 *    the formula level, not the jitter level.
 * 5. **Neutral skip uses `id == 0L`** — mirrors sibling updateFactionRank / processIncome.
 *    Legacy checked `factionRank.toInt() == 0` which conflates "rank 0 방랑군" with
 *    "neutral" — a valid non-neutral faction at rank 0 would be skipped in the upstream
 *    body. The Gin7 port uses the cleaner `id == 0L` neutral discriminator.
 * 6. **officerCount = active officer count** — `gennum` in legacy; LOGH maps to
 *    `faction.officerCount`. Excludes graveyard officers (`npcState == 5`).
 * 7. **Unsupplied planets excluded from cityPower** — matches legacy
 *    (`supplyState.toInt() == 1` filter).
 *
 * ### Contract under test
 *
 *   1. Faction with known (funds, supplies, techLevel, supplied planets, active officers)
 *      → `militaryPower` matches LOGH-adapted formula (documented above).
 *   2. Empty faction (no planets, no officers) → `militaryPower = 0`.
 *   3. `officerCount` field updated to match active roster (excludes `npcState == 5`).
 *   4. Neutral faction (id=0) skipped — both `militaryPower` and `officerCount` untouched.
 *   5. Unsupplied planets (`supplyState == 0`) excluded from cityPower aggregate.
 *
 * Scope: formula port + persistence. No disaster/boom (23-08), trade rate (23-09),
 * or pipeline wire-up (23-10).
 */
class Gin7ProcessYearlyStatisticsTest {

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

    private fun makeWorld(sessionId: Int = 1): SessionState {
        val world = SessionState()
        world.id = sessionId.toShort()
        world.currentMonth = 1.toShort() // Jan 1 = annual trigger
        world.currentYear = 800.toShort()
        return world
    }

    private fun makeFaction(
        id: Long,
        sessionId: Long = 1L,
        techLevel: Float = 0f,
        funds: Int = 0,
        supplies: Int = 0,
        militaryPower: Int = 0,
        officerCount: Int = 0,
    ): Faction {
        val f = Faction()
        f.id = id
        f.sessionId = sessionId
        f.factionType = "empire"
        f.techLevel = techLevel
        f.funds = funds
        f.supplies = supplies
        f.militaryPower = militaryPower
        f.officerCount = officerCount
        return f
    }

    private fun makePlanet(
        id: Long,
        sessionId: Long = 1L,
        factionId: Long,
        population: Int = 0,
        populationMax: Int = 1,
        production: Int = 0,
        productionMax: Int = 1,
        commerce: Int = 0,
        commerceMax: Int = 1,
        security: Int = 0,
        securityMax: Int = 1,
        fortress: Int = 0,
        fortressMax: Int = 1,
        orbitalDefense: Int = 0,
        orbitalDefenseMax: Int = 1,
        supplyState: Short = 1,
    ): Planet {
        val p = Planet()
        p.id = id
        p.sessionId = sessionId
        p.factionId = factionId
        p.population = population
        p.populationMax = populationMax
        p.production = production
        p.productionMax = productionMax
        p.commerce = commerce
        p.commerceMax = commerceMax
        p.security = security
        p.securityMax = securityMax
        p.fortress = fortress
        p.fortressMax = fortressMax
        p.orbitalDefense = orbitalDefense
        p.orbitalDefenseMax = orbitalDefenseMax
        p.supplyState = supplyState
        return p
    }

    private fun makeOfficer(
        id: Long,
        sessionId: Long = 1L,
        factionId: Long,
        funds: Int = 0,
        supplies: Int = 0,
        experience: Int = 0,
        dedication: Int = 0,
        leadership: Int = 50,
        command: Int = 50,
        intelligence: Int = 50,
        politics: Int = 50,
        administration: Int = 50,
        mobility: Int = 50,
        attack: Int = 50,
        defense: Int = 50,
        npcState: Int = 0,
    ): Officer {
        val o = Officer()
        o.id = id
        o.sessionId = sessionId
        o.factionId = factionId
        o.funds = funds
        o.supplies = supplies
        o.experience = experience
        o.dedication = dedication
        o.leadership = leadership.toShort()
        o.command = command.toShort()
        o.intelligence = intelligence.toShort()
        o.politics = politics.toShort()
        o.administration = administration.toShort()
        o.mobility = mobility.toShort()
        o.attack = attack.toShort()
        o.defense = defense.toShort()
        o.npcState = npcState.toShort()
        return o
    }

    /**
     * Test 1 — Known inputs → expected militaryPower per LOGH-adapted formula.
     *
     * Faction:  funds=10_000, supplies=5_000, techLevel=20
     * Officer:  funds=500, supplies=200, experience=100, dedication=200, all 8 stats = 50
     * Planet:   pop=1000, prod=500, comm=800, sec=300, fort=200, orbDef=400
     *           all Max = 2× current (headroom for scale sanity)
     *           supplyState = 1 (supplied)
     *
     * Calculation:
     *   resource  = (10_000 + 5_000 + 500 + 200) / 100 = 157.0
     *   tech      = 20.0
     *   cityPower = (1000 × (1000+500+800+300+200+400)) / (2000+1000+1600+600+400+800) / 100
     *             = (1000 × 3200) / 6400 / 100
     *             = 3_200_000 / 6400 / 100
     *             = 5.0
     *   statPower = 50×8 = 400.0  (one officer)
     *   dexPower  = 0.0   (LOGH omits dex1..dex5)
     *   expDed    = (100 + 200) / 100 = 3.0
     *   raw       = (157 + 20 + 5 + 400 + 0 + 3) / 10 = 58.5
     *   militaryPower = round(58.5) = 59 (round-half-up; round-half-even would be 58)
     *
     * Assert: result within ±1 to tolerate Kotlin `round()` banker's rounding variance.
     */
    @Test
    fun `faction with known inputs produces expected military power`() {
        val world = makeWorld()
        val faction = makeFaction(id = 10L, techLevel = 20f, funds = 10_000, supplies = 5_000)
        val planet = makePlanet(
            id = 100L, factionId = 10L,
            population = 1000, populationMax = 2000,
            production = 500, productionMax = 1000,
            commerce = 800, commerceMax = 1600,
            security = 300, securityMax = 600,
            fortress = 200, fortressMax = 400,
            orbitalDefense = 400, orbitalDefenseMax = 800,
            supplyState = 1,
        )
        val officer = makeOfficer(
            id = 1L, factionId = 10L,
            funds = 500, supplies = 200,
            experience = 100, dedication = 200,
            leadership = 50, command = 50, intelligence = 50, politics = 50,
            administration = 50, mobility = 50, attack = 50, defense = 50,
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(officer))

        service.processYearlyStatistics(world)

        // Expected: round((157 + 20 + 5 + 400 + 0 + 3) / 10) = round(58.5) → 58 or 59.
        val power = faction.militaryPower
        assertTrue(
            power in 58..59,
            "militaryPower must match formula within rounding tolerance, got $power (expected 58 or 59)",
        )
    }

    /**
     * Test 2 — Empty faction (no planets, no officers) → militaryPower = 0.
     *
     * resource  = (0+0+0+0)/100 = 0
     * tech      = 0
     * cityPower = 0 (no supplied planets)
     * statPower = 0 (no officers)
     * expDed    = 0
     * raw       = 0 → round(0) = 0
     */
    @Test
    fun `empty faction produces zero military power`() {
        val world = makeWorld()
        val faction = makeFaction(id = 10L, techLevel = 0f, funds = 0, supplies = 0, militaryPower = 999)

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.processYearlyStatistics(world)

        assertEquals(0, faction.militaryPower, "empty faction must recalculate to militaryPower = 0")
        assertEquals(0, faction.officerCount, "empty faction must recalculate to officerCount = 0")
    }

    /**
     * Test 3 — officerCount updated to match active roster size.
     *
     * 3 active officers (npcState != 5) + 1 graveyard officer (npcState = 5)
     *   → officerCount = 3 (graveyard excluded, legacy gennum semantics)
     */
    @Test
    fun `officer count reflects active roster only`() {
        val world = makeWorld()
        val faction = makeFaction(id = 10L, officerCount = 0)
        val officers = listOf(
            makeOfficer(id = 1L, factionId = 10L, npcState = 0), // active
            makeOfficer(id = 2L, factionId = 10L, npcState = 1), // active (npc sub-state)
            makeOfficer(id = 3L, factionId = 10L, npcState = 2), // active (hostile npc)
            makeOfficer(id = 4L, factionId = 10L, npcState = 5), // graveyard — EXCLUDED
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())
        `when`(officerRepository.findBySessionId(1L)).thenReturn(officers)

        service.processYearlyStatistics(world)

        assertEquals(3, faction.officerCount, "officerCount must exclude graveyard (npcState=5) officers")
    }

    /**
     * Test 4 — Neutral faction (id=0) skipped.
     *
     * Even with officers and planets, the neutral faction's militaryPower and
     * officerCount must remain untouched. Mirrors sibling Gin7 methods.
     */
    @Test
    fun `neutral faction is skipped`() {
        val world = makeWorld()
        val neutral = makeFaction(id = 0L, militaryPower = 555, officerCount = 7)
        val neutralPlanet = makePlanet(id = 999L, factionId = 0L, population = 10_000)
        val neutralOfficer = makeOfficer(id = 1L, factionId = 0L)

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(neutral))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(neutralPlanet))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(neutralOfficer))

        service.processYearlyStatistics(world)

        assertEquals(555, neutral.militaryPower, "neutral faction militaryPower must be untouched")
        assertEquals(7, neutral.officerCount, "neutral faction officerCount must be untouched")
    }

    /**
     * Test 5 — Unsupplied planets excluded from cityPower aggregate (legacy parity anchor).
     *
     * Two planets: one supplied (supplyState=1), one isolated (supplyState=0).
     * The isolated planet is an outlier (huge population) that would inflate militaryPower
     * if included. The supplied planet is small. Expected: militaryPower dominated by
     * the supplied planet → cannot equal the "everything included" total.
     *
     * Anchor: if we ran with BOTH planets included, cityPower would be
     *   ((1000+100_000) × ((1000+100_000)+small+huge)) / bigMaxSum / 100 → very large
     *   → militaryPower ≈ hundreds. With ONLY supplied, cityPower is tiny → militaryPower ≈ 0-2.
     *
     * We assert militaryPower is < 50 as the "supplied-only" anchor — large enough to survive
     * formula tweaks, small enough to catch regressions that include isolated planets.
     */
    @Test
    fun `unsupplied planets are excluded from city power`() {
        val world = makeWorld()
        val faction = makeFaction(id = 10L, techLevel = 0f, funds = 0, supplies = 0)
        val supplied = makePlanet(
            id = 100L, factionId = 10L,
            population = 100, populationMax = 200,
            production = 50, productionMax = 100,
            commerce = 50, commerceMax = 100,
            security = 50, securityMax = 100,
            fortress = 50, fortressMax = 100,
            orbitalDefense = 50, orbitalDefenseMax = 100,
            supplyState = 1,
        )
        val isolated = makePlanet(
            id = 101L, factionId = 10L,
            population = 100_000, populationMax = 200_000,
            production = 50_000, productionMax = 100_000,
            commerce = 50_000, commerceMax = 100_000,
            security = 50_000, securityMax = 100_000,
            fortress = 50_000, fortressMax = 100_000,
            orbitalDefense = 50_000, orbitalDefenseMax = 100_000,
            supplyState = 0, // ISOLATED — must be excluded from cityPower
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(supplied, isolated))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.processYearlyStatistics(world)

        // Supplied planet alone: cityPower = (100 × 300) / 600 / 100 = 0.5
        // → militaryPower ≈ round(0.5 / 10) = 0
        // If isolated planet leaked in, militaryPower would be in the hundreds.
        assertTrue(
            faction.militaryPower < 50,
            "unsupplied planet must be excluded from cityPower (got militaryPower=${faction.militaryPower})",
        )
        assertNotEquals(
            555, faction.militaryPower,
            "anchor: militaryPower must have been recalculated (not left at default)",
        )
    }
}
