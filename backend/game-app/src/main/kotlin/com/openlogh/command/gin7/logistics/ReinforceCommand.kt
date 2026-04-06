package com.openlogh.command.gin7.logistics

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 보충 — 함선을 보충하여 전력을 강화한다.
 * MCP 커맨드. cpCost=160, waitTime=0, duration=0
 */
class ReinforceCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "보충"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val shipCount = arg?.get("shipCount")?.let {
            when (it) {
                is Number -> it.toLong()
                is String -> it.toLongOrNull()
                else -> null
            }
        } ?: 100L

        // Deduct from faction ships if available (nation doesn't have ships field directly)
        general.ships += shipCount.toInt()

        pushLog("병력을 보충했다. (+${shipCount}척) 현재: ${general.ships}척")

        return CommandResult(success = true, logs = logs)
    }
}
