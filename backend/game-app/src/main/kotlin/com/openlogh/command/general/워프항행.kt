package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 워프항행 (Warp Navigation) — gin7: ワープ航行
 *
 * Moves officer (and fleet) to an adjacent star system via star route.
 * Travel duration based on route distance, reduced by mobility stat.
 * Morale decreases by 3 per distance unit.
 */
class 워프항행(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "워프 항행"

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotSameDestCity(),
            ReqGeneralCrew(),
        )

    override val minConditionConstraints: List<Constraint>
        get() = listOf(
            ReqGeneralCrew(),
        )

    override fun getCost(): CommandCost {
        val supplyCost = (general.ships / 200.0).roundToInt().coerceAtLeast(1)
        return CommandCost(funds = 0, supplies = supplyCost)
    }

    override fun getCommandPointCost() = 1
    override fun getCommandPoolType() = StatCategory.MCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override fun getDuration(): Int {
        val baseTime = 300 // 5 min per distance unit
        val distance = getRouteDistance()
        val mobilityFactor = 100.0 / (100.0 + general.mobility.toDouble())
        return (baseTime * distance * mobilityFactor).roundToInt().coerceAtLeast(60)
    }

    private fun getRouteDistance(): Int {
        val destCityId = arg?.let {
            (it["destCityId"] as? Number)?.toLong()
                ?: (it["destCityID"] as? Number)?.toLong()
        } ?: return 1
        return getDistanceTo(destCityId) ?: 1
    }

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val destCityId = arg?.let {
            (it["destCityId"] as? Number)?.toLong()
                ?: (it["destCityID"] as? Number)?.toLong()
        } ?: 0L

        if (destCityId <= 0L) {
            return CommandResult(
                success = false,
                logs = listOf("<R>워프 항행</> 실패 - 목적지를 지정해야 합니다.")
            )
        }

        val distance = getDistanceTo(destCityId) ?: return CommandResult(
            success = false,
            logs = listOf("<R>워프 항행</> 실패 - 목적지까지 항로가 존재하지 않습니다.")
        )

        if (distance > 1) {
            return CommandResult(
                success = false,
                logs = listOf("<R>워프 항행</> 실패 - 인접 성계만 이동 가능합니다. (거리: $distance)")
            )
        }

        val destCityName = destPlanet?.name ?: "알 수 없음"
        val cost = getCost()
        val moralePenalty = 3 * distance
        val newMorale = max(10, general.morale.toInt() - moralePenalty)
        val moraleDelta = newMorale - general.morale.toInt()

        val josaRo = pickJosa(destCityName, "로")
        pushLog("<G><b>${destCityName}</b></>${josaRo} 워프 항행합니다. <1>$date</>")
        pushHistoryLog("<G><b>${destCityName}</b></>${josaRo} 워프 항행했습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <G><b>${destCityName}</b></>${josaRo} 워프 항행했습니다.")

        val exp = 80
        return CommandResult(
            success = true,
            logs = logs,
            message = buildString {
                append("""{"statChanges":{"cityId":"$destCityId","rice":${-cost.supplies},"atmos":$moraleDelta,"experience":$exp,"mobilityExp":1}""")
                append(""","tryUniqueLottery":true""")
                append("}")
            }
        )
    }
}
