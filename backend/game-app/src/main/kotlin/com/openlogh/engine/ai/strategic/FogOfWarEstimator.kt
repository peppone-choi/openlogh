package com.openlogh.engine.ai.strategic

import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * Phase 13 SAI-02 — Strategic fog-of-war noise estimator.
 *
 * Pure object (no Spring DI) following the UtilityScorer pattern.
 *
 * Per D-02 / D-03: 전략 AI는 성계 단위 안개 (전술전 안개와 별개).
 * 적 성계의 전력 평가는 해당 성계에 첩보원(intelligence ≥ 70 인 아군 장교)이
 * 체류 중일 때만 정확하게 측정된다. 첩보원이 없으면 ±40% 노이즈가 적용된 추정치를 사용한다.
 */
object FogOfWarEstimator {

    /** Intelligence stat threshold for treating an officer as an intelligence agent. */
    const val INTELLIGENCE_THRESHOLD: Int = 70

    /** Symmetric noise range (±40% of true power) applied when no agent is present. */
    const val NOISE_RANGE: Double = 0.4

    /**
     * Returns true when at least one friendly officer with intelligence ≥ INTELLIGENCE_THRESHOLD
     * is currently stationed at the target planet.
     */
    fun hasIntelligenceAgent(
        targetPlanetId: Long,
        friendlyOfficers: List<Officer>,
    ): Boolean = friendlyOfficers.any { officer ->
        officer.planetId == targetPlanetId && officer.intelligence >= INTELLIGENCE_THRESHOLD
    }

    /**
     * Apply fog-of-war noise to a true enemy power value.
     *
     * - If [hasAgent] is true: returns [truePower] unchanged (perfect intelligence).
     * - If [hasAgent] is false: returns truePower * (1.0 + (random * 2 - 1) * NOISE_RANGE),
     *   coerced to non-negative.
     *
     * Net effect: estimate falls within [truePower * 0.6, truePower * 1.4] when fog applies.
     */
    fun applyFogNoise(
        truePower: Double,
        hasAgent: Boolean,
        rng: Random,
    ): Double {
        if (hasAgent) return truePower
        val noise = 1.0 + (rng.nextDouble() * 2.0 - 1.0) * NOISE_RANGE
        return (truePower * noise).coerceAtLeast(0.0)
    }
}
