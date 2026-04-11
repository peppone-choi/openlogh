package com.openlogh.engine.tactical

import kotlin.random.Random

/**
 * Ground unit in tactical land combat.
 *
 * gin7 지상전 병종: ARMORED_GRENADIER(장갑유탄병), ARMORED_INFANTRY(장갑병), LIGHT_MARINE(경장육전병)
 */
data class GroundUnit(
    val unitId: Long,
    val factionId: Long,
    /** "ARMORED_INFANTRY", "ARMORED_GRENADIER", "LIGHT_MARINE" */
    val groundUnitType: String,
    var count: Int,
    val maxCount: Int,
    var morale: Int = 60,
) {
    val isAlive get() = count > 0
}

/**
 * Live ground battle box state.
 *
 * gin7 규칙: 지상전 박스 최대 30유닛 (공수 합산), 초과 유닛은 대기 큐에 보관.
 *
 * Phase 24-12 (gap A6/C3, gin7 manual p50): [planetType] gates which unit
 * types can enter the ground battle box:
 *   - "normal"   — all unit types
 *   - "gas"      — no ARMORED_INFANTRY (heavy 装甲兵)
 *   - "fortress" — no ARMORED_INFANTRY (fortress assault)
 */
data class GroundBattleState(
    val planetId: Long,
    val attackerFactionId: Long,
    val defenderFactionId: Long,
    /** "normal" / "gas" / "fortress" per [com.openlogh.entity.Planet.planetType]. */
    val planetType: String = "normal",
    val attackers: MutableList<GroundUnit> = mutableListOf(),
    val defenders: MutableList<GroundUnit> = mutableListOf(),
    val waitingAttackers: MutableList<GroundUnit> = mutableListOf(),
    val rejectedUnits: MutableList<GroundUnit> = mutableListOf(),
    var tickCount: Int = 0,
) {
    /** 현재 박스 내 총 유닛 수 (공수 합산) */
    val totalUnitsInBox: Int get() = attackers.size + defenders.size

    /** 수비군 전멸 → 점령 완료 */
    val isConquestComplete: Boolean get() = defenders.all { !it.isAlive }

    /** 공격군 전멸 + 대기 큐 비어있음 → 공격측 패배 */
    val isAttackerDefeated: Boolean get() =
        attackers.all { !it.isAlive } && waitingAttackers.isEmpty()
}

/**
 * Ground battle tick engine.
 *
 * Manages the 30-unit box limit and per-tick combat between attackers and defenders.
 */
class GroundBattleEngine {

    companion object {
        const val MAX_UNITS_IN_BOX = 30
        /** 기본 지상전 피해 (count * morale / 100 비례) */
        const val BASE_GROUND_DAMAGE = 10

        /**
         * Phase 24-12 (gap A6/C3, gin7 manual p50): returns true if [unit]
         * can legally deploy on a planet of the given [planetType]. Heavy
         * 装甲兵 (ARMORED_INFANTRY) is blocked on gas giants and fortresses;
         * all other unit types are accepted everywhere.
         */
        fun isUnitAllowedOnPlanetType(unit: GroundUnit, planetType: String): Boolean {
            val normalized = unit.groundUnitType.uppercase()
            // Heavy armor banned on gas/fortress per gin7 manual p50.
            val isHeavyArmor = normalized == "ARMORED_INFANTRY"
            return when (planetType) {
                "gas", "fortress" -> !isHeavyArmor
                else -> true
            }
        }
    }

    /**
     * 육전대 강하 — 공격 유닛을 박스에 추가.
     * 30유닛 초과 시 waitingAttackers 큐에 보관.
     *
     * Phase 24-12: units whose type is disallowed on [GroundBattleState.planetType]
     * (e.g. ARMORED_INFANTRY on gas/fortress) are routed to `rejectedUnits`
     * instead of entering the box.
     */
    fun addAttackers(state: GroundBattleState, units: List<GroundUnit>) {
        for (unit in units) {
            if (!isUnitAllowedOnPlanetType(unit, state.planetType)) {
                state.rejectedUnits.add(unit)
                continue
            }
            if (state.totalUnitsInBox < MAX_UNITS_IN_BOX) {
                state.attackers.add(unit)
            } else {
                state.waitingAttackers.add(unit)
            }
        }
    }

    /**
     * 수비대 초기화 — 행성 수비 유닛 등록.
     *
     * Phase 24-12: filters out units disallowed by planet type.
     */
    fun initDefenders(state: GroundBattleState, units: List<GroundUnit>) {
        for (unit in units) {
            if (isUnitAllowedOnPlanetType(unit, state.planetType)) {
                state.defenders.add(unit)
            } else {
                state.rejectedUnits.add(unit)
            }
        }
    }

    /**
     * 지상전 한 틱 처리.
     *
     * - 공격자 ↔ 수비자 간 자동 교전
     * - 전멸 시 대기 공격자 보충
     */
    fun processTick(state: GroundBattleState, rng: Random = Random): List<String> {
        state.tickCount++
        val logs = mutableListOf<String>()

        val aliveAttackers = state.attackers.filter { it.isAlive }
        val aliveDefenders = state.defenders.filter { it.isAlive }

        if (aliveAttackers.isEmpty() || aliveDefenders.isEmpty()) {
            replenishFromQueue(state, logs)
            return logs
        }

        // 공격자 → 수비자 교전
        for (attacker in aliveAttackers) {
            val target = aliveDefenders.filter { it.isAlive }.randomOrNull(rng) ?: break
            val dmg = calculateGroundDamage(attacker)
            target.count = (target.count - dmg).coerceAtLeast(0)
            if (!target.isAlive) logs.add("${target.groundUnitType} 수비대 전멸")
        }

        // 수비자 → 공격자 반격
        for (defender in aliveDefenders.filter { it.isAlive }) {
            val target = aliveAttackers.filter { it.isAlive }.randomOrNull(rng) ?: break
            val dmg = calculateGroundDamage(defender)
            target.count = (target.count - dmg).coerceAtLeast(0)
            if (!target.isAlive) logs.add("${target.groundUnitType} 공격대 전멸 — 대기 유닛 보충 예정")
        }

        // 전멸 후 대기 유닛 보충
        replenishFromQueue(state, logs)
        return logs
    }

    private fun calculateGroundDamage(unit: GroundUnit): Int {
        val typeModifier = when (unit.groundUnitType.uppercase()) {
            "ARMORED_GRENADIER" -> 1.3  // 장갑유탄병: 공격↑
            "ARMORED_INFANTRY"  -> 1.0  // 장갑병: 균형
            "LIGHT_MARINE"      -> 0.8  // 경장육전병: 공격↓, 속도↑
            else -> 1.0
        }
        return ((BASE_GROUND_DAMAGE * typeModifier * unit.morale / 100.0)
            .coerceAtLeast(1.0)).toInt()
    }

    private fun replenishFromQueue(state: GroundBattleState, logs: MutableList<String>) {
        val deadAttackers = state.attackers.count { !it.isAlive }
        repeat(deadAttackers.coerceAtMost(state.waitingAttackers.size)) {
            if (state.waitingAttackers.isNotEmpty()) {
                val next = state.waitingAttackers.removeAt(0)
                state.attackers.add(next)
                logs.add("대기 공격대 ${next.groundUnitType} 투입 (남은 대기: ${state.waitingAttackers.size})")
            }
        }
    }
}
