package com.openlogh.command.gin7.personal

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 퇴역 커맨드 — PCP 160 소모, factionId/fleetId = 0, positionCards 초기화
 */
class RetirementCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "퇴역"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        general.factionId = 0
        general.fleetId = 0
        general.positionCards.clear()

        pushGlobalHistoryLog("${general.name}이(가) 퇴역했다.")
        pushLog("${general.name}이(가) 퇴역했다.")
        return CommandResult.success(logs)
    }
}
