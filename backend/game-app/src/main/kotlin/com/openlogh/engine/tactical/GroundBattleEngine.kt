package com.openlogh.engine.tactical

import kotlin.random.Random

/**
 * Ground unit in tactical land combat.
 *
 * gin7 지상전 병종 (Phase 24-26 gap D4, enum 정렬):
 *   ARMORED_INFANTRY(장갑병), GRENADIER(장갑유탄병), LIGHT_INFANTRY(경장육전병),
 *   + 특수 단위 IMPERIAL_GUARD, GRENADIER_GUARD, ROSENRITTER (Phase 24-24).
 *
 * 레거시 문자열 "ARMORED_GRENADIER" / "LIGHT_MARINE" 은 D4 alias 테이블에서
 * 신규 enum 이름으로 매핑한다 — 기존 저장된 groundUnitType 문자열이 있어도
 * 데미지 계산이 올바르게 동작한다.
 */
data class GroundUnit(
    val unitId: Long,
    val factionId: Long,
    /**
     * GroundUnitType enum 이름 문자열 (D4 신규 정렬 완료):
     *   "ARMORED_INFANTRY", "GRENADIER", "LIGHT_INFANTRY",
     *   "IMPERIAL_GUARD", "GRENADIER_GUARD", "ROSENRITTER"
     * 구 버전 레거시 문자열 "ARMORED_GRENADIER" / "LIGHT_MARINE" 도 읽기는
     * 허용하며 데미지 계산 시 신규 이름과 동일하게 처리된다.
     */
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
 *   - "gas"      — no ARMORED_INFANTRY (heavy 장갑병)
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
         * Phase 24-12 (gap A6/C3, gin7 매뉴얼 p50): returns true if [unit]
         * can legally deploy on a planet of the given [planetType]. 장갑병
         * (ARMORED_INFANTRY) is blocked on gas giants and fortresses;
         * all other unit types are accepted everywhere.
         */
        fun isUnitAllowedOnPlanetType(unit: GroundUnit, planetType: String): Boolean {
            val normalized = unit.groundUnitType.uppercase()
            // gin7 매뉴얼 p50:
            //   · 가스 행성 : 중장비 병종(장갑병/근위사단) 배치 불가
            //   · 요새      : 장갑병 배치 불가 (근위사단은 요새 수비 가능)
            val isHeavyArmor = normalized == "ARMORED_INFANTRY"
            val isImperialGuard = normalized == "IMPERIAL_GUARD"
            return when (planetType) {
                "gas"      -> !isHeavyArmor && !isImperialGuard
                "fortress" -> !isHeavyArmor
                else -> true
            }
        }

        /**
         * Phase 24-26 (gap D4): groundUnitType 문자열 → 데미지 배수 매핑.
         * 신규 enum 이름(`GRENADIER`, `LIGHT_INFANTRY`) 과 레거시 drift 이름
         * (`ARMORED_GRENADIER`, `LIGHT_MARINE`) 양쪽을 모두 수용한다. Phase 24-24
         * 에서 도입한 특수 단위(IMPERIAL_GUARD/GRENADIER_GUARD/ROSENRITTER) 는
         * 엘리트 배수(1.5~1.6) 로 반영한다.
         */
        fun typeModifierFor(rawType: String): Double = when (rawType.uppercase()) {
            "ARMORED_INFANTRY"   -> 1.0   // 장갑병 — 균형
            "GRENADIER",
            "ARMORED_GRENADIER"  -> 1.3   // 장갑유탄병 — 공격↑ (legacy alias)
            "LIGHT_INFANTRY",
            "LIGHT_MARINE"       -> 0.8   // 경장육전병 — 공격↓, 기동↑ (legacy alias)
            "IMPERIAL_GUARD"     -> 1.5   // 근위사단 — 엘리트
            "GRENADIER_GUARD"    -> 1.5   // 척탄병교도대 — 엘리트
            "ROSENRITTER"        -> 1.6   // 장미기사단 — 엘리트 돌격
            else -> 1.0
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
        val typeModifier = typeModifierFor(unit.groundUnitType)
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
