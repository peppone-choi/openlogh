package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 연료보급 — 함대의 물자(supplies)를 보급한다.
 * MCP 커맨드. cpCost=160, waitTime=8, duration=48
 */
class FuelResupplyCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "연료보급"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 8

    override fun getPostReqTurn(): Int = 48

    override suspend fun run(rng: Random): CommandResult {
        val fleet = troop
            ?: return CommandResult.fail("소속 함대 없음")

        val replenishAmount = 1000
        val factionSupplies = nation?.supplies ?: 0
        val actual = minOf(replenishAmount, factionSupplies)

        // Deduct from faction supplies if available
        if (nation != null && actual > 0) {
            nation!!.supplies -= actual
        }

        // Replenish officer's personal supplies as proxy for fleet supplies
        general.supplies = minOf(general.supplies + actual, 9999)

        pushLog("${general.name}의 함대에 연료 보급을 실시했다. (+${actual} 물자)")

        return CommandResult(success = true, logs = logs)
    }
}
