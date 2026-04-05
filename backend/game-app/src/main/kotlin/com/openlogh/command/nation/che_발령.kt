package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

class che_발령(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "발령"

    override val fullConditionConstraints = listOf(
        BeChief(), NotBeNeutral(), OccupiedCity(), SuppliedCity(),
        ExistsDestGeneral(), FriendlyDestGeneral(),
        OccupiedDestCity(), SuppliedDestCity()
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val destGen = destOfficer ?: return CommandResult(false, logs, "대상 장수 정보를 찾을 수 없습니다")
        val dCity = destPlanet ?: return CommandResult(false, logs, "대상 도시 정보를 찾을 수 없습니다")

        if (destGen.id == general.id) {
            return CommandResult(false, logs, "본인입니다")
        }

        destGen.planetId = dCity.id
        destGen.fleetId = 0

        // Set last발령 meta (yearMonth value)
        val yearMonth = env.year * 12 + env.month - 1
        destGen.meta["last발령"] = yearMonth

        pushLog("<Y>${destGen.name}</>을 <G><b>${dCity.name}</b></>으로 발령했습니다. <1>$date</>")
        pushHistoryLog("<Y>${destGen.name}</>을 <G><b>${dCity.name}</b></>으로 발령했습니다. <1>$date</>")
        pushNationalHistoryLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <Y>${destGen.name}</>을 <G><b>${dCity.name}</b></>으로 발령했습니다.")
        pushDestGeneralLog("<G><b>${dCity.name}</b></>으로 발령되었습니다. <1>$date</>")
        return CommandResult(true, logs)
    }
}
