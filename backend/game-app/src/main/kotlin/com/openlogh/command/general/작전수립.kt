package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 작전수립 (Operation Planning) — gin7: 作戦計画
 *
 * Sets a faction-level strategic goal for a target star system.
 * Operation types: occupy (점령), defend (방어), sweep (소탕)
 * Requires OPERATIONS card, costs 2 MCP.
 */
class 작전수립(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "작전 수립"

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
            ReqGeneralCrew(),
        )

    override val minConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
        )

    override fun getCost() = CommandCost(funds = 0, supplies = 0)
    override fun getCommandPointCost() = 2
    override fun getCommandPoolType() = StatCategory.MCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 1
    override fun getDuration() = 600

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val targetStarId = arg?.let {
            (it["targetStarId"] as? Number)?.toLong()
                ?: (it["destCityId"] as? Number)?.toLong()
                ?: (it["destCityID"] as? Number)?.toLong()
        } ?: 0L
        val operationType = (arg?.get("operationType") as? String)?.trim() ?: "occupy"

        val validTypes = setOf("occupy", "defend", "sweep")
        if (operationType !in validTypes) {
            return CommandResult(
                success = false,
                logs = listOf("<R>작전 수립</> 실패 - 유효하지 않은 작전 유형: $operationType (가능: occupy, defend, sweep)")
            )
        }

        if (targetStarId <= 0L) {
            return CommandResult(
                success = false,
                logs = listOf("<R>작전 수립</> 실패 - 목표 성계를 지정해야 합니다.")
            )
        }

        val operationTypeName = when (operationType) {
            "occupy" -> "점령"
            "defend" -> "방어"
            "sweep" -> "소탕"
            else -> operationType
        }

        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 성계 $targetStarId ${operationTypeName} 작전을 수립했습니다. <1>$date</>")
        pushHistoryLog("성계 $targetStarId ${operationTypeName} 작전을 수립했습니다. <1>$date</>")
        pushNationalHistoryLog("<Y>${general.name}</>${pickJosa(general.name, "이")} ${operationTypeName} 작전을 수립했습니다.")

        val exp = 100
        val meritGain = 5

        return CommandResult(
            success = true,
            logs = logs,
            message = buildString {
                append("""{"statChanges":{"experience":$exp,"commandExp":1}""")
                append(""","meritPoints":$meritGain""")
                append(""","factionOperation":{"targetStarId":$targetStarId,"type":"$operationType","officerId":${general.id},"officerName":"${general.name}"}""")
                append("}")
            }
        )
    }
}
