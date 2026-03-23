package com.openlogh.engine.doctrine

import kotlin.math.abs
import kotlin.random.Random

/**
 * 국가 체제 변경 시스템.
 *
 * 3축 각각 변경 난이도가 다름:
 * - 이데올로기: 혁명/쿠데타/자연표류/선거
 * - 정부 형태: 개혁(평화)/쿠데타(강제)/항복
 * - 경제 체제: 정책 전환 (가장 쉬움)
 */

// ===== 전환 상태 추적 =====

/**
 * 진행 중인 체제 전환.
 * Faction.meta["doctrineTransition"]에 JSON으로 저장.
 */
data class DoctrineTransition(
    val axis: TransitionAxis,
    val targetCode: String,
    val totalTurns: Int,
    var elapsedTurns: Int = 0,
    val method: TransitionMethod,
    /** 전환 중 패널티 배율 */
    val penaltyRate: Double = 0.2,
) {
    fun isComplete(): Boolean = elapsedTurns >= totalTurns
    fun progress(): Double = if (totalTurns > 0) elapsedTurns.toDouble() / totalTurns else 1.0

    fun toMeta(): Map<String, Any> = mapOf(
        "axis" to axis.name,
        "targetCode" to targetCode,
        "totalTurns" to totalTurns,
        "elapsedTurns" to elapsedTurns,
        "method" to method.name,
        "penaltyRate" to penaltyRate,
    )

    companion object {
        fun fromMeta(meta: Map<String, Any>): DoctrineTransition? {
            val axis = try { TransitionAxis.valueOf(meta["axis"] as? String ?: return null) } catch (_: Exception) { return null }
            val method = try { TransitionMethod.valueOf(meta["method"] as? String ?: return null) } catch (_: Exception) { return null }
            return DoctrineTransition(
                axis = axis,
                targetCode = meta["targetCode"] as? String ?: return null,
                totalTurns = (meta["totalTurns"] as? Number)?.toInt() ?: return null,
                elapsedTurns = (meta["elapsedTurns"] as? Number)?.toInt() ?: 0,
                method = method,
                penaltyRate = (meta["penaltyRate"] as? Number)?.toDouble() ?: 0.2,
            )
        }
    }
}

enum class TransitionAxis { IDEOLOGY, GOVERNMENT, ECONOMY }

enum class TransitionMethod(val displayName: String) {
    REFORM("개혁"),
    COUP("쿠데타"),
    REVOLUTION("혁명"),
    ELECTION("선거"),
    POLICY_CHANGE("정책 전환"),
    FORCED("강제 변경"),     // 점령/항복
    NATURAL_DRIFT("자연 표류"),
}

// ===== 전환 비용/조건 =====

data class TransitionCost(
    val politicalPowerCost: Int,
    val fundsCost: Int = 0,
    val stabilityLoss: Double = 0.0,
    val approvalLoss: Double = 0.0,
    val turnsRequired: Int,
    val penaltyRate: Double = 0.2,
    val civilWarRisk: Double = 0.0,
)

// ===== 체제 변경 엔진 =====

object DoctrineChangeEngine {

    // === 이데올로기 변경 ===

    /**
     * 자연 표류: 매 턴 호출. 상황에 따라 이데올로기가 미세 이동.
     * @return 새 이데올로기 (변경 시), null (변경 없음)
     */
    fun calculateIdeologyDrift(
        current: Ideology,
        approval: Double,
        warTurns: Int,
        peaceTurns: Int,
        militaryPressure: Double,
        rng: Random,
    ): Ideology? {
        // 전쟁 장기화 → 군국주의 경향
        if (warTurns > 20 && current != Ideology.MILITARISM && current != Ideology.FASCISM) {
            if (rng.nextDouble() < 0.02 * (warTurns - 20)) {
                return when (current) {
                    Ideology.LIBERALISM, Ideology.SOCIAL_DEMOCRACY -> Ideology.NATIONALISM
                    Ideology.NATIONALISM, Ideology.CONSERVATISM -> Ideology.MILITARISM
                    else -> null
                }
            }
        }

        // 장기 평화 → 자유주의 경향
        if (peaceTurns > 30 && current != Ideology.LIBERALISM && current != Ideology.SOCIAL_DEMOCRACY) {
            if (rng.nextDouble() < 0.01 * (peaceTurns - 30)) {
                return when (current) {
                    Ideology.MILITARISM -> Ideology.NATIONALISM
                    Ideology.NATIONALISM, Ideology.CONSERVATISM -> Ideology.LIBERALISM
                    Ideology.FASCISM -> Ideology.CONSERVATISM
                    else -> null
                }
            }
        }

        // 극도의 낮은 지지 → 급진화
        if (approval < 0.15 && rng.nextDouble() < 0.03) {
            return when (current) {
                Ideology.LIBERALISM, Ideology.SOCIAL_DEMOCRACY -> Ideology.COMMUNISM
                Ideology.CONSERVATISM -> Ideology.FASCISM
                else -> null
            }
        }

        return null
    }

