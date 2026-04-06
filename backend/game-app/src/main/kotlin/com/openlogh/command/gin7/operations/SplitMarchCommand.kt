package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 분열행진 — 함대를 분산 이동시킨다. Phase 3 전술전 연동 예정.
 * MCP 커맨드. cpCost=160, waitTime=24, duration=0
 */
class SplitMarchCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "분열행진"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 24

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        // Phase 3 전술전 연동 예정 — 현재는 현재 위치를 유지하며 로그만 기록
        pushLog("${general.name}이(가) 분열 행진을 실시했다.")

        return CommandResult(success = true, logs = logs)
    }
}
