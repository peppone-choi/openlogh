package com.openlogh.engine.tactical

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.random.Random

/**
 * NPC 장교용 전술 AI.
 * 매 턴 자동으로 TacticalOrder를 생성하여 NPC 함대를 지휘.
 *
 * AI 성향(Personality)은 장교 스탯으로 결정:
 * - 공격형: attack ≥ defense → 적극 접근 + 공격
 * - 방어형: defense > attack → 거리 유지 + 방어진형
 * - 기동형: mobility 최고 → 측면 기동 + 우회
 */
@Component
class TacticalAI {

    companion object {
        private val log = LoggerFactory.getLogger(TacticalAI::class.java)

        /** AI가 적을 인식하는 최대 거리 */
        private const val AWARENESS_RANGE = 800.0
        /** 적에게 접근할 때 원하는 최적 교전거리 비율 (사거리의 70%) */
        private const val OPTIMAL_RANGE_RATIO = 0.7
        /** 사기가 이 이하면 퇴각 고려 */
        private const val RETREAT_MORALE_THRESHOLD = 30
    }

    /**
     * NPC 장교의 턴 명령을 생성.
     * @param session 현재 전투 세션
     * @param fleet NPC가 지휘하는 함대
     * @param rng 난수 생성기
     * @return 이번 턴에 실행할 명령 리스트
     */
    fun generateOrders(
        session: TacticalBattleSession,
        fleet: TacticalFleet,
        rng: Random,
    ): List<TacticalOrder> {
        val orders = mutableListOf<TacticalOrder>()
        val personality = resolvePersonality(fleet.officer)

        // 사기 붕괴 시 퇴각
        if (fleet.morale <= RETREAT_MORALE_THRESHOLD) {
            log.debug("AI retreat: officer={}, morale={}", fleet.officer.name, fleet.morale)
            orders.add(TacticalOrder(officerId = fleet.officerId, type = OrderType.RETREAT))
            return orders
        }

        // 최적 진형/에너지 설정 (첫 턴이나 상황 변화 시)
        val formationOrder = chooseFormation(fleet, personality, session)
        if (formationOrder != null) orders.add(formationOrder)

        val energyOrder = chooseEnergy(fleet, personality)
        if (energyOrder != null) orders.add(energyOrder)

        // 특수 능력 사용
        val specialOrder = trySpecialAbility(fleet, session, rng)
        if (specialOrder != null) orders.add(specialOrder)

        // 유닛별 이동/공격 명령
        val enemies = findEnemies(session, fleet.factionId)

        for (unit in fleet.aliveUnits()) {
            val unitOrders = generateUnitOrders(session, fleet, unit, enemies, personality, rng)
            orders.addAll(unitOrders)
        }

        return orders
    }

    // ===== Personality =====

    private enum class Personality { AGGRESSIVE, DEFENSIVE, MOBILE }

    private fun resolvePersonality(officer: OfficerStats): Personality {
        val maxStat = maxOf(officer.attack, officer.defense, officer.mobility)
        return when (maxStat) {
            officer.mobility -> Personality.MOBILE
            officer.attack -> Personality.AGGRESSIVE
            else -> Personality.DEFENSIVE
        }
    }

    // ===== Formation =====

    private fun chooseFormation(
        fleet: TacticalFleet,
        personality: Personality,
        session: TacticalBattleSession,
    ): TacticalOrder? {
        val desiredFormation = when (personality) {
            Personality.AGGRESSIVE -> Formation.SPINDLE
            Personality.DEFENSIVE -> Formation.SQUARE
            Personality.MOBILE -> Formation.ECHELON
        }

        // HP가 50% 이하면 방어진형으로 전환
        val hpRatio = fleet.totalHp().toDouble() / fleet.totalMaxHp().coerceAtLeast(1)
        val actualFormation = if (hpRatio < 0.5 && personality != Personality.DEFENSIVE) {
            Formation.SQUARE
        } else {
            desiredFormation
        }

        if (fleet.formation == actualFormation) return null

        return TacticalOrder(
            officerId = fleet.officerId,
            type = OrderType.FORMATION_CHANGE,
            formation = actualFormation,
        )
    }

    // ===== Energy =====

    private fun chooseEnergy(fleet: TacticalFleet, personality: Personality): TacticalOrder? {
        val desiredEnergy = when (personality) {
            Personality.AGGRESSIVE -> EnergyAllocation.AGGRESSIVE
            Personality.DEFENSIVE -> EnergyAllocation.DEFENSIVE
            Personality.MOBILE -> EnergyAllocation.MOBILE
        }

        if (fleet.energy == desiredEnergy) return null

        return TacticalOrder(
            officerId = fleet.officerId,
            type = OrderType.ENERGY_CHANGE,
            energy = desiredEnergy,
        )
    }

    // ===== Special Abilities =====

    private fun trySpecialAbility(
        fleet: TacticalFleet,
        session: TacticalBattleSession,
        rng: Random,
    ): TacticalOrder? {
        // 요새포 발사
        val fortress = fleet.units.firstOrNull { it.shipClass == TacticalShipClass.FORTRESS && it.isAlive() }
        if (fortress != null) {
            val enemies = findEnemies(session, fleet.factionId)
            val cluster = findEnemyCluster(enemies)
            if (cluster != null) {
                return TacticalOrder(
                    officerId = fleet.officerId,
                    type = OrderType.SPECIAL,
                    specialCode = "fortress_cannon",
                    targetX = cluster.x,
                    targetY = cluster.y,
                )
            }
        }

        // 사기가 낮으면 격려
        if (fleet.morale < 60 && rng.nextDouble() < 0.5) {
            return TacticalOrder(
                officerId = fleet.officerId,
                type = OrderType.SPECIAL,
                specialCode = "rally",
            )
        }

        return null
    }