    /**
     * 혁명 가능 여부 체크.
     * @return 혁명 발생 확률 (0.0 ~ 1.0)
     */
    fun getRevolutionChance(
        approval: Double,
        stability: Double,
        loyaltyAverage: Double,
    ): Double {
        if (approval > 0.3 && stability > 0.3) return 0.0
        val base = (0.3 - approval).coerceAtLeast(0.0) * 0.5 +
            (0.3 - stability).coerceAtLeast(0.0) * 0.3 +
            (0.3 - loyaltyAverage).coerceAtLeast(0.0) * 0.2
        return base.coerceIn(0.0, 0.5)
    }

    /**
     * 쿠데타 가능 여부 (군부).
     * @return 쿠데타 발생 확률
     */
    fun getCoupChance(
        approval: Double,
        militaryLoyalty: Double,
        currentGovernment: GovernmentType,
    ): Double {
        // 이미 군사독재면 쿠데타 불가
        if (currentGovernment == GovernmentType.MILITARY_DICTATORSHIP) return 0.0
        // 군부 충성도 높고 지지도 낮을 때
        if (militaryLoyalty < 0.4) return 0.0
        val base = (0.3 - approval).coerceAtLeast(0.0) * militaryLoyalty
        return base.coerceIn(0.0, 0.4)
    }

    // === 정부 형태 변경 ===

    /** 평화적 개혁 비용 계산 */
    fun calculateReformCost(
        current: GovernmentType,
        target: GovernmentType,
    ): TransitionCost {
        val distance = governmentDistance(current, target)
        return TransitionCost(
            politicalPowerCost = 2000 * distance,
            fundsCost = 5000 * distance,
            stabilityLoss = 0.05 * distance,
            approvalLoss = 0.03 * distance,
            turnsRequired = 5 * distance,
            penaltyRate = 0.1,
        )
    }

    /** 쿠데타 비용 (즉시 전환, 고위험) */
    fun calculateCoupCost(target: GovernmentType): TransitionCost {
        return TransitionCost(
            politicalPowerCost = 500,
            stabilityLoss = 0.30,
            approvalLoss = 0.20,
            turnsRequired = 1,
            penaltyRate = 0.3,
            civilWarRisk = 0.25,
        )
    }

    /** 정부 형태 간 거리 (전환 난이도) */
    private fun governmentDistance(a: GovernmentType, b: GovernmentType): Int {
        if (a == b) return 0
        val authoritarian = setOf(
            GovernmentType.ABSOLUTE_MONARCHY, GovernmentType.MILITARY_DICTATORSHIP,
            GovernmentType.PARTY_STATE, GovernmentType.THEOCRATIC_STATE,
        )
        val democratic = setOf(
            GovernmentType.PRESIDENTIAL_REPUBLIC, GovernmentType.PARLIAMENTARY_REPUBLIC,
            GovernmentType.CONSTITUTIONAL_MONARCHY,
        )
        // 같은 범주 내 전환: 1, 다른 범주: 2, 극단: 3
        return when {
            a in authoritarian && b in authoritarian -> 1
            a in democratic && b in democratic -> 1
            (a in authoritarian && b in democratic) || (a in democratic && b in authoritarian) -> 2
            else -> 1
        }
    }

    // === 경제 체제 변경 ===

    /** 경제 정책 전환 비용 */
    fun calculateEconomicTransitionCost(
        current: EconomicSystem,
        target: EconomicSystem,
    ): TransitionCost {
        val distance = economyDistance(current, target)
        return TransitionCost(
            politicalPowerCost = 1000 * distance,
            fundsCost = 10000 * distance,
            stabilityLoss = 0.03 * distance,
            turnsRequired = 3 * distance,
            penaltyRate = 0.20,
        )
    }

