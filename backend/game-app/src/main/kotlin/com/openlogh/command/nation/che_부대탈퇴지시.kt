package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.NationCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.General
import kotlin.random.Random

class che_부대탈퇴지시(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : NationCommand(general, env, arg) {

    override val actionName = "부대 탈퇴 지시"

    override val fullConditionConstraints = listOf(
        NotBeNeutral(), BeChief(), ExistsDestGeneral(), FriendlyDestGeneral()
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val destGen = destGeneral ?: return CommandResult(false, logs, "대상 장수 정보를 찾을 수 없습니다")

        if (destGen.id == general.id) {
            return CommandResult(false, logs, "본인입니다")
        }

        val destGeneralName = destGen.name

        if (destGen.troopId == 0L) {
            pushLog("<Y>$destGeneralName</>은(는) 부대원이 아닙니다.")
            return CommandResult(true, logs, "부대원이 아닙니다")
        }
        if (destGen.troopId == destGen.id) {
            pushLog("<Y>$destGeneralName</>은(는) 부대장입니다.")
            return CommandResult(true, logs, "부대장입니다")
        }

        destGen.troopId = 0
        pushLog("<Y>$destGeneralName</>에게 부대 탈퇴를 지시했습니다.")
        pushHistoryLog("<Y>$destGeneralName</>에게 부대 탈퇴를 지시했습니다.")
        pushDestGeneralLog("부대 탈퇴를 지시받았습니다.")
        return CommandResult(true, logs)
    }
}
