package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.GeneralCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.General
import kotlin.random.Random

/**
 * 순찰 (Patrol) — general patrols the current city to detect and intercept intruders.
 *
 * Saves patrol state in general.lastTurn for the turn engine.
 */
class 순찰(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : GeneralCommand(general, env, arg) {

    override val actionName = "순찰"

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

    override fun getCost() = CommandCost(gold = 0, rice = 30)

    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        general.lastTurn = mutableMapOf(
            "action" to "순찰",
            "patrolCityId" to general.cityId,
        )

        val josaGa = josa(general.name, "이")
        pushLog("${general.name}${josaGa} 순찰을 시작했습니다.")

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"rice":${-getCost().rice}}}""",
        )
    }
}
