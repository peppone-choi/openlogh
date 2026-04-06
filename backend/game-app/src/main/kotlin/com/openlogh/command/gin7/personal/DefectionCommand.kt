package com.openlogh.command.gin7.personal

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 망명 커맨드 — PCP 320 소모, 다른 진영으로 이전 + officerLevel 강등 페널티 + positionCards 박탈
 */
class DefectionCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "망명"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val targetFaction = destFaction ?: return CommandResult.fail("목표 진영 미지정")

        general.factionId = targetFaction.id
        general.officerLevel = maxOf(1, (general.officerLevel - 2).toShort())
        general.positionCards.clear()

        pushGlobalHistoryLog("${general.name}이(가) ${targetFaction.name}으로 망명했다.")
        pushLog("${general.name}이(가) ${targetFaction.name}으로 망명했다.")
        return CommandResult.success(logs)
    }
}
