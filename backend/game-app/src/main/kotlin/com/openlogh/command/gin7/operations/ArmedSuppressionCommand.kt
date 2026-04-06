package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 무력진압 — 대상 행성의 치안을 강제로 회복하되 지지도가 하락한다.
 * MCP 커맨드. cpCost=160, waitTime=24, duration=0
 */
class ArmedSuppressionCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "무력진압"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 24

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destPlanet
            ?: return CommandResult.fail("대상 행성 미지정")

        val prevSecurity = target.security
        val prevApproval = target.approval

        target.security = minOf(100, target.security + 20)
        target.approval = maxOf(0f, target.approval - 10f)

        pushLog("${general.name}이(가) ${target.name}에서 무력 진압을 실시했다.")
        pushLog("치안 ${prevSecurity} → ${target.security}, 지지도 ${prevApproval} → ${target.approval}")

        return CommandResult(success = true, logs = logs)
    }
}
