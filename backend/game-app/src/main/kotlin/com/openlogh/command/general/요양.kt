package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.GeneralCommand
import com.openlogh.entity.General
import kotlin.random.Random

class 요양(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : GeneralCommand(general, env, arg) {

    override val actionName = "요양"

    // Legacy PHP: no constraints (empty list)
    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()

        pushLog("건강 회복을 위해 요양합니다. <1>$date</>")
        pushHistoryLog("건강 회복을 위해 요양합니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 요양합니다.")

        val exp = 10
        val ded = 7
        // Legacy PHP: sets injury = 0 (full heal)
        val injuryHeal = -general.injury.toInt()

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"injury":$injuryHeal,"experience":$exp,"dedication":$ded}}"""
        )
    }
}
