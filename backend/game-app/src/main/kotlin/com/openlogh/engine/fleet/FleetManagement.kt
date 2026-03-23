package com.openlogh.engine.fleet

/**
 * 함대 관리 확장 시스템.
 *
 * gin7 매뉴얼 기반:
 * - 승무원 4등급 (엘리트/베테랑/노멀/그린)
 * - 항속 (연료) 시스템
 * - 함종 서브타입 (I~VIII형)
 * - 독행함 제한
 * - 부대 편성 제한 (인구 비례)
 */

// ===== 승무원 등급 =====

enum class CrewGrade(
    val code: String,
    val displayName: String,
    /** 전투력 배율 */
    val combatMultiplier: Double,
    /** 기동력 배율 */
    val mobilityMultiplier: Double,
    /** 사기 회복 속도 배율 */
    val moraleRecoveryRate: Double,
) {
    /** 엘리트: 최정예. 전투/기동 우수, 보충 불가 (전투 경험으로만 승급) */
    ELITE("elite", "엘리트", 1.3, 1.2, 1.3),
    /** 베테랑: 숙련. 실전 경험 풍부 */
    VETERAN("veteran", "베테랑", 1.15, 1.1, 1.15),
    /** 노멀: 일반 승무원. 기본 훈련 이수 */
    NORMAL("normal", "노멀", 1.0, 1.0, 1.0),
    /** 그린: 신병. 모병 시 기본 등급. 훈련으로 노멀 승급 */
    GREEN("green", "그린", 0.8, 0.9, 0.8),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): CrewGrade = byCode[code] ?: NORMAL
    }
}

// ===== 함종 서브타입 =====

/**
 * 함종 서브타입 (I~VIII형).
 * 세대가 높을수록 성능 향상, 건조 비용 증가.
 */
data class ShipSubtype(
    val generation: Int, // I=1, II=2, ..., VIII=8
    val attackModifier: Double,
    val defenseModifier: Double,
    val speedModifier: Double,
    val buildCost: Int,
) {
    val displayName: String get() = "${toRoman(generation)}형"

    companion object {
        /** 세대별 기본 스탯 배율 생성 */
        fun forGeneration(gen: Int): ShipSubtype {
            val g = gen.coerceIn(1, 8)
            val bonus = (g - 1) * 0.05 // 세대당 +5%
            return ShipSubtype(
                generation = g,
                attackModifier = 1.0 + bonus,
                defenseModifier = 1.0 + bonus,
                speedModifier = 1.0 + bonus * 0.5,
                buildCost = 100 + (g - 1) * 30, // 기본 100, 세대당 +30
            )
        }

        fun allGenerations(): List<ShipSubtype> = (1..8).map { forGeneration(it) }

        private fun toRoman(n: Int): String = when (n) {
            1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"
            5 -> "V"; 6 -> "VI"; 7 -> "VII"; 8 -> "VIII"
            else -> n.toString()
        }
    }
}

// ===== 항속 (연료) =====

/**
 * 항속 시스템.
 * 워프 항행 시 항속(연료) 소비. 100 미만이면 워프 불가.
 */
object FuelSystem {

    /** 워프 불가 최소 항속 */
    const val MIN_FUEL_FOR_WARP = 100

    /** 그리드 1칸 워프 시 기본 소비 항속 */
    const val FUEL_PER_GRID = 50

    /**
     * 워프 가능 여부.
     * @param currentFuel 현재 항속
     * @param gridDistance 이동 그리드 수
     */
    fun canWarp(currentFuel: Int, gridDistance: Int = 1): Boolean =
        currentFuel >= MIN_FUEL_FOR_WARP && currentFuel >= gridDistance * FUEL_PER_GRID

    /**
     * 워프 시 연료 소비.
     * @return 소비 후 잔여 연료. -1이면 연료 부족.
     */
    fun consumeWarpFuel(currentFuel: Int, gridDistance: Int = 1): Int {
        val cost = gridDistance * FUEL_PER_GRID
        if (currentFuel < cost || currentFuel < MIN_FUEL_FOR_WARP) return -1
        return currentFuel - cost
    }

