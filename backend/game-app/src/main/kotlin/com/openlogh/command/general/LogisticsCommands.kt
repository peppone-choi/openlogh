@file:Suppress("ClassName", "unused")

package com.openlogh.command.general

import com.openlogh.command.*
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.entity.*
import kotlin.math.min
import kotlin.random.Random

// ========== 완전수리 (Full Repair) MCP 640 ==========

class 완전수리(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : BaseCommand(general, env, arg) {
    override val actionName = "완전수리"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.troopId == 0L) return ConstraintResult.Fail("소속 함대가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        general.training = 100.toShort()
        general.morale = 100.toShort()
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} <C>완전수리</C>로 함대를 완전히 수리했습니다."),
        )
    }
}

// ========== 완전보급 (Full Supply) MCP 640 ==========

class 완전보급(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : BaseCommand(general, env, arg) {
    override val actionName = "완전보급"

    private val amount: Int get() = (arg?.get("amount") as? Number)?.toInt() ?: 1000

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.troopId == 0L) return ConstraintResult.Fail("소속 함대가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation
        val added: Int
        if (n != null) {
            val take = min(amount, n.supplies)
            n.supplies -= take
            general.supplies += take
            added = take
        } else {
            general.supplies += amount
            added = amount
        }
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} <C>완전보급</C>으로 물자 ${added}을(를) 보급했습니다."),
        )
    }
}

// ========== 재편성 (Reorganize) MCP 320 ==========

class 재편성(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : BaseCommand(general, env, arg) {
    override val actionName = "재편성"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.troopId == 0L) return ConstraintResult.Fail("소속 함대가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val bonus = ((general.leadership.toInt() + general.command.toInt()) / 2.0
            * 0.15 * (0.8 + rng.nextDouble() * 0.4)).toInt()
        val trainBefore = general.training.toInt()
        val moraleBefore = general.morale.toInt()
        general.training = min(100, trainBefore + bonus).toShort()
        general.morale = min(100, moraleBefore + bonus / 2).toShort()
        general.experience += bonus
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} <C>재편성</C>으로 함대 훈련도가 향상되었습니다."),
        )
    }
}

// ========== 반출입 (Supply Transfer) MCP 160 ==========

class 반출입(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : BaseCommand(general, env, arg) {
    override val actionName = "반출입"

    private val amount: Int get() = (arg?.get("amount") as? Number)?.toInt() ?: 0
    private val isExport: Boolean get() = arg?.get("isExport") as? Boolean ?: true

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation
        val label: String
        if (isExport) {
            val take = min(amount, general.supplies)
            general.supplies -= take
            if (n != null) n.supplies += take
            label = "반출"
        } else {
            val give = if (n != null) min(amount, n.supplies) else amount
            if (n != null) n.supplies -= give
            general.supplies += give
            label = "반입"
        }
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} <C>반출입</C> ${label} 완료했습니다."),
        )
    }
}
