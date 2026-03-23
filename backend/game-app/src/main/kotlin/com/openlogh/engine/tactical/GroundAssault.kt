package com.openlogh.engine.tactical

/**
 * 지상전 줄다리기 시스템 + 병종 상성.
 *
 * 점령 게이지: 0 (방어측 완전방어) ~ 100 (공격측 점령 완료)
 * 단계: ORBITAL → LANDING → COMBAT → CAPTURED
 *
 * COMBAT 단계에서 양측이 매 턴 병종을 선택하면 상성에 따라 줄다리기 결과 변동.
 * 궤도 제공권(함대전)을 잃으면 강습 자동 실패.
 */

// ===== 지상전 병종 =====

enum class GroundUnitType(
    val code: String,
    val displayName: String,
    val basePower: Double,
    val description: String,
    /** 사용 가능 진영. null = 전 진영 가능 */
    val factionRestriction: String? = null,
    /** 전환 소요 턴 (전략 턴 기준). 0 = 즉시, -1 = 전환 불가(초기 편성만 가능) */
    val switchCost: Int = 0,
) {
    /** 경장육전병: 경장비 보병. gin7 軽装陸戦兵 */
    LIGHT_INFANTRY("light_infantry", "경장육전병", 1.0, "경장비 보병. 기갑에 강하고 장갑병에 약함",
        switchCost = 0),
    /** 장갑병: 중장갑 보병. gin7 装甲兵 */
    ARMORED_INFANTRY("armored_infantry", "장갑병", 1.05, "중장갑 보병. 경장육전병에 강하고 로젠리터에 약함",
        switchCost = 1),
    /** 장갑척탄병: 엘리트 중장갑 돌격보병. gin7 装甲擲弾兵. 제국 전용 */
    GRENADIERS("grenadiers", "장갑척탄병", 1.15, "엘리트 돌격보병. 장갑병에 강하고 로젠리터에 약함",
        factionRestriction = "empire", switchCost = 3),
    /** 기갑부대: 전차/기갑차량 기동 타격 */
    ARMOR("armor", "기갑부대", 1.2, "기동 타격. 포병에 강하고 경장육전병에 약함",
        switchCost = 2),
    /** 포병: 원거리 지원 화력 */
    ARTILLERY("artillery", "포병", 0.9, "원거리 지원. 로젠리터에 강하고 기갑에 약함",
        switchCost = 1),
    /** 로젠리터: 백병전 엘리트 특수부대. 동맹 전용, 전환 불가 */
    ROSENRITTER("rosenritter", "로젠리터", 1.2, "백병전 엘리트. 장갑척탄병에 강하고 포병에 약함",
        factionRestriction = "alliance", switchCost = -1),
    ;

    /** 해당 진영이 이 병종을 사용할 수 있는지 */
    fun isAvailableFor(factionType: String): Boolean =
        factionRestriction == null || factionRestriction == factionType

    /** 전환 가능 여부 */
    fun canSwitch(): Boolean = switchCost >= 0

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): GroundUnitType = byCode[code] ?: LIGHT_INFANTRY

        /** 특정 진영이 사용 가능한 병종 목록 */
        fun availableFor(factionType: String): List<GroundUnitType> =
            entries.filter { it.isAvailableFor(factionType) }
    }
}

/**
 * 병종 상성표.
 * 반환값: 공격측 배율 (1.0 = 동등, >1.0 = 유리, <1.0 = 불리)
 */
/**
 * 병종 상성표 (6종):
 *
 * 경장육전병 > 기갑 > 포병 > 로젠리터 > 장갑척탄병 > 장갑병 > 경장육전병 (하위 순환)
 * 장갑척탄병 > 경장육전병 (상위가 하위 압도)
 * 로젠리터 > 장갑척탄병 (엘리트 대 엘리트)
 *
 * 상성 유리: ×1.3, 상성 불리: ×0.7
 */
