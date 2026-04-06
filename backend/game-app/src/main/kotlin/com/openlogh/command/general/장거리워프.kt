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
 * 장거리워프 (Long-range Warp) — gin7: 長距離ワープ
 *
 * Multi-hop warp navigation (2-3 systems away).
 * Higher cost (2 MCP), higher morale penalty (5 per hop).
 * Engine item enables 3-hop range; default max range is 2.
 */
class 장거리워프(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "장거리 워프"

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
        val supplyCost = (general.ships / 100.0).roundToInt().coerceAtLeast(2)
        return CommandCost(funds = 0, supplies = supplyCost)
    }

    override fun getCommandPointCost() = 2
    override fun getCommandPoolType() = StatCategory.MCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 1

    override fun getDuration(): Int {
        val baseTime = 300 // 5 min per distance unit
        val distance = getRouteDistance()
        val mobilityFactor = 100.0 / (100.0 + general.mobility.toDouble())
        return (baseTime * distance * mobilityFactor).roundToInt().coerceAtLeast(120)
    }

    private fun getRouteDistance(): Int {
        val destCityId = arg?.let {
            (it["destCityId"] as? Number)?.toLong()
                ?: (it["destCityID"] as? Number)?.toLong()
        } ?: return 2
        return getDistanceTo(destCityId) ?: 2
    }

    private fun getMaxRange(): Int {
        // Engine item enables 3-hop range
        return if (general.engineCode != "None" && general.engineCode.isNotBlank()) 3 else 2
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
                logs = listOf("<R>장거리 워프</> 실패 - 목적지를 지정해야 합니다.")
            )
        }

        val distance = getDistanceTo(destCityId) ?: return CommandResult(
            success = false,
            logs = listOf("<R>장거리 워프</> 실패 - 목적지까지 항로가 존재하지 않습니다.")
        )

        val maxRange = getMaxRange()
        if (distance < 2) {
            return CommandResult(
                success = false,
                logs = listOf("<R>장거리 워프</> 실패 - 인접 성계는 일반 워프 항행을 사용하세요.")
            )
        }
        if (distance > maxRange) {
            val rangeNote = if (maxRange == 2) " (기관 장착 시 3칸 가능)" else ""
            return CommandResult(
                success = false,
                logs = listOf("<R>장거리 워프</> 실패 - 최대 ${maxRange}칸까지 이동 가능합니다. (거리: $distance)$rangeNote")
            )
        }

        val destCityName = destPlanet?.name ?: "알 수 없음"
        val cost = getCost()
        val moralePenalty = 5 * distance
        val newMorale = max(10, general.morale.toInt() - moralePenalty)
        val moraleDelta = newMorale - general.morale.toInt()

        val josaRo = pickJosa(destCityName, "로")
        pushLog("<G><b>${destCityName}</b></>${josaRo} 장거리 워프합니다. (거리: $distance) <1>$date</>")
        pushHistoryLog("<G><b>${destCityName}</b></>${josaRo} 장거리 워프했습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <G><b>${destCityName}</b></>${josaRo} 장거리 워프했습니다.")

        val exp = 80 + (distance * 30)
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
