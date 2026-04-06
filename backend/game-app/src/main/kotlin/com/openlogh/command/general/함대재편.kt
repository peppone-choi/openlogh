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
 * 함대재편 (Fleet Reorganization) — gin7: 兵站 - 再編成
 *
 * Change ship class of officer's fleet.
 * Training drops by 20% on class change, morale unchanged.
 * Requires being on owned planet.
 */
class 함대재편(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "함대 재편"

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
            ReqGeneralCrew(),
        )

    override fun getCost(): CommandCost {
        val fundsCost = (general.ships / 50.0).toInt().coerceAtLeast(10)
        return CommandCost(funds = fundsCost, supplies = 0)
    }

    override fun getCommandPointCost() = 1
    override fun getCommandPoolType() = StatCategory.MCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 1
    override fun getDuration() = 600

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val newShipClass = (arg?.get("crewType") as? Number)?.toInt()
            ?: (arg?.get("shipClass") as? Number)?.toInt()
            ?: return CommandResult(
                success = false,
                logs = listOf("<R>함대 재편</> 실패 - 함종을 지정해야 합니다.")
            )

        if (newShipClass == general.shipClass.toInt()) {
            return CommandResult(
                success = false,
                logs = listOf("<R>함대 재편</> 실패 - 이미 해당 함종입니다.")
            )
        }

        val shipClassName = getShipClassName(newShipClass)
        val cost = getCost()

        // Training penalty: drop by 20%
        val trainingPenalty = -(general.training * 20 / 100).toInt()

        pushLog("함대를 <C>$shipClassName</>(으)로 재편했습니다. (훈련도 20% 감소) <1>$date</>")
        pushHistoryLog("함대를 $shipClassName(으)로 재편. <1>$date</>")

        return CommandResult(
            success = true,
            logs = logs,
            message = buildString {
                append("""{"statChanges":{"gold":${-cost.funds},"experience":50,"commandExp":1}""")
                append(""","crewTypeChange":$newShipClass""")
                append(""","trainChange":$trainingPenalty""")
                append("}")
            }
        )
    }

    private fun getShipClassName(classId: Int): String {
        return when (classId) {
            0 -> "전함"
            1 -> "순양함"
            2 -> "구축함"
            3 -> "항공모함"
            4 -> "수송함"
            5 -> "병원선"
            else -> "함종$classId"
        }
    }
}
