package com.openlogh.command.gin7.personnel

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 강등 커맨드 — PCP 320 소모, destOfficer.officerLevel -1 (min 0)
 */
class DemoteCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "강등"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destOfficer ?: return CommandResult.fail("대상 장교 미지정")

        target.officerLevel = maxOf(0, (target.officerLevel - 1).toInt()).toShort()

        pushGlobalHistoryLog("${target.name}이(가) ${target.officerLevel}계급으로 강등됐다.")
        pushLog("${target.name}이(가) ${target.officerLevel}계급으로 강등됐다.")
        return CommandResult.success(logs)
    }
}
