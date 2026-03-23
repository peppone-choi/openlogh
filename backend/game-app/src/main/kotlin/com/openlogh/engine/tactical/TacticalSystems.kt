package com.openlogh.engine.tactical

import kotlin.math.sqrt

/**
 * 전술 게임 보완 시스템 모음.
 *
 * gin7 매뉴얼 기반:
 * - 색적 (索敵) / Fog of War
 * - 커맨드 레인지 서클 (コマンドレンジ)
 * - 태세 변경 (4종)
 * - 추가 전술 커맨드 (정지, 반전, 평행이동, 선회, 출격)
 */

// ===== 색적 시스템 (Fog of War) =====

/**
 * 색적 시스템.
 *
 * gin7:
 * - 모든 기함/함선/방어사령부는 색적 능력 보유
 * - SENSOR 에너지 + 정보(intelligence) 능력치가 색적 범위/정밀도 결정
 * - 정지 유닛은 집중 색적 → 정밀도 향상
 * - 정지 유닛은 전자전 → 색적 회피 향상
 * - 색적 정보는 아군 공유
 */
data class DetectionResult(
    val targetUnitId: Int,
    val detected: Boolean,
    /** 정밀도: 0 = 미탐지, 1 = 방향만, 2 = 대략 위치, 3 = 정확 위치+함종 */
    val precision: Int,
)

object ReconSystem {

    /** 기본 색적 범위 (distance units) */
    private const val BASE_DETECTION_RANGE = 400.0
    /** SENSOR 1% 당 색적 범위 추가 */
    private const val SENSOR_RANGE_PER_POINT = 5.0
    /** 정지 시 집중 색적 보너스 */
    private const val STATIONARY_BONUS = 1.5
    /** 정지 시 색적 회피 보너스 */
    private const val STATIONARY_EVASION_BONUS = 1.3

    /**
     * 유닛의 색적 범위 계산.
     */
    fun getDetectionRange(
        sensorEnergy: Int,
        intelligenceStat: Int,
        isStationary: Boolean,
    ): Double {
        val base = BASE_DETECTION_RANGE + sensorEnergy * SENSOR_RANGE_PER_POINT + intelligenceStat * 2.0
        return if (isStationary) base * STATIONARY_BONUS else base
    }

    /**
     * 유닛의 색적 회피력 계산.
     */
    fun getStealthValue(
        shipClass: TacticalShipClass,
        isStationary: Boolean,
    ): Double {
        // 작은 함선일수록 탐지 어려움
        val baseStealth = when (shipClass) {
            TacticalShipClass.DESTROYER -> 1.3
            TacticalShipClass.CRUISER, TacticalShipClass.STRIKE_CRUISER -> 1.1
            TacticalShipClass.TRANSPORT, TacticalShipClass.ENGINEERING -> 1.2
            TacticalShipClass.FORTRESS -> 0.3 // 요새는 무조건 탐지
            else -> 1.0
        }
        return if (isStationary) baseStealth * STATIONARY_EVASION_BONUS else baseStealth
    }

    /**
     * 색적 판정.
     * @param distance 탐지자와 대상 사이 거리
     * @param detectionRange 탐지자의 색적 범위
     * @param targetStealth 대상의 색적 회피값
     * @return 탐지 정밀도 (0~3)
     */
    fun detectUnit(
        distance: Double,
        detectionRange: Double,
        targetStealth: Double,
    ): Int {
        if (distance > detectionRange) return 0

        val ratio = 1.0 - (distance / detectionRange)
        val effectiveRatio = ratio / targetStealth

        return when {
            effectiveRatio >= 0.7 -> 3 // 정확 위치 + 함종
            effectiveRatio >= 0.4 -> 2 // 대략 위치
            effectiveRatio >= 0.1 -> 1 // 방향만
            else -> 0
        }
    }

    /**
     * 전 유닛 색적 처리.
     * 아군 유닛들의 색적 결과를 통합 (최대 정밀도 채택).
     */
    fun processDetection(
        session: TacticalBattleSession,
        factionId: Long,
        sensorEnergy: Int,
        intelligenceStat: Int,
    ): Map<Int, DetectionResult> {
        val myUnits = session.allUnits().filter { it.factionId == factionId && it.isAlive() }
        val enemyUnits = session.allUnits().filter { it.factionId != factionId && it.isAlive() }
        val results = mutableMapOf<Int, DetectionResult>()

        for (enemy in enemyUnits) {
            var bestPrecision = 0
            for (ally in myUnits) {
                val range = getDetectionRange(sensorEnergy, intelligenceStat, isStationary = false)
                val stealth = getStealthValue(enemy.shipClass, isStationary = false)
                val distance = ally.position().distanceTo(enemy.position())
                val precision = detectUnit(distance, range, stealth)
                if (precision > bestPrecision) bestPrecision = precision
            }
            results[enemy.id] = DetectionResult(enemy.id, bestPrecision > 0, bestPrecision)
        }

        return results
    }
}