fun getMatchupMultiplier(attacker: GroundUnitType, defender: GroundUnitType): Double {
    if (attacker == defender) return 1.0
    return when (attacker) {
        GroundUnitType.LIGHT_INFANTRY -> when (defender) {
            GroundUnitType.ARMOR -> 1.3               // 경장육전병 > 기갑 (대전차 전술)
            GroundUnitType.ARMORED_INFANTRY -> 0.7    // 경장육전병 < 장갑병
            GroundUnitType.GRENADIERS -> 0.7          // 경장육전병 < 장갑척탄병
            else -> 1.0
        }
        GroundUnitType.ARMORED_INFANTRY -> when (defender) {
            GroundUnitType.LIGHT_INFANTRY -> 1.3      // 장갑병 > 경장육전병
            GroundUnitType.GRENADIERS -> 0.7          // 장갑병 < 장갑척탄병 (상위 호환)
            GroundUnitType.ROSENRITTER -> 0.7         // 장갑병 < 로젠리터
            else -> 1.0
        }
        GroundUnitType.GRENADIERS -> when (defender) {
            GroundUnitType.LIGHT_INFANTRY -> 1.3      // 장갑척탄병 > 경장육전병
            GroundUnitType.ARMORED_INFANTRY -> 1.3    // 장갑척탄병 > 장갑병 (상위 호환)
            GroundUnitType.ROSENRITTER -> 0.7         // 장갑척탄병 < 로젠리터 (백병전)
            else -> 1.0
        }
        GroundUnitType.ARMOR -> when (defender) {
            GroundUnitType.ARTILLERY -> 1.3           // 기갑 > 포병 (기동 돌파)
            GroundUnitType.LIGHT_INFANTRY -> 0.7      // 기갑 < 경장육전병 (대전차 전술)
            else -> 1.0
        }
        GroundUnitType.ARTILLERY -> when (defender) {
            GroundUnitType.ROSENRITTER -> 1.3         // 포병 > 로젠리터 (원거리 제압)
            GroundUnitType.ARMOR -> 0.7               // 포병 < 기갑 (기동 회피)
            else -> 1.0
        }
        GroundUnitType.ROSENRITTER -> when (defender) {
            GroundUnitType.GRENADIERS -> 1.3          // 로젠리터 > 장갑척탄병 (백병전 엘리트)
            GroundUnitType.ARMORED_INFANTRY -> 1.3    // 로젠리터 > 장갑병
            GroundUnitType.ARTILLERY -> 0.7           // 로젠리터 < 포병 (원거리 제압)
            else -> 1.0
        }
    }
}

// ===== 지상전 상태 =====

enum class GroundPhase(val displayName: String) {
    NONE("미개시"),
    ORBITAL("궤도 돌파"),
    LANDING("지표면 강습"),
    COMBAT("지상 전투"),
    CAPTURED("점령 완료"),
    REPELLED("강습 실패"),
}

data class GroundAssaultState(
    var phase: GroundPhase = GroundPhase.NONE,
    /** 점령 게이지: 0 = 방어 완전, 100 = 점령 완료 */
    var captureGauge: Double = 0.0,
    /** 궤도 방어력 (행성 orbital_defense 기반) */
    var orbitalDefense: Double = 100.0,
    /** 수비대 전력 (행성 garrison 기반) */
    var garrisonStrength: Double = 100.0,
    /** 공격측 강습 참여 유닛 ID 목록 */
    val assaultUnitIds: MutableSet<Int> = mutableSetOf(),
    /** 공격측 병종 (전략 단계에서 결정, 전투 중 변경 불가) */
    var attackerUnitType: GroundUnitType = GroundUnitType.LIGHT_INFANTRY,
    /** 방어측 병종 (행성 수비대 편성, 전투 중 변경 불가) */
    var defenderUnitType: GroundUnitType = GroundUnitType.LIGHT_INFANTRY,
) {
    fun isActive(): Boolean = phase in listOf(GroundPhase.ORBITAL, GroundPhase.LANDING, GroundPhase.COMBAT)
    fun isFinished(): Boolean = phase == GroundPhase.CAPTURED || phase == GroundPhase.REPELLED
}

/** 지상전 턴 처리 결과 */
data class GroundAssaultTurnResult(
    val phase: GroundPhase,
    val captureGauge: Double,
    val attackPower: Double,
    val defensePower: Double,
    val orbitalDefense: Double,
    val garrisonStrength: Double,
    val attackerUnitType: String,
    val defenderUnitType: String,
    val matchupMultiplier: Double,
    val description: String,
)

// ===== 지상전 엔진 =====

object GroundAssaultEngine {

    private const val ORBITAL_BREACH_THRESHOLD = 30.0
    private const val LANDING_COMPLETE_GAUGE = 40.0
    private const val CAPTURE_COMPLETE_GAUGE = 100.0

    fun initiate(orbitalDefense: Double, garrisonStrength: Double): GroundAssaultState {
        return GroundAssaultState(
            phase = GroundPhase.ORBITAL,
            captureGauge = 0.0,
            orbitalDefense = orbitalDefense,
            garrisonStrength = garrisonStrength,
        )
    }

