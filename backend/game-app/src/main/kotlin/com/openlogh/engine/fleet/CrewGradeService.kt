package com.openlogh.engine.fleet

import com.openlogh.entity.Fleet
import org.springframework.stereotype.Service

/**
 * 승무원 등급 서비스.
 *
 * gin7 §6.9: 함대 승무원 4등급 시스템.
 *
 * 등급별 전투/사기 배율:
 * - ELITE(엘리트):  전투 x1.3, 사기 x1.2
 * - VETERAN(베테랑): 전투 x1.15, 사기 x1.1
 * - NORMAL(노멀):   전투 x1.0, 사기 x1.0
 * - GREEN(그린):    전투 x0.8, 사기 x0.85
 *
 * 모병 시: 그린(GREEN)만 생산.
 * 승급: 전투 경험 또는 훈련 누적으로 자동 승급.
 */
@Service
class CrewGradeService {

    /**
     * 승무원 등급.
     *
     * @param combatMultiplier 전투력 배율
     * @param moraleMultiplier 사기 배율
     */
    enum class CrewGrade(
        val code: String,
        val combatMultiplier: Double,
        val moraleMultiplier: Double,
        /** 이 등급으로 승급에 필요한 전투/훈련 누적치 */
        val promotionThreshold: Int,
    ) {
        /** 엘리트: 최정예. 전투/훈련 누적 200 이상 베테랑에서 승급 */
        ELITE("elite", 1.3, 1.2, 200),
        /** 베테랑: 숙련. 전투/훈련 누적 100 이상 노멀에서 승급 */
        VETERAN("veteran", 1.15, 1.1, 100),
        /** 노멀: 일반. 전투/훈련 누적 50 이상 그린에서 승급 */
        NORMAL("normal", 1.0, 1.0, 50),
        /** 그린: 신병. 모병 시 초기 등급 */
        GREEN("green", 0.8, 0.85, 0),
        ;

        companion object {
            private val byCode = entries.associateBy { it.code }
            fun fromCode(code: String): CrewGrade = byCode[code] ?: NORMAL
            /** 등급 순서 (낮은 것부터): GREEN → NORMAL → VETERAN → ELITE */
            val ASCENDING = listOf(GREEN, NORMAL, VETERAN, ELITE)
        }
    }

    /**
     * 모병 시 초기 등급.
     *
     * gin7 §6.9: 새로 모집된 병사는 항상 그린(GREEN).
     */
    fun getRecruitGrade(): CrewGrade = CrewGrade.GREEN

    /**
     * 전투/훈련 후 승급 가능 등급 반환.
     *
     * gin7 §6.9: 전투 경험 또는 훈련 누적으로 등급 상승.
     * 이미 최고 등급(ELITE)이면 null 반환.
     *
     * @param fleet 대상 함대
     * @param training 현재 훈련도 (0~100)
     * @return 승급 가능하면 다음 등급, 불가하면 null
     */
    fun checkGradePromotion(fleet: Fleet, training: Int): CrewGrade? {
        val current = CrewGrade.fromCode(fleet.crewGrade)
        val ascending = CrewGrade.ASCENDING
        val currentIndex = ascending.indexOf(current)
        if (currentIndex < 0 || currentIndex >= ascending.size - 1) return null // 이미 최고 등급

        val next = ascending[currentIndex + 1]
        // 훈련도가 다음 등급 승급 임계값 이상이면 승급 가능
        return if (training >= next.promotionThreshold) next else null
    }

    /**
     * 등급에 따른 전투 대미지 적용.
     *
     * gin7 §6.9: 기본 대미지에 승무원 등급 전투력 배율 적용.
     *
     * @param baseDamage 기본 대미지
     * @param grade 승무원 등급
     * @return 배율 적용 후 대미지
     */
    fun applyCombatModifier(baseDamage: Int, grade: CrewGrade): Int =
        (baseDamage * grade.combatMultiplier).toInt()

    /**
     * 등급에 따른 사기 보정치 적용.
     *
     * @param baseMorale 기본 사기값
     * @param grade 승무원 등급
     * @return 배율 적용 후 사기값
     */
    fun applyMoraleModifier(baseMorale: Int, grade: CrewGrade): Int =
        (baseMorale * grade.moraleMultiplier).toInt()

    /**
     * Fleet 엔티티에서 현재 승무원 등급 추출.
     */
    fun getFleetGrade(fleet: Fleet): CrewGrade = CrewGrade.fromCode(fleet.crewGrade)
}
