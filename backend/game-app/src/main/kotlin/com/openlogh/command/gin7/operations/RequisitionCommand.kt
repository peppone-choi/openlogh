package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 징발 — 대상 행성에서 물자를 징발하여 진영으로 이전한다.
 * MCP 커맨드. cpCost=160, waitTime=24, duration=0
 */
class RequisitionCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "징발"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 24

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destPlanet
            ?: return CommandResult.fail("대상 행성 미지정")

        val amount = 500
        // Planet uses production as the primary resource proxy for supplies
        val actual = minOf(amount, target.production)
        target.production = maxOf(0, target.production - actual)

        // Transfer to faction supplies if available
        if (nation != null) {
            nation!!.supplies += actual
        }

        pushLog("${general.name}이(가) ${target.name}에서 물자를 징발했다. (+${actual} 물자)")

        return CommandResult(success = true, logs = logs)
    }
}
