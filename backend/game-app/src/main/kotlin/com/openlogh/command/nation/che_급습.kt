package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.math.max
import kotlin.random.Random

private const val STRATEGIC_GLOBAL_DELAY = 9
private const val TERM_REDUCE = 3

class che_급습(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "급습"

    override val fullConditionConstraints = listOf(
        OccupiedCity(), BeChief(), ExistsDestNation(),
        AvailableStrategicCommand()
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 40

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")
        val dn = destFaction ?: return CommandResult(false, logs, "대상 국가 정보를 찾을 수 없습니다")

        n.strategicCmdLimit = STRATEGIC_GLOBAL_DELAY.toShort()

        val diplomacyService = services!!.diplomacyService
        val relations = diplomacyService.getRelationsForNation(env.sessionId, dn.id)
        for (rel in relations) {
            if (rel.stateCode == "불가침") {
                rel.term = max(0, rel.term - TERM_REDUCE).toShort()
            }
        }

        general.experience += 50
        general.dedication += 50

        pushLog("$actionName 발동! <1>$date</>")
        pushHistoryLog("$actionName 발동! <1>$date</>")
        pushGlobalLog("<Y>${general.name}</>${pickJosa(general.name, "이")} $actionName 전략을 발동했습니다.")
        return CommandResult(true, logs)
    }
}
