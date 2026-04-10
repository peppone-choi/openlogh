package com.openlogh.engine

import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import org.slf4j.LoggerFactory
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
class Gin7EconomyService(
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository? = null,
) {
    /**
     * Secondary constructor for legacy 2-arg test call sites that do not exercise
     * any officer-touching paths. `processSemiAnnual` (Plan 23-02) requires an
     * `OfficerRepository` because officer personal stockpiles are part of the
     * semi-annual decay pipeline (upstream a7a19cc3 ProcessSemiAnnual.php:89-91).
     *
     * Production wiring resolves all three dependencies via Spring DI; this
     * overload exists purely to preserve source compatibility for sibling Phase 23
     * plans and pre-23 tests that only exercise `processMonthly` / `processWarIncome`.
     */
    constructor(
        factionRepository: FactionRepository,
        planetRepository: PlanetRepository,
    ) : this(factionRepository, planetRepository, null)

    private val logger = LoggerFactory.getLogger(Gin7EconomyService::class.java)

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

        logger.info(
            "[World {}] processIncome({}): {} factions, total delta={}",
            world.id, resource, factions.size, totalDelta,
        )
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
}
