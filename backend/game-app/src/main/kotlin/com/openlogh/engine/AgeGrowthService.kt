package com.openlogh.engine

import com.openlogh.entity.Officer
import com.openlogh.entity.SessionState
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * 나이 기반 능력치 성장/노화 시스템.
 *
 * gin7 매뉴얼:
 * - 청년 (30세 미만): 매월 능력치 + 변화 가능성
 * - 장년 (50세 이상): 매월 능력치 - 변화 가능성
 * - 30~50세: 안정기 (변화 없음)
 */
@Service
class AgeGrowthService(
    private val officerRepository: OfficerRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(AgeGrowthService::class.java)

        /** 청년 상한 나이 */
        const val YOUTH_MAX_AGE = 30
        /** 장년 시작 나이 */
        const val AGING_START_AGE = 50
        /** 청년 월별 성장 확률 */
        const val YOUTH_GROWTH_CHANCE = 0.15
        /** 장년 월별 노화 확률 */
        const val AGING_DECLINE_CHANCE = 0.10
        /** 능력치 상한 */
        const val STAT_CAP: Short = 100
        /** 능력치 하한 */
        const val STAT_FLOOR: Short = 1
    }

    /**
     * 매월 호출. 전 장교의 나이 기반 능력치 변동 처리.
     */
    fun processMonthlyGrowth(world: SessionState) {
        val sessionId = world.id.toLong()
        val currentYear = world.currentYear.toInt()
        val officers = officerRepository.findBySessionId(sessionId)
        val rng = Random(currentYear * 12L + world.currentMonth)

        for (officer in officers) {
            if (officer.deathYear > 0 && officer.deathYear <= currentYear.toShort()) continue // 사망
            val age = currentYear - officer.birthYear.toInt()
            processOfficerGrowth(officer, age, rng)
        }

        officerRepository.saveAll(officers)
    }

    private fun processOfficerGrowth(officer: Officer, age: Int, rng: Random) {
        when {
            age < YOUTH_MAX_AGE -> applyYouthGrowth(officer, rng)
            age >= AGING_START_AGE -> applyAgingDecline(officer, age, rng)
        }
    }

    /** 청년: 10개 능력치 중 랜덤 1~2개 +1 */
    private fun applyYouthGrowth(officer: Officer, rng: Random) {
        if (rng.nextDouble() >= YOUTH_GROWTH_CHANCE) return

        val stats = mutableListOf<() -> Unit>()
        if (officer.leadership < STAT_CAP) stats.add { officer.leadership++ }
        if (officer.command < STAT_CAP) stats.add { officer.command++ }
        if (officer.intelligence < STAT_CAP) stats.add { officer.intelligence++ }
        if (officer.politics < STAT_CAP) stats.add { officer.politics++ }
        if (officer.administration < STAT_CAP) stats.add { officer.administration++ }
        if (officer.mobility < STAT_CAP) stats.add { officer.mobility++ }
        if (officer.attack < STAT_CAP) stats.add { officer.attack++ }
        if (officer.defense < STAT_CAP) stats.add { officer.defense++ }
        if (officer.fighterSkill < STAT_CAP) stats.add { officer.fighterSkill++ }
        if (officer.groundCombat < STAT_CAP) stats.add { officer.groundCombat++ }

        if (stats.isNotEmpty()) {
            stats[rng.nextInt(stats.size)]()
            // 20% 확률로 추가 1개
            if (rng.nextDouble() < 0.2 && stats.size > 1) {
                stats[rng.nextInt(stats.size)]()
            }
        }
    }

    /** 장년: 나이가 많을수록 노화 확률 증가 */
    private fun applyAgingDecline(officer: Officer, age: Int, rng: Random) {
        val agingFactor = (age - AGING_START_AGE) * 0.01
        val chance = AGING_DECLINE_CHANCE + agingFactor
        if (rng.nextDouble() >= chance) return

        val stats = mutableListOf<() -> Unit>()
        if (officer.mobility > STAT_FLOOR) stats.add { officer.mobility-- }
        if (officer.attack > STAT_FLOOR) stats.add { officer.attack-- }
        if (officer.defense > STAT_FLOOR) stats.add { officer.defense-- }
        if (officer.fighterSkill > STAT_FLOOR) stats.add { officer.fighterSkill-- }
        if (officer.groundCombat > STAT_FLOOR) stats.add { officer.groundCombat-- }
        // 지적 능력치는 노화에 강함 (60세 이후에만)
        if (age >= 60) {
            if (officer.command > STAT_FLOOR) stats.add { officer.command-- }
            if (officer.intelligence > STAT_FLOOR) stats.add { officer.intelligence-- }
        }

        if (stats.isNotEmpty()) {
            stats[rng.nextInt(stats.size)]()
        }
    }
}
