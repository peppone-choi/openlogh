package com.openlogh.command.gin7.personnel

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 발탁 커맨드 — PCP 640 소모, destOfficer.officerLevel +2 (max 10, 2계급 특진)
 */
class FieldPromoteCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "발탁"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destOfficer ?: return CommandResult.fail("대상 장교 미지정")

        target.officerLevel = minOf(10, (target.officerLevel + 2).toInt()).toShort()

        pushGlobalHistoryLog("${target.name}이(가) 발탁되어 ${target.officerLevel}계급으로 특진했다.")
        pushLog("${target.name}이(가) 발탁되어 ${target.officerLevel}계급으로 특진했다.")
        return CommandResult.success(logs)
    }
}