    private fun economyDistance(a: EconomicSystem, b: EconomicSystem): Int {
        if (a == b) return 0
        val market = setOf(EconomicSystem.FREE_MARKET, EconomicSystem.MERCANTILISM, EconomicSystem.MIXED_ECONOMY)
        val state = setOf(EconomicSystem.PLANNED_ECONOMY, EconomicSystem.STATE_CAPITALISM, EconomicSystem.WAR_ECONOMY)
        return when {
            a in market && b in market -> 1
            a in state && b in state -> 1
            else -> 2
        }
    }

    // === 선거 (민주정부 전용) ===

    /** 선거 결과: 여론에 따라 이데올로기 소폭 이동 */
    fun resolveElection(
        current: Ideology,
        approval: Double,
        warTurns: Int,
        economicGrowth: Double,
        rng: Random,
    ): Ideology {
        // 경제 호황 → 현 체제 유지 경향
        if (economicGrowth > 0.05 && approval > 0.5) return current

        // 여론에 따른 이동
        val shift = rng.nextDouble()
        return when {
            // 전쟁 중 + 불만 → 평화주의/사민주의
            warTurns > 10 && approval < 0.4 && shift < 0.4 -> Ideology.SOCIAL_DEMOCRACY
            // 경제 불황 + 불만 → 좌파 또는 국가주의
            economicGrowth < -0.05 && approval < 0.3 -> {
                if (shift < 0.5) Ideology.SOCIAL_DEMOCRACY else Ideology.NATIONALISM
            }
            // 높은 지지 → 현 체제 유지
            approval > 0.6 -> current
            // 일반적 변동
            else -> current
        }
    }

    /** 해당 정부가 선거를 실시하는 체제인지 */
    fun isElectoralGovernment(gov: GovernmentType): Boolean = gov in setOf(
        GovernmentType.PRESIDENTIAL_REPUBLIC,
        GovernmentType.PARLIAMENTARY_REPUBLIC,
        GovernmentType.CONSTITUTIONAL_MONARCHY,
    )

    // === 전환 적용 ===

    /**
     * 전환을 시작. DoctrineTransition 객체를 생성하여 Faction.meta에 저장.
     */
    fun initiateTransition(
        axis: TransitionAxis,
        targetCode: String,
        method: TransitionMethod,
        cost: TransitionCost,
    ): DoctrineTransition {
        return DoctrineTransition(
            axis = axis,
            targetCode = targetCode,
            totalTurns = cost.turnsRequired,
            method = method,
            penaltyRate = cost.penaltyRate,
        )
    }

    /**
     * 매 턴 전환 진행. 완료 시 true 반환.
     */
    fun advanceTransition(transition: DoctrineTransition): Boolean {
        transition.elapsedTurns++
        return transition.isComplete()
    }

    /**
     * 완료된 전환을 FactionDoctrine에 적용.
     */
    fun applyCompletedTransition(
        current: FactionDoctrine,
        transition: DoctrineTransition,
    ): FactionDoctrine {
        return when (transition.axis) {
            TransitionAxis.IDEOLOGY -> {
                val newIdeology = Ideology.fromCode(transition.targetCode) ?: return current
                current.copy(ideology = newIdeology)
            }
            TransitionAxis.GOVERNMENT -> {
                val newGov = GovernmentType.fromCode(transition.targetCode) ?: return current
                current.copy(government = newGov)
            }
            TransitionAxis.ECONOMY -> {
                val newEco = EconomicSystem.fromCode(transition.targetCode) ?: return current
                current.copy(economy = newEco)
            }
        }
    }

    /**
     * 전시 자동 경제 전환 체크.
     * 전쟁 돌입 시 전시경제로 자동 전환 여부.
     */
    fun shouldAutoWarEconomy(
        current: EconomicSystem,
        isAtWar: Boolean,
        warTurns: Int,
    ): Boolean {
        if (!isAtWar) return false
        if (current == EconomicSystem.WAR_ECONOMY) return false
        // 전쟁 5턴 이후 자동 전환 고려
        return warTurns >= 5
    }
}
