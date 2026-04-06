package com.openlogh.command.gin7.personal

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 지원 커맨드 — PCP 160 소모, Officer.factionId 변경 + officerLevel = 1 (신병 기본)
 */
class EnlistCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "지원"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val targetFaction = destFaction ?: return CommandResult.fail("목표 진영 미지정")

        general.factionId = targetFaction.id
        general.officerLevel = 1

        pushGlobalHistoryLog("${general.name}이(가) ${targetFaction.name}에 지원했다.")
        pushLog("${general.name}이(가) ${targetFaction.name}에 지원했다.")
        return CommandResult.success(logs)
    }
}
