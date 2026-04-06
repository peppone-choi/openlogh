package com.openlogh.command.gin7.commander

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 작전철회 커맨드 — MCP 5~320 소모 (scale에 비례).
 * nation.meta["operationPlan"]을 제거한다.
 */
class OperationCancelCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "작전철회"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val nation = nation ?: return CommandResult.fail("소속 진영 정보를 찾을 수 없습니다.")

        val planName = (nation.meta["operationPlan"] as? Map<*, *>)?.get("name")
            ?: "진행 중인 작전"

        nation.meta.remove("operationPlan")

        pushLog("${general.name}이(가) '${planName}' 작전을 철회했다.")

        return CommandResult.success(logs)
    }
}
