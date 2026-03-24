package com.openlogh.engine.tactical

import com.openlogh.entity.Officer
import org.springframework.stereotype.Service

/**
 * 전술 커맨드 분배 우선순위 서비스.
 *
 * gin7 §10.20: 동일 그리드에 복수의 장교가 있을 때 유닛 지휘권 우선순위.
 *
 * 우선순위 결정 기준 (순서대로):
 * 1. 온라인(접속 중) > 오프라인
 * 2. 계급(rank) 높은 순
 * 3. 공적(dedication/merit) 높은 순
 * 4. 평가(experience/evaluation) 높은 순
 *
 * 지휘권 이양 조건 (gin7 §10.20):
 * - 대상 유닛이 타 기함 커맨드 레인지 밖에 있을 것
 * - 유닛이 완전 정지(stopped) 상태일 것
 */
@Service
class CommandDistributionService {

    /**
     * 장교 목록에 우선순위 점수를 부여하여 정렬된 리스트 반환.
     *
     * gin7 §10.20: 우선순위가 높은 장교가 유닛 지휘를 먼저 받는다.
     *
     * @param officers 동일 그리드에 있는 장교 목록
     * @param onlineOfficerIds 현재 온라인(접속 중) 장교 ID 집합
     * @return (장교, 우선순위 점수) 쌍의 목록, 점수 내림차순
     */
    fun assignCommandPriority(
        officers: List<Officer>,
        onlineOfficerIds: Set<Long> = emptySet(),
    ): List<Pair<Officer, Int>> {
        return officers
            .map { officer ->
                val score = calculatePriorityScore(officer, onlineOfficerIds)
                Pair(officer, score)
            }
            .sortedByDescending { it.second }
    }

    /**
     * 지휘권 이양 가능 여부.
     *
     * gin7 §10.20:
     * - 대상 유닛이 다른 기함 커맨드 레인지 밖에 있어야 함
     * - 대상 유닛이 완전 정지(stopped) 상태여야 함
     *
     * @param from 현재 지휘자 장교
     * @param to 이양받을 장교
     * @param unit 이양할 전술 유닛
     * @param unitIsStopped 유닛이 완전 정지 상태인지
     * @param unitOutsideOtherFlagshipRange 유닛이 다른 기함 커맨드 레인지 밖인지
     */
    fun canTransferCommand(
        from: Officer,
        to: Officer,
        unit: TacticalUnit,
        unitIsStopped: Boolean,
        unitOutsideOtherFlagshipRange: Boolean,
    ): Boolean {
        // 같은 진영이어야 함
        if (from.factionId != to.factionId) return false
        // 유닛이 정지 상태여야 함
        if (!unitIsStopped) return false
        // 유닛이 다른 기함 커맨드 레인지 밖이어야 함
        if (!unitOutsideOtherFlagshipRange) return false
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 우선순위 점수 계산.
     *
     * gin7 §10.20 기준:
     * 1. 온라인 여부: +100,000
     * 2. 계급(rank 0~10): +rank * 1,000
     * 3. 공적(dedication): +dedication / 100
     * 4. 평가(experience): +experience / 1,000
     */
    private fun calculatePriorityScore(officer: Officer, onlineOfficerIds: Set<Long>): Int {
        var score = 0
        // 1. 온라인 우선
        if (officer.id in onlineOfficerIds) score += 100_000
        // 2. 계급
        score += officer.rank * 1_000
        // 3. 공적 (dedication)
        score += officer.dedication / 100
        // 4. 평가 (experience)
        score += officer.experience / 1_000
        return score
    }
}
