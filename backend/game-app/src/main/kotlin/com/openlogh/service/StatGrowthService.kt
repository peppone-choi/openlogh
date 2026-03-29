package com.openlogh.service

import com.openlogh.entity.Officer
import org.springframework.stereotype.Service

/**
 * 능력치 성장 시스템 (CHAR-04, CHAR-05).
 *
 * gin7 매뉴얼:
 * - 나이 기반 성장 보정: 청년(< 30) +20%, 장년(30-50) 정상, 노년(> 50) -20%
 * - 경험치 누적: *Exp 필드가 100에 도달하면 해당 능력치 +1, 경험치 리셋
 * - 능력치 상한: 100 (초과 불가)
 * - 노년 감소: 55세 초과 시 매 틱마다 1% 확률로 능력치 1 감소
 */
@Service
class StatGrowthService {

    companion object {
        const val EXP_THRESHOLD = 100
        const val STAT_CAP: Short = 100
        const val YOUTH_AGE = 30
        const val ELDER_AGE = 50
        const val DECAY_AGE = 55
        const val YOUTH_MULTIPLIER = 1.2
        const val ELDER_MULTIPLIER = 0.8
        const val DECAY_CHANCE_PER_STAT = 0.01  // 1% per stat per tick
    }

    /**
     * 나이 기반 성장 배율 반환 (CHAR-04).
     * 청년(< 30): +20% 보너스
     * 장년(30-50): 정상
     * 노년(> 50): -20% 패널티
     */
    fun getAgeMultiplier(age: Int): Double {
        return when {
            age < YOUTH_AGE -> YOUTH_MULTIPLIER
            age > ELDER_AGE -> ELDER_MULTIPLIER
            else -> 1.0
        }
    }

    /**
     * 나이 보정 적용 경험치 추가.
     * CP 소비 시 호출 (Phase 3에서 연동).
     * rawExp는 기본 경험치이며, 나이 보정을 적용한다.
     */
    fun addExp(officer: Officer, statName: String, rawExp: Int) {
        val multiplier = getAgeMultiplier(officer.age.toInt())
        val actualExp = (rawExp * multiplier).toInt().coerceAtLeast(1)
        when (statName) {
            "leadership" -> officer.leadershipExp = (officer.leadershipExp + actualExp).toShort()
            "command" -> officer.commandExp = (officer.commandExp + actualExp).toShort()
            "intelligence" -> officer.intelligenceExp = (officer.intelligenceExp + actualExp).toShort()
            "politics" -> officer.politicsExp = (officer.politicsExp + actualExp).toShort()
            "administration" -> officer.administrationExp = (officer.administrationExp + actualExp).toShort()
            "mobility" -> officer.mobilityExp = (officer.mobilityExp + actualExp).toShort()
            "attack" -> officer.attackExp = (officer.attackExp + actualExp).toShort()
            "defense" -> officer.defenseExp = (officer.defenseExp + actualExp).toShort()
        }
    }

