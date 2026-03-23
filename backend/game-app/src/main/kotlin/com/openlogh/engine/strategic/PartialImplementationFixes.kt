package com.openlogh.engine.strategic

import com.openlogh.engine.tactical.EnergyAllocation
import com.openlogh.engine.tactical.Position
import kotlin.random.Random

/**
 * 부분 구현 보완 (#75-86).
 * 기존 코드에 누락된 로직을 보충하는 유틸리티.
 */

// ===== #75: 워프 항행 오차 =====

/**
 * 워프 오차 시스템.
 * gin7: 워프 시 목적지에 정확히 도착하지 않고, 능력치에 따라 오차 발생.
 */
object WarpNavigation {
    /**
     * 워프 오차 계산.
     * @param mobilityStat 장교 기동 능력치
     * @param distance 이동 그리드 수
     * @return 오차 (그리드 수). 0이면 정확 도착
     */
    fun calculateWarpDeviation(mobilityStat: Int, distance: Int, rng: Random): Int {
        if (distance <= 1) return 0
        // 기동 능력이 높을수록 오차 감소
        val deviationChance = 0.3 - mobilityStat * 0.003
        if (rng.nextDouble() >= deviationChance) return 0
        // 최대 오차 = 이동 거리의 25%
        val maxDev = (distance * 0.25).toInt().coerceAtLeast(1)
        return rng.nextInt(1, maxDev + 1)
    }
}

// ===== #77: WARP 에너지 철퇴 조건 =====

/**
 * 철퇴 조건 체크.
 * gin7: 레이더 원외 위치 + WARP 에너지 최대 + 준비시간 2.5분
 */
object RetreatConditions {
    /** 레이더 원외 여부 (맵 가장자리 근처) */
    fun isAtMapEdge(position: Position, fieldSize: Double, edgeMargin: Double = 50.0): Boolean {
        return position.x <= edgeMargin || position.x >= fieldSize - edgeMargin ||
            position.y <= edgeMargin || position.y >= fieldSize - edgeMargin
    }

    /** WARP 에너지 충분 여부 */
    fun hasWarpEnergy(energy: EnergyAllocation): Boolean {
        // 기존 5채널 시스템에서는 engine에 warp가 합산되어 있음
        // engine이 30 이상이면 WARP 충분으로 간주
        return energy.engine >= 30
    }

    /** 철퇴 준비 시간 (턴 단위) */
    const val RETREAT_PREPARATION_TURNS = 1

    /** 철퇴 가능 여부 종합 판정 */
    fun canRetreat(
        position: Position,
        fieldSize: Double,
        energy: EnergyAllocation,
        preparationTurnsElapsed: Int,
    ): Boolean {
        return isAtMapEdge(position, fieldSize) &&
            hasWarpEnergy(energy) &&
            preparationTurnsElapsed >= RETREAT_PREPARATION_TURNS
    }
}

// ===== #80: 기함 격침 시 전사 판정 =====

/**
 * 전사 판정 시스템.
 * gin7: 기함 격침 시 캐릭터 전사 가능성
 */
object DeathJudgment {
    /** 기함 격침 시 전사 확률 */
    private const val BASE_DEATH_CHANCE = 0.15
    /** 행성/요새 주류 시 전사 확률 0% */
    private const val STATIONED_DEATH_CHANCE = 0.0

    /**
     * 전사 판정.
     * @param isStationed 행성/요새 주류 중인지
     * @param flagshipDefense 기함 방어력 (높을수록 생존 확률 증가)
     * @return true = 전사
     */
    fun judgeDeath(
        isStationed: Boolean,
        flagshipDefense: Int,
        rng: Random,
    ): Boolean {
        if (isStationed) return false
        val chance = BASE_DEATH_CHANCE - flagshipDefense * 0.001
        return rng.nextDouble() < chance.coerceIn(0.01, 0.30)
    }

    /** 부상 판정 (전사 안 했을 때) */
    fun judgeInjury(flagshipDefense: Int, rng: Random): Int {
        val injuryChance = 0.3 - flagshipDefense * 0.002
        if (rng.nextDouble() >= injuryChance.coerceIn(0.05, 0.50)) return 0
        return rng.nextInt(10, 60) // 부상도 10~60
    }
}

// ===== #82: 작전 계획 판정 =====

/**
 * 작전 계획 시스템.
 * gin7: 작전 발동 후 30게임일 경과 시 판정.
 */
enum class OperationType(val code: String, val displayName: String) {
    CAPTURE("capture", "점령 작전"),
    DEFEND("defend", "방위 작전"),
    SWEEP("sweep", "소탕 작전"),
}

