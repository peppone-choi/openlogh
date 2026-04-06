package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 작전지시 (Operation Directive) — gin7: 作戦指示
 *
 * Faction-level operation order issued by faction leaders.
 * Sets strategic objective visible to all faction members.
 * Stored in faction.meta["operations"] as a list of active operations.
 */
class 작전지시(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "작전 지시"

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
        )

    override val minConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
        )

    override fun getCost() = CommandCost(funds = 0, supplies = 0)
    override fun getCommandPointCost() = 3
    override fun getCommandPoolType() = StatCategory.MCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 2
    override fun getDuration() = 900

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val targetStarId = arg?.let {
            (it["targetStarId"] as? Number)?.toLong()
                ?: (it["destCityId"] as? Number)?.toLong()
                ?: (it["destCityID"] as? Number)?.toLong()
        } ?: 0L
        val operationType = (arg?.get("operationType") as? String)?.trim() ?: "occupy"
        val directive = (arg?.get("directive") as? String)?.trim() ?: ""

        val validTypes = setOf("occupy", "defend", "sweep", "retreat", "blockade")
        if (operationType !in validTypes) {
            return CommandResult(
                success = false,
                logs = listOf("<R>작전 지시</> 실패 - 유효하지 않은 작전 유형: $operationType")
            )
        }

        if (targetStarId <= 0L) {
            return CommandResult(
                success = false,
                logs = listOf("<R>작전 지시</> 실패 - 목표 성계를 지정해야 합니다.")
            )
        }

        val factionName = nation?.name ?: "알 수 없음"
        val operationTypeName = when (operationType) {
            "occupy" -> "점령"
            "defend" -> "방어"
            "sweep" -> "소탕"
            "retreat" -> "후퇴"
            "blockade" -> "봉쇄"
            else -> operationType
        }

        // Store operation in faction meta
        val n = nation
        if (n != null) {
            @Suppress("UNCHECKED_CAST")
            val operations = (n.meta["operations"] as? MutableList<Map<String, Any>>)
                ?: mutableListOf()
            val newOp = mutableMapOf<String, Any>(
                "targetStarId" to targetStarId,
                "type" to operationType,
                "issuedBy" to general.id,
                "issuedByName" to general.name,
                "issuedAt" to "${env.year}-${env.month}",
                "directive" to directive,
            )
            // Keep max 5 active operations
            val updated = (operations + newOp).takeLast(5).toMutableList()
            n.meta["operations"] = updated
        }

        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} $factionName 전체에 성계 $targetStarId ${operationTypeName} 작전을 지시했습니다. <1>$date</>")
        pushHistoryLog("$factionName 전체에 ${operationTypeName} 작전을 지시했습니다. <1>$date</>")
        pushNationalHistoryLog("<Y>${general.name}</>${pickJosa(general.name, "이")} ${operationTypeName} 작전을 지시했습니다.")

        val exp = 150
        val meritGain = 10

        return CommandResult(
            success = true,
            logs = logs,
            message = buildString {
                append("""{"statChanges":{"experience":$exp,"commandExp":1,"leadershipExp":1}""")
                append(""","meritPoints":$meritGain""")
                append("}")
            }
        )
    }
}
