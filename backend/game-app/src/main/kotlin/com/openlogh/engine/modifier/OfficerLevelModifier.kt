package com.openlogh.engine.modifier

import com.openlogh.entity.Officer
import com.openlogh.entity.SessionState
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 계급 기반 능력치 보정 시스템 (OfficerLevelModifier).
 *
 * gin7 매뉴얼:
 * - 계급이 높을수록 지휘 범위 확대, 최대 사기 증가
 * - 계급별 최대 부대 규모 결정
 * - 매월 계급에 따른 공적 포인트 자연 증감
 * - 계급별 CP 최대치 보너스
 *
 * 이 서비스는 매 턴(월) 호출되어 계급에 따른 간접 효과를 적용한다.
 */
@Service
class OfficerLevelModifier {

    companion object {
        private val log = LoggerFactory.getLogger(OfficerLevelModifier::class.java)

        /**
         * 계급별 최대 사기 보정.
         * gin7: 통솔 기반 최대 사기에 계급 보정 추가.
         * rank 0(소위)~10(원수)
         */
        val RANK_MORALE_BONUS = intArrayOf(
            0,  // 0: 소위
            1,  // 1: 대위
            2,  // 2: 소령
            3,  // 3: 중령
            5,  // 4: 대령
            7,  // 5: 준장
            10, // 6: 소장
            13, // 7: 중장
            17, // 8: 대장
            22, // 9: 상급대장
            30, // 10: 원수
        )

        /**
         * 계급별 커맨드 레인지 배율 (%).
         * gin7: 기함 기본 레인지 x 지휘 능력치 x 계급 보정.
         */
        val RANK_COMMAND_RANGE_PERCENT = intArrayOf(
            50,  // 0: 소위 — 50%
            60,  // 1: 대위
            70,  // 2: 소령
            80,  // 3: 중령
            90,  // 4: 대령
            100, // 5: 준장
            110, // 6: 소장
            120, // 7: 중장
            140, // 8: 대장
            160, // 9: 상급대장
            200, // 10: 원수
        )

        /**
         * 계급별 CP 최대치 보너스.
         */
        val RANK_CP_BONUS = intArrayOf(
            0, 0, 1, 1, 2, 3, 4, 5, 7, 10, 15,
        )

        /**
         * 계급별 월 공적 자연 증가량.
         * gin7: 높은 계급은 자연 공적이 낮고, 낮은 계급은 자연 공적이 높다.
         * (낮은 계급이 빨리 올라가도록 설계)
         */
        val RANK_MONTHLY_MERIT = intArrayOf(
            5,  // 0: 소위 — 월 5 공적 자연 증가
            4,  // 1: 대위
            3,  // 2: 소령
            3,  // 3: 중령
            2,  // 4: 대령
            2,  // 5: 준장
            1,  // 6: 소장
            1,  // 7: 중장
            0,  // 8: 대장
            0,  // 9: 상급대장
            0,  // 10: 원수
        )

        /**
         * 계급별 인원 제한.
         * gin7: 원수 5, 상급대장 5(제국)/미명시(동맹), 대장 10, 중장 20, 소장 40, 준장 80.
         * 대령 이하는 무제한.
         */
        val RANK_SLOT_LIMITS = mapOf(
            10 to 5,   // 원수
            9 to 5,    // 상급대장
            8 to 10,   // 대장
            7 to 20,   // 중장
            6 to 40,   // 소장
            5 to 80,   // 준장
        )
    }

    /**
     * 매 턴(월) 호출: 계급에 따른 월별 효과 적용.
     *
     * - 계급별 월 공적 자연 증가
     * - 계급에 따른 사기 상한 적용
     */
    fun applyMonthlyModifiers(officers: List<Officer>, world: SessionState) {
        for (officer in officers) {
            if (officer.factionId == 0L) continue
            val rank = officer.rank.toInt().coerceIn(0, 10)

            // 월 공적 자연 증가
            val monthlyMerit = RANK_MONTHLY_MERIT.getOrElse(rank) { 0 }
            if (monthlyMerit > 0) {
                officer.experience += monthlyMerit
            }

            // 사기 상한 적용 (통솔 기반 + 계급 보정)
            val moraleBonus = RANK_MORALE_BONUS.getOrElse(rank) { 0 }
            val maxMorale = officer.leadership.toInt() + moraleBonus
            if (officer.morale > maxMorale) {
                officer.morale = maxMorale.toShort()
            }
        }
    }

    /**
     * 특정 장교의 최대 사기 계산.
     */
    fun getMaxMorale(officer: Officer): Int {
        val rank = officer.rank.toInt().coerceIn(0, 10)
        return officer.leadership.toInt() + RANK_MORALE_BONUS.getOrElse(rank) { 0 }
    }

    /**
     * 커맨드 레인지 배율 조회.
     * @return 퍼센트 값 (100 = 1배)
     */
    fun getCommandRangePercent(officer: Officer): Int {
        val rank = officer.rank.toInt().coerceIn(0, 10)
        return RANK_COMMAND_RANGE_PERCENT.getOrElse(rank) { 100 }
    }

    /**
     * 계급에 따른 CP 보너스.
     */
    fun getCpBonus(officer: Officer): Int {
        val rank = officer.rank.toInt().coerceIn(0, 10)
        return RANK_CP_BONUS.getOrElse(rank) { 0 }
    }

    /**
     * 해당 계급에 승진 가능한 빈자리가 있는지 확인.
     * @param targetRank 승진 목표 계급
     * @param factionOfficers 같은 진영 전체 장교 목록
     */
    fun hasRankSlot(targetRank: Int, factionOfficers: List<Officer>): Boolean {
        val limit = RANK_SLOT_LIMITS[targetRank] ?: return true // 대령 이하 무제한
        val currentCount = factionOfficers.count { it.rank.toInt() == targetRank }
        return currentCount < limit
    }

    /**
     * 승진 시 효과 적용.
     * gin7: 승진 시 공적 0 리셋, 개인/함장/봉토 제외 전 직무카드 상실.
     */
    fun applyPromotionEffects(officer: Officer) {
        officer.rank = (officer.rank + 1).toShort()
        officer.experience = 0

        // 직무카드 상실 (개인/함장/봉토 제외)
        @Suppress("UNCHECKED_CAST")
        val cards = (officer.meta["positionCards"] as? MutableList<String>)
        if (cards != null) {
            val retained = setOf("personal", "captain")
            val fiefCards = cards.filter { it.startsWith("fief_") }
            cards.retainAll(retained)
            cards.addAll(fiefCards)
        }

        log.debug("Promoted officer {} to rank {}", officer.name, officer.rank)
    }

    /**
     * 강등 시 효과 적용.
     * gin7: 강등 시 공적 100 설정, 개인/함장/봉토 제외 전 직무카드 상실.
     */
    fun applyDemotionEffects(officer: Officer) {
        officer.rank = (officer.rank - 1).coerceAtLeast(0).toShort()
        officer.experience = 100

        // 직무카드 상실 (개인/함장/봉토 제외)
        @Suppress("UNCHECKED_CAST")
        val cards = (officer.meta["positionCards"] as? MutableList<String>)
        if (cards != null) {
            val retained = setOf("personal", "captain")
            val fiefCards = cards.filter { it.startsWith("fief_") }
            cards.retainAll(retained)
            cards.addAll(fiefCards)
        }

        log.debug("Demoted officer {} to rank {}", officer.name, officer.rank)
    }
}
