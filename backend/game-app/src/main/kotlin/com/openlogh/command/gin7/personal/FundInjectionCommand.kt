package com.openlogh.command.gin7.personal

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 자금투입 커맨드 — PCP 80 소모, 개인 자금을 진영 자금으로 이전
 */
class FundInjectionCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "자금투입"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val amount = when (val v = arg?.get("amount")) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: 1000
            else -> 1000
        }

        if (general.funds < amount) {
            return CommandResult.fail("자금 부족 (보유: ${general.funds}, 필요: $amount)")
        }

        general.funds -= amount

        val currentNation = nation
        if (currentNation != null) {
            currentNation.funds += amount
        }

        pushLog("${general.name}이(가) 진영에 ${amount} 자금을 투입했다.")
        return CommandResult.success(logs)
    }
}
