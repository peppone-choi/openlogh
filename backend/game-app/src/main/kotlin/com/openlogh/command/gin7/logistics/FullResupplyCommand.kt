package com.openlogh.command.gin7.logistics

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 완전보급 — 함대의 물자를 최대치로 보급한다.
 * MCP 커맨드. cpCost=160, waitTime=0, duration=0
 */
class FullResupplyCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "완전보급"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val maxSupplies = 1000
        val prev = general.supplies
        val needed = maxSupplies - prev

        // Deduct from faction supplies if available
        if (nation != null && needed > 0) {
            val actual = minOf(needed, nation!!.supplies)
            nation!!.supplies -= actual
            general.supplies += actual
        } else if (needed > 0) {
            general.supplies = maxSupplies
        }

        pushLog("${general.name}의 함대에 완전 보급을 실시했다. 물자 ${prev} → ${general.supplies}")

        return CommandResult(success = true, logs = logs)
    }
}
