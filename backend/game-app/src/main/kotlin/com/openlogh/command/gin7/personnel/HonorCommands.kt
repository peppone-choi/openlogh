package com.openlogh.command.gin7.personnel

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 서작 커맨드 — PCP 160 소모, destOfficer.meta["title"] 설정
 */
class GrantTitleCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "서작"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destOfficer ?: return CommandResult.fail("대상 장교 미지정")
        val title = arg?.get("title") as? String ?: "귀족"

        target.meta["title"] = title

        pushLog("${target.name}에게 ${title} 작위가 수여됐다.")
        return CommandResult.success(logs)
    }
}

/**
 * 서훈 커맨드 — PCP 160 소모, destOfficer.meta["decoration"] 설정
 */
class AwardDecorationCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "서훈"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destOfficer ?: return CommandResult.fail("대상 장교 미지정")
        val decoration = arg?.get("decoration") as? String ?: "훈장"

        target.meta["decoration"] = decoration

        pushLog("${target.name}에게 ${decoration}이(가) 수여됐다.")
        return CommandResult.success(logs)
    }
}
