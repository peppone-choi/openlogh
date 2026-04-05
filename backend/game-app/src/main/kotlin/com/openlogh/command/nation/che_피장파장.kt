package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

private const val PRE_REQ_TURN = 1
private const val POST_REQ_TURN = 8
private const val STRATEGIC_GLOBAL_DELAY = 9

class che_피장파장(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "피장파장"

    override val fullConditionConstraints = listOf(
        OccupiedCity(),
        BeChief(),
        ExistsDestNation(),
        AvailableStrategicCommand(),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = PRE_REQ_TURN
    override fun getPostReqTurn() = POST_REQ_TURN

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")
        val dn = destFaction ?: return CommandResult(false, logs, "대상 국가 정보를 찾을 수 없습니다")
        val commandType = arg?.get("commandType") as? String ?: "전략"

        val expDed = 5 * (PRE_REQ_TURN + 1)
        general.experience += expDed
        general.dedication += expDed

        n.strategicCmdLimit = STRATEGIC_GLOBAL_DELAY.toShort()
        dn.strategicCmdLimit = STRATEGIC_GLOBAL_DELAY.toShort()

        // Broadcast to friendly generals
        val nationGenerals = services?.officerRepository?.findByFactionId(n.id) ?: emptyList()
        val destNationGenerals = services?.officerRepository?.findByFactionId(dn.id) ?: emptyList()

        pushLog("<G><b>${commandType}</b></> 전략의 $actionName 발동! <1>$date</>")
        pushHistoryLog("<G><b>${commandType}</b></> 전략의 $actionName 발동! <1>$date</>")
        pushGlobalLog("<Y>${general.name}</>${pickJosa(general.name, "이")} ${commandType} 전략의 $actionName 발동했습니다.")
        return CommandResult(true, logs)
    }
}
