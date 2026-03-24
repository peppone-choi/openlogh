package com.openlogh.service

import kotlin.random.Random

/**
 * 제안 수락 확률 서비스 (提案受諾サービス).
 *
 * gin7 매뉴얼 4.4 — 하급자가 상급자에게 제안할 때 수락 확률 계산.
 *
 * 공식:
 *   probability = base_rate × (1 + rank_diff/10) × (1 + friendship/100) × compatibility_modifier
 *
 * where:
 *   base_rate         = 0.3 (30%)
 *   rank_diff         = proposer.rank - target.rank (음수 = 하급자가 제안)
 *   friendship        = 쌍방 우호도 점수 (0~100)
 *   compatibility     = 상성 수정치 (0.8 ~ 1.2)
 */

// ===== 상성 (호환성) 코드 =====

/**
 * 장교 간 상성 타입.
 *
 * gin7: 각 장교는 성격/출신/진영 등에 따라 상성 수정치를 가짐.
 */
enum class CompatibilityType(val code: String, val displayName: String, val modifier: Double) {
    /** 천적: 성향이 정반대 */
    NEMESIS("nemesis", "천적", 0.8),
    /** 불화: 미묘한 갈등 */
    UNFAVORABLE("unfavorable", "불화", 0.9),
    /** 보통: 특별한 관계 없음 */
    NEUTRAL("neutral", "보통", 1.0),
    /** 우호: 성향이 잘 맞음 */
    FAVORABLE("favorable", "우호", 1.1),
    /** 환상의 조합: 완벽한 파트너 */
    SYNERGY("synergy", "환상의 조합", 1.2),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): CompatibilityType = byCode[code] ?: NEUTRAL
    }
}

// ===== 입력/결과 =====

/**
 * 제안 수락 확률 계산에 필요한 장교 데이터.
 */
data class ProposalOfficerData(
    val officerId: Long,
    val name: String,
    val rank: Int,     // 계급 (0~10)
    val politics: Int, // 정치 능력치 (설득력 보정)
)

/**
 * 제안 수락 판정 결과.
 */
data class ProposalResult(
    /** 수락 여부 */
    val accepted: Boolean,
    /** 계산된 수락 확률 (0.0~1.0) */
    val probability: Double,
    /** 판정에 사용된 각 인수 */
    val baseRate: Double,
    val rankDiffFactor: Double,
    val friendshipFactor: Double,
    val compatibilityModifier: Double,
    val log: String,
)

// ===== 서비스 =====

object ProposalService {

    /** 기본 수락률 (gin7 4.4: 30%) */
    private const val BASE_RATE = 0.3

    /** 최소/최대 수락 확률 clamp */
    private const val MIN_PROBABILITY = 0.05
    private const val MAX_PROBABILITY = 0.95

    /**
     * 제안 수락 확률 계산 및 판정.
     *
     * gin7 4.4:
     *   - 하급자가 상급자에게 제안하는 경우 rank_diff < 0 → 확률 감소
     *   - 우호도가 높을수록 확률 증가
     *   - 상성에 따른 0.8~1.2 배율 적용
     *   - proposer의 정치(politics) 능력치는 미세 보정으로 추가 반영
     *
     * @param proposer 제안하는 장교
     * @param target 제안을 받는 장교
     * @param friendship 쌍방 우호도 (0~100, OfficerRelation.friendshipScore)
     * @param compatibility 상성 타입
     * @param rng 난수 생성기
     */
    fun evaluateProposal(
        proposer: ProposalOfficerData,
        target: ProposalOfficerData,
        friendship: Int,
        compatibility: CompatibilityType = CompatibilityType.NEUTRAL,
        rng: Random,
    ): ProposalResult {
        val rankDiff = proposer.rank - target.rank

        // gin7 4.4 공식 각 인수
        val rankDiffFactor = 1.0 + rankDiff / 10.0
        val friendshipFactor = 1.0 + friendship / 100.0
        val compatibilityModifier = compatibility.modifier

        // 정치 능력치 미세 보정: 50 기준 ±10% 범위
        val politicsBonus = 1.0 + (proposer.politics - 50) / 500.0

        val rawProbability = BASE_RATE * rankDiffFactor * friendshipFactor * compatibilityModifier * politicsBonus
        val probability = rawProbability.coerceIn(MIN_PROBABILITY, MAX_PROBABILITY)

        val accepted = rng.nextDouble() < probability

        val log = buildString {
            append("${proposer.name} → ${target.name} 제안: ")
            append("기본 ${(BASE_RATE * 100).toInt()}%")
            append(", 계급차 ×${String.format("%.2f", rankDiffFactor)}")
            append(", 우호도 ×${String.format("%.2f", friendshipFactor)}")
            append(", 상성(${compatibility.displayName}) ×${compatibilityModifier}")
            append(" → 최종 ${String.format("%.1f", probability * 100)}%")
            append(" → ${if (accepted) "수락" else "거절"}")
        }

        return ProposalResult(
            accepted = accepted,
            probability = probability,
            baseRate = BASE_RATE,
            rankDiffFactor = rankDiffFactor,
            friendshipFactor = friendshipFactor,
            compatibilityModifier = compatibilityModifier,
            log = log,
        )
    }

    /**
     * 제안 수락 확률만 계산 (판정 없음).
     *
     * UI 표시용.
     */
    fun calculateProbability(
        proposer: ProposalOfficerData,
        target: ProposalOfficerData,
        friendship: Int,
        compatibility: CompatibilityType = CompatibilityType.NEUTRAL,
    ): Double {
        val rankDiff = proposer.rank - target.rank
        val politicsBonus = 1.0 + (proposer.politics - 50) / 500.0
        val raw = BASE_RATE *
            (1.0 + rankDiff / 10.0) *
            (1.0 + friendship / 100.0) *
            compatibility.modifier *
            politicsBonus
        return raw.coerceIn(MIN_PROBABILITY, MAX_PROBABILITY)
    }
}