    /**
     * 적 유닛들이 가장 밀집된 지점을 찾음 (요새포 타겟용).
     */
    private fun findEnemyCluster(enemies: List<TacticalUnit>): Position? {
        if (enemies.isEmpty()) return null
        // 적 중심점
        val cx = enemies.sumOf { it.x } / enemies.size
        val cy = enemies.sumOf { it.y } / enemies.size
        return Position(cx, cy)
    }

    // ===== Per-Unit Orders =====

    private fun generateUnitOrders(
        session: TacticalBattleSession,
        fleet: TacticalFleet,
        unit: TacticalUnit,
        enemies: List<TacticalUnit>,
        personality: Personality,
        rng: Random,
    ): List<TacticalOrder> {
        val orders = mutableListOf<TacticalOrder>()
        if (enemies.isEmpty()) return orders

        val unitPos = unit.position()
        val nearestEnemy = enemies.minByOrNull { it.position().distanceTo(unitPos) } ?: return orders
        val distToNearest = unitPos.distanceTo(nearestEnemy.position())
        val attackRange = unit.getAttackRange(fleet.energy)

        // 공격 범위 내 + 시야 확보 → 공격
        if (distToNearest <= attackRange && session.grid.hasLineOfFire(unitPos, nearestEnemy.position())) {
            // 우선 공격
            orders.add(TacticalOrder(
                officerId = fleet.officerId,
                unitId = unit.id,
                type = OrderType.ATTACK,
                targetUnitId = nearestEnemy.id,
            ))
        }

        // 이동 판단
        val moveTarget = when (personality) {
            Personality.AGGRESSIVE -> {
                // 사거리의 70% 지점까지 접근
                val optimalDist = attackRange * OPTIMAL_RANGE_RATIO
                if (distToNearest > optimalDist) {
                    moveToward(unitPos, nearestEnemy.position(), distToNearest - optimalDist)
                } else null
            }
            Personality.DEFENSIVE -> {
                // 사거리 끝에서 유지, 너무 가까우면 후퇴
                val optimalDist = attackRange * 0.9
                if (distToNearest < optimalDist * 0.5) {
                    moveAway(unitPos, nearestEnemy.position(), optimalDist - distToNearest, session.grid.fieldSize)
                } else if (distToNearest > attackRange) {
                    moveToward(unitPos, nearestEnemy.position(), distToNearest - optimalDist)
                } else null
            }
            Personality.MOBILE -> {
                // 측면 기동: 적의 측면으로 이동
                val flankPos = computeFlankPosition(unitPos, nearestEnemy.position(), attackRange * 0.6, rng)
                if (unitPos.distanceTo(flankPos) > 10.0) flankPos else null
            }
        }

        if (moveTarget != null) {
            orders.add(TacticalOrder(
                officerId = fleet.officerId,
                unitId = unit.id,
                type = OrderType.MOVE,
                targetX = moveTarget.x,
                targetY = moveTarget.y,
            ))
        }

        return orders
    }

    // ===== Helpers =====

    private fun findEnemies(session: TacticalBattleSession, myFactionId: Long): List<TacticalUnit> =
        session.allUnits().filter { it.isAlive() && it.factionId != myFactionId }

    /** 목표를 향해 distance만큼 이동한 위치 (z는 현재 고도 유지) */
    private fun moveToward(from: Position, target: Position, distance: Double): Position {
        val total = from.horizontalDistanceTo(target)
        if (total < 0.1) return from
        val ratio = (distance / total).coerceAtMost(1.0)
        return Position(
            from.x + (target.x - from.x) * ratio,
            from.y + (target.y - from.y) * ratio,
            from.z,
        )
    }

    /** 목표로부터 distance만큼 뒤로 이동한 위치 */
    private fun moveAway(from: Position, threat: Position, distance: Double, fieldSize: Double): Position {
        val total = from.horizontalDistanceTo(threat)
        if (total < 0.1) return from
        val ratio = distance / total
        return Position(
            (from.x - (threat.x - from.x) * ratio).coerceIn(0.0, fieldSize),
            (from.y - (threat.y - from.y) * ratio).coerceIn(0.0, fieldSize),
            from.z,
        )
    }

    /** 적의 측면 위치 계산 (수평면 90도 회전 + 약간의 z 변화) */
    private fun computeFlankPosition(
        myPos: Position,
        enemyPos: Position,
        distance: Double,
        rng: Random,
    ): Position {
        val dx = enemyPos.x - myPos.x
        val dy = enemyPos.y - myPos.y
        val total = myPos.horizontalDistanceTo(enemyPos)
        if (total < 0.1) return myPos

        // 수직 방향 (좌/우 랜덤)
        val sign = if (rng.nextBoolean()) 1.0 else -1.0
        val perpX = -dy / total * sign
        val perpY = dx / total * sign
        // z축으로도 약간 기동 (상하 우회)
        val flankZ = myPos.z + (rng.nextDouble() - 0.5) * 60.0

        return Position(
            enemyPos.x + perpX * distance,
            enemyPos.y + perpY * distance,
            flankZ.coerceIn(-Position.HEIGHT_SIZE, Position.HEIGHT_SIZE),
        )
    }
}