    /**
     * 보급 시 연료 회복.
     * @param maxFuel 최대 항속 (함종/서브타입에 따라 다름)
     */
    fun refuel(currentFuel: Int, maxFuel: Int, amount: Int): Int =
        (currentFuel + amount).coerceAtMost(maxFuel)
}

// ===== 독행함 제한 =====

/**
 * 독행함(旗艦 단독) 이동 제한 규칙.
 *
 * gin7: 독행함은 적 유닛 또는 적 행성/요새가 있는 그리드에 진입 불가.
 * 단, 아군 함선이 있어서 전술전 중인 그리드는 진입 가능.
 */
object SoloShipRestriction {

    /**
     * 독행함이 해당 그리드에 진입 가능한지.
     * @param isSoloShip 독행함 여부
     * @param hasEnemyUnits 해당 그리드에 적 유닛 존재 여부
     * @param hasEnemyPlanet 해당 그리드에 적 행성/요새 존재 여부
     * @param hasAllyUnitsInBattle 아군 유닛이 해당 그리드에서 전술전 중인지
     */
    fun canEnterGrid(
        isSoloShip: Boolean,
        hasEnemyUnits: Boolean,
        hasEnemyPlanet: Boolean,
        hasAllyUnitsInBattle: Boolean,
    ): Boolean {
        if (!isSoloShip) return true
        if (hasAllyUnitsInBattle) return true
        return !hasEnemyUnits && !hasEnemyPlanet
    }
}

// ===== 부대 편성 제한 =====

/**
 * 인구 비례 부대 편성 제한.
 *
 * gin7: 함대 = 인구 10억당 1개, 순찰대 = 함대의 6배,
 *       수송함대 = 함대의 2배, 지상부대 = 함대의 3배
 */
object FleetFormationLimits {

    /** 10억 인구당 함대 수 */
    const val FLEET_PER_BILLION = 1
    const val PATROL_MULTIPLIER = 6
    const val TRANSPORT_MULTIPLIER = 2
    const val GROUND_MULTIPLIER = 3

    /** 1 그리드당 최대 유닛 수 (1진영 기준) */
    const val MAX_UNITS_PER_GRID = 300

    data class FormationCapacity(
        val maxFleets: Int,
        val maxPatrols: Int,
        val maxTransports: Int,
        val maxGroundForces: Int,
    )

    /**
     * 인구 기반 편성 한도 계산.
     * @param totalPopulation 진영 총 인구 (만 명 단위)
     */
    fun calculateCapacity(totalPopulation: Long): FormationCapacity {
        val billions = (totalPopulation / 10000.0).coerceAtLeast(1.0) // 만명→억 환산
        val maxFleets = (billions * FLEET_PER_BILLION).toInt().coerceAtLeast(1)
        return FormationCapacity(
            maxFleets = maxFleets,
            maxPatrols = maxFleets * PATROL_MULTIPLIER,
            maxTransports = maxFleets * TRANSPORT_MULTIPLIER,
            maxGroundForces = maxFleets * GROUND_MULTIPLIER,
        )
    }

    /**
     * 그리드 유닛 수 제한 체크.
     * @param currentUnits 현재 그리드의 해당 진영 유닛 수
     * @param incomingUnits 진입하려는 유닛 수
     */
    fun canEnterGrid(currentUnits: Int, incomingUnits: Int): Boolean =
        currentUnits + incomingUnits <= MAX_UNITS_PER_GRID
}

// ===== 뇌격정모함 + 공작함 (추가 함종) =====

/**
 * 추가 함종 정의.
 * TacticalShipClass에는 이미 정의된 것 외에 추가되는 함종의 참조 데이터.
 */
object AdditionalShipTypes {
    /** 뇌격정모함: 제국 전용. 어뢰정 운용, 대함 특화 */
    const val TORPEDO_CARRIER_CODE = "torpedo_carrier"
    const val TORPEDO_CARRIER_NAME = "뇌격정모함"

    /** 공작함: 전투 중 기함 수리 */
    const val ENGINEERING_SHIP_CODE = "engineering_ship"
    const val ENGINEERING_SHIP_NAME = "공작함"
}
