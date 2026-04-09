package com.openlogh.engine.ai.strategic

import com.openlogh.engine.ai.PersonalityTrait
import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import kotlin.random.Random

/**
 * Phase 13 SAI-02 — Strategic operation target selector.
 *
 * Pure object (no Spring DI) following the UtilityScorer pattern.
 *
 * Per D-04: 복합 기준(전선 + 전략적 가치)으로 작전 대상 성계 선정.
 *   - CONQUEST: 적 전선 성계 중 전력 약하고 전략적 가치 높은 곳
 *   - DEFENSE : 아군 전선 성계 중 위협받는 곳
 *   - SWEEP   : 아군 영역 내 적 함대 존재 성계
 *
 * Per D-10: 성격(PersonalityTrait) 기반 작전 유형 경향 — 진영 원수 성격이
 * 작전 유형의 점수에 곱셈 보정을 가한다.
 *   - AGGRESSIVE → CONQUEST × 1.5
 *   - DEFENSIVE  → DEFENSE  × 1.5
 *   - CAUTIOUS   → SWEEP    × 0.8 (less aggressive sweeping)
 */
object OperationTargetSelector {

    const val AGGRESSIVE_CONQUEST_BIAS: Double = 1.5
    const val DEFENSIVE_DEFENSE_BIAS: Double = 1.5
    const val CAUTIOUS_SWEEP_BIAS: Double = 0.8

    /** Ratio above which an own front-line system is considered "threatened" by an adjacent enemy. */
    const val DEFENSE_THREAT_RATIO: Double = 0.7

    data class OperationCandidate(
        val targetPlanetId: Long,
        val objective: MissionObjective,
        val score: Double,
        val estimatedEnemyPower: Double,
    )

    /**
     * Select all viable operation candidates across CONQUEST, DEFENSE, and SWEEP categories,
     * sorted by descending score.
     *
     * The caller is expected to slice the returned list as needed (Phase 13 D-05: 무제한 작전 수).
     */
    fun selectTargets(
        ownPlanets: List<Planet>,
        enemyPlanets: List<Planet>,
        ownFleetsByPlanet: Map<Long, List<Fleet>>,
        enemyFleetsByPlanet: Map<Long, List<Fleet>>,
        ownOfficersByPlanet: Map<Long, List<Officer>>,
        enemyOfficersByPlanet: Map<Long, List<Officer>>,
        sovereignPersonality: PersonalityTrait,
        friendlyOfficers: List<Officer>,
        rng: Random,
    ): List<OperationCandidate> {
        val candidates = mutableListOf<OperationCandidate>()

        // ---- 1. CONQUEST candidates: enemy front-line planets, weak relative to own offensive power ----
        for (target in enemyPlanets) {
            if (target.frontState <= 0) continue

            val enemyFleets = enemyFleetsByPlanet[target.id] ?: emptyList()
            val enemyOfficers = enemyOfficersByPlanet[target.id] ?: emptyList()
            val truePower = StrategicPowerScorer.evaluatePower(enemyFleets, enemyOfficers, target).compositeScore

            val hasAgent = FogOfWarEstimator.hasIntelligenceAgent(target.id, friendlyOfficers)
            val estimatedEnemyPower = FogOfWarEstimator.applyFogNoise(truePower, hasAgent, rng)

            // Strategic value: economic output + connectivity + long-term population value
            val strategicValue =
                target.production.toDouble() +
                    target.commerce.toDouble() +
                    target.tradeRoute.toDouble() * 100.0 +
                    target.population.toDouble() / 100.0

            // High-value, low-power systems score highest
            var score = strategicValue / (estimatedEnemyPower + 1.0)
            if (sovereignPersonality == PersonalityTrait.AGGRESSIVE) {
                score *= AGGRESSIVE_CONQUEST_BIAS
            }

            candidates += OperationCandidate(
                targetPlanetId = target.id,
                objective = MissionObjective.CONQUEST,
                score = score,
                estimatedEnemyPower = estimatedEnemyPower,
            )
        }

        // ---- 2. DEFENSE candidates: own front-line planets threatened by adjacent enemy power ----
        for (own in ownPlanets) {
            if (own.frontState <= 0) continue

            val ownFleets = ownFleetsByPlanet[own.id] ?: emptyList()
            val ownOfficers = ownOfficersByPlanet[own.id] ?: emptyList()
            val ownPower = StrategicPowerScorer.evaluatePower(ownFleets, ownOfficers, own).compositeScore

            // Find max enemy power among "adjacent" enemy front-line systems.
            // Lacking explicit adjacency data here, the planner approximates "adjacent" as
            // any enemy front-line planet — the planner side restricts this to genuine
            // neighbors via galaxy graph queries before invoking the selector.
            var maxEnemyThreat = 0.0
            for (enemy in enemyPlanets) {
                if (enemy.frontState <= 0) continue
                val enemyFleets = enemyFleetsByPlanet[enemy.id] ?: emptyList()
                val enemyOfficers = enemyOfficersByPlanet[enemy.id] ?: emptyList()
                val truePower = StrategicPowerScorer.evaluatePower(enemyFleets, enemyOfficers, enemy).compositeScore

                val hasAgent = FogOfWarEstimator.hasIntelligenceAgent(enemy.id, friendlyOfficers)
                val estimated = FogOfWarEstimator.applyFogNoise(truePower, hasAgent, rng)
                if (estimated > maxEnemyThreat) maxEnemyThreat = estimated
            }

            // A system is "threatened" only when nearby enemy power exceeds 70% of own power
            if (maxEnemyThreat <= ownPower * DEFENSE_THREAT_RATIO) continue

            var score = maxEnemyThreat / (ownPower + 1.0)
            if (sovereignPersonality == PersonalityTrait.DEFENSIVE) {
                score *= DEFENSIVE_DEFENSE_BIAS
            }

            candidates += OperationCandidate(
                targetPlanetId = own.id,
                objective = MissionObjective.DEFENSE,
                score = score,
                estimatedEnemyPower = maxEnemyThreat,
            )
        }

        // ---- 3. SWEEP candidates: own-territory planets with intruding enemy fleets ----
        for (own in ownPlanets) {
            val intruders = enemyFleetsByPlanet[own.id] ?: continue
            if (intruders.isEmpty()) continue

            val intruderShips = intruders.sumOf {
                it.currentUnits * StrategicPowerScorer.SHIPS_PER_UNIT
            }
            var score = intruderShips.toDouble()
            if (sovereignPersonality == PersonalityTrait.CAUTIOUS) {
                score *= CAUTIOUS_SWEEP_BIAS
            }

            candidates += OperationCandidate(
                targetPlanetId = own.id,
                objective = MissionObjective.SWEEP,
                score = score,
                estimatedEnemyPower = intruderShips.toDouble(),
            )
        }

        return candidates.sortedByDescending { it.score }
    }
}
