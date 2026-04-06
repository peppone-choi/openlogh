package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 육전대출격 — 육전대를 출격 상태로 전환한다.
 * MCP 커맨드. cpCost=80, waitTime=0, duration=0
 */
class GroundForceDeployCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "육전대출격"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        general.meta["groundForceStance"] = "DEPLOYED"
        pushLog("${general.name}이(가) 육전대를 출격시켰다.")
        return CommandResult(success = true, logs = logs)
    }
}

/**
 * 육전대철수 — 육전대를 철수 상태로 전환한다.
 * MCP 커맨드. cpCost=80, waitTime=0, duration=0
 */
class GroundForceWithdrawCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "육전대철수"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        general.meta["groundForceStance"] = "WITHDRAWN"
        pushLog("${general.name}이(가) 육전대를 철수시켰다.")
        return CommandResult(success = true, logs = logs)
    }
}
