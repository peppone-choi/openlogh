package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.GeneralCommand
import com.openlogh.entity.General
import kotlin.random.Random

class 휴식(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : GeneralCommand(general, env, arg) {

    override val actionName = "휴식"

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0
    override fun getDuration() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        pushLog("아무것도 실행하지 않았습니다. <1>$date</>")
        return CommandResult(success = true, logs = logs)
    }
}
