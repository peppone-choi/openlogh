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
 * 물자배분 (Supply Allocation) — gin7: 兵站 - 物資配分
 *
 * Transfer supplies from planet warehouse to officer's personal stores.
 * Requires officer on owned planet with available supplies.
 */
class 물자배분(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "물자 배분"

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
            SuppliedCity(),
        )

    override val minConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
        )

    override fun getCost() = CommandCost(funds = 0, supplies = 0)
    override fun getCommandPointCost() = 1
    override fun getCommandPoolType() = StatCategory.PCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0
    override fun getDuration() = 300

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val amount = (arg?.get("amount") as? Number)?.toInt() ?: 100
        val resourceType = (arg?.get("resourceType") as? String) ?: "supplies"

        val c = city ?: return CommandResult(
            success = false,
            logs = listOf("<R>물자 배분</> 실패 - 행성 정보를 불러올 수 없습니다.")
        )

        if (amount <= 0) {
            return CommandResult(
                success = false,
                logs = listOf("<R>물자 배분</> 실패 - 배분량은 1 이상이어야 합니다.")
            )
        }

        // Determine what to transfer based on resourceType
        when (resourceType) {
            "funds" -> {
                val n = nation ?: return CommandResult(success = false, logs = listOf("<R>물자 배분</> 실패 - 소속 진영 없음"))
                val available = n.funds
                val actual = amount.coerceAtMost(available)
                if (actual <= 0) {
                    return CommandResult(success = false, logs = listOf("<R>물자 배분</> 실패 - 진영에 배분할 자금이 없습니다."))
                }
                pushLog("진영으로부터 자금 <C>${String.format("%,d", actual)}</> 배분받았습니다. <1>$date</>")
                pushHistoryLog("자금 ${String.format("%,d", actual)} 배분받음. <1>$date</>")
                return CommandResult(
                    success = true,
                    logs = logs,
                    message = """{"statChanges":{"gold":$actual,"experience":30,"administrationExp":1},"nationChanges":{"gold":${-actual}}}"""
                )
            }
            else -> {
                // Default: supplies
                val n = nation ?: return CommandResult(success = false, logs = listOf("<R>물자 배분</> 실패 - 소속 진영 없음"))
                val available = n.supplies
                val actual = amount.coerceAtMost(available)
                if (actual <= 0) {
                    return CommandResult(success = false, logs = listOf("<R>물자 배분</> 실패 - 진영에 배분할 물자가 없습니다."))
                }
                pushLog("진영으로부터 물자 <C>${String.format("%,d", actual)}</> 배분받았습니다. <1>$date</>")
                pushHistoryLog("물자 ${String.format("%,d", actual)} 배분받음. <1>$date</>")
                return CommandResult(
                    success = true,
                    logs = logs,
                    message = """{"statChanges":{"rice":$actual,"experience":30,"administrationExp":1},"nationChanges":{"rice":${-actual}}}"""
                )
            }
        }
    }
}
