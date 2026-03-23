@file:Suppress("ClassName", "unused")

package com.openlogh.command.nation

import com.openlogh.command.*
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.entity.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ========== 국가목표설정 (Set National Goal) PCP 320 ==========
// Sets the faction's strategic goal in meta

class 국가목표설정(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "국가목표설정"

    private val goal: String get() = arg?.get("goal") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (nation == null) return ConstraintResult.Fail("국가 정보가 없습니다")
        if (goal.isBlank()) return ConstraintResult.Fail("목표를 입력해야 합니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        n.meta["strategicGoal"] = goal
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 국가 전략 목표를 설정했습니다: $goal"),
        )
    }
}

// ========== 납입률변경 (Change Deposit Rate) PCP 160 ==========
// Changes the faction's internal tribute/deposit rate (stored in meta)

class 납입률변경(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "납입률변경"

    private val rate: Int get() = (arg?.get("rate") as? Number)?.toInt() ?: 0

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (nation == null) return ConstraintResult.Fail("국가 정보가 없습니다")
        if (rate !in 0..100) return ConstraintResult.Fail("납입률은 0~100 사이여야 합니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        n.meta["depositRate"] = rate
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 납입률을 ${rate}%로 변경했습니다."),
        )
    }
}

// ========== 관세율변경 (Change Customs Rate) PCP 160 ==========
// Changes the faction's customs/tariff rate on trade routes (stored in meta)

class 관세율변경(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "관세율변경"

    private val rate: Int get() = (arg?.get("rate") as? Number)?.toInt() ?: 0

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (nation == null) return ConstraintResult.Fail("국가 정보가 없습니다")
        if (rate !in 0..100) return ConstraintResult.Fail("관세율은 0~100 사이여야 합니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        n.meta["customsRate"] = rate
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 관세율을 ${rate}%로 변경했습니다."),
        )
    }
}

// ========== 분배 (Resource Distribution) PCP 320 ==========
// Distribute faction funds/supplies to a target officer

class 분배(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "분배"

    private val fundsAmount: Int get() = (arg?.get("fundsAmount") as? Number)?.toInt() ?: 0
    private val suppliesAmount: Int get() = (arg?.get("suppliesAmount") as? Number)?.toInt() ?: 0

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.nationId != general.nationId) return ConstraintResult.Fail("아군 장수가 아닙니다")
        val n = nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        if (fundsAmount > n.funds) return ConstraintResult.Fail("국고 자금이 부족합니다")
        if (suppliesAmount > n.supplies) return ConstraintResult.Fail("국가 물자가 부족합니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dg = destGeneral!!
        if (fundsAmount > 0) {
            val take = min(fundsAmount, n.funds)
            n.funds -= take
            dg.funds += take
        }
        if (suppliesAmount > 0) {
            val take = min(suppliesAmount, n.supplies)
            n.supplies -= take
            dg.supplies += take
        }
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}에게 자원을 분배했습니다."),
        )
    }
}

// ========== 처단 (Execute/Exile) PCP 640 ==========
// Execute or exile an arrested officer (blockState > 0)

class 처단(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "처단"

    private val isExecute: Boolean get() = arg?.get("isExecute") as? Boolean ?: true

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.blockState <= 0.toShort()) return ConstraintResult.Fail("체포된 인물이 아닙니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val label: String
        if (isExecute) {
            dg.meta["terminated"] = "executed"
            dg.blockState = -1
            dg.npcState = -1
            label = "처형"
        } else {
            dg.factionId = 0
            dg.fleetId = 0
            dg.blockState = 0
            dg.betray = (dg.betray + 5).toShort()
            label = "추방"
        }
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}을(를) ${label}했습니다."),
        )
    }
}

// ========== 외교 (Diplomacy) PCP 320 ==========
// Unified diplomacy command: declare war / propose ceasefire / propose non-aggression

class 외교(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "외교"

    private val diplomacyType: String get() = arg?.get("diplomacyType") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        if (diplomacyType !in listOf("war", "ceasefire", "nonaggression")) {
            return ConstraintResult.Fail("유효한 외교 유형을 지정해야 합니다 (war/ceasefire/nonaggression)")
        }
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        val svc = services?.diplomacyService

        val label = when (diplomacyType) {
            "war" -> {
                svc?.declareWar(general.worldId, n.id, dn.id)
                "선전포고"
            }
            "ceasefire" -> {
                svc?.proposeCeasefire(general.worldId, n.id, dn.id)
                "종전 제의"
            }
            "nonaggression" -> {
                svc?.proposeNonAggression(general.worldId, n.id, dn.id)
                "불가침 제의"
            }
            else -> diplomacyType
        }

        general.experience += 50
        general.dedication += 50
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dn.name}에 ${label}했습니다."),
        )
    }
}

// ========== 통치목표 (Governance Goal) PCP 160 ==========
// Set planet-level governance goal in planet.meta

class 통치목표(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "통치목표"

    private val goal: String get() = arg?.get("goal") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dc = destCity ?: return ConstraintResult.Fail("대상 행성이 없습니다")
        if (dc.factionId != general.factionId) return ConstraintResult.Fail("아군 행성이 아닙니다")
        if (goal.isBlank()) return ConstraintResult.Fail("통치 목표를 입력해야 합니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        dc.meta["governanceGoal"] = goal
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dc.name}의 통치 목표를 설정했습니다: $goal"),
        )
    }
}
