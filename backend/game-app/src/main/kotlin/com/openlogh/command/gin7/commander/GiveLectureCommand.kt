package com.openlogh.command.gin7.commander

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 강의 커맨드 — MCP 160 소모, restriction: 사관학교.
 * 수강생 훈련도 증가는 수강(AttendLectureCommand)에서 처리한다.
 */
class GiveLectureCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "강의"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        general.meta["lastLectureDate"] = "${env.year}-${env.month}"

        pushLog("${general.name}이(가) 사관학교에서 강의했다.")

        return CommandResult.success(logs)
    }
}
