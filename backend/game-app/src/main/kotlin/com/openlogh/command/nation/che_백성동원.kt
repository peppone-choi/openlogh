package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.NationCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.General
import kotlin.random.Random

private const val STRATEGIC_GLOBAL_DELAY = 9

class che_백성동원(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : NationCommand(general, env, arg) {

    override val actionName = "백성동원"

    override val fullConditionConstraints = listOf(
        OccupiedCity(), BeChief(), OccupiedDestCity(), AvailableStrategicCommand()
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")
        val dCity = destCity ?: return CommandResult(false, logs, "대상 도시 정보를 찾을 수 없습니다")
        val date = formatDate()
        val generalName = general.name
        val josaYi = pickJosa(generalName, "이")
        val destCityName = dCity.name

        general.experience += 5 * (getPreReqTurn() + 1)
        general.dedication += 5 * (getPreReqTurn() + 1)

        // PHP: def = GREATEST(def_max * 0.8, def), wall = GREATEST(wall_max * 0.8, wall)
        dCity.def = maxOf((dCity.defMax * 0.8).toInt(), dCity.def)
        dCity.wall = maxOf((dCity.wallMax * 0.8).toInt(), dCity.wall)

        // Set strategic command cooldown
        n.strategicCmdLimit = STRATEGIC_GLOBAL_DELAY.toShort()

        // Broadcast to own nation generals
        val broadcastMessage = "<Y>${generalName}</>${josaYi} <G><b>${destCityName}</b></>에 <M>백성동원</>을 하였습니다."
        broadcastToNationGenerals(n.id, general.id, broadcastMessage)

        pushLog("백성동원 발동! <1>$date</>")
        pushHistoryLog("<G><b>${destCityName}</b></>에 <M>백성동원</>을 발동")
        pushNationalHistoryLog("<Y>${generalName}</>${josaYi} <G><b>${destCityName}</b></>에 <M>백성동원</>을 발동")

        return CommandResult(true, logs)
    }
}
