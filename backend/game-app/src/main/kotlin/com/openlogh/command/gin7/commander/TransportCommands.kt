package com.openlogh.command.gin7.commander

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 수송계획 커맨드 — MCP 80 소모.
 * nation.meta["transportPlan"]에 수송 계획 데이터를 저장한다.
 */
class TransportPlanCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "수송계획"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val nation = nation ?: return CommandResult.fail("소속 진영 정보를 찾을 수 없습니다.")

        val sourcePlanetId = (arg?.get("sourcePlanetId") as? Number)?.toLong()
            ?: return CommandResult.fail("출발 행성 미지정")
        val destPlanetId = (arg?.get("destPlanetId") as? Number)?.toLong()
            ?: return CommandResult.fail("목표 행성 미지정")
        val amount = arg?.get("amount") ?: 1000

        nation.meta["transportPlan"] = mapOf(
            "from" to sourcePlanetId,
            "to" to destPlanetId,
            "amount" to amount,
        )

        pushLog("수송 계획을 수립했다.")

        return CommandResult.success(logs)
    }
}

/**
 * 수송중지 커맨드 — MCP 80 소모.
 * nation.meta["transportPlan"]을 제거한다.
 */
class TransportCancelCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "수송중지"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val nation = nation ?: return CommandResult.fail("소속 진영 정보를 찾을 수 없습니다.")

        nation.meta.remove("transportPlan")

        pushLog("수송을 중지했다.")

        return CommandResult.success(logs)
    }
}