// ===== 커맨드 레인지 서클 =====

/**
 * 커맨드 레인지 서클.
 *
 * gin7:
 * - 기함 중심 원형 지휘 범위
 * - 최대 반경 = 기함 능력
 * - 확대율 = 지휘(command) 파라미터
 * - 명령 발령 시 반경 0으로 리셋, 시간 경과로 다시 확대
 * - 범위 밖 유닛은 기존 명령 수행 계속, 새 명령 불가
 * - 사기 혼란 유닛도 명령 불가
 */
data class CommandRange(
    /** 현재 반경 (distance units) */
    var currentRadius: Double = 0.0,
    /** 최대 반경 */
    val maxRadius: Double = 300.0,
    /** 확대 속도 (턴당 distance units) */
    val expansionRate: Double = 50.0,
)

object CommandRangeSystem {

    /** 기본 최대 반경 */
    private const val BASE_MAX_RADIUS = 200.0
    /** 지휘 능력치 1당 추가 최대 반경 */
    private const val COMMAND_RADIUS_PER_POINT = 3.0
    /** 기본 확대 속도 (턴당) */
    private const val BASE_EXPANSION_RATE = 30.0
    /** 지휘 능력치 1당 추가 확대 속도 */
    private const val COMMAND_EXPANSION_PER_POINT = 0.5

    /** 장교 스탯으로 커맨드 레인지 생성 */
    fun createForOfficer(officer: OfficerStats): CommandRange {
        return CommandRange(
            currentRadius = 0.0,
            maxRadius = BASE_MAX_RADIUS + officer.command * COMMAND_RADIUS_PER_POINT,
            expansionRate = BASE_EXPANSION_RATE + officer.command * COMMAND_EXPANSION_PER_POINT,
        )
    }

    /** 턴 시작 시 레인지 확대 */
    fun expandRange(range: CommandRange) {
        range.currentRadius = (range.currentRadius + range.expansionRate).coerceAtMost(range.maxRadius)
    }

    /** 명령 발령 시 레인지 리셋 */
    fun onCommandIssued(range: CommandRange) {
        range.currentRadius = 0.0
    }

    /** 유닛이 커맨드 레인지 내에 있는지 */
    fun isInRange(flagshipPos: Position, unitPos: Position, range: CommandRange): Boolean {
        return flagshipPos.distanceTo(unitPos) <= range.currentRadius
    }

    /** 유닛에 명령 가능한지 (레인지 + 사기 체크) */
    fun canCommand(
        flagshipPos: Position,
        unitPos: Position,
        range: CommandRange,
        unitMorale: Int,
    ): Boolean {
        if (unitMorale <= 20) return false // 혼란 상태
        return isInRange(flagshipPos, unitPos, range)
    }
}

// ===== 태세 변경 (4종) =====

/**
 * 함대 태세.
 *
 * gin7:
 * - 항행 (CRUISE): 이동 중. 색적 회피 낮음, 기동성 보통
 * - 정박 (ANCHOR): 정지. 색적 회피 높음, 집중 색적
 * - 주류 (STATION): 행성/요새 주둔. 색적 보너스, 방어 보너스
 * - 전투 (COMBAT): 전투 태세. 무기 사용 가능, 색적 보통
 */
enum class FleetPosture(
    val code: String,
    val displayName: String,
    val speedMultiplier: Double,
    val detectionMultiplier: Double,
    val stealthMultiplier: Double,
    val defenseMultiplier: Double,
    val canAttack: Boolean,
) {
    CRUISE("cruise", "항행", 1.0, 0.8, 0.8, 0.8, false),
    ANCHOR("anchor", "정박", 0.0, 1.5, 1.3, 1.0, false),
    STATION("station", "주류", 0.0, 1.3, 1.5, 1.2, true),
    COMBAT("combat", "전투", 0.8, 1.0, 1.0, 1.0, true),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): FleetPosture = byCode[code] ?: COMBAT
    }
}

// ===== 추가 전술 커맨드 =====

/**
 * 확장된 전술 명령 타입.
 * 기존 OrderType에 추가할 명령들.
 */
