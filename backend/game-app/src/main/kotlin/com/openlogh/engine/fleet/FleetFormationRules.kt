package com.openlogh.engine.fleet

import org.springframework.stereotype.Service

/**
 * 함대 편성 제한 서비스.
 *
 * gin7 §6.12: 인구 비례 부대 편성 한도
 * gin7 §6.14: 독행함 이동 제한
 *
 * 실제 계산 로직은 [FleetFormationLimits] / [SoloShipRestriction] 에 위임한다.
 */
@Service
class FleetFormationRules {

    /**
     * 진영 총 인구에 따른 편성 한도 반환.
     *
     * gin7 §6.12:
     * - 함대: 인구 10억당 1개
     * - 수송함대: 함대의 2배
     * - 순찰대: 함대의 6배
     * - 지상부대: 함대의 3배
     *
     * @param totalPopulation 진영 총 인구 (만 명 단위)
     */
    fun maxFleetsByPopulation(totalPopulation: Long): FleetLimits {
        val capacity = FleetFormationLimits.calculateCapacity(totalPopulation)
        return FleetLimits(
            fleets = capacity.maxFleets,
            transports = capacity.maxTransports,
            patrols = capacity.maxPatrols,
            groundForces = capacity.maxGroundForces,
        )
    }

    /**
     * 독행함의 그리드 진입 가능 여부.
     *
     * gin7 §6.14:
     * - 적 유닛(독행함 제외)이 있는 그리드 진입 불가
     * - 적 행성 성계 진입 불가
     * - 예외: 아군 함선 유닛이 있고 전술전 중인 그리드는 진입 가능
     * - 독행함은 커맨드 레인지 없음
     *
     * @param gridHasEnemyUnits 그리드 내 독행함 제외 적 유닛 존재 여부
     * @param gridHasFriendlyUnits 그리드 내 아군 유닛 존재 여부
     * @param isTacticalBattle 해당 그리드에서 전술전 진행 중 여부
     */
    fun canLoneShipEnterGrid(
        gridHasEnemyUnits: Boolean,
        gridHasFriendlyUnits: Boolean,
        isTacticalBattle: Boolean,
    ): Boolean {
        // 아군이 전술전 중인 그리드는 예외적으로 진입 가능
        if (gridHasFriendlyUnits && isTacticalBattle) return true
        return !gridHasEnemyUnits
    }
}

/**
 * 편성 한도 결과 DTO.
 *
 * gin7 §6.12:
 * @param fleets 함대 최대 수
 * @param transports 수송함대 최대 수
 * @param patrols 순찰대 최대 수
 * @param groundForces 지상부대 최대 수
 */
data class FleetLimits(
    val fleets: Int,
    val transports: Int,
    val patrols: Int,
    val groundForces: Int,
)