    /**
     * 매 턴 지상전 처리.
     *
     * @param state 현재 상태
     * @param attackerAssaultPower 공격측 기본 강습력 (groundCombat × assaultShip HP)
     * @param attackerHasOrbitalControl 궤도 제공권 보유 여부
     * @param attackerChoice 공격측 병종 선택
     * @param defenderChoice 방어측 병종 선택
     */
    fun processTurn(
        state: GroundAssaultState,
        attackerAssaultPower: Double,
        attackerHasOrbitalControl: Boolean,
        attackerChoice: GroundUnitType = state.attackerUnitType,
        defenderChoice: GroundUnitType = state.defenderUnitType,
    ): GroundAssaultTurnResult {
        state.attackerUnitType = attackerChoice
        state.defenderUnitType = defenderChoice

        if (state.isFinished()) {
            return result(state, 0.0, 0.0, 1.0, "지상전 종료됨")
        }

        // 궤도 제공권 상실
        if (!attackerHasOrbitalControl && state.phase != GroundPhase.ORBITAL) {
            state.phase = GroundPhase.REPELLED
            state.captureGauge = (state.captureGauge - 20.0).coerceAtLeast(0.0)
            return result(state, attackerAssaultPower, 0.0, 1.0, "궤도 제공권 상실! 강습부대 철수")
        }

        val matchup = if (state.phase == GroundPhase.COMBAT) {
            getMatchupMultiplier(attackerChoice, defenderChoice)
        } else 1.0

        val effectiveAttack = attackerAssaultPower * attackerChoice.basePower * matchup

        return when (state.phase) {
            GroundPhase.ORBITAL -> processOrbital(state, effectiveAttack, matchup)
            GroundPhase.LANDING -> processLanding(state, effectiveAttack, matchup)
            GroundPhase.COMBAT -> processCombat(state, effectiveAttack, defenderChoice, matchup)
            else -> result(state, 0.0, 0.0, matchup, "처리 불가")
        }
    }

    private fun processOrbital(state: GroundAssaultState, attack: Double, matchup: Double): GroundAssaultTurnResult {
        val damage = attack * 0.3
        state.orbitalDefense = (state.orbitalDefense - damage).coerceAtLeast(0.0)
        state.captureGauge += damage * 0.2

        val desc = if (state.orbitalDefense <= ORBITAL_BREACH_THRESHOLD) {
            state.phase = GroundPhase.LANDING
            "궤도 방어 돌파! 지표면 강습 개시"
        } else {
            "궤도 방어 공격 중 (잔여 ${state.orbitalDefense.toInt()}%)"
        }
        return result(state, attack, state.orbitalDefense, matchup, desc)
    }

    private fun processLanding(state: GroundAssaultState, attack: Double, matchup: Double): GroundAssaultTurnResult {
        val defense = state.garrisonStrength * 0.5
        val net = attack - defense
        state.captureGauge = (state.captureGauge + net * 0.15).coerceIn(0.0, CAPTURE_COMPLETE_GAUGE)
        state.garrisonStrength = (state.garrisonStrength - attack * 0.1).coerceAtLeast(0.0)

        val desc = when {
            state.captureGauge >= LANDING_COMPLETE_GAUGE -> {
                state.phase = GroundPhase.COMBAT
                "양륙 완료! 지상 전투 돌입 — 병종을 선택하세요"
            }
            state.captureGauge <= 0.0 -> {
                state.phase = GroundPhase.REPELLED
                "양륙 실패! 수비대에 의해 격퇴"
            }
            else -> "지표면 강습 중 (게이지 ${state.captureGauge.toInt()}%)"
        }
        return result(state, attack, defense, matchup, desc)
    }

    private fun processCombat(
        state: GroundAssaultState,
        attack: Double,
        defenderChoice: GroundUnitType,
        matchup: Double,
    ): GroundAssaultTurnResult {
        val defense = state.garrisonStrength * 0.8 * defenderChoice.basePower
        val net = attack - defense

        state.captureGauge = (state.captureGauge + net * 0.1).coerceIn(0.0, CAPTURE_COMPLETE_GAUGE)

        if (net > 0) {
            state.garrisonStrength = (state.garrisonStrength - net * 0.05).coerceAtLeast(0.0)
        }

        val matchupDesc = when {
            matchup > 1.1 -> "상성 유리!"
            matchup < 0.9 -> "상성 불리!"
            else -> "동등"
        }

        val desc = when {
            state.captureGauge >= CAPTURE_COMPLETE_GAUGE -> {
                state.phase = GroundPhase.CAPTURED
                "행성 점령 완료!"
            }
            state.captureGauge <= 0.0 -> {
                state.phase = GroundPhase.REPELLED
                "강습 실패! 수비대 승리"
            }
            net > 0 -> "공격측 우세 [$matchupDesc] (게이지 ${state.captureGauge.toInt()}%)"
            else -> "방어측 우세 [$matchupDesc] (게이지 ${state.captureGauge.toInt()}%)"
        }
        return result(state, attack, defense, matchup, desc)
    }

    private fun result(
        state: GroundAssaultState,
        attack: Double,
        defense: Double,
        matchup: Double,
        desc: String,
    ) = GroundAssaultTurnResult(
        phase = state.phase,
        captureGauge = state.captureGauge,
        attackPower = attack,
        defensePower = defense,
        orbitalDefense = state.orbitalDefense,
        garrisonStrength = state.garrisonStrength,
        attackerUnitType = state.attackerUnitType.code,
        defenderUnitType = state.defenderUnitType.code,
        matchupMultiplier = matchup,
        description = desc,
    )
}
