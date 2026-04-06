package com.openlogh.command.gin7.personnel

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 승진 커맨드 — PCP 160 소모, destOfficer.officerLevel +1 (max 10)
 */
class PromoteCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "승진"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destOfficer ?: return CommandResult.fail("대상 장교 미지정")

        target.officerLevel = minOf(10, (target.officerLevel + 1).toInt()).toShort()

        pushGlobalHistoryLog("${target.name}이(가) ${target.officerLevel}계급으로 승진했다.")
        pushLog("${target.name}이(가) ${target.officerLevel}계급으로 승진했다.")
        return CommandResult.success(logs)
    }
}