    /**
     * 경험치 → 능력치 전환 처리 (CHAR-05).
     * *Exp 필드가 100 이상이면 해당 능력치 +1, 경험치에서 100 차감 (잉여분 이월).
     * 능력치는 STAT_CAP(100)을 초과할 수 없다.
     * @return 성장한 능력치 이름 목록
     */
    fun processExpGrowth(officer: Officer): List<String> {
        val grew = mutableListOf<String>()

        if (officer.leadershipExp >= EXP_THRESHOLD && officer.leadership < STAT_CAP) {
            officer.leadership = (officer.leadership + 1).toShort()
            officer.leadershipExp = (officer.leadershipExp - EXP_THRESHOLD).toShort()
            grew.add("leadership")
        }
        if (officer.commandExp >= EXP_THRESHOLD && officer.command < STAT_CAP) {
            officer.command = (officer.command + 1).toShort()
            officer.commandExp = (officer.commandExp - EXP_THRESHOLD).toShort()
            grew.add("command")
        }
        if (officer.intelligenceExp >= EXP_THRESHOLD && officer.intelligence < STAT_CAP) {
            officer.intelligence = (officer.intelligence + 1).toShort()
            officer.intelligenceExp = (officer.intelligenceExp - EXP_THRESHOLD).toShort()
            grew.add("intelligence")
        }
        if (officer.politicsExp >= EXP_THRESHOLD && officer.politics < STAT_CAP) {
            officer.politics = (officer.politics + 1).toShort()
            officer.politicsExp = (officer.politicsExp - EXP_THRESHOLD).toShort()
            grew.add("politics")
        }
        if (officer.administrationExp >= EXP_THRESHOLD && officer.administration < STAT_CAP) {
            officer.administration = (officer.administration + 1).toShort()
            officer.administrationExp = (officer.administrationExp - EXP_THRESHOLD).toShort()
            grew.add("administration")
        }
        if (officer.mobilityExp >= EXP_THRESHOLD && officer.mobility < STAT_CAP) {
            officer.mobility = (officer.mobility + 1).toShort()
            officer.mobilityExp = (officer.mobilityExp - EXP_THRESHOLD).toShort()
            grew.add("mobility")
        }
        if (officer.attackExp >= EXP_THRESHOLD && officer.attack < STAT_CAP) {
            officer.attack = (officer.attack + 1).toShort()
            officer.attackExp = (officer.attackExp - EXP_THRESHOLD).toShort()
            grew.add("attack")
        }
        if (officer.defenseExp >= EXP_THRESHOLD && officer.defense < STAT_CAP) {
            officer.defense = (officer.defense + 1).toShort()
            officer.defenseExp = (officer.defenseExp - EXP_THRESHOLD).toShort()
            grew.add("defense")
        }

        return grew
    }

    /**
     * 노년 능력치 감소 (CHAR-04 확장).
     * 55세 초과 장교는 매 틱마다 능력치당 1% 확률로 1 감소.
     * @return 감소한 능력치 이름 목록
     */
    fun processElderDecay(officer: Officer, random: java.util.Random = java.util.Random()): List<String> {
        if (officer.age <= DECAY_AGE) return emptyList()
        val decayed = mutableListOf<String>()

        if (officer.leadership > 1 && random.nextDouble() < DECAY_CHANCE_PER_STAT) {
            officer.leadership = (officer.leadership - 1).toShort(); decayed.add("leadership")
        }
        if (officer.command > 1 && random.nextDouble() < DECAY_CHANCE_PER_STAT) {
            officer.command = (officer.command - 1).toShort(); decayed.add("command")
        }
        if (officer.intelligence > 1 && random.nextDouble() < DECAY_CHANCE_PER_STAT) {
            officer.intelligence = (officer.intelligence - 1).toShort(); decayed.add("intelligence")
        }
        if (officer.politics > 1 && random.nextDouble() < DECAY_CHANCE_PER_STAT) {
            officer.politics = (officer.politics - 1).toShort(); decayed.add("politics")
        }
        if (officer.administration > 1 && random.nextDouble() < DECAY_CHANCE_PER_STAT) {
            officer.administration = (officer.administration - 1).toShort(); decayed.add("administration")
        }
        if (officer.mobility > 1 && random.nextDouble() < DECAY_CHANCE_PER_STAT) {
            officer.mobility = (officer.mobility - 1).toShort(); decayed.add("mobility")
        }
        if (officer.attack > 1 && random.nextDouble() < DECAY_CHANCE_PER_STAT) {
            officer.attack = (officer.attack - 1).toShort(); decayed.add("attack")
        }
        if (officer.defense > 1 && random.nextDouble() < DECAY_CHANCE_PER_STAT) {
            officer.defense = (officer.defense - 1).toShort(); decayed.add("defense")
        }

        return decayed
    }
}
