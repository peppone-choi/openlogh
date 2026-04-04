package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.NationCommand
import com.openlogh.entity.General
import kotlin.random.Random

class Nation휴식(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : NationCommand(general, env, arg) {

    override val actionName = "휴식"

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        // Do nothing - just rest
        return CommandResult(success = true, logs = emptyList())
    }
}
