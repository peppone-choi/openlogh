package com.openlogh.command.gin7.commander

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 작전계획 커맨드 — MCP 10~1280 소모 (scale에 비례).
 * nation.meta["operationPlan"]에 작전 데이터를 저장한다.
 */
class OperationPlanCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "작전계획"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val nation = nation ?: return CommandResult.fail("소속 진영 정보를 찾을 수 없습니다.")

        val scale = (arg?.get("scale") as? Number)?.toInt() ?: 1
        val planName = arg?.get("planName") as? String
            ?: "작전계획-${env.year}-${env.month}"

        nation.meta["operationPlan"] = mapOf(
            "name" to planName,
            "scale" to scale,
            "year" to env.year,
            "month" to env.month,
        )

        pushLog("${general.name}이(가) '${planName}' 작전계획을 수립했다.")
        pushNationalHistoryLog("작전계획 수립: $planName")

        return CommandResult.success(logs)
    }
}
