package com.openlogh.engine

import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
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
 *
 * 고립 행성(supplyState=0)은 세금 징수에서 제외되며 자원 성장도 없다.
 */
@Service
class Gin7EconomyService(
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
) {
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
}
