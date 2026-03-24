package com.openlogh.engine.planet

import com.openlogh.entity.Planet
import org.springframework.stereotype.Service

/**
 * 행성 유형별 지상군 배치 규칙.
 *
 * gin7 §5.14:
 * - 통상 행성: 장갑병/장갑척탄병/경장육전병 모두 참전 가능
 * - 가스 행성/요새: 장갑척탄병/경장육전병만 참전 (장갑병 불가)
 *
 * 행성 유형은 Planet.meta["planetType"] 에 저장된 코드로 결정.
 */
@Service
class PlanetTypeRules {

    /** 행성 유형 */
    enum class PlanetType {
        /** 통상 행성: 모든 지상군 배치 가능 */
        NORMAL,
        /** 가스 행성: 장갑병 불가, 척탄병/경장보병만 가능 */
        GAS_GIANT,
        /** 요새(이제르론/가이에스부르크 등): 장갑병 불가 */
        FORTRESS,
    }

    /** 지상군 병종 */
    enum class GroundUnitType {
        /** 장갑병 — 통상 행성 전용 */
        ARMORED,
        /** 장갑척탄병 — 모든 행성 가능 */
        GRENADIER,
        /** 경장육전병 — 모든 행성 가능 */
        LIGHT_INFANTRY,
    }

    /**
     * 해당 행성 유형에 병종 배치 가능 여부.
     *
     * gin7 §5.14:
     * - NORMAL: 전 병종 가능
     * - GAS_GIANT, FORTRESS: 장갑병(ARMORED) 불가
     */
    fun canDeployGroundUnit(planetType: PlanetType, unitType: GroundUnitType): Boolean =
        when (planetType) {
            PlanetType.NORMAL -> true
            PlanetType.GAS_GIANT, PlanetType.FORTRESS -> unitType != GroundUnitType.ARMORED
        }

    /**
     * Planet 엔티티에서 행성 유형 추출.
     *
     * Planet.meta["planetType"] 코드:
     * - "gas_giant" → GAS_GIANT
     * - "fortress"  → FORTRESS
     * - 그 외        → NORMAL
     */
    fun getPlanetType(planet: Planet): PlanetType {
        val code = planet.meta["planetType"] as? String ?: ""
        return when (code) {
            "gas_giant" -> PlanetType.GAS_GIANT
            "fortress"  -> PlanetType.FORTRESS
            else        -> PlanetType.NORMAL
        }
    }
}
