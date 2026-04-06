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
 * 함선보급 (Fleet Resupply) — gin7: 兵站 - 補充
 *
 * Transfer ships from planet garrison/production to officer's fleet.
 * Planet must be owned and have production facilities.
 */
class 함선보급(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "함선 보급"

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
        )

    override val minConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
        )

    override fun getCost() = CommandCost(funds = 0, supplies = 0)
    override fun getCommandPointCost() = 1
    override fun getCommandPoolType() = StatCategory.MCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0
    override fun getDuration() = 300

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val amount = (arg?.get("amount") as? Number)?.toInt() ?: 300

        val c = city ?: return CommandResult(
            success = false,
            logs = listOf("<R>함선 보급</> 실패 - 행성 정보를 불러올 수 없습니다.")
        )

        if (amount <= 0) {
            return CommandResult(
                success = false,
                logs = listOf("<R>함선 보급</> 실패 - 보급량은 1 이상이어야 합니다.")
            )
        }

        // Production-based available ships: use planet production as available pool
        val productionCapacity = c.production.coerceAtLeast(0)
        val availableShips = (productionCapacity / 10).coerceAtLeast(0)
        val actual = amount.coerceAtMost(availableShips)

        if (actual <= 0) {
            return CommandResult(
                success = false,
                logs = listOf("<R>함선 보급</> 실패 - 행성에 보급 가능한 함선이 없습니다. (생산력: ${c.production})")
            )
        }

        // Cost: supplies proportional to ships received
        val supplyCost = actual * 2
        val n = nation
        if (n != null && n.supplies < supplyCost) {
            return CommandResult(
                success = false,
                logs = listOf("<R>함선 보급</> 실패 - 진영 물자 부족 (필요: $supplyCost, 보유: ${n.supplies})")
            )
        }

        pushLog("함선 <C>${String.format("%,d", actual)}</> 척을 보급받았습니다. <1>$date</>")
        pushHistoryLog("함선 ${String.format("%,d", actual)} 척 보급. <1>$date</>")

        return CommandResult(
            success = true,
            logs = logs,
            message = buildString {
                append("""{"statChanges":{"crew":$actual,"experience":40,"commandExp":1}""")
                if (n != null) {
                    append(""","nationChanges":{"rice":${-supplyCost}}""")
                }
                append("}")
            }
        )
    }
}
