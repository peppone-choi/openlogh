package com.openlogh.engine.tactical

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Fortress gun system (要塞砲) — gin7 tactical combat Chapter 4.
 *
 * Fires along a line of sight, hitting ALL units in the path including friendlies.
 * Each unit in the path takes FULL damage (not split).
 *
 * Gun position: center-top of battle field (battleBoundsX/2, 0).
 *
 * 4 fortress gun types:
 * - 토르 해머 (이제르론): power=10000, cooldown=120 ticks
 * - 가이에스하켄 (가이에스부르크): power=7000, cooldown=90 ticks
 * - 아르테미스의 목걸이 (하이네센/케니히그라흐): power=3000, cooldown=60 ticks
 * - 경X선 빔포 (가르미슈/렌텐베르크): power=1500, cooldown=60 ticks
 */
enum class FortressGunType(
    val displayNameKo: String,
    val power: Int,
    val cooldownTicks: Int,
    val range: Int,
    val lineWidth: Double,
) {
    /** 토르 해머 — 이제르론 요새 전용 */
    THOR_HAMMER("토르 해머", power = 10_000, cooldownTicks = 120, range = 1000, lineWidth = 50.0),

    /** 가이에스하켄 — 가이에스부르크 요새 전용 */
    GAIESBURGHER("가이에스하켄", power = 7_000, cooldownTicks = 90, range = 1000, lineWidth = 45.0),

    /** 아르테미스의 목걸이 — 하이네센/케니히그라흐 요새 */
    ARTEMIS("아르테미스의 목걸이", power = 3_000, cooldownTicks = 60, range = 800, lineWidth = 35.0),

    /** 경X선 빔포 — 가르미슈/렌텐베르크 요새 */
    LIGHT_XRAY("경X선 빔포", power = 1_500, cooldownTicks = 60, range = 600, lineWidth = 25.0);

    companion object {
        /**
         * Determine gun type from power value.
         * Maps power threshold → gun type (highest matching threshold wins).
         */
        fun fromPower(power: Int): FortressGunType = when {
            power >= 10_000 -> THOR_HAMMER
            power >= 7_000  -> GAIESBURGHER
            power >= 3_000  -> ARTEMIS
            else            -> LIGHT_XRAY
        }
    }
}

/**
 * Handles fortress gun firing logic for one tick.
 * Extracted from TacticalBattleEngine for testability.
 */
class FortressGunSystem {

    /**
     * Process fortress gun fire for one tick.
     * Modifies [state] in-place (HP reduction + tickEvents).
     *
     * gin7 rule: 사선 통과 유닛 각각 전체 위력 적용 (분산 아님).
     * gin7 rule: 아군도 피격 — "[아군 피해!]" suffix in event detail.
     */
    fun processFortressGunFire(state: TacticalBattleState, rng: Random = Random) {
        if (state.fortressGunPower <= 0) return
        if (state.tickCount - state.fortressGunLastFired < state.fortressGunCooldown) return

        // 목표: 적 함대 중 함선 수 최대 함대
        val enemies = state.units.filter {
            it.isAlive && it.factionId != state.fortressFactionId && !it.isRetreating
        }
        if (enemies.isEmpty()) return

        val target = enemies.maxByOrNull { it.ships } ?: return

        // 포문 위치: 전장 중앙 상단
        val gunX = state.battleBoundsX / 2.0
        val gunY = 0.0

        val gunType = FortressGunType.fromPower(state.fortressGunPower)

        // 사선 위 전체 유닛 계산 (아군 포함)
        val unitsInPath = calculateLineOfFire(
            state = state,
            gunX = gunX,
            gunY = gunY,
            targetX = target.posX,
            targetY = target.posY,
            range = state.fortressGunRange.toDouble() * 100,
            lineWidth = gunType.lineWidth,
        )

        if (unitsInPath.isEmpty()) {
            // 사선 위 유닛 없어도 목표에게 직접 명중
            applyFortressHit(target, state.fortressGunPower, state, state.fortressFactionId)
        } else {
            // 사선 위 각 유닛에게 전체 위력 적용 (gin7: 분산 아님)
            for (hitUnit in unitsInPath) {
                applyFortressHit(hitUnit, state.fortressGunPower, state, state.fortressFactionId)
            }
        }

        state.fortressGunLastFired = state.tickCount
        state.tickEvents.add(
            BattleTickEvent(
                "fortress_fire",
                detail = "${gunType.displayNameKo} 발사 (${unitsInPath.size}유닛 피격)",
            )
        )
    }

    private fun applyFortressHit(
        unit: TacticalUnit,
        power: Int,
        state: TacticalBattleState,
        fortressFactionId: Long,
    ) {
        // 실드 흡수 적용
        val absorbed = (power * unit.energy.shieldAbsorption()).toInt()
        val finalDmg = (power - absorbed).coerceAtLeast(1)

        unit.hp -= finalDmg
        val shipLoss = (finalDmg.toDouble() / unit.maxHp.coerceAtLeast(1) * unit.maxShips).toInt()
        unit.ships = (unit.ships - shipLoss).coerceAtLeast(0)

        val isFriendlyFire = unit.factionId == fortressFactionId
        state.tickEvents.add(
            BattleTickEvent(
                type = "fortress_fire",
                targetUnitId = unit.fleetId,
                value = finalDmg,
                detail = "요새포 → ${unit.officerName} ($finalDmg 피해)" +
                    if (isFriendlyFire) " [아군 피해!]" else "",
            )
        )
    }

    /**
     * 사선 위 유닛 계산 — 포문에서 목표까지 직선, lineWidth 이내 유닛 전부.
     * gin7: 아군 포함 사선 위 모든 유닛 명중.
     */
    private fun calculateLineOfFire(
        state: TacticalBattleState,
        gunX: Double,
        gunY: Double,
        targetX: Double,
        targetY: Double,
        range: Double,
        lineWidth: Double,
    ): List<TacticalUnit> {
        return state.units.filter { unit ->
            if (!unit.isAlive || unit.isRetreating) return@filter false
            val dist = distanceToLine(unit.posX, unit.posY, gunX, gunY, targetX, targetY)
            val proj = projectOnLine(unit.posX, unit.posY, gunX, gunY, targetX, targetY)
            dist <= lineWidth && proj >= 0 && proj <= range
        }
    }

    /** Perpendicular distance from point (px, py) to line segment (lx1,ly1)→(lx2,ly2). */
    private fun distanceToLine(
        px: Double, py: Double,
        lx1: Double, ly1: Double,
        lx2: Double, ly2: Double,
    ): Double {
        val dx = lx2 - lx1
        val dy = ly2 - ly1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return sqrt((px - lx1) * (px - lx1) + (py - ly1) * (py - ly1))
        val t = ((px - lx1) * dx + (py - ly1) * dy) / lenSq
        val cx = lx1 + t.coerceIn(0.0, 1.0) * dx
        val cy = ly1 + t.coerceIn(0.0, 1.0) * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    /** Project point (px, py) onto line (lx1,ly1)→(lx2,ly2), return distance along line. */
    private fun projectOnLine(
        px: Double, py: Double,
        lx1: Double, ly1: Double,
        lx2: Double, ly2: Double,
    ): Double {
        val dx = lx2 - lx1
        val dy = ly2 - ly1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return 0.0
        return ((px - lx1) * dx + (py - ly1) * dy) / sqrt(lenSq)
    }
}
