package com.openlogh.engine.tactical

/**
 * 시야선 판정기 (射線判定).
 *
 * gin7 매뉴얼 10.15 — 빔/포/미사일 공격 시 공격선 상의 장애물 확인.
 *
 * 규칙:
 * - 공격선 상에 아군/적군 유닛이 있으면 공격 차단
 * - 소행성대(ASTEROID), 성운(NEBULA) 장애물도 공격 차단
 * - 미사일/함재기(interceptable 무기)는 LoS 면제 (우회 가능)
 * - 2D 선분-구 교차 판정 사용 (z축 평면 투영)
 */

/** 시야선 판정 결과 */
data class LineOfSightResult(
    /** true이면 시야선이 확보됨 (공격 가능) */
    val clear: Boolean,
    /** 차단 원인 설명 (null이면 차단 없음) */
    val blockedBy: String?,
)

object LineOfSightChecker {

    /**
     * 공격선 상 시야선 확인.
     *
     * gin7 10.15:
     * - 공격선 = attacker 위치에서 target 위치까지의 직선
     * - 해당 직선 위에 다른 유닛(아군·적군 모두) 또는 지형 장애물이 있으면 차단
     * - 유닛 충돌 반경: 단순화하여 고정값 사용 (gin7 원본은 함종별 크기)
     *
     * @param attacker 공격 유닛
     * @param target 공격 대상 유닛
     * @param allUnits 전장 내 모든 생존 유닛 목록 (attacker, target 포함)
     * @param obstacles 전장 장애물 목록
     * @param unitCollisionRadius 유닛 충돌 반경 (기본값: 함종 크기 기준)
     */
    fun check(
        attacker: TacticalUnit,
        target: TacticalUnit,
        allUnits: List<TacticalUnit>,
        obstacles: List<Obstacle>,
        unitCollisionRadius: Double = DEFAULT_UNIT_RADIUS,
    ): LineOfSightResult {
        val from = attacker.position()
        val to = target.position()

        // 1. 장애물(소행성/성운) 차단 확인
        for (obs in obstacles) {
            if (!obs.type.blocksLineOfFire) continue
            if (lineIntersectsSphere2D(from, to, obs.center, obs.radius)) {
                return LineOfSightResult(
                    clear = false,
                    blockedBy = "장애물(${obs.type.name}): (${obs.center.x.toInt()},${obs.center.y.toInt()})",
                )
            }
        }

        // 2. 유닛 차단 확인 (attacker/target 자신 제외)
        for (unit in allUnits) {
            if (!unit.isAlive()) continue
            if (unit.id == attacker.id || unit.id == target.id) continue

            val unitPos = unit.position()
            val radius = unitCollisionRadius * unit.shipClass.collisionRadiusMultiplier

            if (lineIntersectsSphere2D(from, to, unitPos, radius)) {
                val side = if (unit.factionId == attacker.factionId) "아군" else "적군"
                return LineOfSightResult(
                    clear = false,
                    blockedBy = "$side 유닛 차단: ${unit.shipClass.displayName}(id=${unit.id})",
                )
            }
        }

        return LineOfSightResult(clear = true, blockedBy = null)
    }

    /**
     * TacticalGrid를 사용하는 편의 메서드.
     * 장애물은 grid에서 가져오고, 유닛 목록은 별도 전달.
     */
    fun checkWithGrid(
        attacker: TacticalUnit,
        target: TacticalUnit,
        allUnits: List<TacticalUnit>,
        grid: TacticalGrid,
    ): LineOfSightResult = check(
        attacker = attacker,
        target = target,
        allUnits = allUnits,
        obstacles = grid.obstacles,
    )

    /**
     * 2D 선분-구 교차 판정 (x/y 평면 투영).
     *
     * gin7: 전술 전장은 기본적으로 2D 평면에서 전투가 이뤄지므로
     * z축을 무시한 수평면 투영으로 LoS 계산.
     *
     * 선분 p1→p2와 구(center, radius)의 교차 여부 반환.
     */
    internal fun lineIntersectsSphere2D(
        p1: Position,
        p2: Position,
        center: Position,
        radius: Double,
    ): Boolean {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val fx = p1.x - center.x
        val fy = p1.y - center.y

        val a = dx * dx + dy * dy
        if (a < 0.0001) return false // p1과 p2가 동일 위치

        val b = 2.0 * (fx * dx + fy * dy)
        val c = fx * fx + fy * fy - radius * radius

        var discriminant = b * b - 4.0 * a * c
        if (discriminant < 0) return false

        discriminant = Math.sqrt(discriminant)
        val t1 = (-b - discriminant) / (2.0 * a)
        val t2 = (-b + discriminant) / (2.0 * a)

        // 교차점이 선분 [0,1] 구간 내에 있어야 함
        return (t1 in 0.0..1.0) || (t2 in 0.0..1.0) || (t1 < 0.0 && t2 > 1.0)
    }

    /** 기본 유닛 충돌 반경 (distance units) */
    private const val DEFAULT_UNIT_RADIUS = 15.0
}

// ===== TacticalShipClass 확장 =====

/** 함종별 충돌 반경 배율 (기본값 1.0 = DEFAULT_UNIT_RADIUS) */
val TacticalShipClass.collisionRadiusMultiplier: Double
    get() = when (this) {
        TacticalShipClass.FORTRESS -> 5.0                       // 요새: 매우 큰 차단 범위
        TacticalShipClass.BATTLESHIP,
        TacticalShipClass.FAST_BATTLESHIP -> 1.5                // 전함/고속전함: 큼
        TacticalShipClass.ASSAULT_SHIP -> 1.3                   // 강습양륙함
        TacticalShipClass.CARRIER,
        TacticalShipClass.TORPEDO_CARRIER -> 1.4                // 항공모함/뇌격정모함
        TacticalShipClass.CRUISER,
        TacticalShipClass.STRIKE_CRUISER -> 1.0                 // 순양함: 기준
        TacticalShipClass.DESTROYER -> 0.7                      // 구축함: 작음
        TacticalShipClass.TRANSPORT,
        TacticalShipClass.HOSPITAL -> 1.2                       // 수송함/병원선
        TacticalShipClass.ENGINEERING -> 0.9                    // 공작함
    }
