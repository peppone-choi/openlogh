package com.openlogh.command.gin7.commander

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 발령 커맨드 — MCP 1~320 소모 (rank에 비례).
 * destOfficer에게 positionCard를 부여하고 fleetId를 배속한다.
 */
class AssignmentCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "발령"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destOfficer ?: return CommandResult.fail("대상 장교 미지정")

        val positionCard = arg?.get("positionCard") as? String
        val fleetId = (arg?.get("fleetId") as? Number)?.toLong()

        if (positionCard != null) {
            if (!target.positionCards.contains(positionCard)) {
                target.positionCards.add(positionCard)
            }
        }

        if (fleetId != null) {
            target.fleetId = fleetId
        }

        pushLog("${target.name}에게 발령을 내렸다.")
        pushGlobalHistoryLog("${target.name} 발령 완료")

        return CommandResult.success(logs)
    }
}