data class OperationPlan(
    val type: OperationType,
    val targetGridId: Long,
    val participatingUnitCount: Int,
    val startTurn: Int,
    /** 작전 기간 (턴) */
    val duration: Int = 30, // 30게임일
    var status: OperationStatus = OperationStatus.PLANNED,
)

enum class OperationStatus {
    PLANNED,    // 계획됨
    ACTIVE,     // 발동 중
    SUCCEEDED,  // 성공
    FAILED,     // 실패
    CANCELLED,  // 취소됨
}

object OperationJudgment {
    /**
     * 작전 성공 판정 (30일 경과 시).
     */
    fun judgeOperation(plan: OperationPlan, currentState: OperationState): OperationStatus {
        return when (plan.type) {
            OperationType.CAPTURE -> {
                when {
                    currentState.allPlanetsOccupied -> OperationStatus.SUCCEEDED
                    currentState.anyPlanetOccupied -> OperationStatus.SUCCEEDED // 부분 성공도 성공
                    else -> OperationStatus.FAILED
                }
            }
            OperationType.DEFEND -> {
                when {
                    currentState.allPlanetsDefended -> OperationStatus.SUCCEEDED
                    currentState.anyPlanetLost -> OperationStatus.FAILED
                    else -> OperationStatus.SUCCEEDED
                }
            }
            OperationType.SWEEP -> {
                if (currentState.enemyUnitsCleared) OperationStatus.SUCCEEDED
                else OperationStatus.FAILED
            }
        }
    }

    data class OperationState(
        val allPlanetsOccupied: Boolean = false,
        val anyPlanetOccupied: Boolean = false,
        val allPlanetsDefended: Boolean = false,
        val anyPlanetLost: Boolean = false,
        val enemyUnitsCleared: Boolean = false,
    )
}

// ===== #83: 계급 래더/자동 승진 =====

/**
 * 자동 승진 시스템.
 * gin7: 공적 포인트 래더, 30게임일마다 자동 승진 판정.
 */
object AutoPromotionSystem {
    /** 자동 승진 주기 (게임일) */
    const val PROMOTION_CYCLE_DAYS = 30

    /** 계급별 승진 필요 공적 */
    val promotionThresholds = mapOf(
        0 to 50,    // 소위→대위
        1 to 100,   // 대위→소령
        2 to 200,   // 소령→중령
        3 to 350,   // 중령→대령
        4 to 550,   // 대령→준장
        5 to 800,   // 준장→소장
        6 to 1100,  // 소장→중장
        7 to 1500,  // 중장→대장
        8 to 2000,  // 대장→상급대장
        9 to 3000,  // 상급대장→원수
    )

    /**
     * 승진 가능 여부.
     * @param currentRank 현재 계급 (0~10)
     * @param merit 공적 포인트
     * @param rankSlotAvailable 상위 계급에 빈자리가 있는지
     */
    fun canPromote(currentRank: Int, merit: Int, rankSlotAvailable: Boolean): Boolean {
        if (currentRank >= 10) return false
        if (!rankSlotAvailable) return false
        val threshold = promotionThresholds[currentRank] ?: return false
        return merit >= threshold
    }
}

// ===== #84: 행성 점령 상세 후처리 =====

/**
 * gin7 6단계 행성 점령 후처리.
 */
object DetailedOccupationProcess {
    /**
     * 점령 후 처리 단계 목록.
     */
    data class OccupationResult(
        /** 1. 방어측 직무카드 상실 목록 */
        val revokedPositionCards: List<String> = emptyList(),
        /** 2. 수비대 항복/탈출 */
        val garrisonSurrendered: Boolean = false,
        val garrisonEscaped: Int = 0,
        /** 3. 건조 중 함선 파괴 */
        val shipsUnderConstructionDestroyed: Int = 0,
        /** 4. 시설 접수 */
        val facilitiesCaptured: Int = 0,
        /** 5. 행성 직할 전환 */
        val directControlApplied: Boolean = false,
        /** 6. 지지율 리셋 */
        val approvalReset: Boolean = true,
    )

    fun processOccupation(
        isCapital: Boolean,
        hasArsenal: Boolean,
        garrisonStrength: Int,
        rng: Random,
    ): OccupationResult {
        val garrisonSurrendered = garrisonStrength <= 0 || rng.nextDouble() < 0.7
        val escaped = if (!garrisonSurrendered) (garrisonStrength * 0.3).toInt() else 0
        val shipsDestroyed = if (hasArsenal) rng.nextInt(1, 5) else 0

        return OccupationResult(
            garrisonSurrendered = garrisonSurrendered,
            garrisonEscaped = escaped,
            shipsUnderConstructionDestroyed = shipsDestroyed,
            facilitiesCaptured = if (garrisonSurrendered) 1 else 0,
            directControlApplied = isCapital,
        )
    }
}
