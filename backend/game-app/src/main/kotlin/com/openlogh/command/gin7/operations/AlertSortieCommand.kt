package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 경계출동 — 함대를 전투 대기 상태로 전환한다.
 * MCP 커맨드. cpCost=160, waitTime=24, duration=0
 */
class AlertSortieCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "경계출동"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 24

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val fleet = troop
            ?: return CommandResult.fail("소속 함대 없음")

        fleet.meta["stance"] = "COMBAT"

        pushLog("${general.name}이(가) 경계 출동을 명령했다. 함대가 전투 대기 상태로 전환되었다.")

        return CommandResult(success = true, logs = logs)
    }
}
