package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 요격 (Ambush/Intercept) — sets a general to ambush a road segment toward destPlanet.
 *
 * Saves interception state in general.lastTurn so the turn engine can trigger
 * FieldBattleService when an enemy general passes through the monitored road.
 */
class 요격(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "요격"

    override val fullConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotBeNeutral(),
                OccupiedCity(),
                ReqGeneralCrew(),
                ReqGeneralRice(cost.supplies),
            )
        }

    override val minConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotBeNeutral(),
                OccupiedCity(),
                ReqGeneralCrew(),
                ReqGeneralRice(cost.supplies),
            )
        }

    override fun getCost() = CommandCost(gold = 0, rice = 50)

    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val destCityId = arg?.get("destCityId") as? Long
            ?: (arg?.get("destCityId") as? Number)?.toLong()
            ?: destPlanet?.id
            ?: 0L

        val destCityName = destPlanet?.name ?: "알 수 없음"

        general.lastTurn = mutableMapOf(
            "action" to "요격",
            "interceptionTargetCityId" to destCityId,
            "originCityId" to general.planetId,
        )

        val josaGa = josa(general.name, "이")
        pushLog("${general.name}${josaGa} ${destCityName} 방면 도로에 매복했습니다.")

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"rice":${-getCost().supplies}}}""",
        )
    }
}
