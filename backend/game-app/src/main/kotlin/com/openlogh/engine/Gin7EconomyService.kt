package com.openlogh.engine

import com.openlogh.engine.economy.BillFormula
import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.service.MapService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Gin7 경제 파이프라인 — Phase 4 구현.
 *
 * 처리 내용:
 *  1. 세금 징수 (month=1,4,7,10 — 90일 주기): commerce 기반 세수 → faction.funds 증가
 *  2. approval 조정: taxRate > 30이면 하락, taxRate < 30이면 상승
 *  3. 행성 자원 성장 (매월): population +0.5%, production/commerce +0.3% (상한값 적용)
 *  4. 반기 유지비 감소 (processSemiAnnual, Plan 23-02): 진영/제독 자산에 progressive
 *     bracket decay 부과 (자금 축적 억제). 자원별 분리 스케줄: 월 1 = 자금, 월 7 = 물자.
 *
 * 고립 행성(supplyState=0)은 세금 징수에서 제외되며 자원 성장도 없다.
 */
@Service
class Gin7EconomyService @Autowired constructor(
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository? = null,
    private val mapService: MapService? = null,
) {
    /**
     * Secondary constructor for legacy 2-arg test call sites that do not exercise
     * any officer-touching paths. `processSemiAnnual` (Plan 23-02) requires an
     * `OfficerRepository` because officer personal stockpiles are part of the
     * semi-annual decay pipeline (upstream a7a19cc3 ProcessSemiAnnual.php:89-91).
     * `updatePlanetSupplyState` (Plan 23-06) additionally requires a `MapService`
     * for map-connectivity BFS.
     *
     * Production wiring resolves all four dependencies via Spring DI; this
     * overload exists purely to preserve source compatibility for sibling Phase 23
     * plans and pre-23 tests that only exercise `processMonthly` / `processWarIncome`.
     */
    constructor(
        factionRepository: FactionRepository,
        planetRepository: PlanetRepository,
    ) : this(factionRepository, planetRepository, null, null)

    /**
     * Three-arg overload for sibling Phase 23 plans (23-01, 23-02, 23-03) that
     * exercise officer-touching paths but not `updatePlanetSupplyState`.
     */
    constructor(
        factionRepository: FactionRepository,
        planetRepository: PlanetRepository,
        officerRepository: OfficerRepository?,
    ) : this(factionRepository, planetRepository, officerRepository, null)

    private val logger = LoggerFactory.getLogger(Gin7EconomyService::class.java)

    companion object {
        /**
         * Faction rank thresholds — count of planets with `level >= 4` required to reach
         * each rank level. Ported from legacy `EconomyService.NATION_LEVEL_THRESHOLDS`
         * (upstream a7a19cc3~1). 10 levels total.
         *
         * ```
         *   index (rank level) → min high-level planet count
         *   0 → 0   (방랑군)
         *   1 → 1   (도위)
         *   2 → 2   (주자사)
         *   3 → 4   (주목)
         *   4 → 6   (중랑장)
         *   5 → 9   (대장군)
         *   6 → 12  (대사마)
         *   7 → 16  (공)
         *   8 → 20  (왕)
         *   9 → 25  (황제)
         * ```
         */
        val FACTION_RANK_THRESHOLDS: IntArray = intArrayOf(0, 1, 2, 4, 6, 9, 12, 16, 20, 25)

        /**
         * Faction rank names — ported verbatim from legacy `EconomyService.NATION_LEVEL_NAME`
         * for OpenSamguk parity. Plan 23-10 will layer LOGH-specific rank titles from
         * CLAUDE.md's Empire/Alliance rank tables alongside history logging.
         */
        val FACTION_RANK_NAME: Array<String> = arrayOf(
            "방랑군", "도위", "주자사", "주목", "중랑장", "대장군", "대사마", "공", "왕", "황제",
        )

        /** Legacy parity helper — returns `"???"` for out-of-range levels. */
        fun getFactionRankName(level: Int): String =
            FACTION_RANK_NAME.getOrElse(level) { "???" }
    }

    /**
     * 월별 경제 처리 진입점.
     * TickEngine.runMonthlyPipeline()에서 호출된다.
     */
    @Transactional
    fun processMonthly(world: SessionState) {
        val sessionId = world.id.toLong()
        val month = world.currentMonth

        val factions = factionRepository.findBySessionId(sessionId)
        val planets = planetRepository.findBySessionId(sessionId)

        val planetsByFaction = planets.groupBy { it.factionId }

        // 1. 세금 징수 (월=1,4,7,10에만)
        var totalRevenue = 0
        if (isTaxMonth(month)) {
            for (faction in factions) {
                if (faction.id == 0L) continue
                val factionPlanets = planetsByFaction[faction.id] ?: continue
                val suppliedPlanets = factionPlanets.filter { it.factionId != 0L && it.supplyState.toInt() == 1 }

                // 세수 계산: sum(planet.commerce * taxRate / 100)
                val taxRevenue = suppliedPlanets.sumOf { planet ->
                    planet.commerce * faction.taxRate.toInt() / 100
                }

                faction.funds += taxRevenue
                totalRevenue += taxRevenue

                // approval 조정: taxRate > 30이면 하락, taxRate < 30이면 상승
                val taxRateInt = faction.taxRate.toInt()
                for (planet in suppliedPlanets) {
                    val delta = when {
                        taxRateInt > 30 -> -((taxRateInt - 30) * 0.5f)
                        taxRateInt < 30 -> (30 - taxRateInt) * 0.3f
                        else -> 0f
                    }
                    planet.approval = (planet.approval + delta).coerceIn(0f, 100f)
                }
            }

            factionRepository.saveAll(factions)
        }

        // 2. 행성 자원 성장 (매월, supplyState=1인 행성만)
        for (planet in planets) {
            if (planet.supplyState.toInt() != 1) continue

            planet.population = (planet.population * 1.005).toInt()
                .coerceAtMost(planet.populationMax)
            planet.production = (planet.production * 1.003).toInt()
                .coerceAtMost(planet.productionMax)
            planet.commerce = (planet.commerce * 1.003).toInt()
                .coerceAtMost(planet.commerceMax)
        }

        planetRepository.saveAll(planets)

        logger.info(
            "[World {}] 세수 처리: {} 진영, 총 {} 자금 징수 (month={})",
            world.id, factions.size, totalRevenue, month
        )
    }

    /**
     * 세금 징수 월 여부 확인 (1, 4, 7, 10 — 분기마다 = 90일 주기).
     */
    fun isTaxMonth(month: Short): Boolean = month.toInt() in setOf(1, 4, 7, 10)

    /**
     * Per-resource income event entry point — upstream a7a19cc3 parity.
     *
     * Legacy source: `com.opensam.engine.EconomyService.processIncome(world, nations, cities, generals, resourceType)`.
     * Scenario events call this on a strict per-resource schedule to avoid the upstream 12x drain bug:
     *   - `["ProcessIncome", "gold"]` runs in **month 1** → mutates `faction.funds` only
     *   - `["ProcessIncome", "rice"]` runs in **month 7** → mutates `faction.supplies` only
     *
     * The wire literal is `"gold"` / `"rice"` (OpenSamguk convention). Internally this
     * maps to LOGH's `faction.funds` / `faction.supplies` so imported legacy event JSON
     * works without translation (see Phase 22-03 D-01 decision).
     *
     * Calculation (gin7 narrow port, Phase 23-01 scope):
     *   - gold: Σ(planet.commerce × faction.taxRate / 100) over supplied planets → funds
     *   - rice: Σ(planet.production) over supplied planets → supplies
     *
     * Isolated planets (`supplyState == 0`) are excluded. Salary outlay / semi-annual decay
     * / faction-rank updates are deliberately out of scope — sibling plans 23-02..23-10 own
     * those per the Phase 23 CONTEXT.md plan breakdown.
     *
     * CRITICAL: This method MUST NOT be called every month. It is designed to run
     * ONCE per year per resource (gold in Jan, rice in Jul).
     *
     * @param world the active session
     * @param resource "gold" (→ funds) or "rice" (→ supplies)
     * @throws IllegalArgumentException if resource is anything other than "gold" or "rice"
     */
    @Transactional
    fun processIncome(world: SessionState, resource: String) {
        require(resource == "gold" || resource == "rice") {
            "Invalid resource for processIncome: $resource (expected 'gold' or 'rice')"
        }
        val sessionId = world.id.toLong()

        val factions = factionRepository.findBySessionId(sessionId)
        if (factions.isEmpty()) {
            logger.debug("[World {}] processIncome({}): no factions, no-op", world.id, resource)
            return
        }

        val planets = planetRepository.findBySessionId(sessionId)
        val planetsByFaction = planets.groupBy { it.factionId }

        val isGold = resource == "gold"
        var totalDelta = 0

        for (faction in factions) {
            if (faction.id == 0L) continue
            val factionPlanets = planetsByFaction[faction.id] ?: continue
            val suppliedPlanets = factionPlanets.filter { it.supplyState.toInt() == 1 }
            if (suppliedPlanets.isEmpty()) continue

            val delta = if (isGold) {
                // gold: Σ(commerce × taxRate / 100) → funds
                val taxRate = faction.taxRate.toInt()
                suppliedPlanets.sumOf { planet -> planet.commerce * taxRate / 100 }
            } else {
                // rice: Σ(production) → supplies
                suppliedPlanets.sumOf { planet -> planet.production }
            }

            if (isGold) faction.funds += delta else faction.supplies += delta
            totalDelta += delta
        }

        factionRepository.saveAll(factions)

        // Plan 23-04: salary outlay runs only on the gold branch (month 1 schedule).
        // Officer salaries are paid in funds, not supplies, so the rice branch
        // never triggers the outlay step. Guarded on non-null officerRepository
        // to preserve the 2-arg legacy test path (Plan 23-02 precedent).
        var totalSalariesPaid = 0
        val officerRepo = officerRepository
        if (isGold && officerRepo != null) {
            val allOfficers = officerRepo.findBySessionId(sessionId)
            val officersByFaction = allOfficers.groupBy { it.factionId }
            for (faction in factions) {
                if (faction.id == 0L) continue
                val factionOfficers = officersByFaction[faction.id] ?: continue
                if (factionOfficers.isEmpty()) continue
                totalSalariesPaid += payOfficerSalaries(world, faction, factionOfficers)
            }
            factionRepository.saveAll(factions)
            if (allOfficers.isNotEmpty()) officerRepo.saveAll(allOfficers)
        }

        logger.info(
            "[World {}] processIncome({}): {} factions, total delta={}, salaries={}",
            world.id, resource, factions.size, totalDelta, totalSalariesPaid,
        )
    }

    /**
     * Faction → officer monthly salary transfer — Phase 23-04.
     *
     * Legacy reference: upstream opensamguk inlines this loop inside
     * `com.opensam.engine.EconomyService.processIncome(world, …, "gold")`. The
     * LOGH port extracts it as its own public method so it can be unit-tested
     * in isolation and composed by sibling scheduled events.
     *
     * ### Formula (Phase 22-01 parity)
     *
     * Per active officer (`npcState.toInt() != 5`):
     * ```
     *   individualSalary = BillFormula.fromDedication(officer.dedication) * faction.taxRate / 100
     *   faction.funds   -= individualSalary
     *   officer.funds   += individualSalary
     * ```
     *
     * The returned `totalPaid` is the sum of `individualSalary` across active
     * officers — equal to `-(faction.funds delta)` by conservation.
     *
     * ### Legacy semantics preserved
     *
     * - **Inactive officers excluded** — `npcState == 5` (graveyard sentinel)
     *   officers do not contribute to the outlay and are not paid. Matches
     *   Phase 22-01 `FactionAI.adjustTaxAndBill` filter.
     * - **Negative funds allowed** — legacy PHP
     *   `hwe/sammo/Event/Action/ProcessIncome.php` does not guard against
     *   overdraft. Faction.funds can go below zero; NPC `FactionAI` recovers
     *   via `adjustTaxAndBill` / disband cycles on subsequent ticks.
     * - **Formula authority** — [BillFormula.fromDedication] is the single
     *   source of truth shared with Phase 22-01 `FactionAI.getBillFromDedication`.
     *
     * ### Not persisted here
     *
     * Callers are responsible for persisting the mutated `faction` and
     * `officers` via their respective repositories. When invoked from
     * `processIncome(world, "gold")`, both `factionRepository.saveAll` and
     * `officerRepository.saveAll` are called once after all factions are
     * processed, so this method is a pure in-memory transform.
     *
     * @param world the active session (reserved for logging context)
     * @param faction the payer — `faction.funds` is decremented by `totalPaid`
     * @param officers candidate recipients — inactive entries are filtered out
     * @return `totalPaid` — sum of individual salaries across active officers
     */
    fun payOfficerSalaries(
        world: SessionState,
        faction: Faction,
        officers: List<Officer>,
    ): Int {
        val taxRate = faction.taxRate.toInt()
        var totalPaid = 0
        for (officer in officers) {
            if (officer.npcState.toInt() == 5) continue // graveyard — skip
            val bill = BillFormula.fromDedication(officer.dedication)
            val individualSalary = bill * taxRate / 100
            officer.funds += individualSalary
            totalPaid += individualSalary
        }
        faction.funds -= totalPaid
        return totalPaid
    }

    /**
     * War income — upstream a7a19cc3 `com.opensam.engine.EconomyService.processWarIncome` port.
     *
     * Unlike tax collection (month 1/4/7/10 only), war income is paid **every month** via the
     * scenario pre_month event `["ProcessWarIncome"]`. It models casualty salvage:
     *   - For each planet with `dead > 0`:
     *       - The owning faction gains `dead / 10` funds (material/ship salvage)
     *       - The planet recovers `(dead * 0.2)` population, capped by headroom to populationMax
     *       - `planet.dead` is reset to 0
     *
     * Legacy PHP source: `hwe/sammo/Event/Action/ProcessWarIncome.php`.
     *
     * **Note on the 23-03 plan description vs. upstream body:**
     * Plan 23-03 described a "factions with warState > 0 receive a bonus" filter, but the
     * actual upstream Kotlin body (see a7a19cc3 opensam `EconomyService.kt`) has no such
     * warState gate — it iterates every city with `dead > 0`. This implementation follows
     * the upstream body faithfully; the gate is `planet.dead > 0`, not `faction.warState`.
     * Documented as Rule 1 (plan-vs-reality correction) in Plan 23-03 SUMMARY deviations.
     *
     * Domain mapping (삼국지 → LOGH):
     *   - `Nation.gold` → `Faction.funds`
     *   - `City.dead` → `Planet.dead` (casualty count)
     *   - `City.pop` → `Planet.population`
     *   - `City.popMax` → `Planet.populationMax`
     *
     * Takes no resource parameter: war income is always paid in funds.
     *
     * @param world the active session
     */
    @Transactional
    fun processWarIncome(world: SessionState) {
        val sessionId = world.id.toLong()

        val factions = factionRepository.findBySessionId(sessionId)
        val planets = planetRepository.findBySessionId(sessionId)

        if (planets.isEmpty()) {
            logger.debug("[World {}] processWarIncome: no planets, no-op", world.id)
            return
        }

        val factionMap = factions.associateBy { it.id }
        var totalFundsCredited = 0
        var totalPopulationRecovered = 0
        var payoutCount = 0

        for (planet in planets) {
            if (planet.dead <= 0) continue
            val faction = factionMap[planet.factionId] ?: continue

            // Legacy formula: nation.gold += city.dead / 10
            val fundsGain = planet.dead / 10
            faction.funds += fundsGain
            totalFundsCredited += fundsGain

            // Legacy formula: popGain = (city.dead * 0.2).toInt().coerceAtMost(headroom)
            // headroom = (popMax - pop).coerceAtLeast(0)
            val uncappedPopGain = (planet.dead * 0.2).toInt()
            val headroom = (planet.populationMax - planet.population).coerceAtLeast(0)
            val popGain = uncappedPopGain.coerceAtMost(headroom)
            planet.population += popGain
            totalPopulationRecovered += popGain

            // Clear casualty counter after payout
            planet.dead = 0
            payoutCount++
        }

        if (payoutCount > 0) {
            factionRepository.saveAll(factions)
            planetRepository.saveAll(planets)
        }

        logger.info(
            "[World {}] processWarIncome: {} planets paid, funds+={}, pop+={}",
            world.id, payoutCount, totalFundsCredited, totalPopulationRecovered,
        )
    }

    /**
     * Semi-annual upkeep decay — upstream a7a19cc3 `com.opensam.engine.EconomyService.processSemiAnnual` port.
     *
     * Applies a progressive-bracket decay to faction treasury AND officer personal
     * stockpiles for **one resource only**, on the legacy month 1 (funds) / month 7
     * (supplies) schedule. Called exclusively via the scenario event
     * `["ProcessSemiAnnual", "gold"|"rice"]` — **must NOT be called monthly**,
     * else the upstream 4x-decay drain bug reappears.
     *
     * ### Decay brackets (Legacy `ProcessSemiAnnual.php:89-96`)
     *
     * **Officer** (per-person upkeep, strict > threshold):
     * ```
     *   > 10_000 → × 0.97  (3% decay)
     *   > 1_000  → × 0.99  (1% decay)
     *   ≤ 1_000  → unchanged  (below-threshold protection)
     * ```
     *
     * **Faction** treasury (progressive — larger stockpiles decay faster to discourage hoarding):
     * ```
     *   > 100_000 → × 0.95  (5% decay)
     *   > 10_000  → × 0.97  (3% decay)
     *   > 1_000   → × 0.99  (1% decay)
     *   ≤ 1_000   → unchanged
     * ```
     *
     * ### Per-resource isolation contract
     *
     * `resource = "gold"` mutates **only** `faction.funds` and `officer.funds`.
     * `resource = "rice"` mutates **only** `faction.supplies` and `officer.supplies`.
     * This is the upstream a7a19cc3 fix: the pre-fix version decayed both resources
     * per call AND was triggered from both `postUpdateMonthly` and the scenario
     * event, yielding 4x decay per year.
     *
     * Wire literals are OpenSamguk convention (`"gold"` / `"rice"`) to keep imported
     * legacy event JSON working without translation. Internally they map to
     * `faction.funds` / `faction.supplies` and `officer.funds` / `officer.supplies`.
     *
     * ### Domain mapping (삼국지 → LOGH)
     *
     * | Legacy         | LOGH              |
     * |----------------|-------------------|
     * | `Nation.gold`  | `Faction.funds`   |
     * | `Nation.rice`  | `Faction.supplies`|
     * | `General.gold` | `Officer.funds`   |
     * | `General.rice` | `Officer.supplies`|
     *
     * If the constructor was resolved without an `OfficerRepository` (legacy 2-arg
     * test path), the officer-decay step is silently skipped with a warning log —
     * production wiring always supplies the repository.
     *
     * @param world the active session
     * @param resource `"gold"` (→ funds decay) or `"rice"` (→ supplies decay)
     * @throws IllegalArgumentException if `resource` is anything other than `"gold"` or `"rice"`
     */
    @Transactional
    fun processSemiAnnual(world: SessionState, resource: String) {
        require(resource == "gold" || resource == "rice") {
            "Invalid resource for processSemiAnnual: $resource (expected 'gold' or 'rice')"
        }
        val isGold = resource == "gold"
        val sessionId = world.id.toLong()

        // 1. Faction treasury decay — progressive bracket (legacy ProcessSemiAnnual.php:94-96)
        val factions = factionRepository.findBySessionId(sessionId)
        for (faction in factions) {
            if (isGold) {
                faction.funds = applyFactionBracket(faction.funds)
            } else {
                faction.supplies = applyFactionBracket(faction.supplies)
            }
        }
        if (factions.isNotEmpty()) {
            factionRepository.saveAll(factions)
        }

        // 2. Officer personal stockpile decay — two-bracket (legacy ProcessSemiAnnual.php:89-91)
        //    Officers with resource ≤ 1000 are protected (below-threshold).
        val officerRepo = officerRepository
        if (officerRepo == null) {
            logger.warn(
                "[World {}] processSemiAnnual({}): officerRepository not wired — skipping officer decay. " +
                    "Legacy 2-arg constructor path. Production wiring must supply OfficerRepository.",
                world.id, resource,
            )
        } else {
            val officers = officerRepo.findBySessionId(sessionId)
            for (officer in officers) {
                if (isGold) {
                    officer.funds = applyOfficerBracket(officer.funds)
                } else {
                    officer.supplies = applyOfficerBracket(officer.supplies)
                }
            }
            if (officers.isNotEmpty()) {
                officerRepo.saveAll(officers)
            }
        }

        logger.info(
            "[World {}] processSemiAnnual({}): {} factions, {} officers decayed",
            world.id, resource, factions.size,
            if (officerRepo == null) 0 else officerRepo.findBySessionId(sessionId).size,
        )
    }

    /**
     * Progressive faction-treasury decay bracket.
     * Legacy: IF(%b > 100000, %b*0.95, IF(%b > 10000, %b*0.97, IF(%b > 1000, %b*0.99, %b)))
     */
    private fun applyFactionBracket(value: Int): Int = when {
        value > 100_000 -> (value * 0.95).toInt()
        value > 10_000 -> (value * 0.97).toInt()
        value > 1_000 -> (value * 0.99).toInt()
        else -> value
    }

    /**
     * Two-bracket officer personal upkeep decay.
     * Legacy: IF(%b > 10000, %b*0.97, %b*0.99) WHERE %b > 1000
     * Strict greater-than: exactly 1000 is protected.
     */
    private fun applyOfficerBracket(value: Int): Int = when {
        value > 10_000 -> (value * 0.97).toInt()
        value > 1_000 -> (value * 0.99).toInt()
        else -> value
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Plan 23-06: Planet supply state (map-connectivity based)
    //
    // Moved from legacy `EconomyService.updateCitySupplyState` +
    // private `updateCitySupply` helper (EconomyService.kt:134-353).
    // The BFS algorithm is unchanged; only the internal variable names were
    // renamed for LOGH domain consistency:
    //   cities  → planets
    //   nations → factions
    //   generals → officers
    // Legacy `EconomyService.updateCitySupplyState` now delegates to this.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Recalculates `Planet.supplyState` for every planet in the world.
     *
     * Algorithm:
     *   1. Neutral planets (`factionId = 0`) are always supplied.
     *   2. For each faction, BFS from its capital planet through adjacent
     *      same-faction planets via `mapService.getAdjacentCities(mapCode, mapPlanetId)`.
     *   3. Planets reachable from the capital → `supplyState = 1` (supplied).
     *   4. Planets NOT reached → `supplyState = 0` (isolated) and suffer
     *      10% decay on population / approval / production / commerce / security /
     *      orbital_defense / fortress. Officers stationed on isolated planets
     *      lose 5% ships / morale / training.
     *   5. Isolated planets below `approval < 30` (and not a capital) defect
     *      to neutral (`factionId = 0`).
     *
     * Called by `TurnService` (traffic update) every tick, and by
     * `UpdateCitySupplyAction` scheduled event.
     *
     * If `mapService` is null (test-only legacy constructors), the method
     * silently degrades: no planets will be marked isolated, and the method
     * acts as a defensive no-op. Production wiring always supplies the
     * MapService via Spring DI.
     *
     * @param world the active session
     */
    @Transactional
    fun updatePlanetSupplyState(world: SessionState) {
        val sessionId = world.id.toLong()
        val planets = planetRepository.findBySessionId(sessionId)
        val factions = factionRepository.findBySessionId(sessionId)
        val officerRepo = officerRepository
        val officers = officerRepo?.findBySessionId(sessionId) ?: emptyList()

        updatePlanetSupply(world, factions, planets, officers)

        if (planets.isNotEmpty()) planetRepository.saveAll(planets)
        if (officerRepo != null && officers.isNotEmpty()) officerRepo.saveAll(officers)
    }

    /**
     * Private BFS helper — direct move of legacy `EconomyService.updateCitySupply`.
     * Internal vars renamed to LOGH domain (cities→planets, nations→factions,
     * generals→officers) but the control flow is identical.
     */
    private fun updatePlanetSupply(
        world: SessionState,
        factions: List<Faction>,
        planets: List<Planet>,
        officers: List<Officer>,
    ) {
        val mapCode = (world.config["mapCode"] as? String) ?: "logh"
        val planetsByFaction = planets.groupBy { it.factionId }
        val planetById = planets.associateBy { it.id }
        val officersByPlanet = officers.groupBy { it.planetId }

        // Build map mapPlanetId <-> DB planetId lookups
        val dbToMapId = planets.associate { it.id to it.mapPlanetId }
        val mapToDbId = planets.associate { it.mapPlanetId to it.id }

        // Neutral planets are always supplied
        for (planet in planets) {
            if (planet.factionId == 0L) {
                planet.supplyState = 1
            }
        }

        val mapSvc = mapService
        if (mapSvc == null) {
            logger.warn(
                "[World {}] updatePlanetSupplyState: mapService not wired — skipping BFS. " +
                    "Legacy test constructor path. Production wiring must supply MapService.",
                world.id,
            )
            return
        }

        for (faction in factions) {
            val factionPlanets = planetsByFaction[faction.id] ?: continue
            val suppliedMapIds = mutableSetOf<Int>()

            val capitalId = faction.capitalPlanetId
            val capitalMapId = if (capitalId != null) dbToMapId[capitalId] else null
            if (capitalMapId != null && capitalMapId != 0) {
                val queue = ArrayDeque<Int>()
                queue.add(capitalMapId)
                suppliedMapIds.add(capitalMapId)

                while (queue.isNotEmpty()) {
                    val currentMapId = queue.removeFirst()
                    val adjacentMapIds = try {
                        mapSvc.getAdjacentCities(mapCode, currentMapId)
                    } catch (e: Exception) {
                        logger.warn("Failed to get adjacent cities for mapPlanetId={}: {}", currentMapId, e.message)
                        emptyList()
                    }
                    for (adjMapId in adjacentMapIds) {
                        if (adjMapId !in suppliedMapIds) {
                            val adjDbId = mapToDbId[adjMapId]
                            val adjPlanet = if (adjDbId != null) planetById[adjDbId] else null
                            if (adjPlanet != null && adjPlanet.factionId == faction.id) {
                                suppliedMapIds.add(adjMapId)
                                queue.add(adjMapId)
                            }
                        }
                    }
                }
            }

            val supplied = suppliedMapIds.mapNotNull { mapToDbId[it] }.toMutableSet()
            if (capitalId != null) supplied.add(capitalId)

            for (planet in factionPlanets) {
                if (planet.id in supplied) {
                    planet.supplyState = 1
                } else {
                    planet.supplyState = 0
                    planet.population = (planet.population * 0.9).toInt()
                    planet.approval = planet.approval * 0.9F
                    planet.production = (planet.production * 0.9).toInt()
                    planet.commerce = (planet.commerce * 0.9).toInt()
                    planet.security = (planet.security * 0.9).toInt()
                    planet.orbitalDefense = (planet.orbitalDefense * 0.9).toInt()
                    planet.fortress = (planet.fortress * 0.9).toInt()

                    val planetOfficers = officersByPlanet[planet.id] ?: emptyList()
                    for (officer in planetOfficers) {
                        officer.ships = (officer.ships * 0.95).toInt()
                        officer.morale = (officer.morale * 0.95).toInt().coerceIn(0, 150).toShort()
                        officer.training = (officer.training * 0.95).toInt().coerceIn(0, 110).toShort()
                    }

                    if (planet.approval < 30 && planet.id != faction.capitalPlanetId) {
                        logger.info("Planet {} (id={}) lost to isolation (approval={})", planet.name, planet.id, planet.approval)
                        for (officer in planetOfficers) {
                            if (officer.officerPlanet == planet.id.toInt()) {
                                officer.officerLevel = 1
                                officer.officerPlanet = 0
                            }
                        }
                        planet.factionId = 0
                        planet.officerSet = 0
                        planet.conflict = mutableMapOf()
                        planet.term = 0
                        planet.frontState = 0
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Plan 23-05: Faction rank update (legacy updateNationLevelEvent)
    //
    // Ports upstream `com.opensam.engine.EconomyService.updateNationLevel` (last seen
    // intact in commit `a7a19cc3~1`) into LOGH's Gin7EconomyService. The rank level
    // is derived from the count of planets with `level >= 4` owned by each faction.
    // Unlike the upstream body (which only promotes), this LOGH port writes the new
    // level unconditionally so factions can rank DOWN when they lose high-level
    // planets — per Plan 23-05 acceptance criterion. History logging, level-up gold
    // rewards, and inheritance point accrual are deferred to Plan 23-10 cleanup.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Recalculates `Faction.factionRank` for every non-neutral faction in the world.
     *
     * Algorithm:
     *   1. Neutral faction (`id = 0`) is skipped.
     *   2. For each faction, count owned planets with `level >= 4` → `highCount`.
     *   3. Walk `FACTION_RANK_THRESHOLDS` and pick the highest index whose threshold
     *      is `<= highCount`. That index becomes the new rank level.
     *   4. Write `faction.factionRank = newLevel.coerceIn(0, 9).toShort()`
     *      **unconditionally** — supports both rank-up and rank-down.
     *
     * Triggered annually via scenario event (legacy month 1 / Jan 1) or by the
     * `UpdateNationLevelAction` event bridge. See Plan 23-10 for pipeline wire-up.
     *
     * @param world the active session
     */
    @Transactional
    fun updateFactionRank(world: SessionState) {
        val sessionId = world.id.toLong()
        val factions = factionRepository.findBySessionId(sessionId)
        if (factions.isEmpty()) {
            logger.debug("[World {}] updateFactionRank: no factions, no-op", world.id)
            return
        }
        val planets = planetRepository.findBySessionId(sessionId)
        val planetsByFaction = planets.groupBy { it.factionId }

        var mutations = 0
        for (faction in factions) {
            if (faction.id == 0L) continue // skip neutral
            val factionPlanets = planetsByFaction[faction.id] ?: emptyList()
            val highCount = factionPlanets.count { it.level.toInt() >= 4 }

            // Walk thresholds ascending; last threshold that fits becomes new level.
            var newLevel = 0
            for (level in FACTION_RANK_THRESHOLDS.indices) {
                if (highCount >= FACTION_RANK_THRESHOLDS[level]) {
                    newLevel = level
                }
            }

            val clamped = newLevel.coerceIn(0, 9).toShort()
            if (faction.factionRank != clamped) {
                val oldName = getFactionRankName(faction.factionRank.toInt())
                val newName = getFactionRankName(clamped.toInt())
                val direction = if (clamped > faction.factionRank) "승격" else "강등"
                logger.info(
                    "[World {}] Faction {} (id={}) 등급 {} → {} ({} → {}, highCount={})",
                    world.id, faction.name, faction.id, direction,
                    oldName, newName, highCount,
                )
                faction.factionRank = clamped
                mutations++
            }
        }

        if (mutations > 0) {
            factionRepository.saveAll(factions)
        }
        logger.info(
            "[World {}] updateFactionRank: {} factions scanned, {} rank changes",
            world.id, factions.size, mutations,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Plan 23-08: processDisasterOrBoom (disaster/boom event generator)
    //
    // Ports legacy `EconomyService.processDisasterOrBoom` (EconomyService.kt:484-644)
    // and its private `DisasterOrBoomEntry` data class (EconomyService.kt:98-102) into
    // Gin7EconomyService. This is the largest single method in the legacy economy
    // pipeline (~164 lines). Domain names were already LOGH-mapped in the legacy copy
    // (city→planet, nation→faction, pop→population, agri→production, comm→commerce,
    // trust→approval); this port preserves the exact probability table + affectRatio
    // formulas + Korean event templates verbatim.
    //
    // **Deviations from legacy**:
    //   1. RNG: Gin7 port accepts a `kotlin.random.Random` instance as an optional
    //      method parameter instead of using `DeterministicRng.create(hiddenSeed, ...)`.
    //      This lets tests inject stub RNGs (AlwaysZeroRandom / AlwaysHighRandom) and
    //      seeded Randoms without reaching through the config map. Production callers
    //      omit the parameter and get a `Random.Default` instance.
    //   2. History logging + officer injury messages: legacy calls `historyService.logWorldHistory`
    //      and writes per-officer injury Messages. Neither `HistoryService` nor
    //      `MessageRepository` is wired into `Gin7EconomyService` (Plan 23-10 will wire
    //      the event broadcast bus). This port preserves the Korean title/body templates
    //      in the entry table and logs via `logger.info` with a TODO marker.
    //   3. `officerRepository.findBySessionIdAndPlanetIdIn` officer injury loop is
    //      preserved behind a null-guard on `officerRepository` — sibling Phase 23
    //      plans already wire this repository, but older 2-arg test ctors skip it.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Single disaster/boom entry — ported verbatim from legacy `EconomyService.DisasterOrBoomEntry`.
     * `stateCode` becomes `Planet.state` for the duration of the event window.
     * `title` + `body` are Korean flavor strings for history logging.
     *
     * State code ranges (legacy convention, preserved):
     *   - 1..10  → transient disaster/boom (cleared at top of next cycle)
     *   - 11+    → persistent (not cleared; not yet used)
     */
    private data class DisasterOrBoomEntry(
        val stateCode: Short,
        val title: String,
        val body: String,
    )

    /**
     * Processes a single disaster-or-boom event cycle for the active session.
     *
     * Algorithm (verbatim legacy port):
     *   1. Skip the first 3 years after session start
     *      (`startYear + 3 > currentYear` → no-op).
     *   2. Reset every planet's `state` to 0 if it was in a transient code (<= 10).
     *   3. Roll the boom probability: months 4/7 have a 25% chance of being a boom
     *      cycle; other months are always disaster cycles (`boomRate = 0`).
     *   4. Per-planet probability:
     *       - boom:     `0.02 + secuRatio * 0.05`     (2%..7%)
     *       - disaster: `0.06 - secuRatio * 0.05`     (1%..6%)
     *      where `secuRatio = security / securityMax`.
     *   5. For targeted planets, apply `affectRatio` to population/approval/production/
     *      commerce/security/orbitalDefense/fortress:
     *       - boom:     `1.01 + min(secuRatio/0.8, 1)*0.04`   (1.01..1.05), coerced to max
     *       - disaster: `0.80 + min(secuRatio/0.8, 1)*0.15`   (0.80..0.95)
     *   6. Write `planet.state` to the selected entry's `stateCode`.
     *   7. Emit a history log line (TODO: Plan 23-10 will wire the event bus).
     *
     * @param world the active session
     * @param rng   optional RNG for test determinism — defaults to `Random.Default`
     */
    @Transactional
    fun processDisasterOrBoom(world: SessionState, rng: kotlin.random.Random = kotlin.random.Random.Default) {
        val sessionId = world.id.toLong()

        val startYear = try {
            (world.config["startYear"] as? Number)?.toInt() ?: world.currentYear.toInt()
        } catch (e: Exception) {
            logger.warn("Failed to resolve startYear from config: {}", e.message)
            world.currentYear.toInt()
        }

        // Skip first 3 years
        if (startYear + 3 > world.currentYear.toInt()) {
            logger.debug(
                "[World {}] processDisasterOrBoom: skipped ({}+3 > {})",
                world.id, startYear, world.currentYear,
            )
            return
        }

        val planets = planetRepository.findBySessionId(sessionId)
        if (planets.isEmpty()) return

        val month = world.currentMonth.toInt()

        // Step 1: Reset transient disaster state on every planet
        for (planet in planets) {
            if (planet.state <= 10) {
                planet.state = 0
            }
        }

        // Step 2: Boom probability by month (4,7 = 25%, others = 0)
        val boomRate = when (month) {
            4, 7 -> 0.25
            else -> 0.0
        }
        val isGood = boomRate > 0 && rng.nextDouble() < boomRate

        // Step 3: Per-planet targeting roll
        val targetPlanets = mutableListOf<Planet>()
        for (planet in planets) {
            val secuRatio = if (planet.securityMax > 0) {
                planet.security.toDouble() / planet.securityMax
            } else 0.0
            val raiseProp = if (isGood) {
                0.02 + secuRatio * 0.05  // 2~7%
            } else {
                0.06 - secuRatio * 0.05  // 1~6%
            }
            if (rng.nextDouble() < raiseProp) {
                targetPlanets.add(planet)
            }
        }

        if (targetPlanets.isEmpty()) {
            // State-reset pass still needs to persist
            planetRepository.saveAll(planets)
            return
        }

        // Step 4: Entry table — Korean flavor text preserved verbatim from legacy.
        // LOGH re-skinning deferred to a later phase; state codes match legacy numbers.
        val disasterEntries = mapOf(
            1 to listOf(
                DisasterOrBoomEntry(4, "【재난】", "역병이 발생하여 행성이 황폐해지고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "항성 폭풍으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(3, "【재난】", "에너지 부족으로 주민들이 고통받고 있습니다."),
                DisasterOrBoomEntry(9, "【재난】", "반란군이 출현해 행성을 습격하고 있습니다."),
            ),
            4 to listOf(
                DisasterOrBoomEntry(7, "【재난】", "우주 방사선으로 인해 피해가 급증하고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "항성 폭풍으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(6, "【재난】", "소행성 충돌로 인해 피해가 속출하고 있습니다."),
            ),
            7 to listOf(
                DisasterOrBoomEntry(8, "【재난】", "자원 고갈로 인해 행성이 황폐해지고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "항성 폭풍으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(8, "【재난】", "흉작으로 굶어죽는 주민들이 늘어나고 있습니다."),
            ),
            10 to listOf(
                DisasterOrBoomEntry(3, "【재난】", "혹한 행성 환경으로 행성이 황폐해지고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "항성 폭풍으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(3, "【재난】", "함대 봉쇄로 인해 행성이 황폐해지고 있습니다."),
                DisasterOrBoomEntry(9, "【재난】", "반란군이 출현해 행성을 습격하고 있습니다."),
            ),
        )
        val boomEntries = mapOf(
            4 to DisasterOrBoomEntry(2, "【호황】", "호황으로 행성이 번창하고 있습니다."),
            7 to DisasterOrBoomEntry(1, "【풍작】", "풍년으로 행성이 번창하고 있습니다."),
        )

        if (isGood) {
            // Step 5a: Boom path — multiply up, coerce to max
            val entry = boomEntries[month] ?: boomEntries[4]!!
            for (planet in targetPlanets) {
                val secuRatio = if (planet.securityMax > 0) {
                    planet.security.toDouble() / planet.securityMax / 0.8
                } else 0.0
                val affectRatio = 1.01 + secuRatio.coerceIn(0.0, 1.0) * 0.04

                planet.state = entry.stateCode
                planet.population = (planet.population * affectRatio).toInt()
                    .coerceAtMost(planet.populationMax)
                planet.approval = (planet.approval * affectRatio.toFloat()).coerceAtMost(100F)
                planet.production = (planet.production * affectRatio).toInt()
                    .coerceAtMost(planet.productionMax)
                planet.commerce = (planet.commerce * affectRatio).toInt()
                    .coerceAtMost(planet.commerceMax)
                planet.security = (planet.security * affectRatio).toInt()
                    .coerceAtMost(planet.securityMax)
                planet.orbitalDefense = (planet.orbitalDefense * affectRatio).toInt()
                    .coerceAtMost(planet.orbitalDefenseMax)
                planet.fortress = (planet.fortress * affectRatio).toInt()
                    .coerceAtMost(planet.fortressMax)
            }

            val planetNames = targetPlanets.joinToString(" ") { it.name }
            // TODO(Plan 23-10): replace with historyService.logWorldHistory once event bus is wired
            logger.info(
                "[World {}] {} {}에 {} (year={}, month={})",
                world.id, entry.title, planetNames, entry.body, world.currentYear, month,
            )
        } else {
            // Step 5b: Disaster path — multiply down
            val entries = disasterEntries[month] ?: disasterEntries[1]!!
            val entry = entries[rng.nextInt(entries.size)]
            for (planet in targetPlanets) {
                val secuRatio = if (planet.securityMax > 0) {
                    planet.security.toDouble() / planet.securityMax / 0.8
                } else 0.0
                val affectRatio = 0.8 + secuRatio.coerceIn(0.0, 1.0) * 0.15

                planet.state = entry.stateCode
                planet.population = (planet.population * affectRatio).toInt()
                planet.approval = planet.approval * affectRatio.toFloat()
                planet.production = (planet.production * affectRatio).toInt()
                planet.commerce = (planet.commerce * affectRatio).toInt()
                planet.security = (planet.security * affectRatio).toInt()
                planet.orbitalDefense = (planet.orbitalDefense * affectRatio).toInt()
                planet.fortress = (planet.fortress * affectRatio).toInt()
            }

            // Officer injury loop — only if officerRepository is wired (non-null)
            val officerRepo = officerRepository
            if (officerRepo != null) {
                val affectedPlanetIds = targetPlanets.map { it.id }
                val officers = try {
                    officerRepo.findBySessionIdAndPlanetIdIn(sessionId, affectedPlanetIds)
                } catch (e: Exception) {
                    logger.warn(
                        "[World {}] findBySessionIdAndPlanetIdIn unavailable — skipping officer injury: {}",
                        world.id, e.message,
                    )
                    emptyList()
                }
                var injuredCount = 0
                for (officer in officers) {
                    if (rng.nextDouble() >= 0.3) continue
                    val injuryAmount = rng.nextInt(1, 17)
                    officer.injury = (officer.injury + injuryAmount).coerceIn(0, 80).toShort()
                    officer.ships = (officer.ships * 0.98).toInt()
                    officer.morale = (officer.morale * 0.98).toInt().coerceIn(0, 150).toShort()
                    officer.training = (officer.training * 0.98).toInt().coerceIn(0, 110).toShort()
                    injuredCount++
                }
                if (injuredCount > 0) {
                    officerRepo.saveAll(officers)
                    // TODO(Plan 23-10): emit per-officer injury Messages via MessageRepository
                    logger.info(
                        "[World {}] disaster injured {} officers (of {} on affected planets)",
                        world.id, injuredCount, officers.size,
                    )
                }
            }

            val planetNames = targetPlanets.joinToString(" ") { it.name }
            // TODO(Plan 23-10): replace with historyService.logWorldHistory once event bus is wired
            logger.info(
                "[World {}] {} {}에 {} (year={}, month={})",
                world.id, entry.title, planetNames, entry.body, world.currentYear, month,
            )
        }

        planetRepository.saveAll(planets)
        logger.info(
            "[World {}] processDisasterOrBoom: month={}, isGood={}, {} planets affected",
            world.id, month, isGood, targetPlanets.size,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Plan 23-07: Annual statistics refresh (militaryPower + officerCount)
    //
    // Ports legacy `EconomyService.processYearlyStatistics` (EconomyService.kt:362-438)
    // into LOGH's Gin7EconomyService. Refreshes each non-neutral faction's
    // `militaryPower` (was `nation.power`) and `officerCount` (was `nation.gennum`)
    // based on current roster, resources, tech level, and supplied-planet aggregates.
    //
    // Triggered annually on Jan 1 via scenario event (pipeline wire-up in Plan 23-10).
    //
    // LOGH deviations from legacy formula (documented in Plan 23-07 SUMMARY):
    //   1. Repository-based access — uses factionRepository/planetRepository/
    //      officerRepository instead of worldPortFactory (matches sibling Gin7 methods).
    //   2. No `dex1..dex5` term — LOGH Officer lacks these fields (ship-class mastery
    //      was not ported). `dexPower = 0` for parity-audit traceability.
    //   3. Officer statPower = sum of all 8 LOGH stats per active officer
    //      (leadership+command+intelligence+politics+administration+mobility+attack+defense),
    //      replacing legacy's `npcMul * leaderCore * 2 + (sqrt(intel*str)*2 + lead/2)/2`.
    //   4. No RNG jitter — legacy applied `DeterministicRng.nextDouble(0.95, 1.05)` as
    //      seed-noise. Deterministic output for testability.
    //   5. Neutral skip uses `id == 0L` (mirrors sibling updateFactionRank / processIncome),
    //      not `factionRank.toInt() == 0` which would also skip valid rank-0 factions.
    //   6. officerCount = active officer count (excludes npcState == 5 graveyard),
    //      matching legacy `gennum` semantics.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Recalculates `Faction.militaryPower` and `Faction.officerCount` for every
     * non-neutral faction in the world.
     *
     * Legacy source: `EconomyService.processYearlyStatistics(world)` (lines 362-438).
     *
     * ### Formula (LOGH-adapted)
     *
     * ```
     *   militaryPower = round((resource + tech + cityPower + statPower + dexPower + expDed) / 10)
     *
     *   resource  = (faction.funds + faction.supplies + Σ(officer.funds + officer.supplies)) / 100
     *   tech      = faction.techLevel
     *   cityPower = if (maxSum > 0) (popSum × valueSum) / maxSum / 100 else 0
     *     popSum    = Σ(planet.population) over supplied planets (supplyState==1)
     *     valueSum  = Σ(pop+prod+comm+sec+fort+orbDef) over supplied planets
     *     maxSum    = Σ(popMax+prodMax+commMax+secMax+fortMax+orbDefMax) over supplied planets
     *   statPower = Σ(leadership+command+intelligence+politics+administration+mobility+attack+defense)
     *               over active officers (npcState != 5)
     *   dexPower  = 0   (LOGH Officer has no dex1..dex5 — dropped from formula)
     *   expDed    = Σ(officer.experience + officer.dedication) / 100
     * ```
     *
     * ### Algorithm
     *
     *   1. Fetch all factions / planets / officers for the session.
     *   2. Group planets and officers by faction.
     *   3. For each faction (skip `id == 0L` neutral):
     *      a. Compute resource / tech / cityPower / statPower / dexPower / expDed.
     *      b. militaryPower = round(sum / 10), coerced >= 0.
     *      c. Record max-power watermark in `faction.meta["maxPower"]` (legacy parity).
     *      d. officerCount = count(officers where npcState != 5).
     *   4. Persist via factionRepository.saveAll.
     *
     * Empty world short-circuits before touching the planet/officer repositories.
     *
     * @param world the active session
     */
    @Transactional
    fun processYearlyStatistics(world: SessionState) {
        val sessionId = world.id.toLong()
        val factions = factionRepository.findBySessionId(sessionId)
        if (factions.isEmpty()) {
            logger.debug("[World {}] processYearlyStatistics: no factions, no-op", world.id)
            return
        }

        val planets = planetRepository.findBySessionId(sessionId)
        val officerRepo = officerRepository
        val officers = officerRepo?.findBySessionId(sessionId) ?: emptyList()

        val planetsByFaction = planets.groupBy { it.factionId }
        val officersByFaction = officers
            .filter { it.npcState.toInt() != 5 }
            .groupBy { it.factionId }

        var mutatedCount = 0
        for (faction in factions) {
            if (faction.id == 0L) continue // skip neutral

            val factionOfficers = officersByFaction[faction.id] ?: emptyList()
            val factionPlanets = planetsByFaction[faction.id] ?: emptyList()

            // 자원: (faction.funds + faction.supplies + Σ(officer.funds + officer.supplies)) / 100
            val officerStockpile = factionOfficers.sumOf { (it.funds + it.supplies).toLong() }
            val resource = ((faction.funds + faction.supplies).toLong() + officerStockpile) / 100.0

            // 기술
            val tech = faction.techLevel.toDouble()

            // 도시파워: sum(pop) * sum(pop+production+commerce+security+fortress+orbitalDefense)
            //           / sum(popMax+productionMax+commerceMax+securityMax+fortressMax+orbitalDefenseMax)
            //           / 100
            // Only supplied planets contribute (supplyState == 1).
            val suppliedPlanets = factionPlanets.filter { it.supplyState.toInt() == 1 }
            val cityPower = if (suppliedPlanets.isNotEmpty()) {
                val popSum = suppliedPlanets.sumOf { it.population.toLong() }
                val valueSum = suppliedPlanets.sumOf {
                    (it.population + it.production + it.commerce + it.security + it.fortress + it.orbitalDefense).toLong()
                }
                val maxSum = suppliedPlanets.sumOf {
                    (it.populationMax + it.productionMax + it.commerceMax + it.securityMax + it.fortressMax + it.orbitalDefenseMax).toLong()
                }
                if (maxSum > 0) (popSum.toDouble() * valueSum) / maxSum / 100.0 else 0.0
            } else 0.0

            // 장수능력: Σ 8-stat sum over active officers (LOGH adaptation)
            val statPower = factionOfficers.sumOf { o ->
                (o.leadership.toInt() + o.command.toInt() + o.intelligence.toInt() +
                    o.politics.toInt() + o.administration.toInt() + o.mobility.toInt() +
                    o.attack.toInt() + o.defense.toInt()).toLong()
            }.toDouble()

            // 숙련: LOGH Officer has no dex1..dex5 → dexPower = 0 (documented deviation)
            val dexPower = 0.0

            // 경험공헌: Σ(experience + dedication) / 100
            val expDed = factionOfficers.sumOf { (it.experience + it.dedication).toLong() } / 100.0

            val rawPower = (resource + tech + cityPower + statPower + dexPower + expDed) / 10.0
            val power = kotlin.math.round(rawPower).toInt().coerceAtLeast(0)

            // 최대 국력 기록 (legacy parity — faction.meta["maxPower"] watermark)
            val prevMaxPower = (faction.meta["maxPower"] as? Number)?.toInt() ?: 0
            if (power > prevMaxPower) {
                faction.meta["maxPower"] = power
            }

            faction.militaryPower = power
            faction.officerCount = factionOfficers.size
            mutatedCount++
        }

        if (mutatedCount > 0) {
            factionRepository.saveAll(factions)
        }
        logger.info(
            "[World {}] processYearlyStatistics: {} factions updated (of {} total)",
            world.id, mutatedCount, factions.size,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Plan 23-09: randomizePlanetTradeRate (periodic trade-rate shuffle)
    //
    // Ports legacy `EconomyService.randomizeCityTradeRate` (EconomyService.kt:648-672,
    // ~25 lines) into Gin7EconomyService. The method periodically re-rolls each
    // planet's `tradeRoute` to introduce market variance: high-level planets
    // (level >= 4) get a level-scaled chance of randomising into 95..105, while
    // low-level planets (level < 4) are reset to the default 100.
    //
    // **Deviations from legacy**:
    //   1. Domain rename: `city.tradeRoute → planet.tradeRoute` (same field name,
    //      entity renamed per Phase 23 CONTEXT.md).
    //   2. Isolated-planet skip: `supplyState != 1` planets are excluded from the
    //      randomisation pass, mirroring the sibling Gin7 convention documented in
    //      the class-level KDoc (line 26: "고립 행성은 세금 징수에서 제외"). The
    //      legacy body processed every city unconditionally. Rule 2 additive
    //      correctness tweak — isolated planets have no trade route to shuffle.
    //   3. Deterministic seed tag preserved verbatim:
    //      `hiddenSeed | "tradeRate" | year | month`.
    //   4. Value bounds preserved: `rng.nextInt(95, 106)` yields 95..105 inclusive,
    //      default reset value remains 100.
    //
    // Wire-up to the TickEngine monthly pipeline is deferred to Plan 23-10.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Re-rolls every supplied planet's `tradeRoute` using a deterministic RNG.
     *
     * Algorithm (verbatim legacy port with LOGH isolation guard):
     *   1. Derive RNG: `DeterministicRng.create(hiddenSeed, "tradeRate", year, month)`
     *      — replay-safe because inputs are the session's hidden seed plus the turn
     *      coordinates.
     *   2. Walk every planet returned by `planetRepository.findBySessionId(sessionId)`.
     *   3. Skip planets with `supplyState != 1` (isolated — no trade route to shuffle).
     *   4. Lookup level probability in `probByLevel`:
     *      `4→0.2, 5→0.4, 6→0.6, 7→0.8, 8→1.0`. Levels outside this table (≤3 or ≥9)
     *      → prob=0.0 → reset branch.
     *   5. Roll `rng.nextDouble()`. If it falls under prob, assign
     *      `rng.nextInt(95, 106)` (95..105 inclusive). Otherwise reset to 100.
     *   6. Persist all touched planets via `planetRepository.saveAll(planets)`.
     *
     * @param world the active session — must expose `currentYear`, `currentMonth`,
     *              and optionally `config["hiddenSeed"]`. Falls back to `world.id`
     *              if the seed is missing, matching legacy behaviour.
     */
    @Transactional
    fun randomizePlanetTradeRate(world: SessionState) {
        val sessionId = world.id.toLong()
        val planets = planetRepository.findBySessionId(sessionId)
        if (planets.isEmpty()) {
            logger.debug("[World {}] randomizePlanetTradeRate: no planets, no-op", world.id)
            return
        }

        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
        val rng = DeterministicRng.create(
            hiddenSeed, "tradeRate",
            world.currentYear, world.currentMonth,
        )

        // Level-based randomisation probability — verbatim from legacy
        // `EconomyService.randomizeCityTradeRate` (EconomyService.kt:658-660).
        val probByLevel = mapOf(
            4 to 0.2, 5 to 0.4, 6 to 0.6, 7 to 0.8, 8 to 1.0,
        )

        var mutated = 0
        var reset = 0
        var skipped = 0
        for (planet in planets) {
            // LOGH addition: skip isolated planets (see class KDoc line 26).
            if (planet.supplyState.toInt() != 1) {
                skipped++
                continue
            }
            val prob = probByLevel[planet.level.toInt()] ?: 0.0
            if (prob > 0 && rng.nextDouble() < prob) {
                planet.tradeRoute = rng.nextInt(95, 106) // 95..105 inclusive
                mutated++
            } else {
                planet.tradeRoute = 100
                reset++
            }
        }

        planetRepository.saveAll(planets)
        logger.info(
            "[World {}] randomizePlanetTradeRate: year={}, month={}, mutated={}, reset={}, skipped={}",
            world.id, world.currentYear, world.currentMonth, mutated, reset, skipped,
        )
    }
}