enum class ExtendedOrderType(val code: String, val displayName: String) {
    /** 전 유닛 정지 */
    HALT("halt", "정지"),
    /** 180도 회두 */
    REVERSE("reverse", "반전"),
    /** 정면 유지 측방 이동 (속도 50%) */
    STRAFE("strafe", "평행이동"),
    /** 선회 (각도 지정) */
    TURN("turn", "선회"),
    /** 행성/요새 주류 유닛 출격 (대기 5초, 소요 20초/유닛) */
    SORTIE("sortie", "출격"),
    /** 전투정 출격 */
    LAUNCH_FIGHTERS("launch_fighters", "공전 출격"),
    /** 태세 변경 */
    POSTURE_CHANGE("posture_change", "태세 변경"),
}

// ===== WARP 에너지 채널 =====

/**
 * 6채널 에너지 분배 (gin7 원본).
 * 기존 5채널 + WARP 추가.
 *
 * WARP 에너지: 전술 게임에서 철퇴 시 필요.
 * 최대값 할당 시 맵 원외에서 철퇴 가능.
 */
data class FullEnergyAllocation(
    val beam: Int,
    val gun: Int,
    val shield: Int,
    val engine: Int,
    val warp: Int,
    val sensor: Int,
) {
    init {
        require(beam + gun + shield + engine + warp + sensor == 100) {
            "에너지 총합은 100이어야 합니다 (현재: ${beam + gun + shield + engine + warp + sensor})"
        }
    }

    /** 기존 5채널 EnergyAllocation으로 변환 (warp는 engine에 합산) */
    fun toBasicAllocation(): EnergyAllocation = EnergyAllocation(
        beam = beam, gun = gun, shield = shield,
        engine = engine + warp, sensor = sensor,
    )

    /** 철퇴 가능 여부 (warp == 최대 가능값) */
    fun canRetreat(): Boolean = warp >= 30

    companion object {
        val BALANCED = FullEnergyAllocation(beam = 17, gun = 17, shield = 17, engine = 17, warp = 15, sensor = 17)
        val RETREAT_READY = FullEnergyAllocation(beam = 10, gun = 10, shield = 15, engine = 10, warp = 40, sensor = 15)
        val COMBAT = FullEnergyAllocation(beam = 25, gun = 20, shield = 20, engine = 15, warp = 5, sensor = 15)
    }
}

// ===== gin7 진형 (7종) =====

/**
 * gin7 원본 진형 7종.
 * 기존 Formation(6종)과의 매핑.
 *
 * gin7: 紡錘 / 艦種1 / 艦種2 / 混成1 / 混成2 / 三列 / 隊列解除
 */
enum class Gin7Formation(
    val code: String,
    val displayName: String,
    val description: String,
    val attackBonus: Double,
    val defenseBonus: Double,
    val mobilityBonus: Double,
) {
    /** 방추형: 돌파 특화 */
    SPINDLE("spindle", "紡錘(방추)", "중앙 돌파 진형", 1.3, 1.0, 1.0),
    /** 함종별1: 함종별 분류 배치 (전함 전면) */
    BY_CLASS_1("by_class_1", "艦種1(함종1)", "전함 전면, 순양함 측면", 1.1, 1.15, 0.95),
    /** 함종별2: 함종별 분류 배치 (구축함 전면) */
    BY_CLASS_2("by_class_2", "艦種2(함종2)", "구축함 전면, 전함 후방", 1.0, 1.0, 1.15),
    /** 혼성1: 혼합 배치 (균형) */
    MIXED_1("mixed_1", "混成1(혼성1)", "균형 혼합 배치", 1.05, 1.05, 1.05),
    /** 혼성2: 혼합 배치 (방어 강화) */
    MIXED_2("mixed_2", "混成2(혼성2)", "방어 강화 혼합 배치", 0.95, 1.2, 1.0),
    /** 삼열: 3열 종대 */
    THREE_COLUMN("three_column", "三列(삼열)", "3열 종대 진형", 1.0, 1.1, 1.1),
    /** 대열 해제: 자유 기동 */
    DISPERSED("dispersed", "隊列解除(해제)", "대열 해제, 자유 기동", 0.85, 0.85, 1.3),
    ;

    /** 기존 Formation으로 근사 매핑 */
    fun toFormation(): Formation = when (this) {
        SPINDLE -> Formation.SPINDLE
        BY_CLASS_1 -> Formation.SQUARE
        BY_CLASS_2 -> Formation.ECHELON
        MIXED_1 -> Formation.WHEEL
        MIXED_2 -> Formation.CRANE_WING
        THREE_COLUMN -> Formation.CRANE_WING
        DISPERSED -> Formation.DISPERSED
    }

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): Gin7Formation = byCode[code] ?: SPINDLE
    }
}
