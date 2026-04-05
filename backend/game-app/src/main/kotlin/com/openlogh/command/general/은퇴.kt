package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

private const val REQ_AGE = 60

class 은퇴(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "은퇴"

    override val fullConditionConstraints = listOf(
        ReqGeneralAge(REQ_AGE),
    )

    override val minConditionConstraints = listOf(
        ReqGeneralAge(REQ_AGE),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 1
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()

        pushLog("은퇴하였습니다. <1>$date</>")
        pushHistoryLog("은퇴하였습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 은퇴했습니다.")

        // Legacy PHP: checks isunited==0 before CheckHall
        val isUnited = (env.gameStor["isunited"] as? Number)?.toInt() ?: 0

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"rebirth":true,"checkHall":${isUnited == 0},"tryUniqueLottery":true}"""
        )
    }
}
