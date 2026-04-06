package com.openlogh.command.gin7.personal

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 회견 커맨드 — PCP 10 소모, 대상 장교와 회견 (Phase 5 AI 연동 예정)
 */
class AudienceCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "회견"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destOfficer ?: return CommandResult.fail("대상 장교 미지정")

        pushLog("${general.name}이(가) ${target.name}과(와) 회견했다.")
        return CommandResult.success(logs)
    }
}
