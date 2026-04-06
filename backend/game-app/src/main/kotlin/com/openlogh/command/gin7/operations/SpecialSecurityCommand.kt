package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 특별경비 — 현재 위치 행성의 치안을 강화한다.
 * MCP 커맨드. cpCost=160, waitTime=0, duration=24
 */
class SpecialSecurityCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "특별경비"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 24

    override suspend fun run(rng: Random): CommandResult {
        val planet = city
            ?: return CommandResult.fail("현재 행성 정보 없음")

        val prev = planet.security
        planet.security = minOf(100, planet.security + 10)

        pushLog("${general.name}이(가) ${planet.name}에서 특별 경비를 실시했다. 치안 ${prev} → ${planet.security}")

        return CommandResult(success = true, logs = logs)
    }
}
