package com.openlogh.engine.war

import com.openlogh.entity.Officer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * 전투 경험치 시스템.
 *
 * gin7 매뉴얼:
 * - CP 사용 경험치와는 별도로, 전투 결과에 따른 경험치 획득
 * - MCP 계열 능력치(지휘/기동/공격/방어) 경험치 증가
 * - 승리 시 대폭 증가, 패배 시 소폭 증가
 * - 적 격침 수에 비례한 보너스
 * - 전투 참가만으로도 최소 경험치 획득
 * - 경험치 100 누적 시 능력치 1 상승 후 리셋
 */
@Service
class BattleExperienceService {

    companion object {
        private val log = LoggerFactory.getLogger(BattleExperienceService::class.java)

        /** 전투 참가 기본 경험치 */
        const val BASE_PARTICIPATION_EXP = 5

        /** 승리 보너스 경험치 */
        const val VICTORY_BONUS_EXP = 15

        /** 패배 시 경험치 (참가 기본만) */
        const val DEFEAT_EXP = 3

        /** 적 유닛 1척 격침당 경험치 */
        const val KILL_EXP_PER_UNIT = 2

        /** 최대 킬 경험치 캡 */
        const val MAX_KILL_EXP = 50

        /** 경험치 → 능력치 전환 임계치 */
        const val EXP_TO_STAT_THRESHOLD = 100

        /** 공적 포인트: 승리 */
        const val MERIT_VICTORY = 30

        /** 공적 포인트: 패배 */
        const val MERIT_DEFEAT = 5

        /** 공적 포인트: 적 유닛당 */
        const val MERIT_PER_KILL = 1

        /** 작전 성공 보너스 공적 (100% 성공) */
        const val MERIT_OPERATION_FULL = 100

        /** 작전 부분 성공 공적 (50%) */
        const val MERIT_OPERATION_PARTIAL = 50
    }

    /**
     * 전투 결과를 기반으로 장교에게 경험치/공적 부여.
     *
     * @param officer 전투 참가 장교
     * @param isVictory 승리 여부
     * @param enemyUnitsDestroyed 격침한 적 유닛 수
     * @param isCommander 함대 사령관 여부 (사령관은 추가 보너스)
     * @param rng 랜덤 소스
     */
    fun awardBattleExperience(
        officer: Officer,
        isVictory: Boolean,
        enemyUnitsDestroyed: Int,
        isCommander: Boolean = false,
        rng: Random = Random.Default,
    ) {
        // 1. MCP 계열 경험치 산정
        val baseExp = if (isVictory) {
            BASE_PARTICIPATION_EXP + VICTORY_BONUS_EXP
        } else {
            DEFEAT_EXP
        }

        val killExp = (enemyUnitsDestroyed * KILL_EXP_PER_UNIT).coerceAtMost(MAX_KILL_EXP)
        val commanderBonus = if (isCommander) 10 else 0
        val totalExp = baseExp + killExp + commanderBonus

        // 2. MCP 능력치 4개에 분배 (지휘/기동/공격/방어)
        distributeExperience(officer, totalExp, rng)

        // 3. 공적 포인트 부여
        val baseMerit = if (isVictory) MERIT_VICTORY else MERIT_DEFEAT
        val killMerit = (enemyUnitsDestroyed * MERIT_PER_KILL).coerceAtMost(50)
        officer.experience += baseMerit + killMerit

        log.debug(
            "Battle exp awarded: officer={} victory={} kills={} totalExp={} merit={}",
            officer.name, isVictory, enemyUnitsDestroyed, totalExp, baseMerit + killMerit,
        )
    }

    /**
     * 작전 완료 보너스.
     * gin7: 작전 계획 성공/부분 성공 시 추가 공적.
     */
    fun awardOperationBonus(officer: Officer, fullSuccess: Boolean) {
        val merit = if (fullSuccess) MERIT_OPERATION_FULL else MERIT_OPERATION_PARTIAL
        officer.experience += merit
        log.debug("Operation bonus: officer={} merit={} full={}", officer.name, merit, fullSuccess)
    }

    /**
     * 전투 참가자 전체에 일괄 경험치 부여.
     */
    fun awardBattleExperienceToAll(
        participants: List<Officer>,
        isVictory: Boolean,
        totalEnemyUnitsDestroyed: Int,
        commanderOfficerId: Long?,
        rng: Random = Random.Default,
    ) {
        val perOfficerKills = if (participants.isNotEmpty()) {
            totalEnemyUnitsDestroyed / participants.size
        } else 0

        for (officer in participants) {
            val isCommander = officer.id == commanderOfficerId
            awardBattleExperience(
                officer = officer,
                isVictory = isVictory,
                enemyUnitsDestroyed = if (isCommander) totalEnemyUnitsDestroyed else perOfficerKills,
                isCommander = isCommander,
                rng = rng,
            )
        }
    }

    /**
     * MCP 경험치를 4개 능력치에 분배.
     * 경험치가 100 이상이면 능력치 +1 후 리셋.
     */
    private fun distributeExperience(officer: Officer, totalExp: Int, rng: Random) {
        // 가중 분배: 전투에서는 공격/방어 비중 높음
        val weights = intArrayOf(25, 20, 30, 25) // 지휘, 기동, 공격, 방어
        val totalWeight = weights.sum()

        for (i in weights.indices) {
            val expForStat = (totalExp * weights[i] / totalWeight).coerceAtLeast(1)
            // 약간의 랜덤 변동
            val actualExp = expForStat + rng.nextInt(-1, 2)
            if (actualExp <= 0) continue

            when (i) {
                0 -> {
                    officer.commandExp = (officer.commandExp + actualExp).toShort()
                    if (officer.commandExp >= EXP_TO_STAT_THRESHOLD) {
                        officer.commandExp = (officer.commandExp - EXP_TO_STAT_THRESHOLD).toShort()
                        officer.command = (officer.command + 1).coerceAtMost(100).toShort()
                    }
                }
                1 -> {
                    officer.mobilityExp = (officer.mobilityExp + actualExp).toShort()
                    if (officer.mobilityExp >= EXP_TO_STAT_THRESHOLD) {
                        officer.mobilityExp = (officer.mobilityExp - EXP_TO_STAT_THRESHOLD).toShort()
                        officer.mobility = (officer.mobility + 1).coerceAtMost(100).toShort()
                    }
                }
                2 -> {
                    officer.attackExp = (officer.attackExp + actualExp).toShort()
                    if (officer.attackExp >= EXP_TO_STAT_THRESHOLD) {
                        officer.attackExp = (officer.attackExp - EXP_TO_STAT_THRESHOLD).toShort()
                        officer.attack = (officer.attack + 1).coerceAtMost(100).toShort()
                    }
                }
                3 -> {
                    officer.defenseExp = (officer.defenseExp + actualExp).toShort()
                    if (officer.defenseExp >= EXP_TO_STAT_THRESHOLD) {
                        officer.defenseExp = (officer.defenseExp - EXP_TO_STAT_THRESHOLD).toShort()
                        officer.defense = (officer.defense + 1).coerceAtMost(100).toShort()
                    }
                }
            }
        }
    }
}
